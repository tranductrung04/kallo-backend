ALTER TABLE users
    ADD COLUMN height_cm NUMERIC(5, 1),
    ADD COLUMN weight_kg NUMERIC(5, 1),
    ADD COLUMN gender VARCHAR(20),
    ADD COLUMN birth_date DATE,
    ADD COLUMN target_weight_kg NUMERIC(5, 1),
    ADD COLUMN step_goal INTEGER;

ALTER TABLE users
    ADD CONSTRAINT chk_users_height_cm CHECK (height_cm IS NULL OR height_cm BETWEEN 50.0 AND 250.0),
    ADD CONSTRAINT chk_users_weight_kg CHECK (weight_kg IS NULL OR weight_kg BETWEEN 20.0 AND 300.0),
    ADD CONSTRAINT chk_users_target_weight_kg CHECK (target_weight_kg IS NULL OR target_weight_kg BETWEEN 20.0 AND 300.0),
    ADD CONSTRAINT chk_users_step_goal CHECK (step_goal IS NULL OR step_goal BETWEEN 1000 AND 100000);

ALTER TABLE activity_logs
    ADD COLUMN custom_name VARCHAR(100),
    ADD COLUMN manual_calories_burned NUMERIC(7, 1);

ALTER TABLE activity_logs
    ADD CONSTRAINT chk_activity_manual_calories CHECK (
        manual_calories_burned IS NULL OR manual_calories_burned BETWEEN 0 AND 999999.9
    );

CREATE TABLE body_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid UUID NOT NULL,
    waist_cm NUMERIC(5, 1) NOT NULL CHECK (waist_cm BETWEEN 20.0 AND 300.0),
    hip_cm NUMERIC(5, 1) NOT NULL CHECK (hip_cm BETWEEN 20.0 AND 300.0),
    logged_at TIMESTAMPTZ NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_body_metrics_client UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_body_metrics_user_logged
    ON body_metrics (user_id, logged_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_body_metrics_user_updated
    ON body_metrics (user_id, updated_at);
