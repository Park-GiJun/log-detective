CREATE SCHEMA IF NOT EXISTS ingest;

CREATE TABLE ingest.log_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID        NOT NULL UNIQUE,
    source          VARCHAR(128) NOT NULL,
    level           VARCHAR(16) NOT NULL,
    message         TEXT        NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    host            VARCHAR(255),
    ip              INET,
    user_id         VARCHAR(128),
    attributes      JSONB,
    ingested_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_log_events_timestamp   ON ingest.log_events (event_timestamp DESC);
CREATE INDEX idx_log_events_source      ON ingest.log_events (source, level);
CREATE INDEX idx_log_events_ip          ON ingest.log_events (ip);
CREATE INDEX idx_log_events_user_id     ON ingest.log_events (user_id);
