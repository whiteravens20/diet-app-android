#!/usr/bin/env bash
# Starts a diet-app instance for the screens' tests on an emulator: the API and the worker
# of the version contract/diet-app.env names (its image by digest; the compose file and the
# settings template of its commit), with secrets made for this run and the API published on
# 127.0.0.1:4000, which an emulator reaches as 10.0.2.2:4000. Then it loads the catalogue
# through the admin API, as an administrator does after an installation, so that plans can
# be made.
#
# Usage: .github/scripts/start-diet-app.sh <empty directory>
# `docker compose --env-file .env -f docker-compose.yml -f api-port.yml logs` in that
# directory shows what the instance did.
set -euo pipefail

# shellcheck source=contract/diet-app.env
. "$(dirname "$0")/../../contract/diet-app.env"

dir=$1
mkdir -p "$dir"
cd "$dir"

raw="https://raw.githubusercontent.com/whiteravens20/diet-app/$DIET_APP_COMMIT"
curl -fsSL "$raw/infra/docker-compose.prod.yml" -o docker-compose.yml
curl -fsSL "$raw/.env.example" -o .env.example

admin_password=$(openssl rand -hex 16)
sed -e "s|^JWT_ACCESS_SECRET=.*|JWT_ACCESS_SECRET=$(openssl rand -hex 32)|" \
    -e "s|^AI_KEY_ENCRYPTION_SECRET=.*|AI_KEY_ENCRYPTION_SECRET=$(openssl rand -hex 32)|" \
    -e "s|^DATA_ENCRYPTION_SECRET=.*|DATA_ENCRYPTION_SECRET=$(openssl rand -hex 32)|" \
    -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 16)|" \
    -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$admin_password|" \
    -e "s|^IMAGE_TAG=.*|IMAGE_TAG=$DIET_APP_IMAGE_TAG|" \
    .env.example > .env

cat > api-port.yml <<'YAML'
services:
  api:
    ports:
      - '127.0.0.1:4000:4000'
YAML

compose() { docker compose --env-file .env -f docker-compose.yml -f api-port.yml "$@"; }
compose up -d api worker

# localhost rather than 127.0.0.1: curl sends the admin session's cookie over plain HTTP
# to localhost only.
api=http://localhost:4000/api
for _ in $(seq 1 90); do
  curl -fsS "$api/health" > /dev/null 2>&1 && break
  sleep 2
done
if ! curl -fsS "$api/health" > /dev/null; then
  compose ps
  compose logs --no-color --tail 80 api
  echo "::error::The API did not come up."
  exit 1
fi

# The admin API takes requests only from the application's own origin.
origin="Origin: $(sed -n 's/^APP_URL=//p' .env)"
jar=$(mktemp)
printf '{"user":"%s","password":"%s"}' "$(sed -n 's/^ADMIN_USER=//p' .env)" "$admin_password" |
  curl -fsS -c "$jar" -b "$jar" -H "$origin" -H 'Content-Type: application/json' --data @- "$api/admin/session"
curl -fsS -c "$jar" -b "$jar" -H "$origin" -X POST "$api/admin/db/update" > /dev/null
status=unknown
for _ in $(seq 1 300); do
  state=$(curl -fsS -c "$jar" -b "$jar" -H "$origin" "$api/admin/db/update/status")
  status=$(jq -r .status <<< "$state")
  [ "$status" = "done" ] && break
  if [ "$status" = error ]; then
    echo "::error::The catalogue update failed: $(jq -r .error <<< "$state")"
    exit 1
  fi
  sleep 1
done
if [ "$status" != "done" ]; then
  echo "::error::The catalogue update did not finish: $state"
  exit 1
fi
curl -fsS -c "$jar" -b "$jar" -H "$origin" -X DELETE "$api/admin/session" > /dev/null
rm -f "$jar"

echo "diet-app $(curl -fsS "$api/config" | jq -r .instance.version) is up, with its catalogue loaded"
