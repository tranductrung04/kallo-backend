CREATE TABLE health_sync_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(20)   NOT NULL,
    data_type   VARCHAR(20)   NOT NULL,
    external_id VARCHAR(100)  NOT NULL,
    value       NUMERIC(12, 2) NOT NULL,
    value_min   NUMERIC(12, 2),
    value_max   NUMERIC(12, 2),
    data_origin VARCHAR(100),
    start_time  TIMESTAMPTZ   NOT NULL,
    end_time    TIMESTAMPTZ   NOT NULL,
    synced_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_health_sync_range CHECK (end_time >= start_time),
    CONSTRAINT uq_health_sync UNIQUE (user_id, provider, data_type, external_id)
);

CREATE INDEX idx_health_sync_user_time
    ON health_sync_logs (user_id, data_type, start_time);
