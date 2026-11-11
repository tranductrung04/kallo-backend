CREATE TABLE activity_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid     UUID NOT NULL,
    activity_type   VARCHAR(40) NOT NULL REFERENCES activity_types (code),
    intensity       VARCHAR(10) NOT NULL DEFAULT 'MODERATE',
    met             NUMERIC(4, 1) NOT NULL,
    weight_kg_used  NUMERIC(5, 1) NOT NULL,
    duration_min    INTEGER NOT NULL CHECK (duration_min BETWEEN 1 AND 1440),
    distance_km     NUMERIC(6, 2) CHECK (distance_km >= 0),
    calories_burned NUMERIC(7, 1) NOT NULL CHECK (calories_burned >= 0),
    source          VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    external_id     VARCHAR(100),
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ NOT NULL,
    superseded_by   UUID REFERENCES activity_logs (id) ON DELETE SET NULL,
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_activity_logs_client UNIQUE (user_id, client_uuid)
);
CREATE UNIQUE INDEX uq_activity_logs_external
    ON activity_logs (user_id, source, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_activity_logs_user_started
    ON activity_logs (user_id, started_at DESC) WHERE deleted_at IS NULL;
