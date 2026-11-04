-- V2: поведенческие события (раздел 4.4 ТЗ).
-- payload содержит только числа, перечисления и псевдонимы полей (проверяется на сервере).

CREATE TABLE behavioral_events (
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organizations (id),
    session_id       UUID        NOT NULL REFERENCES sessions (id),
    event_type       VARCHAR(40) NOT NULL,
    event_time       TIMESTAMPTZ NOT NULL,
    payload          JSONB       NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX behavioral_events_session_time_idx ON behavioral_events (session_id, event_time);
CREATE INDEX behavioral_events_org_received_idx ON behavioral_events (organization_id, received_at);
