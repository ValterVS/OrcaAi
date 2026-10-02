#!/bin/sh
# Runs once, when the data volume is created. POSTGRES_USER owns the schema and runs Flyway.
# APP_DB_USER is the runtime login: a member of orcaai_runtime, which migrations grant per table.
# No default privileges are set, so new tables (and flyway_schema_history) are never exposed implicitly.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE orcaai_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS
  IN ROLE orcaai_runtime;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO orcaai_runtime;
SQL
