-- =============================================================================
-- File SQL hoàn chỉnh: Seed & Upsert dữ liệu bảng ingredients
-- Tổng hợp từ:
--   - V20261124140000__food_schema.sql (schema & ràng buộc bảng ingredients)
--   - V20261124140001__seed_ingredients.sql (10 nhóm nguyên liệu dị ứng chuẩn)
--   - Bổ sung nhóm nguyên liệu nấu ăn thông dụng (is_allergen = false) để tạo Recipe
--
-- GIẢI ĐÁP & HƯỚNG DẪN SỬ DỤNG:
-- 1. Nguyên liệu thường (is_allergen = false, source = 'SYSTEM', owner_user_id = NULL):
--    - Hiển thị bình thường khi tìm kiếm nguyên liệu tạo món Recipe
--      (GET /api/ingredients?q=... hoặc allergenOnly=false).
--    - KHÔNG xuất hiện trên màn hình khai báo dị ứng Onboarding
--      (GET /api/ingredients?allergenOnly=true chỉ lọc is_allergen = true).
-- 2. Ràng buộc CHECK bắt buộc trong bảng ingredients:
--    - calories_per_100g BETWEEN 0 AND 950
--    - protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g >= 0
--    - protein_per_100g + carbs_per_100g + fat_per_100g <= 105.0
--    - Không chèn cột name_unaccented (GENERATED ALWAYS AS ... STORED)
-- 3. Ràng buộc code:
--    - Partial Unique Index: uq_ingredients_system_code ON ingredients(code)
--      WHERE code IS NOT NULL AND owner_user_id IS NULL.
--    - Với nguyên liệu SYSTEM, có thể đặt code duy nhất (VD: 'RICE_WHITE') hoặc để NULL.
-- 4. Lưu ý lọc dị ứng:
--    - Hệ thống kiểm tra dị ứng dựa trên ingredient_id có is_allergen = true.
--    - Do đó, khi tạo món có nhóm dị ứng (trứng, sữa, cá, tôm/cua, đậu nành, lúa mì/gluten,
--      đậu phộng, hạt cây, mè, ốc/mực), hãy gắn các ID dị ứng (...0001..0010) tương ứng.
-- 5. File chạy idempotent (ON CONFLICT (id) DO UPDATE): chỉnh sửa và chạy lại không lỗi.
-- =============================================================================

BEGIN;

INSERT INTO ingredients (
    id, code, name,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    is_allergen, source, owner_user_id, is_active
) VALUES
-- -----------------------------------------------------------------------------
-- A. 10 NHÓM NGUYÊN LIỆU DỊ ỨNG HỆ THỐNG (is_allergen = true)
-- -----------------------------------------------------------------------------
('00000000-0000-0000-0000-000000000001', 'PEANUT',      'Đậu phụng (Lạc)',                      567.0, 25.8, 16.1, 49.2,  4.7,  8.5, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000002', 'DAIRY',       'Sữa và Sản phẩm từ sữa',               61.0,  3.2,  4.8,  3.3,  5.1,  0.0, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000003', 'EGG',         'Trứng các loại',                       155.0, 13.0,  1.1, 11.0,  1.1,  0.0, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000004', 'TREE_NUTS',   'Hạt cây (Hạnh nhân, Óc chó, Hạt điều)',579.0, 21.0, 22.0, 50.0,  4.4, 12.5, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000005', 'SOY',         'Đậu nành (Đậu hũ, Tương)',             446.0, 36.0, 30.0, 20.0,  7.3,  9.3, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000006', 'GLUTEN',      'Gluten (Lúa mì, Bột mì)',              364.0, 10.0, 76.0,  1.0,  0.3,  2.7, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000007', 'FISH',        'Cá các loại',                          206.0, 22.0,  0.0, 12.0,  0.0,  0.0, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000008', 'CRUSTACEANS', 'Giáp xác (Tôm, Cua, Ghẹ)',              99.0, 24.0,  0.2,  0.3,  0.0,  0.0, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000009', 'SESAME',      'Vừng (Mè)',                            573.0, 17.7, 23.4, 49.7,  0.3, 11.8, true, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000010', 'MOLLUSCS',    'Thân mềm (Ốc, Mực, Nghêu, Sò)',         92.0, 16.0,  3.0,  1.4,  0.0,  0.0, true, 'SYSTEM', NULL, true),

-- -----------------------------------------------------------------------------
-- B. NGUYÊN LIỆU NẤU ĂN THÔNG DỤNG (is_allergen = false)
-- -----------------------------------------------------------------------------
-- Nhóm Tinh bột & Ngũ cốc (không chứa gluten)
('00000000-0000-0000-0000-000000000101', 'RICE_WHITE',      'Gạo tẻ trắng (khô)',     365.0,  7.1, 80.0,  0.7,  0.1,  1.3, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000102', 'RICE_BROWN',      'Gạo lứt (khô)',          362.0,  7.5, 76.2,  2.7,  0.7,  3.4, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000103', 'STICKY_RICE',     'Gạo nếp (khô)',          370.0,  6.8, 81.7,  0.6,  0.1,  2.8, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000104', 'RICE_NOODLE',     'Bánh phở / Bún tươi',    110.0,  1.7, 25.0,  0.2,  0.1,  0.5, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000105', 'SWEET_POTATO',    'Khoai lang',              86.0,  1.6, 20.1,  0.1,  4.2,  3.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000106', 'POTATO',          'Khoai tây',               77.0,  2.0, 17.5,  0.1,  0.8,  2.2, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000107', 'OATS',            'Yến mạch cán dẹt',       389.0, 16.9, 66.3,  6.9,  0.0, 10.6, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000108', 'CORN',            'Ngô (Bắp) ngọt',          86.0,  3.3, 18.7,  1.4,  6.3,  2.0, false, 'SYSTEM', NULL, true),

-- Nhóm Thịt gia súc & Gia cầm
('00000000-0000-0000-0000-000000000109', 'PORK_LEAN',       'Thịt heo nạc thăn',      143.0, 21.0,  0.0,  6.0,  0.0,  0.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000110', 'PORK_BELLY',      'Thịt ba chỉ heo',        394.0, 14.0,  0.0, 37.0,  0.0,  0.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000111', 'BEEF_LEAN',       'Thịt thăn bò',           142.0, 22.0,  0.0,  5.5,  0.0,  0.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000112', 'CHICKEN_BREAST',  'Ức gà không da',         165.0, 31.0,  0.0,  3.6,  0.0,  0.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000113', 'CHICKEN_THIGH',   'Đùi gà',                 209.0, 26.0,  0.0, 10.9,  0.0,  0.0, false, 'SYSTEM', NULL, true),

-- Nhóm Rau củ & Nấm
('00000000-0000-0000-0000-000000000114', 'WATER_SPINACH',   'Rau muống',               19.0,  2.6,  3.1,  0.2,  0.0,  2.1, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000115', 'BROCCOLI',        'Bông cải xanh',           34.0,  2.8,  6.6,  0.4,  1.7,  2.6, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000116', 'BOK_CHOY',        'Cải thìa / Cải ngọt',     13.0,  1.5,  2.2,  0.2,  1.2,  1.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000117', 'TOMATO',          'Cà chua',                 18.0,  0.9,  3.9,  0.2,  2.6,  1.2, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000118', 'CUCUMBER',        'Dưa leo (Dưa chuột)',      15.0,  0.7,  3.6,  0.1,  1.7,  0.5, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000119', 'CARROT',          'Cà rốt',                  41.0,  0.9,  9.6,  0.2,  4.7,  2.8, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000120', 'ONION',           'Hành tây',                40.0,  1.1,  9.3,  0.1,  4.2,  1.7, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000121', 'SHIITAKE',        'Nấm hương / Nấm đông cô', 34.0,  2.2,  6.8,  0.5,  2.4,  2.5, false, 'SYSTEM', NULL, true),

-- Nhóm Trái cây
('00000000-0000-0000-0000-000000000122', 'BANANA',          'Chuối chín',              89.0,  1.1, 22.8,  0.3, 12.2,  2.6, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000123', 'APPLE',           'Táo tươi',                52.0,  0.3, 13.8,  0.2, 10.4,  2.4, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000124', 'AVOCADO',         'Quả bơ',                 160.0,  2.0,  8.5, 14.7,  0.7,  6.7, false, 'SYSTEM', NULL, true),

-- Nhóm Dầu ăn & Gia vị
('00000000-0000-0000-0000-000000000125', 'OLIVE_OIL',       'Dầu ô liu / Dầu thực vật',884.0,  0.0,  0.0,100.0,  0.0,  0.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000126', 'SUGAR_WHITE',     'Đường cát trắng',        387.0,  0.0,100.0,  0.0,100.0,  0.0, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000127', 'HONEY',           'Mật ong',                304.0,  0.3, 82.4,  0.0, 82.1,  0.2, false, 'SYSTEM', NULL, true),
('00000000-0000-0000-0000-000000000128', 'GARLIC',          'Tỏi tươi',               149.0,  6.4, 33.1,  0.5,  1.0,  2.1, false, 'SYSTEM', NULL, true)
ON CONFLICT (id) DO UPDATE SET
    code              = EXCLUDED.code,
    name              = EXCLUDED.name,
    calories_per_100g = EXCLUDED.calories_per_100g,
    protein_per_100g  = EXCLUDED.protein_per_100g,
    carbs_per_100g    = EXCLUDED.carbs_per_100g,
    fat_per_100g      = EXCLUDED.fat_per_100g,
    sugar_per_100g    = EXCLUDED.sugar_per_100g,
    fiber_per_100g    = EXCLUDED.fiber_per_100g,
    is_allergen       = EXCLUDED.is_allergen,
    source            = EXCLUDED.source,
    owner_user_id     = EXCLUDED.owner_user_id,
    is_active         = EXCLUDED.is_active,
    updated_at        = now();

-- =============================================================================
-- MẪU THÊM NGUYÊN LIỆU MỚI (Bỏ comment /* ... */ để bổ sung thêm)
-- =============================================================================
/*
INSERT INTO ingredients (
    id, code, name,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    is_allergen, source, owner_user_id, is_active
) VALUES
('00000000-0000-0000-0000-000000000129', 'PUMPKIN', 'Bí đỏ', 26.0, 1.0, 6.5, 0.1, 2.8, 0.5, false, 'SYSTEM', NULL, true)
ON CONFLICT (id) DO UPDATE SET
    code              = EXCLUDED.code,
    name              = EXCLUDED.name,
    calories_per_100g = EXCLUDED.calories_per_100g,
    protein_per_100g  = EXCLUDED.protein_per_100g,
    carbs_per_100g    = EXCLUDED.carbs_per_100g,
    fat_per_100g      = EXCLUDED.fat_per_100g,
    sugar_per_100g    = EXCLUDED.sugar_per_100g,
    fiber_per_100g    = EXCLUDED.fiber_per_100g,
    is_allergen       = EXCLUDED.is_allergen,
    is_active         = EXCLUDED.is_active,
    updated_at        = now();
*/

COMMIT;
