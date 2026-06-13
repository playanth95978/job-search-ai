#!/bin/sh
# ===================================================================
# One-time TLS bootstrap for the production stack.
# Creates a temporary self-signed cert so nginx can start, then
# obtains a real Let's Encrypt certificate and reloads nginx.
#
# Run from the deploy/ directory AFTER filling .env and sed-ing the domain:
#   cd deploy && sh init-letsencrypt.sh
# ===================================================================
set -e

COMPOSE="docker compose -f docker-compose.prod.yml"

# Load DOMAIN / LETSENCRYPT_EMAIL from .env
if [ ! -f .env ]; then
  echo "ERROR: deploy/.env not found. Copy .env.example to .env and fill it." >&2
  exit 1
fi
# shellcheck disable=SC1091
. ./.env

if [ -z "$DOMAIN" ] || [ -z "$LETSENCRYPT_EMAIL" ]; then
  echo "ERROR: DOMAIN and LETSENCRYPT_EMAIL must be set in .env" >&2
  exit 1
fi

CERT_DIR="./nginx/certbot/conf/live/$DOMAIN"
WWW_DIR="./nginx/certbot/www"
mkdir -p "$CERT_DIR" "$WWW_DIR"

echo "### Creating a temporary self-signed certificate for $DOMAIN ..."
docker run --rm -v "$(pwd)/nginx/certbot/conf:/etc/letsencrypt" certbot/certbot \
  sh -c "openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=$DOMAIN'"

echo "### Starting nginx ..."
$COMPOSE up -d nginx
sleep 3

echo "### Removing the temporary certificate ..."
docker run --rm -v "$(pwd)/nginx/certbot/conf:/etc/letsencrypt" certbot/certbot \
  rm -rf "/etc/letsencrypt/live/$DOMAIN" \
         "/etc/letsencrypt/archive/$DOMAIN" \
         "/etc/letsencrypt/renewal/$DOMAIN.conf"

echo "### Requesting the real Let's Encrypt certificate for $DOMAIN ..."
$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    -d $DOMAIN \
    --email $LETSENCRYPT_EMAIL \
    --rsa-key-size 4096 \
    --agree-tos --no-eff-email --force-renewal" certbot

echo "### Reloading nginx with the real certificate ..."
$COMPOSE exec nginx nginx -s reload

echo "### Done. https://$DOMAIN should now be served with a valid certificate."
