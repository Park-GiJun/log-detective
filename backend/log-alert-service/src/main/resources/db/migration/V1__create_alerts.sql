CREATE SCHEMA IF NOT EXISTS alert;

CREATE TABLE alert.alerts (
    id              BIGSERIAL PRIMARY KEY,
    alert_id        UUID         NOT NULL UNIQUE,
    fingerprint     VARCHAR(128) NOT NULL UNIQUE,
    rule_id         VARCHAR(32)  NOT NULL,
    severity        VARCHAR(16)  NOT NULL,
    summary         TEXT         NOT NULL,
    first_seen_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    hit_count       INT          NOT NULL DEFAULT 1,
    status          VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    dispatched_at   TIMESTAMPTZ,
    payload         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alerts_rule_severity   ON alert.alerts (rule_id, severity);
CREATE INDEX idx_alerts_status          ON alert.alerts (status);
CREATE INDEX idx_alerts_last_seen       ON alert.alerts (last_seen_at DESC);

CREATE TABLE alert.alert_detections (
    alert_id        UUID        NOT NULL REFERENCES alert.alerts(alert_id) ON DELETE CASCADE,
    detection_id    UUID        NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, detection_id)
);
