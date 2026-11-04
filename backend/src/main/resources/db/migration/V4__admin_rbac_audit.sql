-- V4: администраторы банка, RBAC, токены входа, аудит (раздел 8 ТЗ).

CREATE TABLE admin_users (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    -- логин глобально уникален: вход выполняется только по логину, без указания организации
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_logins   INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT admin_users_role_chk CHECK (role IN ('ADMIN', 'ANALYST')),
    CONSTRAINT admin_users_status_chk CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT admin_users_failed_chk CHECK (failed_logins >= 0)
);

-- Токены храним хешем, сам токен отдаётся один раз при входе.
CREATE TABLE admin_tokens (
    id              UUID PRIMARY KEY,
    admin_user_id   UUID         NOT NULL REFERENCES admin_users (id),
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ
);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    actor_type      VARCHAR(20)  NOT NULL,
    actor_id        VARCHAR(64)  NOT NULL,
    action          VARCHAR(60)  NOT NULL,
    target          VARCHAR(200),
    details         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_org_created_idx ON audit_log (organization_id, created_at DESC);
