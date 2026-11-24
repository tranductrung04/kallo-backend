-- 1. Bảng hồ sơ sức khỏe người dùng
CREATE TABLE user_health_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    dob DATE NOT NULL,
    height_cm NUMERIC(5, 2) NOT NULL CHECK (height_cm BETWEEN 50.0 AND 250.0),
    weight_kg NUMERIC(5, 2) NOT NULL CHECK (weight_kg BETWEEN 20.0 AND 300.0),
    activity_level VARCHAR(20) NOT NULL CHECK (activity_level IN ('SEDENTARY', 'LIGHTLY_ACTIVE', 'MODERATELY_ACTIVE', 'VERY_ACTIVE', 'EXTRA_ACTIVE')),
    goal_type VARCHAR(20) NOT NULL CHECK (goal_type IN ('LOSE_WEIGHT', 'MAINTAIN_WEIGHT', 'GAIN_WEIGHT')),
    target_weight_kg NUMERIC(5, 2) CHECK (target_weight_kg IS NULL OR (target_weight_kg BETWEEN 20.0 AND 300.0)),
    weekly_rate_kg NUMERIC(3, 2) NOT NULL DEFAULT 0.00 CHECK (weekly_rate_kg BETWEEN 0.00 AND 1.50),
    bmr NUMERIC(6, 2) NOT NULL CHECK (bmr > 0),
    tdee NUMERIC(6, 2) NOT NULL CHECK (tdee > 0),
    daily_calorie_goal NUMERIC(6, 2) NOT NULL CHECK (daily_calorie_goal > 0),
    carb_grams NUMERIC(6, 2) NOT NULL CHECK (carb_grams >= 0),
    protein_grams NUMERIC(6, 2) NOT NULL CHECK (protein_grams >= 0),
    fat_grams NUMERIC(6, 2) NOT NULL CHECK (fat_grams >= 0),
    water_liters NUMERIC(4, 2) NOT NULL CHECK (water_liters >= 0),
    recalculation_recommended BOOLEAN NOT NULL DEFAULT FALSE,
    recommended_tdee NUMERIC(6, 2),
    recommended_daily_calorie_goal NUMERIC(6, 2),
    latest_logged_weight_kg NUMERIC(5, 2),
    rate_clamped BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_target_weight_required CHECK (
        (goal_type = 'MAINTAIN_WEIGHT' AND target_weight_kg IS NULL AND weekly_rate_kg = 0.00) OR
        (goal_type IN ('LOSE_WEIGHT', 'GAIN_WEIGHT') AND target_weight_kg IS NOT NULL AND weekly_rate_kg > 0.00)
    )
);

-- 2. Bảng danh mục bệnh mạn tính
CREATE TABLE chronic_conditions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng liên kết bệnh mạn tính người dùng
CREATE TABLE user_chronic_conditions (
    user_id UUID NOT NULL REFERENCES user_health_profiles(user_id) ON DELETE CASCADE,
    condition_id UUID NOT NULL REFERENCES chronic_conditions(id),
    diagnosed_at DATE,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, condition_id)
);

-- 4. Bảng dị ứng nguyên liệu của người dùng
CREATE TABLE user_allergies (
    user_id UUID NOT NULL REFERENCES user_health_profiles(user_id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, ingredient_id)
);

CREATE INDEX idx_user_allergies_user_id ON user_allergies(user_id);
CREATE INDEX idx_user_chronic_user_id ON user_chronic_conditions(user_id);
