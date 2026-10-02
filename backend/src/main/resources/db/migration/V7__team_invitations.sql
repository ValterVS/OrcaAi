-- Exactly one OWNER per organization, enforced by the database (no transfer of ownership yet).
CREATE UNIQUE INDEX users_one_owner_per_organization_uk ON users (organization_id) WHERE role = 'OWNER';

-- Invitations to join an organization. Only the SHA-256 of the token is stored. An invitation can
-- never grant OWNER. At most one pending invitation per organization and address: inviting again
-- renews that row (new token, new expiry) instead of creating another.
CREATE TABLE organization_invitations (
    id                 UUID         PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organizations (id),
    email              VARCHAR(254) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    token_hash         BYTEA        NOT NULL,
    invited_by_user_id UUID         NOT NULL REFERENCES users (id),
    expires_at         TIMESTAMPTZ  NOT NULL,
    last_sent_at       TIMESTAMPTZ  NOT NULL,
    accepted_at        TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT organization_invitations_role_ck CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT organization_invitations_email_normalized_ck CHECK (email = lower(btrim(email))),
    CONSTRAINT organization_invitations_hash_uk UNIQUE (token_hash),
    CONSTRAINT organization_invitations_hash_length_ck CHECK (octet_length(token_hash) = 32),
    CONSTRAINT organization_invitations_final_state_ck CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

CREATE UNIQUE INDEX organization_invitations_pending_uk ON organization_invitations (organization_id, email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

-- Read by token before any tenant exists (accepting an invitation), so no RLS: like users and the
-- identity token tables. Administrative access is tenant-filtered by Hibernate (@TenantId).
GRANT SELECT, INSERT, UPDATE ON organization_invitations TO orcaai_runtime;
