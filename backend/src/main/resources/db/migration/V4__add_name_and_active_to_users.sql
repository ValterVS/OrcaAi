ALTER TABLE users ADD COLUMN name VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE users ALTER COLUMN name DROP DEFAULT;

ALTER TABLE users RENAME COLUMN enabled TO active;

-- Emails are stored normalized (trim + lowercase); uniqueness is enforced by users_email_uk.
ALTER TABLE users DROP CONSTRAINT users_email_lowercase_ck;
ALTER TABLE users ADD CONSTRAINT users_email_normalized_ck CHECK (email = lower(btrim(email)));
