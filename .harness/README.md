# Pipelines Harness

Deux pipelines, importés dans Harness via **Git Experience** (Pipeline → Import From Git,
en pointant sur ce dossier) :

| Fichier                 | Rôle                                                        | Déclencheur conseillé   |
| ----------------------- | ----------------------------------------------------------- | ----------------------- |
| `job_search_ai_ci.yaml` | lint + prettier + tests front (vitest) + tests back (Maven) | Pull Request sur `main` |
| `job_search_ai_cd.yaml` | build `-Pprod` + push Jib sur GHCR + redéploiement VPS      | Push sur `main`         |

Le script exécuté sur le VPS est [`deploy/vps-deploy.sh`](../deploy/vps-deploy.sh) — le même
enchaînement que `.github/workflows/deploy.yml`, factorisé pour être réutilisable.

## À renseigner à l'import

Les champs `<+input>` sont demandés à l'import / à l'exécution :

- `orgIdentifier`, `projectIdentifier` : org et projet Harness cibles
- `properties.ci.codebase.connectorRef` : connector GitHub sur ce dépôt
- `ghcr_owner` (CD) : propriétaire GitHub du package GHCR (l'image devient
  `ghcr.io/<owner>/job-search-ai`)

## Secrets Harness attendus

À créer dans le **Secret Manager** du projet, avec exactement ces identifiants :

`ghcr_user`, `ghcr_token`, `vps_host`, `vps_user`, `vps_ssh_key`,
`spring_datasource_url`, `spring_datasource_username`, `spring_datasource_password`,
`jwt_base64_secret`, `google_client_id`, `google_client_secret`, `google_redirect_uri`,
`ollama_base_url`, `ollama_model`, `ollama_token`,
`france_travail_client_id`, `france_travail_client_secret`,
`adzuna_app_id`, `adzuna_app_key`,
`spring_mail_host`, `spring_mail_port`, `spring_mail_username`, `spring_mail_password`

Ce sont les mêmes valeurs que les secrets GitHub Actions actuels. `ghcr_token` doit être un
PAT avec `write:packages` (Harness n'a pas d'équivalent du `GITHUB_TOKEN` automatique).

## Coexistence avec GitHub Actions

`.github/workflows/deploy.yml` reste en place et fait le même déploiement. Tant que les deux
sont actifs, un push sur `main` déclenche **deux** déploiements concurrents : désactive l'un
des deux (workflow GitHub désactivé, ou trigger Harness en `manual`) avant de brancher le
trigger sur push.
