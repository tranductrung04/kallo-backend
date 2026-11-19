-- V20261120100000__nutri_schema.sql
-- Module NUTRI: User Food Logs, Water Logs, and Daily Summaries

-- 1. Bảng nhật ký ăn uống (User Food Logs)
CREATE TABLE user_food_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    food_id                 UUID NOT NULL REFERENCES foods(id),
    meal_type               VARCHAR(20) NOT NULL, -- BREAKFAST, LUNCH, DINNER, SNACK
    serving_name            VARCHAR(100) NOT NULL,
    serving_grams           NUMERIC(6, 1) NOT NULL,
    serving_quantity        NUMERIC(4, 2) NOT NULL DEFAULT 1.0,
    source                  VARCHAR(30) NOT NULL DEFAULT 'SEARCH', -- AI_PHOTO, BARCODE, SEARCH, CUSTOM, MEAL_PLAN
    logged_date             DATE NOT NULL,
    logged_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_uuid             UUID NOT NULL,
    image_url               TEXT,

    -- Nutrition Snapshot (Đóng băng dinh dưỡng theo lượng ăn thực tế)
    calories                NUMERIC(6, 1) NOT NULL,
    protein_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    carbs_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    fat_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    sugar_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    fiber_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,

    -- Metadata AI feedback
    ai_predicted_class_id   INTEGER,
    feedback_given          BOOLEAN NOT NULL DEFAULT FALSE,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_food_log_client_uuid UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_user_food_logs_user_date ON user_food_logs (user_id, logged_date);
CREATE INDEX idx_user_food_logs_food ON user_food_logs (food_id);

-- 2. Bảng nhật ký uống nước (Water Logs)
CREATE TABLE water_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_ml               INTEGER NOT NULL CHECK (amount_ml > 0 AND amount_ml <= 5000),
    logged_date             DATE NOT NULL,
    logged_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_uuid             UUID NOT NULL,
    note                    VARCHAR(255),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_water_log_client_uuid UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_water_logs_user_date ON water_logs (user_id, logged_date);

-- 3. Bảng tổng hợp nóng hàng ngày (Daily Summaries)
CREATE TABLE daily_summaries (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    summary_date            DATE NOT NULL,

    -- Dinh dưỡng nạp vào (Calories In)
    calories_consumed       INTEGER NOT NULL DEFAULT 0,
    target_calories         INTEGER NOT NULL DEFAULT 2000,
    protein_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    carbs_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    fat_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    sugar_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    fiber_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,

    -- Nước uống
    water_ml                INTEGER NOT NULL DEFAULT 0,
    target_water_ml         INTEGER NOT NULL DEFAULT 2000,

    -- Tiêu hao & Vận động (Calories Out)
    calories_burned         INTEGER NOT NULL DEFAULT 0,
    steps                   INTEGER NOT NULL DEFAULT 0,
    weight_kg               NUMERIC(5, 1),

    -- Trạng thái mục tiêu
    is_goal_achieved        BOOLEAN NOT NULL DEFAULT FALSE,
    is_sugar_exceeded       BOOLEAN NOT NULL DEFAULT FALSE,
    is_protein_exceeded     BOOLEAN NOT NULL DEFAULT FALSE,

    -- Số bữa đã ghi trong ngày
    meals_logged_count      INTEGER NOT NULL DEFAULT 0,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_daily_summary_user_date UNIQUE (user_id, summary_date)
);

CREATE INDEX idx_daily_summaries_user_date ON daily_summaries (user_id, summary_date);
