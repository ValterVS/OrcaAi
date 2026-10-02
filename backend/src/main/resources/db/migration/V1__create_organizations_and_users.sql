CREATE TABLE organizations (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    enabled         BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_email_lowercase_ck CHECK (email = lower(email))
);

CREATE INDEX users_organization_id_idx ON users (organization_id);
