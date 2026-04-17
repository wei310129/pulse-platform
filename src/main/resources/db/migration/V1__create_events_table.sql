-- V1: 建立 events 資料表
-- event_id UNIQUE 為 Phase 2 冪等消費預留(ADR-005)

CREATE TABLE events (
    id          BIGSERIAL    PRIMARY KEY,
    event_id    VARCHAR(64)  UNIQUE NOT NULL,
    service_id  VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(32)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    latency_ms  INT,
    payload     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_service_created ON events (service_id, created_at DESC);
CREATE INDEX idx_events_status_created  ON events (status, created_at DESC) WHERE status = 'ERROR';
