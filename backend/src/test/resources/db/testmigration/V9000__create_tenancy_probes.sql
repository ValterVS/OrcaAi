-- Test-only table used to verify tenant isolation on a TenantOwnedEntity.
CREATE TABLE tenancy_probes (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id),
    label           VARCHAR(50) NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
