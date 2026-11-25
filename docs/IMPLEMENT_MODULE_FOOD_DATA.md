# Kế hoạch triển khai & Đặc tả chi tiết Module FOODDATA — Kallo Backend

**Ngày đồng bộ mã nguồn:** 24/11/2026

**Module:** FOODDATA — Dữ liệu Thực phẩm, Nguyên liệu, Recipe & Mã vạch (SRS Mục 4.9, FR-FD-01 … FR-FD-08)

**Trạng thái đồng bộ:** Đã đồng bộ 100% với Module `PROFILE` (chuẩn hóa mô hình dị ứng theo `ingredients`, cung cấp `IngredientLookup` SPI), bổ sung tính năng **Tự tạo Nguyên liệu & Món ăn cá nhân (User Custom Ingredients & Custom Foods)**.

**Tập dữ liệu AI tích hợp:** VietFood68 (67 lớp món ăn thực tế + 1 lớp đối tượng `Con nguoi` được xử lý lọc tầng AI)

**Phạm vi sở hữu:** Bảng `foods`, `ingredients`, `food_ingredients`, `barcode_misses`, PG Trigram Search, Open Food Facts API v2.

---

## 1. Phạm vi, Mục tiêu & Thống nhất Kiến trúc

Module **FOODDATA** đóng vai trò là "kho từ điển thực phẩm, nguyên liệu và năng lượng" trung tâm của toàn bộ hệ thống Kallo. Mọi tác vụ ghi nhật ký ăn uống (**NUTRI**), tính toán tổng hợp calo hàng ngày (**DailySummary**), đề xuất thực đơn thông minh không dị ứng (**PLAN**), và hiển thị thống kê biểu đồ (**REPORT**) đều phụ thuộc trực tiếp vào tính chính xác của dữ liệu món ăn và nguyên liệu tại module này.

### 1.1. Chuẩn hóa Mô hình Nguyên liệu & Dị ứng (Đồng bộ với Module PROFILE & Mở rộng Custom Items)

Thực hiện đồng bộ theo **Quyết định D16** của Module `PROFILE`: Loại bỏ hoàn toàn mảng `allergens JSONB` cũ và các bảng trung gian `allergens`/`food_allergens`. Dị ứng được quản lý tập trung thông qua bảng nguyên liệu `ingredients` với cờ `is_allergen = true`.

```
                  ┌─────────────────────────────────────────────────────────┐
                  │                 user_health_profiles                    │
                  └────────────────────────────┬────────────────────────────┘
                                               │ (1)
                                               │
                                               ▼ (*)
                                         user_allergies
                                               │ (*)
                                               │
                                               ▼ (1)
 foods (1) ── * food_ingredients * ── (1) ingredients
 (SYSTEM /                              (SYSTEM / USER_CUSTOM)
  OFF / USER_CUSTOM)                           │
                                               └── Cung cấp SPI IngredientLookup cho Module PROFILE
```

#### Ranh giới nghiệp vụ giữa Nguyên liệu Cá nhân (User Custom Ingredient) & Dị ứng:
1. **Nguyên liệu Hệ thống (`source = 'SYSTEM'`, `owner_user_id IS NULL`):** 
   - Có `code` định danh (VD: `PEANUT`, `CRUSTACEANS`, `GLUTEN`...).
   - Được phép đánh cờ `is_allergen = true` (8–10 nhóm dị ứng nền) để hiển thị trên màn hình Onboarding khai báo dị ứng của Module `PROFILE`.
   - Dùng để liên kết dị ứng cho các món ăn VietFood68 và sản phẩm mã vạch Open Food Facts.

2. **Nguyên liệu Cá nhân (`source = 'USER_CUSTOM'`, `owner_user_id = currentUserId`):**
   - Được người dùng tự do khởi tạo (tên, calo, protein, carbs, fat, sugar, fiber trên 100g).
   - **Chỉ phục vụ ghép món Recipe cá nhân (`FR-FD-08`)** của chính người tạo.
   - **Không hiển thị trong danh sách dị ứng `PROFILE`** và không tự động gán nhãn dị ứng dùng chung (đảm bảo an toàn sức khỏe, tránh việc người dùng tạo nguyên liệu rác làm sai lệch bộ lọc dị ứng của hệ thống `PLAN`).

### 1.2. Cổng tích hợp liên Module (Ports & SPIs)

Module `FOODDATA` chịu trách nhiệm cung cấp triển khai cho interface `IngredientLookup` mà Module `PROFILE` cần. Khi `PROFILE` gọi tra cứu dị ứng, hệ thống chỉ trả về các nguyên liệu dị ứng thuộc **hệ thống** (`owner_user_id IS NULL`):

```java
package com.healthcare.kallo.domain.port.in;

public interface IngredientLookup {
    List<IngredientView> findAllAllergenIngredients();
    Optional<IngredientView> findById(UUID id);
    boolean existsById(UUID id);
}
```

### 1.3. Bảng ánh xạ yêu cầu chức năng (FR-FD-01 … FR-FD-08)

| Mã yêu cầu | Tên yêu cầu nghiệp vụ | Mức ưu tiên | Mô tả chi tiết kỹ thuật | Trạng thái |
| :--- | :--- | :--- | :--- | :--- |
| **FR-FD-01** | Lưu danh mục món ăn chuẩn | **M** | Quản lý tên, nhóm món, khẩu phần, nutrition 100g/serving, ảnh, barcode, nguồn và bữa phù hợp. | Chuẩn CSDL mới |
| **FR-FD-02** | Phân cấp món ăn & Nguyên liệu | **M** | Món & Nguyên liệu `SYSTEM`/`OFF` dùng chung; `USER_CUSTOM` gắn `owner_user_id`, hỗ trợ soft delete (`deleted_at` / `is_active = false`). | Chuẩn CSDL mới |
| **FR-FD-03** | Tìm kiếm món/nguyên liệu không dấu | **M** | `immutable_unaccent` + `pg_trgm` kết hợp `ILIKE` / `word_similarity` tối ưu từ ngắn. | Sẵn sàng DDL |
| **FR-FD-04** | Nhãn dị ứng thực phẩm | **M** | Liên kết trực tiếp `food_ingredients` -> `ingredients` (`is_allergen = true`). | Đồng bộ PROFILE |
| **FR-FD-05** | Giá tham khảo | **S** | `reference_price_vnd` phục vụ hiển thị & thuật toán PLAN. | Chuẩn CSDL mới |
| **FR-FD-06** | Hạt giống VietFood68 | **S** | Seed migration 67 lớp thực phẩm, ánh xạ nguyên liệu & dị ứng chuẩn hóa. | Seed sẵn sàng |
| **FR-FD-07a** | Tra cứu barcode nội bộ | **M** | Tra cứu `foods.barcode` (Unique partial index) và cache misses. | Chuẩn CSDL mới |
| **FR-FD-07b** | Open Food Facts Read-Through | **S** | Gọi OFF v2 với timeout $2.5\text{s}$, tự động map `allergens_tags` sang `ingredients`. | Sẵn sàng Client |
| **FR-FD-08a** | Tự tạo Nguyên liệu cá nhân | **M** | Cho phép user nhập chỉ số dinh dưỡng 100g để tạo `USER_CUSTOM` ingredient. | Mới mở rộng |
| **FR-FD-08b** | Tạo món từ nguyên liệu (Recipe) | **M** | Chọn `ingredients` (System + Custom) + `quantity_grams`, backend tự động tính dinh dưỡng snapshot cho món. | Sẵn sàng Service |

### 1.4. Tiêu chí chấp nhận (Acceptance Criteria)

* **AC1 (Hiệu năng tìm kiếm):** API `GET /api/foods/search?q=...` và `GET /api/ingredients?q=...` phản hồi $< 100\text{ ms}$. Tìm kiếm chính xác cả từ tiếng Việt không dấu ngắn (ví dụ: `"pho"`, `"com"`, `"bun"`, `"ca"`).
* **AC2 (Độ trễ quét mã vạch):** Tra cứu barcode nội bộ phản hồi $\le 10\text{ ms}$. Tra cứu OFF timeout cứng $2500\text{ ms}$. Khi sập mạng hoặc timeout, hệ thống không bị treo, không ghi cache 7 ngày cho lỗi mạng transient, trả `404 NOT_FOUND` kèm gợi ý tạo món.
* **AC3 (Bảo vệ món/nguyên liệu hệ thống & Phân quyền IDOR):** Món/nguyên liệu hệ thống (`owner_user_id IS NULL`) không thể sửa/xóa bởi user (`403 FORBIDDEN`). Món/nguyên liệu cá nhân chỉ chính chủ nhân đọc/sửa/xóa.
* **AC4 (Xử lý đối tượng AI ID 27):** Mô hình YOLO AI nhận diện 68 lớp. Lớp ID 27 (`Con nguoi`) bị loại bỏ hoàn toàn tại tầng `FoodRecognitionService`, không lưu vào bảng `foods` và không xuất hiện trên giao diện tìm kiếm.
* **AC5 (Đồng bộ Dị ứng Onboarding):** API `GET /api/ingredients?allergenOnly=true` phản hồi đúng danh sách 8–10 nhóm dị ứng chuẩn của hệ thống (`owner_user_id IS NULL`) để màn hình Onboarding của `PROFILE` lấy dữ liệu hiển thị.
* **AC6 (Tự động cập nhật Nutrition Snapshot của Recipe):** Khi người dùng cập nhật chỉ số 100g của một nguyên liệu cá nhân (`USER_CUSTOM`), hệ thống tự động tính toán lại tổng calo/macro snapshot trên tất cả các món Recipe (`USER_CUSTOM`) của chính người đó đang chứa nguyên liệu này.
* **AC7 (Bảo vệ Nhật ký ăn uống - Soft Delete):** Xóa nguyên liệu cá nhân (`DELETE /api/ingredients/custom/{id}`) dùng `is_active = false`. Xóa món tùy chỉnh (`DELETE /api/foods/custom/{id}`) áp dụng `deleted_at = now()`. Cả hai không làm vỡ dữ liệu nhật ký ăn uống cũ ở module `NUTRI`.

---

## 2. Kiến trúc CSDL & Tệp Migration Flyway

Migration duy nhất cho module dữ liệu thực phẩm và nguyên liệu:

`src/main/resources/db/migration/V20261118100000__food_schema.sql`

```sql
-- 1. Kích hoạt tiện ích mở rộng phục vụ tìm kiếm tiếng Việt không dấu
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Hàm chuyển đổi chuỗi bỏ dấu bất biến (Immutable)
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
    vietfood_class_id       INTEGER UNIQUE CHECK (vietfood_class_id BETWEEN 0 AND 66),
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

-- 5. Bảng food_ingredients: Liên kết Món ăn - Nguyên liệu & Dị ứng
CREATE TABLE food_ingredients (
    food_id                 UUID NOT NULL REFERENCES foods(id) ON DELETE CASCADE,
    ingredient_id           UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    quantity_grams          NUMERIC(7, 1) CHECK (quantity_grams IS NULL OR quantity_grams > 0),
    PRIMARY KEY (food_id, ingredient_id)
);

-- 6. Bảng barcode_misses: Bộ đệm mã vạch không tồn tại trên OFF
CREATE TABLE barcode_misses (
    barcode                 VARCHAR(64) PRIMARY KEY,
    queried_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '7 days')
);

CREATE INDEX idx_barcode_misses_expiry ON barcode_misses (expires_at);
```

### 2.1. Từ điển dữ liệu bảng `ingredients` (Đã cập nhật)

| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa và mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Định danh duy nhất UUID v4 của nguyên liệu. |
| `code` | `VARCHAR(50)` | `NULLABLE` | Mã định danh chuẩn cho món hệ thống (VD: `PEANUT`, `CRUSTACEANS`). Nguyên liệu custom có giá trị `NULL`. |
| `name` | `VARCHAR(255)` | `NOT NULL` | Tên hiển thị nguyên liệu (VD: *Đậu nành*, *Sốt XO tự làm*). |
| `name_unaccented` | `VARCHAR(255)` | `GENERATED STORED` | Tên chuyển về dạng thường không dấu dùng cho PG Trigram index. |
| `calories_per_100g` | `NUMERIC(6,1)` | `0 .. 950` | Năng lượng (kcal) trên 100g nguyên liệu thô. |
| `source` | `VARCHAR(30)` | `NOT NULL` | `SYSTEM` (nguyên liệu dùng chung) hoặc `USER_CUSTOM` (do user tự tạo). |
| `owner_user_id` | `UUID` | `FK -> users` | `NULL` cho nguyên liệu hệ thống; chứa ID người dùng nếu là `USER_CUSTOM`. |
| `is_allergen` | `BOOLEAN` | `NOT NULL` | `true` đối với 8–10 nhóm dị ứng hệ thống (`owner_user_id IS NULL`). Nguyên liệu custom luôn là `false`. |
| `is_active` | `BOOLEAN` | `NOT NULL` | `true` (hoạt động), `false` (xóa mềm). |

---

## 3. Danh mục Nguyên liệu Dị ứng & Hạt giống VietFood68

### 3.1. Danh mục 8-10 Allergen Ingredients Nền (Seed Migration)

File seed danh mục nguyên liệu dị ứng: `src/main/resources/db/migration/V20261118100001__seed_ingredients.sql`

```sql
INSERT INTO ingredients (code, name, calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, is_allergen, source, is_active) VALUES
('PEANUT', 'Đậu phụng (Lạc)', 567.0, 25.8, 16.1, 49.2, true, 'SYSTEM', true),
('CRUSTACEANS', 'Giáp xác (Tôm, Cua, Ghẹ)', 99.0, 24.0, 0.2, 0.3, true, 'SYSTEM', true),
('FISH', 'Cá các loại', 206.0, 22.0, 0.0, 12.0, true, 'SYSTEM', true),
('GLUTEN', 'Gluten (Lúa mì, Bột mì)', 364.0, 10.0, 76.0, 1.0, true, 'SYSTEM', true),
('DAIRY', 'Sữa và Sản phẩm từ sữa', 61.0, 3.2, 4.8, 3.3, true, 'SYSTEM', true),
('EGG', 'Trứng các loại', 155.0, 13.0, 1.1, 11.0, true, 'SYSTEM', true),
('SOY', 'Đậu nành (Đậu hũ, Tương)', 446.0, 36.0, 30.0, 20.0, true, 'SYSTEM', true),
('MOLLUSCS', 'Thân mềm (Ốc, Mực, Nghêu, Sò)', 92.0, 16.0, 3.0, 1.4, true, 'SYSTEM', true),
('TREE_NUTS', 'Hạt cây (Hạnh nhân, Óc chó, Cừu)', 579.0, 21.0, 22.0, 50.0, true, 'SYSTEM', true),
('SESAME', 'Vừng (Mè)', 573.0, 17.7, 23.4, 49.7, true, 'SYSTEM', true);
```

---

## 4. Thiết Kế Chi Tiết Tầng Kỹ Thuật (Architecture & APIs)

### 4.1. Cấu trúc gói Java (`com.healthcare.kallo.*`)

```
src/main/java/com/healthcare/kallo/
├── controller/food/
│   ├── FoodController.java           # /api/foods/** (Search, Detail, CRUD Custom Food/Recipe)
│   ├── IngredientController.java     # /api/ingredients/** (Search, Tra cứu Onboarding, CRUD Custom Ingredient)
│   └── BarcodeController.java        # /api/foods/barcode/{code}
├── model/food/
│   ├── Food.java                     # JPA Entity cho bảng foods
│   ├── Ingredient.java               # JPA Entity cho bảng ingredients
│   ├── FoodIngredient.java           # JPA Entity khóa hợp phần (food_id, ingredient_id)
│   ├── BarcodeMiss.java              # JPA Entity cho bảng barcode_misses
│   └── enums/
│       ├── FoodCategory.java         # Enum nhóm món ăn chuẩn hóa
│       ├── FoodSource.java           # Enum nguồn gốc (SYSTEM, OFF, USER_CUSTOM)
│       └── MealType.java             # Enum bữa ăn (BREAKFAST, LUNCH, DINNER, SNACK)
├── repository/food/
│   ├── FoodRepository.java           # Query pg_trgm unaccent, barcode unique, vietfood_class_id
│   ├── IngredientRepository.java     # Query danh mục dị ứng system & custom ingredient của user
│   ├── FoodIngredientRepository.java # Query liên kết món - nguyên liệu
│   └── BarcodeMissRepository.java    # Cache misses tra cứu
├── service/food/
│   ├── FoodService.java              # Interface nghiệp vụ tìm kiếm, custom food & recipe calculation
│   ├── IngredientService.java        # Interface nghiệp vụ quản lý nguyên liệu cá nhân & hệ thống
│   ├── BarcodeService.java           # Interface Read-Through Cache mã vạch
│   ├── client/
│   │   └── OpenFoodFactsClient.java  # RestClient gọi API Open Food Facts v2
│   ├── adapter/
│   │   └── IngredientLookupImpl.java # SPI Implementation cho Module PROFILE
│   └── impl/
│       ├── FoodServiceImpl.java
│       ├── IngredientServiceImpl.java
│       └── BarcodeServiceImpl.java
└── dto/
    ├── request/food/
    │   ├── CustomIngredientRequest.java # Payload tạo/sửa nguyên liệu cá nhân
    │   ├── CustomFoodRequest.java       # Payload tạo món nhập tay
    │   └── RecipeFoodRequest.java       # Payload tạo món recipe (gồm danh sách ingredient_id + quantity_grams)
    └── response/food/
        ├── FoodResponse.java             # Chi tiết món ăn đầy đủ
        ├── IngredientResponse.java       # Chi tiết nguyên liệu
        └── FoodSearchItemResponse.java   # Phần tử rút gọn trong tìm kiếm
```

### 4.2. Danh mục Endpoints REST API (Đã cập nhật)

| HTTP Method | Đường dẫn Endpoint | Quyền hạn | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/ingredients` | `USER` | Lấy danh mục nguyên liệu. Tra cứu dị ứng (`?allergenOnly=true`) chỉ trả về System Ingredients. Lọc thường (`?q=`) trả về System + Custom Ingredients của chính User. |
| `POST` | `/api/ingredients/custom` | `USER` | Tạo nguyên liệu cá nhân mới (`source = 'USER_CUSTOM'`). |
| `PUT` | `/api/ingredients/custom/{id}` | `USER` | Cập nhật nguyên liệu cá nhân. Tự động tính toán lại dinh dưỡng cho các món Recipe đang chứa nguyên liệu này. |
| `DELETE` | `/api/ingredients/custom/{id}` | `USER` | Xóa mềm nguyên liệu cá nhân (`is_active = false`). |
| `GET` | `/api/foods/search` | `USER` | Tìm kiếm món ăn có phân trang (`q`, `category`, `page`, `size`). Bao gồm món System + Custom của chính User. |
| `GET` | `/api/foods/{id}` | `USER` | Lấy chi tiết món ăn theo UUID (kiểm tra phân quyền IDOR nếu là món custom). |
| `GET` | `/api/foods/barcode/{code}` | `USER` | Tra cứu thực phẩm theo mã vạch qua Read-Through Cache. |
| `POST` | `/api/foods/custom` | `USER` | Tạo món ăn tùy chỉnh nhập thẳng số liệu calo/macro (`source = 'USER_CUSTOM'`). |
| `POST` | `/api/foods/recipe` | `USER` | Tạo món Recipe ghép từ danh sách nguyên liệu (`ingredient_id` + `quantity_grams`). Backend tự động tính Snapshot dinh dưỡng. |
| `PUT` | `/api/foods/custom/{id}` | `USER` | Cập nhật món ăn tùy chỉnh (Chỉ chính chủ mới sửa được). |
| `DELETE` | `/api/foods/custom/{id}` | `USER` | Soft delete món ăn tùy chỉnh (`deleted_at = now()`), giữ nguyên lịch sử nhật ký ăn uống ở `NUTRI`. |

---

## 5. Logic Nghiệp Vụ Tự Tạo Nguyên Liệu & Ghép Món Recipe

### 5.1. Quy trình Tạo Món Recipe từ Nguyên liệu (`POST /api/foods/recipe`)

Khi người dùng gửi danh sách các nguyên liệu kèm trọng lượng gam:

```json
{
  "name": "Bát Yến Mạch Trái Cây Sáng",
  "category": "STAPLE",
  "defaultServingName": "1 tô",
  "items": [
    { "ingredientId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "quantityGrams": 50.0 },
    { "ingredientId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", "quantityGrams": 150.0 }
  ]
}
```

**Các bước xử lý tại `FoodServiceImpl.createRecipeFood()`:**
1. **Kiểm tra quyền sở hữu nguyên liệu (IDOR Check):** Đảm bảo tất cả `ingredientId` trong danh sách thuộc về **Hệ thống** (`owner_user_id IS NULL`) HOẶC thuộc **chính người dùng hiện tại** (`owner_user_id = currentUserId`). Nếu chứa ID của người dùng khác -> ném `403 FORBIDDEN_INGREDIENT_ACCESS`.
2. **Tính toán chỉ số dinh dưỡng 100g & Serving:**
   $$\text{Total Grams} = \sum \text{quantityGrams}_i$$
   $$\text{Total Calo} = \sum \left( \frac{\text{calories\_per\_100g}_i}{100} \times \text{quantityGrams}_i \right)$$
   $$\text{Calories per 100g of Food} = \left( \frac{\text{Total Calo}}{\text{Total Grams}} \right) \times 100$$
   *(Tương tự cho Protein, Carbs, Fat, Sugar, Fiber).*
3. **Lưu bản ghi vào CSDL:**
   - Tạo dòng mới tại `foods` với `source = 'USER_CUSTOM'`, `owner_user_id = currentUserId`.
   - Tạo các dòng tương ứng tại `food_ingredients` lưu nguyên bản `quantity_grams`.

### 5.2. Quy trình Tự Động Tính Toán Lại Recipe Khi Sửa Nguyên Liệu (`PUT /api/ingredients/custom/{id}`)

Khi người dùng cập nhật chỉ số dinh dưỡng (ví dụ sửa `calories_per_100g`) của một `USER_CUSTOM` ingredient:

```java
@Transactional
public IngredientResponse updateCustomIngredient(UUID currentUserId, UUID ingredientId, CustomIngredientRequest request) {
    Ingredient ingredient = ingredientRepository.findById(ingredientId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));

    // 1. Phân quyền chính chủ
    if (ingredient.getSource() == FoodSource.SYSTEM || !currentUserId.equals(ingredient.getOwnerUserId())) {
        throw new BusinessException(ErrorCode.CANNOT_MODIFY_SYSTEM_INGREDIENT);
    }

    // 2. Cập nhật thông tin nguyên liệu
    ingredient.setName(request.getName());
    ingredient.setCaloriesPer100g(request.getCaloriesPer100g());
    ingredient.setProteinPer100g(request.getProteinPer100g());
    ingredient.setCarbsPer100g(request.getCarbsPer100g());
    ingredient.setFatPer100g(request.getFatPer100g());
    ingredientRepository.save(ingredient);

    // 3. Quét tất cả các món Recipe cá nhân đang sử dụng nguyên liệu này để tính toán lại Snapshot
    List<Food> affectedFoods = foodRepository.findAllCustomRecipesByIngredientAndOwner(ingredientId, currentUserId);
    for (Food food : affectedFoods) {
        recalculateAndSaveFoodRecipe(food);
    }

    return mapToIngredientResponse(ingredient);
}
```

---

## 6. Sơ Đồ Trình Tự Nghiệp Vụ (Sequence Diagrams)

### 6.1. Tạo Nguyên liệu Cá nhân & Dùng Tạo Recipe Món Ăn

```sequenceDiagram
    autonumber
    actor Mobile as Mobile App
    participant Controller as Food / Ingredient Controller
    participant Service as Food / Ingredient Service
    participant Repo as Repositories (Food/Ingredient)
    participant DB as PostgreSQL

    Mobile->>Controller: POST /api/ingredients/custom (name, calo/100g, protein...)
    Controller->>Service: createCustomIngredient(currentUserId, request)
    Service->>Repo: save(Ingredient entity with source='USER_CUSTOM')
    Repo-->>Service: Ingredient (Saved with UUID)
    Service-->>Controller: 201 Created (IngredientResponse)
    Controller-->>Mobile: 201 Created

    Mobile->>Controller: POST /api/foods/recipe (name, items: [{ingredientId, quantityGrams}])
    Controller->>Service: createRecipeFood(currentUserId, recipeRequest)
    Service->>Repo: validateIngredientsOwnership(items, currentUserId)
    Note over Service: 1. Kiểm tra 100% ingredientId thuộc SYSTEM hoặc User<br/>2. Sum totalGrams & calculate nutrients per 100g & per serving
    Service->>Repo: save(Food) & saveAll(FoodIngredients)
    Repo-->>Service: Saved Food Recipe
    Service-->>Controller: 201 Created (FoodResponse)
    Controller-->>Mobile: 201 Created (Món recipe sẵn sàng ghi log)
```

---

## 7. Mã Lỗi Nghiệp Vụ (`ErrorCode.java` - Đã cập nhật)

```java
public enum ErrorCode {
  FOOD_NOT_FOUND(HttpStatus.NOT_FOUND, "Food item not found"),
  INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Ingredient not found"),
  CANNOT_MODIFY_SYSTEM_FOOD(HttpStatus.FORBIDDEN, "Cannot modify or delete system food"),
  CANNOT_MODIFY_SYSTEM_INGREDIENT(HttpStatus.FORBIDDEN, "Cannot modify or delete system ingredient"),
  FORBIDDEN_FOOD_ACCESS(HttpStatus.FORBIDDEN, "You do not have permission to access or modify this food item"),
  FORBIDDEN_INGREDIENT_ACCESS(HttpStatus.FORBIDDEN, "You cannot use ingredients owned by another user"),
  INVALID_BARCODE(HttpStatus.BAD_REQUEST, "Invalid barcode format"),
  BARCODE_NOT_FOUND(HttpStatus.NOT_FOUND, "Barcode not found in local database or Open Food Facts"),
  NON_FOOD_DETECTED(HttpStatus.BAD_REQUEST, "No valid food items detected in the uploaded image");
}
```

---

## 8. Kế Hoạch Kiểm Thử Toàn Diện (Test Matrix - Đã cập nhật)

| Lớp kiểm thử | Phương thức / Kịch bản | Mục tiêu kiểm thử | Tiêu chuẩn đạt |
| :--- | :--- | :--- | :--- |
| `IngredientServiceTest` | `testCreateCustomIngredient` | User tạo nguyên liệu cá nhân. | `source = 'USER_CUSTOM'`, `owner_user_id` chính xác, `is_allergen = false`. |
| `IngredientServiceTest` | `testAllergenSearchIsolation` | Gọi `GET /api/ingredients?allergenOnly=true`. | Chỉ trả về nguyên liệu hệ thống (`owner_user_id IS NULL`), không chứa bất kỳ nguyên liệu custom nào. |
| `FoodRecipeTest` | `testCreateRecipeWithCustomIngredient` | Ghép món recipe từ nguyên liệu custom và nguyên liệu hệ thống. | Tính toán chính xác tổng calo/macro trên 100g & khẩu phần. |
| `FoodRecipeTest` | `testPreventIDOROnIngredients` | User A dùng `ingredient_id` của User B để tạo recipe. | Hệ thống ném `403 FORBIDDEN_INGREDIENT_ACCESS`. |
| `FoodRecipeTest` | `testRecalculateRecipeOnIngredientUpdate` | User A sửa calo/100g của 1 nguyên liệu custom. | Tất cả món Recipe của User A chứa nguyên liệu đó tự động được tính lại snapshot calo. |
| `IngredientServiceTest` | `testSoftDeleteCustomIngredient` | Gọi `DELETE /api/ingredients/custom/{id}`. | `is_active = false`, món recipe cũ vẫn giữ nguyên liên kết và hiển thị bình thường. |
| `BarcodeServiceTest` | `testReadThroughCache_LocalHit` | Quét mã vạch có sẵn trong CSDL. | Phản hồi $\le 5\text{ ms}$, không kích hoạt RestClient. |

---

## 9. Quản Lý Rủi Ro Technical & Cấu Hình An Toàn Dữ Liệu

1. **Chống lạm dụng / Spam dữ liệu nguyên liệu:**
   - Tầng Controller áp dụng giới hạn Rate Limit: Tối đa 30 nguyên liệu custom / user / ngày.
   - Validation kiểm tra chặt chẽ ngưỡng dinh dưỡng: `calories_per_100g <= 950` và tổng `protein + carbs + fat <= 105.0g` trên 100g nguyên liệu.

2. **Bảo mật ranh giới Dị ứng:**
   - Tầng Service của `IngredientLookupImpl` (cung cấp SPI cho `PROFILE`) áp dụng truy vấn cứng:
     ```sql
     SELECT i FROM Ingredient i WHERE i.isAllergen = true AND i.ownerUserId IS NULL AND i.isActive = true
     ```
   - Điều này triệt tiêu hoàn toàn khả năng nguyên liệu custom do người dùng tự tạo lọt vào danh sách dị ứng chung của hệ thống.

3. **Bảo vệ toàn vẹn Nhật ký Ăn uống (`user_food_logs`):**
   - Khi xóa nguyên liệu cá nhân -> áp dụng Soft Delete (`is_active = false`).
   - Khi xóa món ăn cá nhân -> áp dụng Soft Delete (`deleted_at = now()`).
   - Dữ liệu nutrition snapshot đã ghi nhận tại nhật ký ăn uống ở module `NUTRI` không bao giờ bị ảnh hưởng khi người dùng chỉnh sửa hoặc xóa món/nguyên liệu cá nhân trong quá khứ.