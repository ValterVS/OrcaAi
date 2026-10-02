-- The application connects as a login user that is a member of orcaai_runtime (created by database
-- provisioning, see docs/security.md). Privileges are granted per table and per operation; nothing is
-- granted on flyway_schema_history. Every new table must grant only what the application needs.
GRANT SELECT, INSERT, UPDATE ON organizations TO orcaai_runtime;
GRANT SELECT, INSERT, UPDATE ON users TO orcaai_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session TO orcaai_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session_attributes TO orcaai_runtime;
