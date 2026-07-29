#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=keycloak_password="$KEYCLOAK_DB_PASSWORD" <<'EOSQL'
CREATE USER keycloak WITH PASSWORD :'keycloak_password';
EOSQL
createdb --username "$POSTGRES_USER" --owner keycloak keycloak
