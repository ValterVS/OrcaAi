-- First business table: tenant isolation by Hibernate (@TenantId) and, independently, by RLS.
CREATE TABLE customers (
    id              UUID          PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organizations (id),
    name            VARCHAR(150)  NOT NULL,
    phone           VARCHAR(40),
    email           VARCHAR(254),
    notes           VARCHAR(4000),
    archived_at     TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT customers_name_not_blank_ck CHECK (btrim(name) <> ''),
    CONSTRAINT customers_email_normalized_ck CHECK (email IS NULL OR email = lower(btrim(email)))
);

-- Every list is per organization, newest changes first.
CREATE INDEX customers_organization_updated_idx ON customers (organization_id, updated_at DESC, id DESC);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers FORCE ROW LEVEL SECURITY;

CREATE POLICY customers_tenant_isolation ON customers
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid);

-- No DELETE: customers are archived, never removed through the application.
GRANT SELECT, INSERT, UPDATE ON customers TO orcaai_runtime;
