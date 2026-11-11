CREATE TABLE weight_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid UUID          NOT NULL,
    weight_kg   NUMERIC(5, 1) NOT NULL,
    logged_at   TIMESTAMPTZ   NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_weight_logs_client UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_weight_logs_user_logged
    ON weight_logs (user_id, logged_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_weight_logs_user_updated
    ON weight_logs (user_id, updated_at);
