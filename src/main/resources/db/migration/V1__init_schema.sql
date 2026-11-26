-- ============================================================
-- Kallo Backend — Consolidated Initial Schema (V1)
-- ============================================================

-- ============================================================
-- Extensions, Functions & Sequences
-- ============================================================
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text AS $$
  SELECT public.unaccent('public.unaccent', $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

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

-- ============================================================
-- AUTH module: users, refresh_tokens, password_reset_tokens,
-- email_verification_tokens
-- ============================================================
CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash  VARCHAR(100),
    google_sub     VARCHAR(255),
    auth_provider  VARCHAR(20) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    avatar_url     VARCHAR(500),
    role           VARCHAR(20) NOT NULL DEFAULT 'USER',
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    timezone       VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_credential CHECK (
        password_hash IS NOT NULL
        OR google_sub IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub)
    WHERE google_sub IS NOT NULL;

CREATE INDEX idx_users_display_name_prefix
    ON users (lower(display_name) text_pattern_ops);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_user
    ON password_reset_tokens (user_id, created_at DESC);

CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_user
    ON email_verification_tokens (user_id, created_at DESC);

-- ============================================================
-- SYS module: device_tokens, notifications, notification_settings
-- ============================================================
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

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    data       JSONB,
    dedupe_key VARCHAR(120),
    is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMPTZ,
    pushed_at  TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notifications_dedupe
    ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id) WHERE is_read = FALSE AND deleted_at IS NULL;

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
    meal_dinner_time            TIME,
    calorie_alert_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    calorie_alert_threshold_pct INTEGER NOT NULL DEFAULT 100,
    social_enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    chat_reply_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    dnd_enabled                 BOOLEAN NOT NULL DEFAULT FALSE,
    dnd_start_time              TIME NOT NULL DEFAULT '22:00',
    dnd_end_time                TIME NOT NULL DEFAULT '07:00',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_water_interval CHECK (water_interval_minutes BETWEEN 30 AND 720),
    CONSTRAINT chk_calorie_threshold CHECK (calorie_alert_threshold_pct BETWEEN 100 AND 200)
);

-- ============================================================
-- FOOD module: ingredients, foods, food_ingredients, barcode_misses
-- ============================================================
CREATE TABLE ingredients (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(50),
    name              VARCHAR(255) NOT NULL,
    name_unaccented   VARCHAR(255) GENERATED ALWAYS AS (immutable_unaccent(lower(name))) STORED,

    calories_per_100g NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (calories_per_100g BETWEEN 0 AND 950),
    protein_per_100g  NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (protein_per_100g >= 0),
    carbs_per_100g    NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_per_100g >= 0),
    fat_per_100g      NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fat_per_100g >= 0),
    sugar_per_100g    NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_per_100g >= 0),
    fiber_per_100g    NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_per_100g >= 0),

    is_allergen       BOOLEAN NOT NULL DEFAULT FALSE,
    source            VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    owner_user_id     UUID REFERENCES users (id) ON DELETE CASCADE,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ingredients_macros_100g CHECK (protein_per_100g + carbs_per_100g + fat_per_100g <= 105.0)
);

CREATE UNIQUE INDEX uq_ingredients_system_code
    ON ingredients (code)
    WHERE code IS NOT NULL AND owner_user_id IS NULL;

CREATE INDEX idx_ingredients_allergen
    ON ingredients (is_allergen)
    WHERE is_active = TRUE AND owner_user_id IS NULL;

CREATE INDEX idx_ingredients_name_trgm
    ON ingredients USING GIN (name_unaccented gin_trgm_ops);

CREATE INDEX idx_ingredients_owner
    ON ingredients (owner_user_id) WHERE is_active = TRUE;

CREATE TABLE foods (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(255) NOT NULL,
    name_unaccented       VARCHAR(255) GENERATED ALWAYS AS (immutable_unaccent(lower(name))) STORED,
    category              VARCHAR(50) NOT NULL,
    default_serving_name  VARCHAR(100) NOT NULL,
    default_serving_grams NUMERIC(6, 1) NOT NULL CHECK (default_serving_grams > 0),

    calories_per_100g     NUMERIC(6, 1) NOT NULL CHECK (calories_per_100g >= 0),
    protein_per_100g      NUMERIC(5, 1) NOT NULL CHECK (protein_per_100g >= 0),
    carbs_per_100g        NUMERIC(5, 1) NOT NULL CHECK (carbs_per_100g >= 0),
    fat_per_100g          NUMERIC(5, 1) NOT NULL CHECK (fat_per_100g >= 0),
    sugar_per_100g        NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_per_100g >= 0),
    fiber_per_100g        NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_per_100g >= 0),

    calories_per_serving  INTEGER NOT NULL CHECK (calories_per_serving >= 0),
    protein_per_serving   NUMERIC(5, 1) NOT NULL CHECK (protein_per_serving >= 0),
    carbs_per_serving     NUMERIC(5, 1) NOT NULL CHECK (carbs_per_serving >= 0),
    fat_per_serving       NUMERIC(5, 1) NOT NULL CHECK (fat_per_serving >= 0),
    sugar_per_serving     NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_per_serving >= 0),
    fiber_per_serving     NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_per_serving >= 0),

    image_url             TEXT,
    barcode               VARCHAR(64),
    source                VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    vietfood_class_id     INTEGER UNIQUE CHECK (vietfood_class_id BETWEEN 0 AND 67 AND vietfood_class_id <> 27),
    suitable_meal_types   JSONB NOT NULL DEFAULT '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb,
    reference_price_vnd   INTEGER CHECK (reference_price_vnd IS NULL OR reference_price_vnd >= 0),
    feature_vector        JSONB DEFAULT '[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]'::jsonb,

    is_verified           BOOLEAN NOT NULL DEFAULT TRUE,
    owner_user_id         UUID REFERENCES users (id) ON DELETE CASCADE,
    deleted_at            TIMESTAMPTZ,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_foods_shared_barcode
    ON foods (barcode)
    WHERE barcode IS NOT NULL AND owner_user_id IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_foods_name_trgm
    ON foods USING GIN (name_unaccented gin_trgm_ops);

CREATE INDEX idx_foods_owner
    ON foods (owner_user_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_foods_category
    ON foods (category) WHERE deleted_at IS NULL;

CREATE INDEX idx_foods_vietfood_class
    ON foods (vietfood_class_id) WHERE vietfood_class_id IS NOT NULL;

CREATE TABLE food_ingredients (
    food_id        UUID NOT NULL REFERENCES foods (id) ON DELETE CASCADE,
    ingredient_id  UUID NOT NULL REFERENCES ingredients (id) ON DELETE RESTRICT,
    quantity_grams NUMERIC(7, 1) CHECK (quantity_grams IS NULL OR quantity_grams > 0),
    PRIMARY KEY (food_id, ingredient_id)
);

CREATE INDEX idx_food_ingredients_ingredient ON food_ingredients (ingredient_id);

CREATE TABLE barcode_misses (
    barcode    VARCHAR(64) PRIMARY KEY,
    queried_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '7 days')
);

CREATE INDEX idx_barcode_misses_expiry ON barcode_misses (expires_at);

-- ============================================================
-- PROFILE module: user_health_profiles, chronic_conditions,
-- user_chronic_conditions, user_allergies
-- ============================================================
CREATE TABLE user_health_profiles (
    user_id                        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    gender                         VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    dob                            DATE NOT NULL,
    height_cm                      NUMERIC(5, 2) NOT NULL CHECK (height_cm BETWEEN 50.0 AND 250.0),
    weight_kg                      NUMERIC(5, 2) NOT NULL CHECK (weight_kg BETWEEN 20.0 AND 300.0),
    activity_level                 VARCHAR(20) NOT NULL CHECK (activity_level IN ('SEDENTARY', 'LIGHTLY_ACTIVE', 'MODERATELY_ACTIVE', 'VERY_ACTIVE', 'EXTRA_ACTIVE')),
    goal_type                      VARCHAR(20) NOT NULL CHECK (goal_type IN ('LOSE_WEIGHT', 'MAINTAIN_WEIGHT', 'GAIN_WEIGHT')),
    target_weight_kg               NUMERIC(5, 2) CHECK (target_weight_kg IS NULL OR (target_weight_kg BETWEEN 20.0 AND 300.0)),
    weekly_rate_kg                 NUMERIC(3, 2) NOT NULL DEFAULT 0.00 CHECK (weekly_rate_kg BETWEEN 0.00 AND 1.50),
    bmr                            NUMERIC(6, 2) NOT NULL CHECK (bmr > 0),
    tdee                           NUMERIC(6, 2) NOT NULL CHECK (tdee > 0),
    daily_calorie_goal             NUMERIC(6, 2) NOT NULL CHECK (daily_calorie_goal > 0),
    carb_grams                     NUMERIC(6, 2) NOT NULL CHECK (carb_grams >= 0),
    protein_grams                  NUMERIC(6, 2) NOT NULL CHECK (protein_grams >= 0),
    fat_grams                      NUMERIC(6, 2) NOT NULL CHECK (fat_grams >= 0),
    water_liters                   NUMERIC(4, 2) NOT NULL CHECK (water_liters >= 0),
    step_goal                      INTEGER,
    recalculation_recommended      BOOLEAN NOT NULL DEFAULT FALSE,
    recommended_tdee               NUMERIC(6, 2),
    recommended_daily_calorie_goal NUMERIC(6, 2),
    latest_logged_weight_kg        NUMERIC(5, 2),
    rate_clamped                   BOOLEAN NOT NULL DEFAULT FALSE,
    preference_vector              JSONB NOT NULL DEFAULT '[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]'::jsonb,
    version                        BIGINT NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_target_weight_required CHECK (
        (goal_type = 'MAINTAIN_WEIGHT' AND target_weight_kg IS NULL AND weekly_rate_kg = 0.00) OR
        (goal_type IN ('LOSE_WEIGHT', 'GAIN_WEIGHT') AND target_weight_kg IS NOT NULL AND weekly_rate_kg > 0.00)
    ),
    CONSTRAINT chk_user_health_profiles_step_goal CHECK (
        step_goal IS NULL OR step_goal BETWEEN 1000 AND 100000
    )
);

CREATE TABLE chronic_conditions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_chronic_conditions (
    user_id      UUID NOT NULL REFERENCES user_health_profiles (user_id) ON DELETE CASCADE,
    condition_id UUID NOT NULL REFERENCES chronic_conditions (id),
    diagnosed_at DATE,
    note         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, condition_id)
);

CREATE INDEX idx_user_chronic_user_id ON user_chronic_conditions (user_id);

CREATE TABLE user_allergies (
    user_id       UUID NOT NULL REFERENCES user_health_profiles (user_id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL,
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, ingredient_id),
    CONSTRAINT fk_user_allergies_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_allergies_user_id ON user_allergies (user_id);

-- ============================================================
-- ACT module: weight_logs, activity_types, activity_logs,
-- health_sync_logs, body_metrics
-- ============================================================
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

CREATE TABLE activity_types (
    code         VARCHAR(40) PRIMARY KEY,
    name_vi      VARCHAR(100) NOT NULL,
    category     VARCHAR(20) NOT NULL,
    met_light    NUMERIC(4, 1),
    met_moderate NUMERIC(4, 1) NOT NULL CHECK (met_moderate > 0 AND met_moderate <= 25),
    met_vigorous NUMERIC(4, 1),
    has_distance BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE activity_logs (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid            UUID NOT NULL,
    activity_type          VARCHAR(40) NOT NULL REFERENCES activity_types (code),
    intensity              VARCHAR(10) NOT NULL DEFAULT 'MODERATE',
    met                    NUMERIC(4, 1) NOT NULL,
    weight_kg_used         NUMERIC(5, 1) NOT NULL,
    duration_min           INTEGER NOT NULL CHECK (duration_min BETWEEN 1 AND 1440),
    distance_km            NUMERIC(6, 2) CHECK (distance_km >= 0),
    calories_burned        NUMERIC(7, 1) NOT NULL CHECK (calories_burned >= 0),
    custom_name            VARCHAR(100),
    manual_calories_burned NUMERIC(7, 1),
    source                 VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    external_id            VARCHAR(100),
    started_at             TIMESTAMPTZ NOT NULL,
    ended_at               TIMESTAMPTZ NOT NULL,
    superseded_by          UUID REFERENCES activity_logs (id) ON DELETE SET NULL,
    note                   VARCHAR(255),
    route_polyline         TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    CONSTRAINT chk_activity_route_polyline_size
        CHECK (route_polyline IS NULL OR octet_length(route_polyline) <= 102400),
    CONSTRAINT chk_activity_manual_calories
        CHECK (manual_calories_burned IS NULL OR manual_calories_burned BETWEEN 0 AND 999999.9),
    CONSTRAINT uq_activity_logs_client UNIQUE (user_id, client_uuid)
);

CREATE UNIQUE INDEX uq_activity_logs_external
    ON activity_logs (user_id, source, external_id) WHERE external_id IS NOT NULL;

CREATE INDEX idx_activity_logs_user_started
    ON activity_logs (user_id, started_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_activity_logs_user_updated
    ON activity_logs (user_id, updated_at);

CREATE TABLE health_sync_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider       VARCHAR(20)    NOT NULL,
    data_type      VARCHAR(20)    NOT NULL,
    external_id    VARCHAR(100)   NOT NULL,
    value          NUMERIC(12, 2) NOT NULL,
    value_min      NUMERIC(12, 2),
    value_max      NUMERIC(12, 2),
    data_origin    VARCHAR(100),
    start_time     TIMESTAMPTZ    NOT NULL,
    end_time       TIMESTAMPTZ    NOT NULL,
    synced_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    last_synced_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_health_sync_range CHECK (end_time >= start_time),
    CONSTRAINT uq_health_sync UNIQUE (user_id, provider, data_type, external_id)
);

CREATE INDEX idx_health_sync_user_time
    ON health_sync_logs (user_id, data_type, start_time);

CREATE TABLE body_metrics (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid UUID NOT NULL,
    waist_cm    NUMERIC(5, 1) NOT NULL CHECK (waist_cm BETWEEN 20.0 AND 300.0),
    hip_cm      NUMERIC(5, 1) NOT NULL CHECK (hip_cm BETWEEN 20.0 AND 300.0),
    logged_at   TIMESTAMPTZ NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_body_metrics_client UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_body_metrics_user_logged
    ON body_metrics (user_id, logged_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_body_metrics_user_updated
    ON body_metrics (user_id, updated_at);

-- ============================================================
-- NUTRI module: user_food_logs, water_logs, daily_summaries
-- ============================================================
CREATE TABLE user_food_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    food_id                 UUID REFERENCES foods (id),
    meal_type               VARCHAR(20) CHECK (meal_type IS NULL OR meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    serving_name            VARCHAR(100),
    serving_grams           NUMERIC(6, 1) CHECK (serving_grams IS NULL OR serving_grams > 0),
    serving_quantity        NUMERIC(4, 2) NOT NULL DEFAULT 1.00 CHECK (serving_quantity > 0),
    source                  VARCHAR(30) NOT NULL DEFAULT 'SEARCH'
                            CHECK (source IN ('AI_PHOTO', 'BARCODE', 'SEARCH', 'CUSTOM', 'MEAL_PLAN', 'COPY')),
    logged_date             DATE,
    logged_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    image_key               TEXT,

    calories                NUMERIC(6, 1) CHECK (calories IS NULL OR calories >= 0),
    protein_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (protein_g >= 0),
    carbs_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_g >= 0),
    fat_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fat_g >= 0),
    sugar_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_g >= 0),
    fiber_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_g >= 0),

    ai_predicted_class_id   INTEGER CHECK (
                                ai_predicted_class_id IS NULL
                                OR (ai_predicted_class_id BETWEEN 0 AND 67 AND ai_predicted_class_id <> 27)
                            ),
    feedback_given          BOOLEAN NOT NULL DEFAULT FALSE,
    user_consented_feedback BOOLEAN NOT NULL DEFAULT FALSE,

    client_uuid             UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    sync_seq                BIGINT NOT NULL DEFAULT nextval('sync_seq'),
    server_updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT chk_user_food_logs_active_fields CHECK (
        deleted_at IS NOT NULL OR (
            food_id IS NOT NULL
            AND meal_type IS NOT NULL
            AND serving_name IS NOT NULL
            AND serving_grams IS NOT NULL
            AND logged_date IS NOT NULL
            AND calories IS NOT NULL
        )
    ),
    CONSTRAINT uq_user_food_log_client_uuid UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_user_food_logs_user_date
    ON user_food_logs (user_id, logged_date, logged_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_user_food_logs_food
    ON user_food_logs (food_id);

CREATE INDEX idx_user_food_logs_sync
    ON user_food_logs (user_id, sync_seq);

CREATE INDEX idx_user_food_logs_deleted
    ON user_food_logs (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE TRIGGER trg_user_food_logs_sync
    BEFORE UPDATE ON user_food_logs
    FOR EACH ROW EXECUTE FUNCTION touch_sync();

CREATE TABLE water_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount_ml         INTEGER CHECK (amount_ml IS NULL OR (amount_ml > 0 AND amount_ml <= 5000)),
    logged_date       DATE,
    logged_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    note              VARCHAR(255),

    client_uuid       UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    sync_seq          BIGINT NOT NULL DEFAULT nextval('sync_seq'),
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT chk_water_logs_active_fields CHECK (
        deleted_at IS NOT NULL OR (
            amount_ml IS NOT NULL
            AND logged_date IS NOT NULL
        )
    ),
    CONSTRAINT uq_water_log_client_uuid UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_water_logs_user_date
    ON water_logs (user_id, logged_date, logged_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_water_logs_sync
    ON water_logs (user_id, sync_seq);

CREATE INDEX idx_water_logs_deleted
    ON water_logs (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE TRIGGER trg_water_logs_sync
    BEFORE UPDATE ON water_logs
    FOR EACH ROW EXECUTE FUNCTION touch_sync();

CREATE TABLE daily_summaries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    summary_date        DATE NOT NULL,

    calories_consumed   INTEGER NOT NULL DEFAULT 0 CHECK (calories_consumed >= 0),
    target_calories     INTEGER CHECK (target_calories IS NULL OR target_calories > 0),
    protein_g           NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (protein_g >= 0),
    carbs_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_g >= 0),
    fat_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fat_g >= 0),
    sugar_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_g >= 0),
    fiber_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_g >= 0),

    water_ml            INTEGER NOT NULL DEFAULT 0 CHECK (water_ml >= 0),
    target_water_ml     INTEGER CHECK (target_water_ml IS NULL OR target_water_ml > 0),

    calories_burned     INTEGER NOT NULL DEFAULT 0 CHECK (calories_burned >= 0),
    steps               INTEGER NOT NULL DEFAULT 0 CHECK (steps >= 0),
    weight_kg           NUMERIC(5, 1) CHECK (weight_kg IS NULL OR weight_kg > 0),

    is_goal_achieved    BOOLEAN NOT NULL DEFAULT FALSE,
    is_sugar_exceeded   BOOLEAN NOT NULL DEFAULT FALSE,
    is_protein_exceeded BOOLEAN NOT NULL DEFAULT FALSE,

    meals_logged_count  INTEGER NOT NULL DEFAULT 0 CHECK (meals_logged_count BETWEEN 0 AND 4),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_daily_summary_user_date UNIQUE (user_id, summary_date)
);

CREATE INDEX idx_daily_summaries_user_date
    ON daily_summaries (user_id, summary_date);

-- ============================================================
-- PLAN module: meal_plans, meal_plan_foods
-- ============================================================
CREATE TABLE meal_plans (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                   VARCHAR(150) NOT NULL,
    description            TEXT,
    type                   VARCHAR(30) NOT NULL DEFAULT 'DAILY' CHECK (type IN ('DAILY', 'WEEKLY')),
    plan_date              DATE NOT NULL,
    days_count             INTEGER NOT NULL DEFAULT 1 CHECK (days_count BETWEEN 1 AND 7),
    status                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SAVED', 'APPLIED')),

    target_calories        INTEGER NOT NULL CHECK (target_calories > 0),
    total_calories         INTEGER NOT NULL DEFAULT 0 CHECK (total_calories >= 0),
    total_protein_g        NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_protein_g >= 0),
    total_carbs_g          NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_carbs_g >= 0),
    total_fat_g            NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_fat_g >= 0),
    total_sugar_g          NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_sugar_g >= 0),
    total_fiber_g          NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_fiber_g >= 0),
    estimated_cost_vnd     INTEGER NOT NULL DEFAULT 0 CHECK (estimated_cost_vnd >= 0),
    max_budget_vnd         INTEGER CHECK (max_budget_vnd IS NULL OR max_budget_vnd > 0),

    preference_match_score NUMERIC(4, 2) NOT NULL DEFAULT 0.0,
    medical_warning        TEXT,

    applied_at             TIMESTAMPTZ,
    applied_date           DATE,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_plans_user_date ON meal_plans (user_id, plan_date);
CREATE INDEX idx_meal_plans_status ON meal_plans (user_id, status);

CREATE TABLE meal_plan_foods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_plan_id        UUID NOT NULL REFERENCES meal_plans (id) ON DELETE CASCADE,
    food_id             UUID NOT NULL REFERENCES foods (id),
    day_index           INTEGER NOT NULL DEFAULT 1 CHECK (day_index BETWEEN 1 AND 7),
    meal_date           DATE NOT NULL,
    meal_type           VARCHAR(20) NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),

    serving_name        VARCHAR(100) NOT NULL,
    serving_grams       NUMERIC(6, 1) NOT NULL CHECK (serving_grams > 0),
    serving_multiplier  NUMERIC(4, 2) NOT NULL DEFAULT 1.00 CHECK (serving_multiplier > 0),

    calories            INTEGER NOT NULL CHECK (calories >= 0),
    protein_g           NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (protein_g >= 0),
    carbs_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_g >= 0),
    fat_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fat_g >= 0),
    sugar_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_g >= 0),
    fiber_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_g >= 0),
    estimated_price_vnd INTEGER NOT NULL DEFAULT 0 CHECK (estimated_price_vnd >= 0),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_plan_foods_plan ON meal_plan_foods (meal_plan_id, day_index, meal_type);
CREATE INDEX idx_meal_plan_foods_food ON meal_plan_foods (food_id);

-- ============================================================
-- GAME module: user_game_settings, friendships, streaks,
-- badges, user_badges, challenges, user_challenges, point_transactions
-- ============================================================
CREATE TABLE user_game_settings (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    friend_code  VARCHAR(12) NOT NULL,
    discoverable BOOLEAN     NOT NULL DEFAULT TRUE,
    share_streak BOOLEAN     NOT NULL DEFAULT TRUE,
    share_points BOOLEAN     NOT NULL DEFAULT TRUE,
    share_steps  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_game_settings_friend_code
    ON user_game_settings (friend_code);

CREATE TABLE friendships (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_friendships_not_self CHECK (requester_id <> addressee_id),
    CONSTRAINT chk_friendships_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE UNIQUE INDEX uq_friendships_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX idx_friendships_addressee ON friendships (addressee_id, status);
CREATE INDEX idx_friendships_requester ON friendships (requester_id, status);
CREATE INDEX idx_friendships_declined_responded
    ON friendships (responded_at)
    WHERE status = 'DECLINED';

CREATE TABLE streaks (
    user_id             UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    current_streak      INTEGER     NOT NULL DEFAULT 0,
    longest_streak      INTEGER     NOT NULL DEFAULT 0,
    last_completed_date DATE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_streaks_values CHECK (current_streak >= 0 AND longest_streak >= current_streak)
);

CREATE TABLE badges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon_key    VARCHAR(200),
    rule_type   VARCHAR(30)  NOT NULL,
    threshold   INTEGER,
    points      INTEGER      NOT NULL DEFAULT 0 CHECK (points >= 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_badges (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_id  UUID        NOT NULL REFERENCES badges (id),
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_badges UNIQUE (user_id, badge_id)
);

CREATE INDEX idx_user_badges_user ON user_badges (user_id, earned_at DESC);

CREATE TABLE challenges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(150)  NOT NULL,
    description   VARCHAR(1000) NOT NULL,
    metric        VARCHAR(30)   NOT NULL,
    target_value  INTEGER       NOT NULL CHECK (target_value > 0),
    start_date    DATE          NOT NULL,
    end_date      DATE          NOT NULL,
    points_reward INTEGER       NOT NULL DEFAULT 0 CHECK (points_reward >= 0),
    badge_id      UUID REFERENCES badges (id),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_challenges_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_challenges_active_end ON challenges (active, end_date);

CREATE TABLE user_challenges (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    challenge_id      UUID        NOT NULL REFERENCES challenges (id),
    progress          INTEGER     NOT NULL DEFAULT 0 CHECK (progress >= 0),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    joined_local_date DATE        NOT NULL,
    completed_at      TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_challenges UNIQUE (user_id, challenge_id),
    CONSTRAINT chk_user_challenges_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED'))
);

CREATE INDEX idx_user_challenges_user ON user_challenges (user_id, status);
CREATE INDEX idx_user_challenges_challenge ON user_challenges (challenge_id);

CREATE TABLE point_transactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    points      INTEGER     NOT NULL CHECK (points > 0),
    source_type VARCHAR(30) NOT NULL,
    source_id   UUID        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_point_source UNIQUE (user_id, source_type, source_id)
);

CREATE INDEX idx_point_transactions_user_time ON point_transactions (user_id, occurred_at);

-- ============================================================
-- CHAT module: conversations, chat_messages
-- ============================================================
CREATE TABLE conversations (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                     VARCHAR(120),
    summary                   TEXT,
    message_count             INTEGER NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    last_message_at           TIMESTAMPTZ,
    deleted_at                TIMESTAMPTZ,
    initial_client_message_id UUID,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_recent
    ON conversations (user_id, last_message_at DESC NULLS LAST, id DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_conversations_initial_idempotency
    ON conversations (user_id, initial_client_message_id)
    WHERE initial_client_message_id IS NOT NULL;

CREATE INDEX idx_conversations_deleted_at
    ON conversations (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE TABLE chat_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content           TEXT,
    status            VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
                      CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    client_message_id UUID,
    parent_message_id UUID REFERENCES chat_messages (id),
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    model_version     VARCHAR(64),
    latency_ms        INTEGER,
    metadata          JSONB,
    feedback          SMALLINT CHECK (feedback IN (-1, 1)),
    pending_since     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_messages_content_status CHECK (
        (status = 'COMPLETED' AND content IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND role = 'ASSISTANT' AND content IS NULL)
    )
);

CREATE INDEX idx_chat_messages_conv_time
    ON chat_messages (conversation_id, created_at, id);

CREATE UNIQUE INDEX uq_chat_messages_idempotency
    ON chat_messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE INDEX idx_chat_messages_pending_since
    ON chat_messages (status, pending_since)
    WHERE status = 'PENDING';

CREATE INDEX idx_chat_messages_parent
    ON chat_messages (parent_message_id)
    WHERE parent_message_id IS NOT NULL;

CREATE UNIQUE INDEX uq_chat_messages_pending_assistant
    ON chat_messages (conversation_id)
    WHERE role = 'ASSISTANT' AND status = 'PENDING';
