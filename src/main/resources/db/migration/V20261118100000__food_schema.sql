-- ============================================================
-- 1. Tiện ích mở rộng tìm kiếm mờ tiếng Việt không dấu
-- ============================================================
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================================
-- 2. Hàm chuyển đổi chuỗi bỏ dấu bất biến (Immutable)
-- ============================================================
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text AS $$
  SELECT public.unaccent('public.unaccent', $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- ============================================================
-- 3. Bảng foods: Lưu trữ thực phẩm hệ thống, OFF và món tùy chỉnh
-- ============================================================
CREATE TABLE foods (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255) NOT NULL,
    name_unaccented         VARCHAR(255) NOT NULL,
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
    source                  VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    vietfood_class_id       INTEGER UNIQUE CHECK (vietfood_class_id BETWEEN 0 AND 67),
    allergens               JSONB NOT NULL DEFAULT '[]'::jsonb,
    suitable_meal_types     JSONB NOT NULL DEFAULT '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb,
    reference_price_vnd     INTEGER CHECK (reference_price_vnd IS NULL OR reference_price_vnd >= 0),
    
    is_verified             BOOLEAN NOT NULL DEFAULT TRUE,
    owner_user_id           UUID REFERENCES users (id) ON DELETE CASCADE,
    
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Chỉ mục tìm kiếm Full-Text tiếng Việt không dấu (Trigram GIN)
CREATE INDEX idx_foods_name_trgm 
ON foods USING GIN (immutable_unaccent(lower(name)) gin_trgm_ops);

-- Chỉ mục phục vụ tìm kiếm theo mã vạch
CREATE INDEX idx_foods_barcode ON foods (barcode) WHERE barcode IS NOT NULL;

-- Chỉ mục phục vụ phân quyền dữ liệu người dùng
CREATE INDEX idx_foods_owner ON foods (owner_user_id);

-- Chỉ mục lọc theo nhóm món
CREATE INDEX idx_foods_category ON foods (category);

-- ============================================================
-- 4. Bảng barcode_misses: Lưu bộ đệm các mã vạch quét thất bại
-- ============================================================
CREATE TABLE barcode_misses (
    barcode                 VARCHAR(64) PRIMARY KEY,
    queried_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '7 days')
);

CREATE INDEX idx_barcode_misses_expiry ON barcode_misses (expires_at);
