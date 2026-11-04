-- V3: поведенческие профили (раздел 4.5 ТЗ) и оценки риска (раздел 4.6 ТЗ).
-- Дополнительно сверх ТЗ: risk_assessments.recommendation и details — без них решение нельзя объяснить.

CREATE TABLE behavioral_profiles (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organizations (id),
    user_id          UUID        NOT NULL REFERENCES users (id),
    profile_version  INTEGER     NOT NULL DEFAULT 1,
    -- для каждого признака: {"mean": x, "m2": y, "n": k}; std = sqrt(m2 / (n - 1))
    features         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    session_count    INTEGER     NOT NULL DEFAULT 0,
    model_version    VARCHAR(40),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT behavioral_profiles_user_uk UNIQUE (organization_id, user_id),
    CONSTRAINT behavioral_profiles_session_count_chk CHECK (session_count >= 0)
);

CREATE TABLE risk_assessments (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id),
    session_id       UUID         NOT NULL REFERENCES sessions (id),
    -- NULL для UNKNOWN: без данных балл не выставляется
    risk_score       SMALLINT,
    risk_level       VARCHAR(20)  NOT NULL,
    recommendation   VARCHAR(40)  NOT NULL,
    reasons          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    details          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    model_version    VARCHAR(40)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT risk_assessments_score_chk CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100),
    CONSTRAINT risk_assessments_level_chk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'))
);
CREATE INDEX risk_assessments_session_idx ON risk_assessments (organization_id, session_id, created_at DESC);
