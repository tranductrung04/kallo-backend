-- ============================================================
-- V20261125100000__nutri_schema.sql
-- Module NUTRI: Food Logs, Water Logs, Daily Summaries + SYS Sync Infrastructure
-- ============================================================

-- 1. Food logs with Nutrition Snapshot & SYS Sync Tombstone support
CREATE TABLE user_food_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    food_id                 UUID REFERENCES foods(id),
    meal_type               VARCHAR(20) CHECK (meal_type IS NULL OR meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    serving_name            VARCHAR(100),
    serving_grams           NUMERIC(6, 1) CHECK (serving_grams IS NULL OR serving_grams > 0),
    serving_quantity        NUMERIC(4, 2) NOT NULL DEFAULT 1.00 CHECK (serving_quantity > 0),
    source                  VARCHAR(30) NOT NULL DEFAULT 'SEARCH'
                            CHECK (source IN ('AI_PHOTO', 'BARCODE', 'SEARCH', 'CUSTOM', 'MEAL_PLAN', 'COPY')),
    logged_date             DATE,
    logged_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    image_key               TEXT,

    -- Nutrition Snapshot (frozen at log time)
    calories                NUMERIC(6, 1) CHECK (calories IS NULL OR calories >= 0),
    protein_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (protein_g >= 0),
    carbs_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_g >= 0),
    fat_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fat_g >= 0),
    sugar_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_g >= 0),
    fiber_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_g >= 0),

    -- AI feedback metadata (VietFood68 classes 0..67 excluding 27)
    ai_predicted_class_id   INTEGER CHECK (
                                ai_predicted_class_id IS NULL
                                OR (ai_predicted_class_id BETWEEN 0 AND 67 AND ai_predicted_class_id <> 27)
                            ),
    feedback_given          BOOLEAN NOT NULL DEFAULT FALSE,
    user_consented_feedback BOOLEAN NOT NULL DEFAULT FALSE,

    -- SYS offline sync infrastructure columns (V20261113063404__sys_schema.sql)
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


-- 2. Water logs with SYS Sync Tombstone support
CREATE TABLE water_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_ml               INTEGER CHECK (amount_ml IS NULL OR (amount_ml > 0 AND amount_ml <= 5000)),
    logged_date             DATE,
    logged_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    note                    VARCHAR(255),

    -- SYS offline sync infrastructure columns
    client_uuid             UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    sync_seq                BIGINT NOT NULL DEFAULT nextval('sync_seq'),
    server_updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

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


-- 3. Daily summaries hot table
CREATE TABLE daily_summaries (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    summary_date            DATE NOT NULL,

    -- Energy & Macros In
    calories_consumed       INTEGER NOT NULL DEFAULT 0 CHECK (calories_consumed >= 0),
    target_calories         INTEGER CHECK (target_calories IS NULL OR target_calories > 0),
    protein_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (protein_g >= 0),
    carbs_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_g >= 0),
    fat_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fat_g >= 0),
    sugar_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_g >= 0),
    fiber_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_g >= 0),

    -- Hydration
    water_ml                INTEGER NOT NULL DEFAULT 0 CHECK (water_ml >= 0),
    target_water_ml         INTEGER CHECK (target_water_ml IS NULL OR target_water_ml > 0),

    -- Activity & Energy Out (aggregated from ACT)
    calories_burned         INTEGER NOT NULL DEFAULT 0 CHECK (calories_burned >= 0),
    steps                   INTEGER NOT NULL DEFAULT 0 CHECK (steps >= 0),
    weight_kg               NUMERIC(5, 1) CHECK (weight_kg IS NULL OR weight_kg > 0),

    -- Goal & Alert flags
    is_goal_achieved        BOOLEAN NOT NULL DEFAULT FALSE,
    is_sugar_exceeded       BOOLEAN NOT NULL DEFAULT FALSE,
    is_protein_exceeded     BOOLEAN NOT NULL DEFAULT FALSE,

    -- Distinct meal types logged on this date (0..4)
    meals_logged_count      INTEGER NOT NULL DEFAULT 0 CHECK (meals_logged_count BETWEEN 0 AND 4),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_daily_summary_user_date UNIQUE (user_id, summary_date)
);

CREATE INDEX idx_daily_summaries_user_date
    ON daily_summaries (user_id, summary_date);
