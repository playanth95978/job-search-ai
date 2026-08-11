#!/usr/bin/env bash
#
# Mesure de bout en bout de la recherche d'offres, à niveaux de concurrence croissants.
#
# Complète le banc isolé (RerankerConcurrencyBenchmark, qui mesure le seul reranker) : ici on
# mesure la requête complète — embedding Ollama + pgvector + BM25 + reranking + sérialisation —
# telle que la subit un utilisateur.
#
# Usage :
#   ./scripts/loadtest-search.sh https://mon-vps.example <token-jwt> "développeur java"
#
# Le token s'obtient via POST /api/authenticate. Lancer depuis une machine PROCHE du serveur :
# sur une liaison lente, on mesurerait le réseau, pas le serveur.
#
set -euo pipefail

BASE_URL="${1:?usage: $0 <base-url> [jwt] [query] [limit]}"
TOKEN="${2:-}"
QUERY="${3:-développeur java spring}"
# limit=30 est le défaut du endpoint : le pipeline reranke limit*2 = 60 documents par requête.
LIMIT="${4:-30}"

# /jobs/search-smart et non /jobs/search : seul le premier emprunte le chemin coûteux
# (embedding + pgvector + BM25 + RRF + cross-encoder). /jobs/search interroge les boards externes
# en direct — on y mesurerait leur latence réseau, pas la charge CPU du serveur.
ENDPOINT="$BASE_URL/api/job-copilot/jobs/search-smart"
CONCURRENCY_LEVELS=(1 2 5 10)
REQUESTS_PER_LEVEL=20

command -v curl >/dev/null || { echo "curl requis" >&2; exit 1; }

# Le jeton peut être fourni en argument, ou obtenu ici via /api/authenticate.
# Le mot de passe est lu depuis l'environnement ou saisi sans écho : il n'apparaît jamais dans
# la ligne de commande, donc ni dans l'historique du shell ni dans la liste des processus.
if [ -z "$TOKEN" ]; then
  LOGIN="${JOBSEARCH_USER:-}"
  [ -n "$LOGIN" ] || read -r -p "Identifiant : " LOGIN
  PASSWORD="${JOBSEARCH_PASSWORD:-}"
  if [ -z "$PASSWORD" ]; then
    read -r -s -p "Mot de passe : " PASSWORD
    echo
  fi

  # Échappement JSON en pur bash : aucun sous-processus ne reçoit le mot de passe, il reste donc
  # invisible dans `ps`. Boucle caractère par caractère plutôt que ${v//motif/remplacement} : le
  # remplacement d'un antislash y est réduit silencieusement (vérifié en bash 5.2), ce qui
  # produirait un JSON invalide pour tout mot de passe contenant « \ ».
  # Les caractères de contrôle ne sont pas traités : `read -s` ne peut pas en saisir.
  json_string() {
    local input=$1 out='' char i
    for (( i = 0; i < ${#input}; i++ )); do
      char=${input:i:1}
      case $char in
        '\') out+='\'; out+='\' ;;
        '"') out+='\'; out+='"' ;;
        *)   out+=$char ;;
      esac
    done
    printf '"%s"' "$out"
  }

  TOKEN=$(
    printf '{"username":%s,"password":%s,"rememberMe":false}' \
      "$(json_string "$LOGIN")" "$(json_string "$PASSWORD")" |
      curl -s -X POST "$BASE_URL/api/authenticate" \
        -H 'Content-Type: application/json' --data-binary @- |
      sed -n 's/.*"id_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
  )
  unset PASSWORD

  [ -n "$TOKEN" ] || { echo "Authentification échouée : vérifiez l'identifiant et l'URL." >&2; exit 1; }
  echo "Authentification réussie."
fi

# Une requête, latence en millisecondes sur stdout ; 0 si la requête échoue (compté à part).
time_one_request() {
  local ms
  ms=$(curl -s -o /dev/null -w '%{time_total}' \
        -G --data-urlencode "query=$QUERY" --data-urlencode "limit=$LIMIT" \
        -H "Authorization: Bearer $TOKEN" \
        --max-time 120 \
        "$ENDPOINT" 2>/dev/null) || { echo "0"; return; }
  awk -v t="$ms" 'BEGIN { printf "%.0f", t * 1000 }'
}

printf '\n=== Charge sur %s ===\n' "$ENDPOINT"
printf 'requête : "%s" (limit=%s, soit %s documents rerankés) — %d requêtes par niveau\n\n' \
  "$QUERY" "$LIMIT" "$((LIMIT * 2))" "$REQUESTS_PER_LEVEL"
printf '%-12s %10s %10s %10s %12s %8s\n' "concurrence" "p50 (ms)" "p95 (ms)" "max (ms)" "débit (/s)" "échecs"

# Chauffe : la première requête paie le chargement des modèles et des caches JIT.
for _ in 1 2 3; do time_one_request >/dev/null; done

for concurrency in "${CONCURRENCY_LEVELS[@]}"; do
  results_dir=$(mktemp -d)
  wall_start=$(date +%s%N)

  launched=0
  while [ "$launched" -lt "$REQUESTS_PER_LEVEL" ]; do
    # Maintient exactement `concurrency` requêtes en vol.
    running=$(jobs -rp | wc -l)
    if [ "$running" -lt "$concurrency" ]; then
      time_one_request > "$results_dir/$launched" &
      launched=$((launched + 1))
    else
      wait -n 2>/dev/null || true
    fi
  done
  wait

  wall_ms=$(( ($(date +%s%N) - wall_start) / 1000000 ))

  # Les latences nulles signalent un échec : on les sort du calcul des percentiles.
  mapfile -t latencies < <(cat "$results_dir"/* | sort -n)
  failures=$(printf '%s\n' "${latencies[@]}" | grep -c '^0$' || true)
  mapfile -t ok < <(printf '%s\n' "${latencies[@]}" | grep -v '^0$' || true)
  rm -rf "$results_dir"

  count=${#ok[@]}
  if [ "$count" -eq 0 ]; then
    printf '%-12d %10s %10s %10s %12s %8d\n' "$concurrency" "-" "-" "-" "-" "$failures"
    continue
  fi

  p50=${ok[$(( (count * 50 + 99) / 100 - 1 ))]}
  p95=${ok[$(( (count * 95 + 99) / 100 - 1 ))]}
  max=${ok[$((count - 1))]}
  throughput=$(awk -v n="$count" -v w="$wall_ms" 'BEGIN { printf "%.2f", (w > 0) ? n * 1000 / w : 0 }')

  printf '%-12d %10d %10d %10d %12s %8d\n' "$concurrency" "$p50" "$p95" "$max" "$throughput" "$failures"
done

printf '\nLecture : si le p95 croît plus vite que la concurrence et que le débit stagne, le serveur\n'
printf 'est saturé en CPU — pas en attente d'"'"'E/S. C'"'"'est la signature du reranker qui monopolise les cœurs.\n'
