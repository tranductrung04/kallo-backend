-- 1. Kích hoạt tiện ích mở rộng phục vụ tìm kiếm tiếng Việt không dấu và Full-text trigram
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Hàm chuyển đổi chuỗi bỏ dấu bất biến (Immutable) dùng cho GENERATED ALWAYS AS
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text AS $$
  SELECT public.unaccent('public.unaccent', $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- 3. Bảng ingredients: Danh mục nguyên liệu hệ thống & nguyên liệu tự tạo
CREATE TABLE ingredients (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(50), -- Only for SYSTEM ingredients (e.g. PEANUT, GLUTEN)
    name                    VARCHAR(255) NOT NULL,
    name_unaccented         VARCHAR(255) GENERATED ALWAYS AS (immutable_unaccent(lower(name))) STORED,
    
    -- Chỉ số dinh dưỡng trên 100g nguyên liệu thô
    calories_per_100g       NUMERIC(6, 1) NOT NULL DEFAULT 0.0 CHECK (calories_per_100g BETWEEN 0 AND 950),
    protein_per_100g        NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (protein_per_100g >= 0),
    carbs_per_100g          NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (carbs_per_100g >= 0),
    fat_per_100g            NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fat_per_100g >= 0),
    sugar_per_100g          NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_per_100g >= 0),
    fiber_per_100g          NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_per_100g >= 0),
    
    is_allergen             BOOLEAN NOT NULL DEFAULT FALSE,
    source                  VARCHAR(30) NOT NULL DEFAULT 'SYSTEM', -- SYSTEM, USER_CUSTOM
    owner_user_id           UUID REFERENCES users (id) ON DELETE CASCADE,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ingredients_macros_100g CHECK (protein_per_100g + carbs_per_100g + fat_per_100g <= 105.0)
);

-- Partial Unique Index cho code nguyên liệu hệ thống
CREATE UNIQUE INDEX uq_ingredients_system_code 
ON ingredients (code) 
WHERE code IS NOT NULL AND owner_user_id IS NULL;

-- Index tra cứu nguyên liệu dị ứng cho Onboarding PROFILE
CREATE INDEX idx_ingredients_allergen 
ON ingredients (is_allergen) 
WHERE is_active = TRUE AND owner_user_id IS NULL;

-- Index phục vụ tìm kiếm nguyên liệu theo tên không dấu & theo owner
CREATE INDEX idx_ingredients_name_trgm ON ingredients USING GIN (name_unaccented gin_trgm_ops);
CREATE INDEX idx_ingredients_owner ON ingredients (owner_user_id) WHERE is_active = TRUE;


-- 4. Bảng foods: Thực phẩm hệ thống, sản phẩm OFF và món tùy chỉnh
CREATE TABLE foods (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255) NOT NULL,
    name_unaccented         VARCHAR(255) GENERATED ALWAYS AS (immutable_unaccent(lower(name))) STORED,
    category                VARCHAR(50) NOT NULL,
    default_serving_name    VARCHAR(100) NOT NULL,
    default_serving_grams   NUMERIC(6, 1) NOT NULL CHECK (default_serving_grams > 0),
    
    -- Chỉ số dinh dưỡng trên 100g thực phẩm
    calories_per_100g       NUMERIC(6, 1) NOT NULL CHECK (calories_per_100g >= 0),
    protein_per_100g        NUMERIC(5, 1) NOT NULL CHECK (protein_per_100g >= 0),
    carbs_per_100g          NUMERIC(5, 1) NOT NULL CHECK (carbs_per_100g >= 0),
    fat_per_100g            NUMERIC(5, 1) NOT NULL CHECK (fat_per_100g >= 0),
    sugar_per_100g          NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_per_100g >= 0),
    fiber_per_100g          NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_per_100g >= 0),

    -- Chỉ số dinh dưỡng quy đổi trên 1 khẩu phần chuẩn (default serving)
    calories_per_serving    INTEGER NOT NULL CHECK (calories_per_serving >= 0),
    protein_per_serving     NUMERIC(5, 1) NOT NULL CHECK (protein_per_serving >= 0),
    carbs_per_serving       NUMERIC(5, 1) NOT NULL CHECK (carbs_per_serving >= 0),
    fat_per_serving         NUMERIC(5, 1) NOT NULL CHECK (fat_per_serving >= 0),
    sugar_per_serving       NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (sugar_per_serving >= 0),
    fiber_per_serving       NUMERIC(5, 1) NOT NULL DEFAULT 0.0 CHECK (fiber_per_serving >= 0),

    image_url               TEXT,
    barcode                 VARCHAR(64),
    source                  VARCHAR(30) NOT NULL DEFAULT 'SYSTEM', -- SYSTEM, OFF, USER_CUSTOM
    vietfood_class_id       INTEGER UNIQUE CHECK (vietfood_class_id BETWEEN 0 AND 67 AND vietfood_class_id <> 27),
    suitable_meal_types     JSONB NOT NULL DEFAULT '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb,
    reference_price_vnd     INTEGER CHECK (reference_price_vnd IS NULL OR reference_price_vnd >= 0),
    feature_vector          JSONB,
    
    is_verified             BOOLEAN NOT NULL DEFAULT TRUE,
    owner_user_id           UUID REFERENCES users (id) ON DELETE CASCADE,
    deleted_at              TIMESTAMPTZ,
    
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index Unique partial cho barcode món hệ thống/dùng chung (Chống Race Condition)
CREATE UNIQUE INDEX uq_foods_shared_barcode 
ON foods (barcode) 
WHERE barcode IS NOT NULL AND owner_user_id IS NULL AND deleted_at IS NULL;

-- Index Full-Text Trigram trên cột name_unaccented của món ăn
CREATE INDEX idx_foods_name_trgm 
ON foods USING GIN (name_unaccented gin_trgm_ops);

-- Index phục vụ phân quyền và lọc nhóm món
CREATE INDEX idx_foods_owner ON foods (owner_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_foods_category ON foods (category) WHERE deleted_at IS NULL;
CREATE INDEX idx_foods_vietfood_class ON foods (vietfood_class_id) WHERE vietfood_class_id IS NOT NULL;

-- 5. Bảng food_ingredients: Liên kết Món ăn - Nguyên liệu & Dị ứng
CREATE TABLE food_ingredients (
    food_id                 UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    ingredient_id           UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    quantity_grams          NUMERIC(7, 1) CHECK (quantity_grams IS NULL OR quantity_grams > 0),
    PRIMARY KEY (food_id, ingredient_id)
);

CREATE INDEX idx_food_ingredients_ingredient ON food_ingredients (ingredient_id);

-- 6. Bảng barcode_misses: Bộ đệm mã vạch không tồn tại trên OFF
CREATE TABLE barcode_misses (
    barcode                 VARCHAR(64) PRIMARY KEY,
    queried_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '7 days')
);

CREATE INDEX idx_barcode_misses_expiry ON barcode_misses (expires_at);

-- 7. Ràng buộc FK từ bảng user_allergies sang bảng ingredients
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_allergies_ingredient'
  ) THEN
    ALTER TABLE user_allergies
      ADD CONSTRAINT fk_user_allergies_ingredient
      FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT;
  END IF;
END $$;
