#!/usr/bin/env python3
"""Genere des recherches et des clics simules dans search_event / search_impression.

But : valider toute la plomberie du Learning to Rank — journalisation, extraction, entrainement,
service — avant d'avoir un seul utilisateur reel. Les lignes produites portent `simulated = true`
et ne doivent jamais etre melangees a des clics reels dans un meme entrainement : un modele
entraine dessus apprend a imiter le juge, pas les utilisateurs.

Les notes de pertinence viennent du cache du harnais Langflow (`qrels_cache.json`), pas d'appels
LLM : le quota Mistral est a zero sans moyen de paiement, et ces notes sont deja payees.

Le clic n'est pas une copie de la note. Un utilisateur reel clique en fonction de deux choses :
la pertinence percue *et* la position dans la liste. On modelise donc

    P(clic) = P(examine la position r) x P(clic | pertinence)

avec P(examine) qui decroit avec le rang. Sans ce biais de position, un modele entraine sur ces
donnees n'apprendrait rien de ce qu'il doit precisement corriger.

Usage :
    python scripts/simulate_search_clicks.py \
        --dsn postgresql://job-search-ai:MDP@localhost:5433/job-search-ai \
        --qrels ~/Documents/langflow-components/jobsearch/qrels_cache.json \
        --dry-run
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

import psycopg2
import psycopg2.extras

# P(examine | rang) : decroissance geometrique, calee sur l'allure des etudes de biais de position
# (le premier resultat est vu presque toujours, le dixieme environ une fois sur cinq).
POSITION_EXAMINE = [0.95, 0.85, 0.72, 0.60, 0.50, 0.42, 0.35, 0.29, 0.24, 0.20]

# P(clic | note du juge). Une note 0 garde une probabilite non nulle : un utilisateur clique parfois
# a tort, et un modele qui n'a jamais vu de clic bruite devient trop confiant.
CLICK_GIVEN_GRADE = {0: 0.02, 1: 0.25, 2: 0.65}

# P(candidature | clic, note). Rare, et concentre sur les offres vraiment pertinentes.
APPLY_GIVEN_CLICK = {0: 0.0, 1: 0.05, 2: 0.20}

VECTOR_SQL = """
SELECT id FROM job_offer
 WHERE embedding IS NOT NULL AND (expires_at IS NULL OR expires_at > now())
   AND (%(loc)s IS NULL OR location ILIKE %(loc_like)s OR country ILIKE %(loc_like)s)
 ORDER BY embedding <=> (SELECT embedding FROM job_offer WHERE id = %(seed)s)
 LIMIT %(limit)s
"""

BM25_SQL = """
SELECT id, paradedb.score(id) AS score FROM job_offer
 WHERE search_text_plain ||| %(query)s AND (expires_at IS NULL OR expires_at > now())
   AND (%(loc)s IS NULL OR location ILIKE %(loc_like)s OR country ILIKE %(loc_like)s)
 ORDER BY paradedb.score(id) DESC
 LIMIT %(limit)s
"""


def load_qrels(path: Path) -> dict[str, dict[str, int]]:
    """Cle du cache : "<requete>::<uuid>". On regroupe par requete."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    grouped: dict[str, dict[str, int]] = {}
    for key, grade in raw.items():
        query, _, offer_id = key.rpartition("::")
        if not query or not offer_id:
            continue
        grouped.setdefault(query, {})[offer_id] = int(grade)
    return grouped


def rrf(ranked_lists: list[list[str]], k: int, limit: int) -> list[tuple[str, float]]:
    fused: dict[str, float] = {}
    for rows in ranked_lists:
        for rank, offer_id in enumerate(rows, start=1):
            fused[offer_id] = fused.get(offer_id, 0.0) + 1.0 / (k + rank)
    return sorted(fused.items(), key=lambda kv: kv[1], reverse=True)[:limit]


def retrieve(cur, query: str, location: str | None, graded_ids: list[str], limit: int):
    """Reconstitue les deux bras et leurs rangs.

    Le bras vectoriel part de l'embedding d'une offre deja notee comme graine, plutot que d'appeler
    un modele d'embedding : le script reste sans dependance ML et produit malgre tout un voisinage
    realiste, ce qui suffit a exercer la plomberie.
    """
    loc = {"loc": location or None, "loc_like": f"%{location}%" if location else None}

    seed = graded_ids[0]
    cur.execute(VECTOR_SQL, {"seed": seed, "limit": limit * 3, **loc})
    vector_ids = [str(r[0]) for r in cur.fetchall()]

    try:
        cur.execute(BM25_SQL, {"query": query, "limit": limit * 3, **loc})
        bm25_rows = cur.fetchall()
    except psycopg2.Error:
        cur.connection.rollback()
        bm25_rows = []
    bm25_ids = [str(r[0]) for r in bm25_rows]
    bm25_scores = {str(r[0]): float(r[1]) for r in bm25_rows}

    vector_ranks = {oid: i + 1 for i, oid in enumerate(vector_ids)}
    bm25_ranks = {oid: i + 1 for i, oid in enumerate(bm25_ids)}
    fused = rrf([vector_ids, bm25_ids], k=60, limit=limit)
    return fused, vector_ranks, bm25_ranks, bm25_scores


def simulate_clicks(fused, grades: dict[str, int], rng: random.Random):
    """Rend, pour chaque offre du classement, (cliquee, candidature)."""
    outcomes = []
    for rank, (offer_id, _score) in enumerate(fused, start=1):
        examine = POSITION_EXAMINE[rank - 1] if rank <= len(POSITION_EXAMINE) else 0.15
        grade = grades.get(offer_id, 0)
        clicked = rng.random() < examine * CLICK_GIVEN_GRADE[grade]
        applied = clicked and rng.random() < APPLY_GIVEN_CLICK[grade]
        outcomes.append((clicked, applied))
    return outcomes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dsn", required=True)
    parser.add_argument("--qrels", required=True, type=Path)
    parser.add_argument("--limit", type=int, default=10, help="Resultats par recherche")
    parser.add_argument("--repeats", type=int, default=20, help="Recherches simulees par requete")
    parser.add_argument("--seed", type=int, default=42, help="Graine, pour un tirage reproductible")
    parser.add_argument("--dry-run", action="store_true", help="N'ecrit rien, affiche le resume")
    args = parser.parse_args()

    qrels = load_qrels(args.qrels)
    if not qrels:
        print("Aucune note dans le cache : lance d'abord le harnais d'ablation.", file=sys.stderr)
        return 1

    rng = random.Random(args.seed)
    conn = psycopg2.connect(args.dsn)
    events = impressions = clicks = applications = 0

    try:
        with conn.cursor() as cur:
            for raw_query, grades in qrels.items():
                intent, _, location = raw_query.partition("|")
                intent, location = intent.strip(), location.strip() or None
                graded_ids = list(grades)

                for _ in range(args.repeats):
                    fused, vector_ranks, bm25_ranks, bm25_scores = retrieve(
                        cur, intent, location, graded_ids, args.limit
                    )
                    if not fused:
                        continue
                    outcomes = simulate_clicks(fused, grades, rng)

                    events += 1
                    impressions += len(fused)
                    clicks += sum(1 for c, _ in outcomes if c)
                    applications += sum(1 for _, a in outcomes if a)

                    if args.dry_run:
                        continue

                    cur.execute(
                        """
                        INSERT INTO search_event (id, user_id, query, location, mode, origin, result_count, created_at)
                        VALUES (gen_random_uuid(), NULL, %s, %s, 'hybrid', 'simulation', %s, now())
                        RETURNING id
                        """,
                        (intent[:1000], location, len(fused)),
                    )
                    event_id = cur.fetchone()[0]

                    psycopg2.extras.execute_batch(
                        cur,
                        """
                        INSERT INTO search_impression
                            (id, search_event_id, job_offer_id, rank, vector_rank, bm25_rank,
                             bm25_score, fused_score, clicked_at, applied, simulated, created_at)
                        VALUES (gen_random_uuid(), %s, %s, %s, %s, %s, %s, %s,
                                CASE WHEN %s THEN now() ELSE NULL END, %s, true, now())
                        """,
                        [
                            (
                                event_id,
                                offer_id,
                                rank,
                                vector_ranks.get(offer_id),
                                bm25_ranks.get(offer_id),
                                bm25_scores.get(offer_id),
                                score,
                                clicked,
                                applied,
                            )
                            for rank, ((offer_id, score), (clicked, applied)) in enumerate(
                                zip(fused, outcomes), start=1
                            )
                        ],
                    )
        if not args.dry_run:
            conn.commit()
    finally:
        conn.close()

    mode = "SIMULATION (rien ecrit)" if args.dry_run else "ECRIT EN BASE"
    print(f"{mode}")
    print(f"  requetes distinctes : {len(qrels)}")
    print(f"  evenements          : {events}")
    print(f"  impressions         : {impressions}")
    print(f"  clics               : {clicks} ({clicks / impressions:.1%})" if impressions else "  clics : 0")
    print(f"  candidatures        : {applications}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
