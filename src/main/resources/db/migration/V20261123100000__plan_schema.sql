-- V20261123100000__plan_schema.sql
-- Module PLAN: Meal Plans, Vectors, and Medical Conditions

-- 1. Bổ sung Vector đặc trưng vào bảng foods
ALTER TABLE foods 
ADD COLUMN IF NOT EXISTS feature_vector JSONB DEFAULT '[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]'::jsonb;

-- 2. Bổ sung Vector sở thích & Bệnh lý vào bảng user_health_profiles
ALTER TABLE user_health_profiles 
ADD COLUMN IF NOT EXISTS preference_vector JSONB DEFAULT '[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]'::jsonb,
ADD COLUMN IF NOT EXISTS chronic_conditions JSONB DEFAULT '[]'::jsonb;

-- 3. Bảng Thực đơn (Meal Plans)
CREATE TABLE meal_plans (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                        VARCHAR(150) NOT NULL,
    description                 TEXT,
    type                        VARCHAR(30) NOT NULL DEFAULT 'DAILY', -- DAILY, WEEKLY
    plan_date                   DATE NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SAVED, APPLIED
    
    -- Mục tiêu & Tổng dinh dưỡng
    target_calories             INTEGER NOT NULL,
    total_calories              INTEGER NOT NULL DEFAULT 0,
    total_protein_g             NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    total_carbs_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    total_fat_g                 NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    total_sugar_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    total_fiber_g               NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    estimated_cost_vnd          INTEGER NOT NULL DEFAULT 0,
    
    -- Điểm số tương thích sở thích & Cảnh báo y tế
    preference_match_score      NUMERIC(4, 2) NOT NULL DEFAULT 0.0,
    medical_warning             TEXT,
    
    -- Vết áp dụng nhật ký
    applied_at                  TIMESTAMPTZ,
    applied_date                DATE,
    
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_plans_user_date ON meal_plans (user_id, plan_date);
CREATE INDEX idx_meal_plans_status ON meal_plans (user_id, status);

-- 4. Bảng Chi tiết món ăn trong thực đơn (Meal Plan Foods)
CREATE TABLE meal_plan_foods (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_plan_id                UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    food_id                     UUID NOT NULL REFERENCES foods(id),
    meal_type                   VARCHAR(20) NOT NULL, -- BREAKFAST, LUNCH, DINNER, SNACK
    
    serving_name                VARCHAR(100) NOT NULL,
    serving_grams               NUMERIC(6, 1) NOT NULL,
    serving_multiplier          NUMERIC(4, 2) NOT NULL DEFAULT 1.0, -- 0.75, 1.0, 1.25, 1.5
    
    -- Snapshot Dinh dưỡng món
    calories                    INTEGER NOT NULL,
    protein_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    carbs_g                     NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    fat_g                       NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    sugar_g                     NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    fiber_g                     NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    estimated_price_vnd         INTEGER NOT NULL DEFAULT 0,
    
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_plan_foods_plan ON meal_plan_foods (meal_plan_id);
CREATE INDEX idx_meal_plan_foods_food ON meal_plan_foods (food_id);
