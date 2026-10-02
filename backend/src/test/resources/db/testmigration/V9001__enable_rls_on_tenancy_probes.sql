-- Reference implementation of the RLS design in docs/security.md, applied to the test table only.
GRANT SELECT, INSERT, UPDATE, DELETE ON tenancy_probes TO orcaai_runtime;

ALTER TABLE tenancy_probes ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenancy_probes FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenancy_probes
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid);
