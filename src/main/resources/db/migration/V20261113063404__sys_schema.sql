-- SYS module schema

-- ---------------------------------------------------------------------------
-- Device tokens
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    data        JSONB,
    dedupe_key  VARCHAR(120),
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMPTZ,
    pushed_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notifications_dedupe
    ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id) WHERE is_read = FALSE AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Notification settings
-- ---------------------------------------------------------------------------
CREATE TABLE notification_settings (
    user_id                     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    language                    VARCHAR(5) NOT NULL DEFAULT 'vi',
    push_enabled                BOOLEAN NOT NULL DEFAULT TRUE,
    water_reminder_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    water_start_time            TIME NOT NULL DEFAULT '10:00',
    water_end_time              TIME NOT NULL DEFAULT '18:00',
    water_interval_minutes      INTEGER NOT NULL DEFAULT 240,
    meal_reminder_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    meal_breakfast_time         TIME,
    meal_lunch_time             TIME,
    meal_dinner_time             TIME,
    calorie_alert_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    calorie_alert_threshold_pct INTEGER NOT NULL DEFAULT 100,
    social_enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    dnd_enabled                 BOOLEAN NOT NULL DEFAULT FALSE,
    dnd_start_time              TIME NOT NULL DEFAULT '22:00',
    dnd_end_time                TIME NOT NULL DEFAULT '07:00',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_water_interval CHECK (water_interval_minutes BETWEEN 30 AND 720),
    CONSTRAINT chk_calorie_threshold CHECK (calorie_alert_threshold_pct BETWEEN 100 AND 200)
);

-- ---------------------------------------------------------------------------
-- Sync infrastructure
-- ---------------------------------------------------------------------------
CREATE SEQUENCE sync_seq START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION touch_sync()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.sync_seq := nextval('sync_seq');
  NEW.server_updated_at := clock_timestamp();
  RETURN NEW;
END;
$$;
