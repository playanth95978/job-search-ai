#!/bin/sh
# Redéploiement du conteneur job-search-ai sur le VPS.
# Exécuté à distance (ssh ... 'sh -s' < deploy/vps-deploy.sh) ; toutes les valeurs
# arrivent par variables d'environnement, aucune n'est écrite dans ce fichier.
set -e

echo "==> Login GHCR"
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "==> Pull image $IMAGE"
docker pull "$IMAGE"

echo "==> Stop & remove old container"
docker stop job-search-ai 2>/dev/null || true
docker rm   job-search-ai 2>/dev/null || true

# On ne passe que les variables réellement définies, pour qu'un secret absent
# n'écrase jamais la valeur par défaut de l'application.
echo "==> Assemble optional env vars"
RUN_OPTS=""
for v in OLLAMA_TOKEN ADZUNA_APP_ID ADZUNA_APP_KEY SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD JHIPSTER_MAIL_FROM JHIPSTER_MAIL_BASE_URL; do
  eval "val=\$$v"
  [ -n "$val" ] && RUN_OPTS="$RUN_OPTS -e $v=$val"
done

echo "==> Start container"
docker run -d \
  --name job-search-ai \
  --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  -e SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
  -e SPRING_DATASOURCE_URL="$SPRING_DATASOURCE_URL" \
  -e SPRING_DATASOURCE_USERNAME="$SPRING_DATASOURCE_USERNAME" \
  -e SPRING_DATASOURCE_PASSWORD="$SPRING_DATASOURCE_PASSWORD" \
  -e SPRING_LIQUIBASE_URL="$SPRING_DATASOURCE_URL" \
  -e JWT_BASE64_SECRET="$JWT_BASE64_SECRET" \
  -e GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
  -e GOOGLE_CLIENT_SECRET="$GOOGLE_CLIENT_SECRET" \
  -e GOOGLE_REDIRECT_URI="$GOOGLE_REDIRECT_URI" \
  -e OLLAMA_BASE_URL="$OLLAMA_BASE_URL" \
  -e OLLAMA_MODEL="$OLLAMA_MODEL" \
  -e FRANCE_TRAVAIL_CLIENT_ID="$FRANCE_TRAVAIL_CLIENT_ID" \
  -e FRANCE_TRAVAIL_CLIENT_SECRET="$FRANCE_TRAVAIL_CLIENT_SECRET" \
  $RUN_OPTS \
  "$IMAGE"

echo "==> Nettoyage images obsolètes"
docker image prune -f

echo "==> Healthcheck"
sleep 15
docker inspect --format='{{.State.Status}}' job-search-ai
