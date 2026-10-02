ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Accounts created before verification existed came from development only; treat them as verified.
UPDATE users SET email_verified_at = created_at;

-- One-time tokens. Only the SHA-256 of the token is stored; the token itself exists only in the email.
-- Superseded tokens are expired (expires_at = now()), so consumed_at always means "used".
CREATE TABLE email_verification_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  BYTEA       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT email_verification_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT email_verification_tokens_hash_length_ck CHECK (octet_length(token_hash) = 32)
);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id, created_at);

CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  BYTEA       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT password_reset_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT password_reset_tokens_hash_length_ck CHECK (octet_length(token_hash) = 32)
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id, created_at);

-- Identity tables, read before any tenant exists: no RLS (see docs/security.md). No DELETE for the runtime.
GRANT SELECT, INSERT, UPDATE ON email_verification_tokens TO orcaai_runtime;
GRANT SELECT, INSERT, UPDATE ON password_reset_tokens TO orcaai_runtime;
