CREATE SCHEMA IF NOT EXISTS detection;

CREATE TABLE detection.detection_rules (
    id          VARCHAR(32) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    severity    VARCHAR(16) NOT NULL,
    config      JSONB,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE detection.detection_history (
    id              BIGSERIAL PRIMARY KEY,
    detection_id    UUID        NOT NULL UNIQUE,
    rule_id         VARCHAR(32) NOT NULL REFERENCES detection.detection_rules(id),
    event_id        UUID        NOT NULL,
    source          VARCHAR(128) NOT NULL,
    severity        VARCHAR(16) NOT NULL,
    reason          TEXT        NOT NULL,
    evidence        JSONB,
    detected_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_detection_history_rule  ON detection.detection_history (rule_id, detected_at DESC);
CREATE INDEX idx_detection_history_event ON detection.detection_history (event_id);
