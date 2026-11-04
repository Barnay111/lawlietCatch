-- V6: последний уровень риска хранится в сессии. Обзор дашборда читает по одной строке на сессию,
-- а не сортирует все оценки периода (DISTINCT ON по 200 тысячам строк был узким местом).
ALTER TABLE sessions
    ADD COLUMN latest_risk_level VARCHAR(20),
    ADD COLUMN latest_risk_at TIMESTAMPTZ;

-- Заполняем для уже существующих оценок.
UPDATE sessions s
SET latest_risk_level = x.risk_level,
    latest_risk_at = x.created_at
FROM (
    SELECT DISTINCT ON (session_id) session_id, risk_level, created_at
    FROM risk_assessments
    ORDER BY session_id, created_at DESC
) x
WHERE s.id = x.session_id;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_latest_risk_chk
        CHECK (latest_risk_level IS NULL OR latest_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'));

CREATE INDEX sessions_org_latest_risk_idx
    ON sessions (organization_id, latest_risk_at)
    INCLUDE (latest_risk_level);
