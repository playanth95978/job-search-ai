#!/usr/bin/env bash
#
# Déploie le portfolio statique (deploy/portfolio/) vers S3 + invalide le cache CloudFront.
#
# Prérequis :
#   - AWS CLI v2 installée et configurée (aws configure)
#   - un bucket S3 privé et une distribution CloudFront (origin access control)
#
# Usage :
#   export PORTFOLIO_BUCKET=aicarreerfinder-portfolio
#   export PORTFOLIO_DISTRIBUTION_ID=E123456ABCDEF
#   ./deploy/aws/portfolio-deploy.sh
#
set -euo pipefail

BUCKET="${PORTFOLIO_BUCKET:-}"
DISTRIBUTION_ID="${PORTFOLIO_DISTRIBUTION_ID:-}"
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../portfolio" && pwd)"

if [[ -z "$BUCKET" ]]; then
  echo "Erreur : PORTFOLIO_BUCKET n'est pas défini." >&2
  exit 1
fi
if ! command -v aws >/dev/null 2>&1; then
  echo "Erreur : AWS CLI introuvable. Voir https://aws.amazon.com/cli/" >&2
  exit 1
fi

echo "Source      : $SOURCE_DIR"
echo "Destination : s3://$BUCKET"

# Le HTML est versionné par le contenu, pas par l'URL : on le garde en cache court côté navigateur
# (5 min) et on s'appuie sur l'invalidation CloudFront pour la mise à jour immédiate.
# --delete garde le bucket aligné sur le dossier local ; README.md n'a rien à faire en ligne.
aws s3 sync "$SOURCE_DIR" "s3://$BUCKET" \
  --delete \
  --exclude "README.md" \
  --content-type "text/html; charset=utf-8" \
  --cache-control "public, max-age=300"

if [[ -n "$DISTRIBUTION_ID" ]]; then
  # Sans invalidation, CloudFront continue de servir l'ancienne version jusqu'à expiration du TTL.
  # Les 1000 premiers chemins invalidés par mois sont gratuits.
  echo "Invalidation du cache CloudFront ($DISTRIBUTION_ID)…"
  INVALIDATION_ID=$(aws cloudfront create-invalidation \
    --distribution-id "$DISTRIBUTION_ID" \
    --paths "/*" \
    --query 'Invalidation.Id' --output text)
  echo "Invalidation $INVALIDATION_ID créée (propagation ~1-2 min)."
else
  echo "PORTFOLIO_DISTRIBUTION_ID non défini : invalidation CloudFront ignorée."
  echo "Le site servira l'ancienne version jusqu'à expiration du cache."
fi

echo "Déploiement terminé."
