-- Mirrors production provisioning (docs/security.md) inside the throwaway test container.
-- The passwords below only exist in that container.
CREATE ROLE orcaai_owner LOGIN PASSWORD 'owner' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE orcaai_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE orcaai_app LOGIN PASSWORD 'app' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS IN ROLE orcaai_runtime;

ALTER SCHEMA public OWNER TO orcaai_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO orcaai_runtime;
