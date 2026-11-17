-- ============================================================
-- PROFILE module schema: User Health Profiles
-- ============================================================

CREATE TABLE user_health_profiles (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                        UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    gender                         VARCHAR(20) NOT NULL,
    birth_date                     DATE NOT NULL,
    height_cm                      NUMERIC(5, 1) NOT NULL CHECK (height_cm BETWEEN 100 AND 250),
    weight_kg                      NUMERIC(5, 1) NOT NULL CHECK (weight_kg BETWEEN 20 AND 300),
    activity_level                 VARCHAR(30) NOT NULL,
    goal_type                      VARCHAR(30) NOT NULL,
    target_weight_kg               NUMERIC(5, 1) CHECK (target_weight_kg IS NULL OR (target_weight_kg BETWEEN 20 AND 300)),
    weekly_rate_kg                 NUMERIC(4, 2) NOT NULL DEFAULT 0.0 CHECK (weekly_rate_kg BETWEEN 0.0 AND 2.0),
    bmi                            NUMERIC(4, 1) NOT NULL,
    bmi_category                   VARCHAR(30) NOT NULL,
    bmr                            INTEGER NOT NULL,
    tdee                           INTEGER NOT NULL,
    daily_calorie_goal             INTEGER NOT NULL,
    safety_clamped                 BOOLEAN NOT NULL DEFAULT FALSE,
    protein_goal_g                 NUMERIC(5, 1) NOT NULL,
    carbs_goal_g                   NUMERIC(5, 1) NOT NULL,
    fat_goal_g                     NUMERIC(5, 1) NOT NULL,
    protein_pct                    NUMERIC(4, 1) NOT NULL,
    carbs_pct                      NUMERIC(4, 1) NOT NULL,
    fat_pct                        NUMERIC(4, 1) NOT NULL,
    custom_macro                   BOOLEAN NOT NULL DEFAULT FALSE,
    water_goal_ml                  INTEGER NOT NULL CHECK (water_goal_ml > 0),
    allergies                      JSONB NOT NULL DEFAULT '[]'::jsonb,
    recalculation_recommended      BOOLEAN NOT NULL DEFAULT FALSE,
    recommended_tdee               INTEGER,
    recommended_daily_calorie_goal INTEGER,
    latest_logged_weight_kg        NUMERIC(5, 1),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_health_profiles_user ON user_health_profiles (user_id);
