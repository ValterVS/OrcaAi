-- Reference implementation of the RLS design in docs/security.md, applied to the test table only.
-- Tests connect as a superuser, which always bypasses RLS; RowLevelSecurityIntegrationTest switches
-- to this non-owner role to exercise the policy.
CREATE ROLE rls_probe_app NOLOGIN NOSUPERUSER NOBYPASSRLS;
GRANT USAGE ON SCHEMA public TO rls_probe_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenancy_probes TO rls_probe_app;

ALTER TABLE tenancy_probes ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenancy_probes FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenancy_probes
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid);
