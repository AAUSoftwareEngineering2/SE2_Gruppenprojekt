#!/bin/sh
set -eu

role_exists() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -tAc \
    "SELECT 1 FROM pg_roles WHERE rolname = '$1'" | grep -q 1
}

database_exists() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = '$1'" | grep -q 1
}

if ! role_exists "$APP_DB_USER"; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -c "CREATE ROLE \"$APP_DB_USER\" LOGIN PASSWORD '$APP_DB_PASSWORD';"
fi

if ! database_exists "$STAGING_DB_NAME"; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -c "CREATE DATABASE \"$STAGING_DB_NAME\" OWNER \"$APP_DB_USER\";"
fi

if ! database_exists "$PRODUCTION_DB_NAME"; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -c "CREATE DATABASE \"$PRODUCTION_DB_NAME\" OWNER \"$APP_DB_USER\";"
fi
