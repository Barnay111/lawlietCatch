-- V5: индекс под окна по времени в дашборде (агрегаты обзора, лента оценок).
-- INCLUDE: session_id и risk_level в индексе, чтобы агрегаты читались без обращения к таблице.
CREATE INDEX risk_assessments_org_created_idx
    ON risk_assessments (organization_id, created_at)
    INCLUDE (session_id, risk_level);
