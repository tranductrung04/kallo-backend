-- V20261125120000__plan_schema.sql
-- Module PLAN: Seed 67 VietFood68 Feature Vectors, User Preference Vector, Meal Plans & Meal Plan Foods

-- 1. Seed 12D feature_vector cho toàn bộ 67 món VietFood68 (0..67 <> 27)
-- Thứ tự chiều: [SOUP_BASED, DRY_BASED, BEEF, PORK, POULTRY, SEAFOOD, VEGETARIAN, FRIED_FATTY, STEAMED_BOILED, SPICY, SWEET, HIGH_PROTEIN]
UPDATE foods SET feature_vector = CASE vietfood_class_id
    WHEN 0  THEN '[1.0, 0.0, 0.0, 0.7, 0.0, 0.3, 0.1, 0.1, 0.9, 0.1, 0.1, 0.5]'::jsonb -- Bánh canh
    WHEN 1  THEN '[0.0, 1.0, 0.0, 0.8, 0.0, 0.0, 0.1, 0.4, 0.6, 0.1, 0.1, 0.3]'::jsonb -- Bánh chưng
    WHEN 2  THEN '[0.0, 1.0, 0.0, 0.7, 0.0, 0.0, 0.2, 0.2, 0.9, 0.1, 0.1, 0.4]'::jsonb -- Bánh cuốn
    WHEN 3  THEN '[0.0, 1.0, 0.0, 0.2, 0.0, 0.8, 0.1, 0.9, 0.0, 0.1, 0.2, 0.3]'::jsonb -- Bánh khọt
    WHEN 4  THEN '[0.0, 1.0, 0.0, 0.8, 0.2, 0.0, 0.1, 0.4, 0.2, 0.2, 0.2, 0.4]'::jsonb -- Bánh mì
    WHEN 5  THEN '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.1, 0.1, 0.1, 0.1]'::jsonb -- Bánh tráng
    WHEN 6  THEN '[0.0, 0.9, 0.3, 0.0, 0.0, 0.2, 0.2, 0.5, 0.0, 0.7, 0.4, 0.3]'::jsonb -- Bánh tráng trộn
    WHEN 7  THEN '[0.0, 1.0, 0.0, 0.6, 0.0, 0.7, 0.2, 0.9, 0.0, 0.1, 0.2, 0.4]'::jsonb -- Bánh xèo
    WHEN 8  THEN '[1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.4, 0.6, 0.5, 0.2, 0.9]'::jsonb -- Bò kho
    WHEN 9  THEN '[0.0, 0.8, 1.0, 0.1, 0.0, 0.0, 0.2, 0.6, 0.2, 0.2, 0.1, 0.8]'::jsonb -- Bò lá lốt
    WHEN 10 THEN '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.4]'::jsonb -- Bông cải
    WHEN 11 THEN '[0.2, 0.8, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.9, 0.0, 0.0, 0.1]'::jsonb -- Bún
    WHEN 12 THEN '[1.0, 0.0, 0.8, 0.4, 0.0, 0.0, 0.1, 0.3, 0.7, 0.8, 0.1, 0.8]'::jsonb -- Bún bò Huế
    WHEN 13 THEN '[0.3, 0.8, 0.0, 1.0, 0.0, 0.0, 0.2, 0.6, 0.3, 0.2, 0.5, 0.6]'::jsonb -- Bún chả
    WHEN 14 THEN '[0.0, 1.0, 0.0, 0.9, 0.0, 0.2, 0.2, 0.8, 0.3, 0.3, 0.1, 0.7]'::jsonb -- Bún đậu
    WHEN 15 THEN '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.1, 0.2, 0.8, 0.4, 0.2, 0.8]'::jsonb -- Bún mắm
    WHEN 16 THEN '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb -- Bún riêu
    WHEN 17 THEN '[0.0, 0.8, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.1, 0.0, 1.0]'::jsonb -- Cá
    WHEN 18 THEN '[0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.3, 0.1]'::jsonb -- Cà chua
    WHEN 19 THEN '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.7, 0.2, 0.1, 0.1]'::jsonb -- Cà pháo
    WHEN 20 THEN '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.4, 0.1]'::jsonb -- Cà rốt
    WHEN 21 THEN '[1.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.2]'::jsonb -- Canh
    WHEN 22 THEN '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.3, 0.8, 0.2, 0.1, 0.8]'::jsonb -- Chả
    WHEN 23 THEN '[0.0, 1.0, 0.0, 0.8, 0.0, 0.3, 0.1, 1.0, 0.0, 0.1, 0.1, 0.3]'::jsonb -- Chả giò
    WHEN 24 THEN '[0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.1, 0.1]'::jsonb -- Chanh
    WHEN 25 THEN '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.1]'::jsonb -- Cơm
    WHEN 26 THEN '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.5]'::jsonb -- Cơm tấm
    WHEN 28 THEN '[0.0, 0.6, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.2, 0.6, 0.0]'::jsonb -- Củ kiệu
    WHEN 29 THEN '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.1, 0.0, 1.0]'::jsonb -- Cua
    WHEN 30 THEN '[0.0, 0.9, 0.0, 0.0, 0.0, 0.0, 1.0, 0.7, 0.3, 0.0, 0.0, 0.8]'::jsonb -- Đậu hũ
    WHEN 31 THEN '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.1, 0.2, 0.1]'::jsonb -- Dưa chua
    WHEN 32 THEN '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.2, 0.1]'::jsonb -- Dưa leo
    WHEN 33 THEN '[0.0, 0.8, 0.0, 0.5, 0.0, 0.6, 0.5, 0.0, 1.0, 0.1, 0.2, 0.5]'::jsonb -- Gỏi cuốn
    WHEN 34 THEN '[0.0, 1.0, 1.0, 0.2, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.6]'::jsonb -- Hamburger
    WHEN 35 THEN '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.2, 0.2, 0.6]'::jsonb -- Heo quay
    WHEN 36 THEN '[1.0, 0.0, 0.0, 0.7, 0.0, 0.5, 0.1, 0.1, 0.9, 0.1, 0.2, 0.6]'::jsonb -- Hủ tiếu
    WHEN 37 THEN '[1.0, 0.0, 0.0, 0.8, 0.0, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.8]'::jsonb -- Khổ qua thịt
    WHEN 38 THEN '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.8, 1.0, 0.0, 0.1, 0.1, 0.1]'::jsonb -- Khoai tây chiên
    WHEN 39 THEN '[1.0, 0.0, 0.6, 0.3, 0.2, 0.6, 0.4, 0.2, 0.8, 0.6, 0.2, 0.9]'::jsonb -- Lẩu
    WHEN 40 THEN '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 0.9, 0.1, 0.0, 0.9]'::jsonb -- Lòng heo
    WHEN 41 THEN '[0.8, 0.3, 0.2, 0.5, 0.0, 0.2, 0.1, 0.6, 0.3, 0.4, 0.1, 0.3]'::jsonb -- Mì
    WHEN 42 THEN '[0.0, 0.7, 0.0, 0.0, 0.0, 1.0, 0.0, 0.4, 0.7, 0.2, 0.0, 1.0]'::jsonb -- Mực
    WHEN 43 THEN '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.0, 0.1, 0.5]'::jsonb -- Nấm
    WHEN 44 THEN '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.3, 0.7, 0.5, 0.1, 1.0]'::jsonb -- Ốc
    WHEN 45 THEN '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.3, 0.2]'::jsonb -- Ớt chuông
    WHEN 46 THEN '[1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb -- Phở
    WHEN 47 THEN '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.4, 0.6, 0.2, 0.0, 0.2, 0.6]'::jsonb -- Phô mai
    WHEN 48 THEN '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.1, 0.1, 0.4]'::jsonb -- Rau
    WHEN 49 THEN '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.9, 0.0, 0.2, 0.2]'::jsonb -- Salad
    WHEN 50 THEN '[0.0, 0.9, 1.0, 0.0, 0.0, 0.0, 0.0, 0.6, 0.3, 0.2, 0.0, 1.0]'::jsonb -- Thịt bò
    WHEN 51 THEN '[0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.4, 0.7, 0.1, 0.0, 1.0]'::jsonb -- Thịt gà
    WHEN 52 THEN '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 0.9, 0.0, 0.0, 0.9]'::jsonb -- Thịt heo
    WHEN 53 THEN '[0.2, 0.7, 0.0, 1.0, 0.0, 0.0, 0.0, 0.6, 0.4, 0.1, 0.4, 0.7]'::jsonb -- Thịt kho
    WHEN 54 THEN '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.3, 0.3, 0.8]'::jsonb -- Thịt nướng
    WHEN 55 THEN '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0]'::jsonb -- Tôm
    WHEN 56 THEN '[0.0, 0.8, 0.0, 0.0, 0.3, 0.0, 0.2, 0.6, 0.4, 0.0, 0.1, 0.8]'::jsonb -- Trứng
    WHEN 57 THEN '[0.0, 1.0, 0.0, 0.6, 0.4, 0.0, 0.1, 0.4, 0.7, 0.1, 0.2, 0.3]'::jsonb -- Xôi
    WHEN 58 THEN '[0.0, 0.9, 0.0, 0.3, 0.0, 0.6, 0.1, 0.2, 0.9, 0.2, 0.2, 0.3]'::jsonb -- Bánh bèo
    WHEN 59 THEN '[0.3, 0.8, 0.0, 0.9, 0.0, 0.0, 0.2, 0.4, 0.5, 0.2, 0.2, 0.6]'::jsonb -- Cao lầu
    WHEN 60 THEN '[0.4, 0.7, 0.0, 0.6, 0.2, 0.6, 0.1, 0.3, 0.7, 0.3, 0.2, 0.6]'::jsonb -- Mì Quảng
    WHEN 61 THEN '[0.0, 1.0, 0.0, 0.6, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.1, 0.4]'::jsonb -- Cơm chiên dương châu
    WHEN 62 THEN '[1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb -- Bún chả cá
    WHEN 63 THEN '[0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.9, 0.0, 0.1, 0.1, 0.5]'::jsonb -- Cơm chiên gà
    WHEN 64 THEN '[1.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.0, 0.2, 0.9, 0.2, 0.0, 0.7]'::jsonb -- Cháo lòng
    WHEN 65 THEN '[0.0, 0.4, 0.0, 0.4, 0.0, 0.0, 0.7, 0.0, 0.9, 0.3, 0.6, 0.3]'::jsonb -- Nộm hoa chuối
    WHEN 66 THEN '[0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.2, 0.5]'::jsonb -- Nui xào bò
    WHEN 67 THEN '[1.0, 0.0, 0.0, 0.0, 0.3, 0.9, 0.1, 0.1, 0.9, 0.1, 0.1, 0.7]'::jsonb -- Súp cua
    ELSE feature_vector
END
WHERE vietfood_class_id IS NOT NULL;

UPDATE foods
SET feature_vector = '[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]'::jsonb
WHERE feature_vector IS NULL;

ALTER TABLE foods
ALTER COLUMN feature_vector SET DEFAULT '[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]'::jsonb;

-- 2. Bổ sung Vector sở thích vào bảng user_health_profiles (KHÔNG thêm chronic_conditions JSONB)
ALTER TABLE user_health_profiles
ADD COLUMN IF NOT EXISTS preference_vector JSONB NOT NULL DEFAULT '[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5]'::jsonb;

-- 3. Bảng Thực đơn (Meal Plans)
CREATE TABLE meal_plans (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                        VARCHAR(150) NOT NULL,
    description                 TEXT,
    type                        VARCHAR(30) NOT NULL DEFAULT 'DAILY' CHECK (type IN ('DAILY', 'WEEKLY')),
    plan_date                   DATE NOT NULL,
    days_count                  INTEGER NOT NULL DEFAULT 1 CHECK (days_count BETWEEN 1 AND 7),
    status                      VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SAVED', 'APPLIED')),

    target_calories             INTEGER NOT NULL CHECK (target_calories > 0),
    total_calories              INTEGER NOT NULL DEFAULT 0 CHECK (total_calories >= 0),
    total_protein_g             NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_protein_g >= 0),
    total_carbs_g               NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_carbs_g >= 0),
    total_fat_g                 NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_fat_g >= 0),
    total_sugar_g               NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_sugar_g >= 0),
    total_fiber_g               NUMERIC(7, 1) NOT NULL DEFAULT 0.0 CHECK (total_fiber_g >= 0),
    estimated_cost_vnd          INTEGER NOT NULL DEFAULT 0 CHECK (estimated_cost_vnd >= 0),
    max_budget_vnd              INTEGER CHECK (max_budget_vnd IS NULL OR max_budget_vnd > 0),

    preference_match_score      NUMERIC(4, 2) NOT NULL DEFAULT 0.0,
    medical_warning             TEXT,

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
    day_index                   INTEGER NOT NULL DEFAULT 1 CHECK (day_index BETWEEN 1 AND 7),
    meal_date                   DATE NOT NULL,
    meal_type                   VARCHAR(20) NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),

    serving_name                VARCHAR(100) NOT NULL,
    serving_grams               NUMERIC(6, 1) NOT NULL CHECK (serving_grams > 0),
    serving_multiplier          NUMERIC(4, 2) NOT NULL DEFAULT 1.00 CHECK (serving_multiplier > 0),

    calories                    INTEGER NOT NULL CHECK (calories >= 0),
    protein_g                   NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (protein_g >= 0),
    carbs_g                     NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_g >= 0),
    fat_g                       NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fat_g >= 0),
    sugar_g                     NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_g >= 0),
    fiber_g                     NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_g >= 0),
    estimated_price_vnd         INTEGER NOT NULL DEFAULT 0 CHECK (estimated_price_vnd >= 0),

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_plan_foods_plan ON meal_plan_foods (meal_plan_id, day_index, meal_type);
CREATE INDEX idx_meal_plan_foods_food ON meal_plan_foods (food_id);
