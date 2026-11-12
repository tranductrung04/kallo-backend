CREATE TABLE device_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(10)  NOT NULL,
    locale       VARCHAR(10),
    app_version  VARCHAR(20),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_device_tokens_token ON device_tokens (token);
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
