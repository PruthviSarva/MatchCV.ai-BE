-- Initial schema: accounts, sessions, subscriptions, resumes, tailorings.
--
-- PostgreSQL-specific. Tests run on H2 with Hibernate's generated schema
-- instead (see src/test/resources/application-test.yml); this file is what the
-- real database actually gets, and `ddl-auto: validate` on boot is what proves
-- the entities and this SQL agree.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT users_email_unique UNIQUE (email)
);

-- Opaque, hashed refresh tokens. token_hash is hex SHA-256, hence exactly 64
-- characters; the unique index is what makes rotation lookups cheap.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

-- One row per user. The billing period is stored rather than derived from the
-- calendar, so a plan bought on the 20th gives a full month and a real
-- gateway's own period boundaries can be written here unchanged.
CREATE TABLE subscriptions (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan                     VARCHAR(16)  NOT NULL,
    status                   VARCHAR(16)  NOT NULL,
    current_period_start     TIMESTAMPTZ  NOT NULL,
    current_period_end       TIMESTAMPTZ  NOT NULL,
    provider_customer_id     VARCHAR(128),
    provider_subscription_id VARCHAR(128),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT subscriptions_user_unique UNIQUE (user_id),
    CONSTRAINT subscriptions_plan_check CHECK (plan IN ('FREE', 'PRO')),
    CONSTRAINT subscriptions_status_check
        CHECK (status IN ('ACTIVE', 'CANCELED', 'EXPIRED')),
    CONSTRAINT subscriptions_period_check CHECK (current_period_end > current_period_start)
);

-- Uploaded resumes, stored as extracted text. The original file is not kept:
-- the text is all the rewriting needs, and the output is a fresh document.
CREATE TABLE base_resumes (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label           VARCHAR(255) NOT NULL,
    source_filename VARCHAR(255),
    source_type     VARCHAR(16)  NOT NULL,
    extracted_text  TEXT         NOT NULL,
    character_count INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT base_resumes_source_type_check
        CHECK (source_type IN ('pdf', 'docx', 'text'))
);

CREATE INDEX base_resumes_user_created_idx ON base_resumes (user_id, created_at DESC);

-- One tailoring. base_resume_id is nullable and set NULL on delete: removing a
-- base resume must not destroy the tailored copies already produced from it.
CREATE TABLE tailored_resumes (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    base_resume_id  BIGINT       REFERENCES base_resumes (id) ON DELETE SET NULL,
    job_title       VARCHAR(255),
    job_description TEXT         NOT NULL,
    resume_json     TEXT         NOT NULL,
    match_score     INTEGER      NOT NULL,
    model_name      VARCHAR(64)  NOT NULL,
    -- The billing period this tailoring was spent in, stamped at creation.
    -- Stamped rather than derived from created_at because the system clock does
    -- not always advance between two reads: a tailoring run moments before an
    -- upgrade would otherwise land on the exact instant the new period starts
    -- and be charged to the plan the user just bought.
    billing_period_start TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tailored_resumes_score_check CHECK (match_score BETWEEN 0 AND 100)
);

-- The history list is "this user's tailorings, newest first".
CREATE INDEX tailored_resumes_user_created_idx ON tailored_resumes (user_id, created_at DESC);

-- The quota count is an equality match on (user, period), so it gets its own
-- index rather than relying on a prefix of the one above.
CREATE INDEX tailored_resumes_user_period_idx
    ON tailored_resumes (user_id, billing_period_start);
