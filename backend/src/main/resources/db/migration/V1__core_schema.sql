-- V1: базовая схема MVP (разделы 4.1–4.3 ТЗ) и ключи API.

CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT organizations_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- Ключи интеграции. Храним только SHA-256 от ключа, сам ключ показывается один раз.
-- Публичный ключ SDK (PUBLIC_CLIENT) идентифицирует интеграцию и не является секретом;
-- серверный ключ (SERVER) нужен бэкенду банка для вызова Risk API.
CREATE TABLE api_keys (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organizations (id),
    key_type         VARCHAR(20) NOT NULL,
    key_hash         VARCHAR(64) NOT NULL UNIQUE,
    key_prefix       VARCHAR(16) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at       TIMESTAMPTZ,
    CONSTRAINT api_keys_type_chk CHECK (key_type IN ('PUBLIC_CLIENT', 'SERVER'))
);
CREATE INDEX api_keys_org_idx ON api_keys (organization_id);

-- Анализируемые пользователи. Только псевдоним из системы клиента (раздел 4.2).
CREATE TABLE users (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL REFERENCES organizations (id),
    external_user_id  VARCHAR(128) NOT NULL,
    profile_status    VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_org_external_uk UNIQUE (organization_id, external_user_id),
    CONSTRAINT users_profile_status_chk CHECK (profile_status IN ('NEW', 'LEARNING', 'READY'))
);

-- Сессии пользователя (раздел 4.3).
CREATE TABLE sessions (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organizations (id),
    user_id          UUID        NOT NULL REFERENCES users (id),
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    device_type      VARCHAR(30) NOT NULL,
    sdk_version      VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    event_count      INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT sessions_device_chk CHECK (device_type IN ('DESKTOP', 'MOBILE', 'TABLET')),
    CONSTRAINT sessions_status_chk CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT sessions_event_count_chk CHECK (event_count >= 0)
);
CREATE INDEX sessions_org_user_idx ON sessions (organization_id, user_id);
CREATE INDEX sessions_org_started_idx ON sessions (organization_id, started_at DESC);
