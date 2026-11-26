-- =============================================================================
-- File SQL hoàn chỉnh: Seed & Upsert dữ liệu bảng foods & food_ingredients
-- Tổng hợp đầy đủ các trường từ:
--   - V20261124140000__food_schema.sql (schema bảng foods)
--   - V20261124140002__seed_vietfood68.sql (67 món VietFood68 + dị ứng)
--   - V20261125120000__plan_schema.sql (12D feature_vector cho 67 món)
--
-- HƯỚNG DẪN SỬ DỤNG & MỞ RỘNG DATA:
-- 1. File này chạy idempotent (ON CONFLICT DO UPDATE): chạy lại nhiều lần không lỗi.
--    Khi chỉnh sửa giá trị của món cũ và chạy lại, dữ liệu sẽ tự động được cập nhật.
--    Lưu ý: image_url và barcode dùng COALESCE để không bị ghi đè thành NULL nếu DB đã có sẵn.
-- 2. Giá trị hợp lệ cho category:
--    STAPLE, NOODLE, RICE, SOUP, MEAT, SEAFOOD, VEGETABLE, SNACK, BEVERAGE, OTHER
-- 3. Giá trị hợp lệ cho suitable_meal_types (JSONB array):
--    "BREAKFAST", "LUNCH", "DINNER", "SNACK"
-- 4. Thứ tự 12 chiều của feature_vector (mỗi chiều trong [0.0, 1.0]):
--    [SOUP_BASED, DRY_BASED, BEEF, PORK, POULTRY, SEAFOOD, VEGETARIAN,
--     FRIED_FATTY, STEAMED_BOILED, SPICY, SWEET, HIGH_PROTEIN]
-- 5. Khi THÊM MÓN MỚI ngoài 67 món VietFood68:
--    - Đặt id là UUID cố định mới (vd: '10000000-0000-0000-0000-000000000101') hoặc gen_random_uuid()
--    - BẮT BUỘC để vietfood_class_id = NULL (vì 0..67 <> 27 đã được gán UNIQUE)
--    - Không chèn cột name_unaccented (cột này là GENERATED ALWAYS AS ... STORED)
-- 6. Mã UUID 10 nguyên liệu dị ứng hệ thống (bảng ingredients):
--    PEANUT      = 00000000-0000-0000-0000-000000000001
--    DAIRY       = 00000000-0000-0000-0000-000000000002
--    EGG         = 00000000-0000-0000-0000-000000000003
--    TREE_NUTS   = 00000000-0000-0000-0000-000000000004
--    SOY         = 00000000-0000-0000-0000-000000000005
--    GLUTEN      = 00000000-0000-0000-0000-000000000006
--    FISH        = 00000000-0000-0000-0000-000000000007
--    CRUSTACEANS = 00000000-0000-0000-0000-000000000008
--    SESAME      = 00000000-0000-0000-0000-000000000009
--    MOLLUSCS    = 00000000-0000-0000-0000-000000000010
-- =============================================================================

BEGIN;

INSERT INTO foods (
    id, name, category, default_serving_name, default_serving_grams,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    calories_per_serving, protein_per_serving, carbs_per_serving, fat_per_serving, sugar_per_serving, fiber_per_serving,
    image_url, barcode, source, vietfood_class_id, suitable_meal_types,
    reference_price_vnd, feature_vector, is_verified, owner_user_id, deleted_at
) VALUES
('10000000-0000-0000-0000-000000000000', 'Bánh canh', 'NOODLE', '1 tô', 450.0, 89.0, 4.5, 14.2, 1.8, 0.5, 0.6, 400, 20.3, 63.9, 8.1, 2.3, 2.7, NULL, NULL, 'SYSTEM', 0, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.7, 0.0, 0.3, 0.1, 0.1, 0.9, 0.1, 0.1, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000001', 'Bánh chưng', 'STAPLE', '1 góc (1/8 bánh)', 150.0, 220.0, 7.8, 32.5, 6.8, 0.8, 1.5, 330, 11.7, 48.8, 10.2, 1.2, 2.3, NULL, NULL, 'SYSTEM', 1, '["BREAKFAST", "LUNCH"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 0.8, 0.0, 0.0, 0.1, 0.4, 0.6, 0.1, 0.1, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000002', 'Bánh cuốn', 'STAPLE', '1 đĩa', 250.0, 145.0, 5.2, 22.0, 4.1, 0.8, 0.9, 363, 13.0, 55.0, 10.3, 2.0, 2.3, NULL, NULL, 'SYSTEM', 2, '["BREAKFAST", "DINNER"]'::jsonb, 35000, '[0.0, 1.0, 0.0, 0.7, 0.0, 0.0, 0.2, 0.2, 0.9, 0.1, 0.1, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000003', 'Bánh khọt', 'SNACK', '1 đĩa (8 cái)', 200.0, 185.0, 6.5, 23.0, 7.5, 1.2, 0.8, 370, 13.0, 46.0, 15.0, 2.4, 1.6, NULL, NULL, 'SYSTEM', 3, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 45000, '[0.0, 1.0, 0.0, 0.2, 0.0, 0.8, 0.1, 0.9, 0.0, 0.1, 0.2, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000004', 'Bánh mì', 'STAPLE', '1 ổ thịt đầy đủ', 180.0, 250.0, 9.8, 35.2, 7.9, 2.5, 1.8, 450, 17.6, 63.4, 14.2, 4.5, 3.2, NULL, NULL, 'SYSTEM', 4, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 0.8, 0.2, 0.0, 0.1, 0.4, 0.2, 0.2, 0.2, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000005', 'Bánh tráng', 'SNACK', '1 cái bánh nướng', 50.0, 330.0, 3.5, 75.0, 1.2, 0.5, 1.0, 165, 1.8, 37.5, 0.6, 0.3, 0.5, NULL, NULL, 'SYSTEM', 5, '["SNACK"]'::jsonb, 10000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.1, 0.1, 0.1, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000006', 'Bánh tráng trộn', 'SNACK', '1 bịch', 120.0, 280.0, 7.5, 42.0, 9.2, 4.5, 1.2, 336, 9.0, 50.4, 11.0, 5.4, 1.4, NULL, NULL, 'SYSTEM', 6, '["SNACK"]'::jsonb, 20000, '[0.0, 0.9, 0.3, 0.0, 0.0, 0.2, 0.2, 0.5, 0.0, 0.7, 0.4, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000007', 'Bánh xèo', 'STAPLE', '1 cái lớn', 250.0, 190.0, 6.2, 22.5, 8.5, 1.0, 1.2, 475, 15.5, 56.3, 21.3, 2.5, 3.0, NULL, NULL, 'SYSTEM', 7, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.6, 0.0, 0.7, 0.2, 0.9, 0.0, 0.1, 0.2, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000008', 'Bò kho', 'SOUP', '1 tô vừa', 350.0, 120.0, 10.5, 7.5, 5.2, 1.5, 1.1, 420, 36.8, 26.3, 18.2, 5.3, 3.9, NULL, NULL, 'SYSTEM', 8, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, '[1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.4, 0.6, 0.5, 0.2, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000009', 'Bò lá lốt', 'MEAT', '1 đĩa (8 cuốn)', 200.0, 175.0, 14.5, 6.0, 10.2, 0.8, 1.4, 350, 29.0, 12.0, 20.4, 1.6, 2.8, NULL, NULL, 'SYSTEM', 9, '["LUNCH", "DINNER"]'::jsonb, 60000, '[0.0, 0.8, 1.0, 0.1, 0.0, 0.0, 0.2, 0.6, 0.2, 0.2, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000000a', 'Bông cải', 'VEGETABLE', '1 đĩa luộc', 150.0, 34.0, 2.8, 6.6, 0.4, 1.7, 2.6, 51, 4.2, 9.9, 0.6, 2.6, 3.9, NULL, NULL, 'SYSTEM', 10, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000000b', 'Bún', 'STAPLE', '1 bát bún tươi', 150.0, 110.0, 1.7, 25.0, 0.2, 0.1, 0.5, 165, 2.6, 37.5, 0.3, 0.2, 0.8, NULL, NULL, 'SYSTEM', 11, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 10000, '[0.2, 0.8, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.9, 0.0, 0.0, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000000c', 'Bún bò Huế', 'NOODLE', '1 tô', 500.0, 95.0, 6.2, 11.5, 2.8, 0.8, 0.7, 475, 31.0, 57.5, 14.0, 4.0, 3.5, NULL, NULL, 'SYSTEM', 12, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, '[1.0, 0.0, 0.8, 0.4, 0.0, 0.0, 0.1, 0.3, 0.7, 0.8, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000000d', 'Bún chả', 'NOODLE', '1 phần', 400.0, 135.0, 7.8, 16.5, 4.2, 2.5, 0.8, 540, 31.2, 66.0, 16.8, 10.0, 3.2, NULL, NULL, 'SYSTEM', 13, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.3, 0.8, 0.0, 1.0, 0.0, 0.0, 0.2, 0.6, 0.3, 0.2, 0.5, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000000e', 'Bún đậu', 'NOODLE', '1 mẹt đầy đủ', 450.0, 150.0, 8.5, 15.2, 6.2, 1.0, 1.2, 675, 38.3, 68.4, 27.9, 4.5, 5.4, NULL, NULL, 'SYSTEM', 14, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 1.0, 0.0, 0.9, 0.0, 0.2, 0.2, 0.8, 0.3, 0.3, 0.1, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000000f', 'Bún mắm', 'NOODLE', '1 tô', 500.0, 105.0, 7.2, 13.0, 2.5, 1.2, 0.9, 525, 36.0, 65.0, 12.5, 6.0, 4.5, NULL, NULL, 'SYSTEM', 15, '["LUNCH", "DINNER"]'::jsonb, 55000, '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.1, 0.2, 0.8, 0.4, 0.2, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000010', 'Bún riêu', 'NOODLE', '1 tô', 480.0, 92.0, 5.8, 12.0, 2.1, 0.9, 0.8, 442, 27.8, 57.6, 10.1, 4.3, 3.8, NULL, NULL, 'SYSTEM', 16, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000011', 'Cá', 'SEAFOOD', '1 lát cá rán', 150.0, 180.0, 20.5, 0.5, 10.5, 0.0, 0.0, 270, 30.8, 0.8, 15.8, 0.0, 0.0, NULL, NULL, 'SYSTEM', 17, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.8, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.1, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000012', 'Cà chua', 'VEGETABLE', '1 quả vừa', 100.0, 18.0, 0.9, 3.9, 0.2, 2.6, 1.2, 18, 0.9, 3.9, 0.2, 2.6, 1.2, NULL, NULL, 'SYSTEM', 18, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, '[0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.3, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000013', 'Cà pháo', 'VEGETABLE', '1 đĩa nhỏ muối', 80.0, 24.0, 1.2, 4.5, 0.1, 1.5, 1.8, 19, 1.0, 3.6, 0.1, 1.2, 1.4, NULL, NULL, 'SYSTEM', 19, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.7, 0.2, 0.1, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000014', 'Cà rốt', 'VEGETABLE', '1 củ vừa', 100.0, 41.0, 0.9, 9.6, 0.2, 4.7, 2.8, 41, 0.9, 9.6, 0.2, 4.7, 2.8, NULL, NULL, 'SYSTEM', 20, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 8000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.4, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000015', 'Canh', 'SOUP', '1 bát canh rau', 250.0, 28.0, 1.5, 3.2, 0.8, 0.6, 1.0, 70, 3.8, 8.0, 2.0, 1.5, 2.5, NULL, NULL, 'SYSTEM', 21, '["LUNCH", "DINNER"]'::jsonb, 15000, '[1.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000016', 'Chả', 'MEAT', '1 khoanh chả lụa', 100.0, 215.0, 15.2, 3.5, 15.5, 0.5, 0.0, 215, 15.2, 3.5, 15.5, 0.5, 0.0, NULL, NULL, 'SYSTEM', 22, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.3, 0.8, 0.2, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000017', 'Chả giò', 'SNACK', '1 đĩa (5 cuốn)', 180.0, 260.0, 8.5, 22.0, 15.2, 1.2, 1.0, 468, 15.3, 39.6, 27.4, 2.2, 1.8, NULL, NULL, 'SYSTEM', 23, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 45000, '[0.0, 1.0, 0.0, 0.8, 0.0, 0.3, 0.1, 1.0, 0.0, 0.1, 0.1, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000018', 'Chanh', 'VEGETABLE', '1 quả', 60.0, 29.0, 1.1, 9.3, 0.3, 2.5, 2.8, 17, 0.7, 5.6, 0.2, 1.5, 1.7, NULL, NULL, 'SYSTEM', 24, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 3000, '[0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.1, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000019', 'Cơm', 'RICE', '1 chén cơm trắng', 150.0, 130.0, 2.7, 28.2, 0.3, 0.1, 0.4, 195, 4.1, 42.3, 0.5, 0.2, 0.6, NULL, NULL, 'SYSTEM', 25, '["LUNCH", "DINNER"]'::jsonb, 5000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000001a', 'Cơm tấm', 'RICE', '1 đĩa sườn bì chả', 400.0, 185.0, 8.2, 22.5, 6.8, 1.5, 1.0, 740, 32.8, 90.0, 27.2, 6.0, 4.0, NULL, NULL, 'SYSTEM', 26, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000001c', 'Củ kiệu', 'VEGETABLE', '1 đĩa nhỏ chua ngọt', 50.0, 65.0, 0.8, 15.0, 0.1, 12.0, 1.5, 33, 0.4, 7.5, 0.1, 6.0, 0.8, NULL, NULL, 'SYSTEM', 28, '["LUNCH", "DINNER"]'::jsonb, 15000, '[0.0, 0.6, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.2, 0.6, 0.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000001d', 'Cua', 'SEAFOOD', '1 con cua hấp vừa', 250.0, 87.0, 18.2, 0.0, 1.1, 0.0, 0.0, 218, 45.5, 0.0, 2.8, 0.0, 0.0, NULL, NULL, 'SYSTEM', 29, '["LUNCH", "DINNER"]'::jsonb, 150000, '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.1, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000001e', 'Đậu hũ', 'STAPLE', '1 bìa đậu rán', 150.0, 140.0, 11.5, 3.2, 8.8, 0.5, 1.0, 210, 17.3, 4.8, 13.2, 0.8, 1.5, NULL, NULL, 'SYSTEM', 30, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 0.9, 0.0, 0.0, 0.0, 0.0, 1.0, 0.7, 0.3, 0.0, 0.0, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000001f', 'Dưa chua', 'VEGETABLE', '1 chén nhỏ', 100.0, 19.0, 1.1, 3.2, 0.2, 1.0, 1.5, 19, 1.1, 3.2, 0.2, 1.0, 1.5, NULL, NULL, 'SYSTEM', 31, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.1, 0.2, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000020', 'Dưa leo', 'VEGETABLE', '1 quả vừa', 120.0, 15.0, 0.7, 3.6, 0.1, 1.7, 0.5, 18, 0.8, 4.3, 0.1, 2.0, 0.6, NULL, NULL, 'SYSTEM', 32, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.2, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000021', 'Gỏi cuốn', 'SNACK', '1 đĩa (4 cuốn)', 240.0, 130.0, 6.8, 19.5, 2.5, 1.5, 1.2, 312, 16.3, 46.8, 6.0, 3.6, 2.9, NULL, NULL, 'SYSTEM', 33, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 35000, '[0.0, 0.8, 0.0, 0.5, 0.0, 0.6, 0.5, 0.0, 1.0, 0.1, 0.2, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000022', 'Hamburger', 'STAPLE', '1 cái vừa', 200.0, 260.0, 13.5, 28.0, 10.5, 4.5, 1.8, 520, 27.0, 56.0, 21.0, 9.0, 3.6, NULL, NULL, 'SYSTEM', 34, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 1.0, 1.0, 0.2, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000023', 'Heo quay', 'MEAT', '1 đĩa nhỏ', 150.0, 320.0, 16.0, 1.5, 28.0, 0.5, 0.0, 480, 24.0, 2.3, 42.0, 0.8, 0.0, NULL, NULL, 'SYSTEM', 35, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.2, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000024', 'Hủ tiếu', 'NOODLE', '1 tô Nam Vang', 450.0, 95.0, 5.5, 13.0, 2.1, 0.8, 0.6, 428, 24.8, 58.5, 9.5, 3.6, 2.7, NULL, NULL, 'SYSTEM', 36, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.7, 0.0, 0.5, 0.1, 0.1, 0.9, 0.1, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000025', 'Khổ qua thịt', 'SOUP', '1 quả nhồi thịt', 200.0, 75.0, 6.2, 4.0, 3.5, 1.0, 1.5, 150, 12.4, 8.0, 7.0, 2.0, 3.0, NULL, NULL, 'SYSTEM', 37, '["LUNCH", "DINNER"]'::jsonb, 30000, '[1.0, 0.0, 0.0, 0.8, 0.0, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000026', 'Khoai tây chiên', 'SNACK', '1 phần vừa', 120.0, 312.0, 3.4, 41.5, 15.0, 0.3, 3.8, 374, 4.1, 49.8, 18.0, 0.4, 4.6, NULL, NULL, 'SYSTEM', 38, '["SNACK"]'::jsonb, 30000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.8, 1.0, 0.0, 0.1, 0.1, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000027', 'Lẩu', 'SOUP', '1 phần ăn kèm nước', 500.0, 85.0, 7.0, 6.5, 3.2, 1.2, 1.5, 425, 35.0, 32.5, 16.0, 6.0, 7.5, NULL, NULL, 'SYSTEM', 39, '["LUNCH", "DINNER"]'::jsonb, 180000, '[1.0, 0.0, 0.6, 0.3, 0.2, 0.6, 0.4, 0.2, 0.8, 0.6, 0.2, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000028', 'Lòng heo', 'MEAT', '1 đĩa luộc', 150.0, 165.0, 15.5, 0.5, 11.2, 0.0, 0.0, 248, 23.3, 0.8, 16.8, 0.0, 0.0, NULL, NULL, 'SYSTEM', 40, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 0.9, 0.1, 0.0, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000029', 'Mì', 'NOODLE', '1 tô mì gói xào/nước', 350.0, 130.0, 4.5, 18.5, 4.2, 1.2, 0.8, 455, 15.8, 64.8, 14.7, 4.2, 2.8, NULL, NULL, 'SYSTEM', 41, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 30000, '[0.8, 0.3, 0.2, 0.5, 0.0, 0.2, 0.1, 0.6, 0.3, 0.4, 0.1, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000002a', 'Mực', 'SEAFOOD', '1 con mực xào/hấp', 150.0, 92.0, 17.5, 3.0, 1.4, 0.0, 0.0, 138, 26.3, 4.5, 2.1, 0.0, 0.0, NULL, NULL, 'SYSTEM', 42, '["LUNCH", "DINNER"]'::jsonb, 70000, '[0.0, 0.7, 0.0, 0.0, 0.0, 1.0, 0.0, 0.4, 0.7, 0.2, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000002b', 'Nấm', 'VEGETABLE', '1 đĩa nấm xào', 150.0, 45.0, 3.1, 6.2, 0.8, 1.5, 2.1, 68, 4.7, 9.3, 1.2, 2.3, 3.2, NULL, NULL, 'SYSTEM', 43, '["LUNCH", "DINNER"]'::jsonb, 30000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.0, 0.1, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000002c', 'Ốc', 'SEAFOOD', '1 đĩa ốc luộc/xào', 200.0, 85.0, 14.2, 3.5, 1.2, 0.5, 0.0, 170, 28.4, 7.0, 2.4, 1.0, 0.0, NULL, NULL, 'SYSTEM', 44, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 55000, '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.3, 0.7, 0.5, 0.1, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000002d', 'Ớt chuông', 'VEGETABLE', '1 quả vừa', 120.0, 26.0, 1.0, 6.0, 0.3, 4.2, 2.1, 31, 1.2, 7.2, 0.4, 5.0, 2.5, NULL, NULL, 'SYSTEM', 45, '["LUNCH", "DINNER"]'::jsonb, 12000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.3, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000002e', 'Phở', 'NOODLE', '1 tô phở bò', 500.0, 90.0, 5.8, 11.2, 2.2, 0.8, 0.6, 450, 29.0, 56.0, 11.0, 4.0, 3.0, NULL, NULL, 'SYSTEM', 46, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, '[1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000002f', 'Phô mai', 'STAPLE', '1 miếng tam giác', 30.0, 350.0, 21.0, 2.0, 28.0, 1.5, 0.0, 105, 6.3, 0.6, 8.4, 0.5, 0.0, NULL, NULL, 'SYSTEM', 47, '["BREAKFAST", "SNACK"]'::jsonb, 8000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.4, 0.6, 0.2, 0.0, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000030', 'Rau', 'VEGETABLE', '1 đĩa rau muống xào', 150.0, 40.0, 2.5, 3.8, 1.8, 0.5, 2.2, 60, 3.8, 5.7, 2.7, 0.8, 3.3, NULL, NULL, 'SYSTEM', 48, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.1, 0.1, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000031', 'Salad', 'VEGETABLE', '1 dĩa dầu giấm', 180.0, 55.0, 1.5, 5.2, 3.2, 2.5, 1.8, 99, 2.7, 9.4, 5.8, 4.5, 3.2, NULL, NULL, 'SYSTEM', 49, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.9, 0.0, 0.2, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000032', 'Thịt bò', 'MEAT', '1 phần xào/nướng', 150.0, 210.0, 26.0, 0.0, 11.5, 0.0, 0.0, 315, 39.0, 0.0, 17.3, 0.0, 0.0, NULL, NULL, 'SYSTEM', 50, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 0.9, 1.0, 0.0, 0.0, 0.0, 0.0, 0.6, 0.3, 0.2, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000033', 'Thịt gà', 'MEAT', '1 góc đùi luộc/rán', 160.0, 215.0, 24.5, 0.5, 12.0, 0.0, 0.0, 344, 39.2, 0.8, 19.2, 0.0, 0.0, NULL, NULL, 'SYSTEM', 51, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.4, 0.7, 0.1, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000034', 'Thịt heo', 'MEAT', '1 đĩa thịt luộc', 150.0, 240.0, 22.0, 0.0, 16.5, 0.0, 0.0, 360, 33.0, 0.0, 24.8, 0.0, 0.0, NULL, NULL, 'SYSTEM', 52, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 0.9, 0.0, 0.0, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000035', 'Thịt kho', 'MEAT', '1 bát thịt kho tàu', 200.0, 260.0, 16.5, 3.5, 20.0, 3.0, 0.0, 520, 33.0, 7.0, 40.0, 6.0, 0.0, NULL, NULL, 'SYSTEM', 53, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.2, 0.7, 0.0, 1.0, 0.0, 0.0, 0.0, 0.6, 0.4, 0.1, 0.4, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000036', 'Thịt nướng', 'MEAT', '1 xiên', 100.0, 280.0, 20.0, 4.0, 20.0, 3.5, 0.0, 280, 20.0, 4.0, 20.0, 3.5, 0.0, NULL, NULL, 'SYSTEM', 54, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 30000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.3, 0.3, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000037', 'Tôm', 'SEAFOOD', '1 đĩa tôm luộc', 150.0, 99.0, 24.0, 0.2, 0.3, 0.0, 0.0, 149, 36.0, 0.3, 0.5, 0.0, 0.0, NULL, NULL, 'SYSTEM', 55, '["LUNCH", "DINNER"]'::jsonb, 60000, '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000038', 'Trứng', 'STAPLE', '1 quả trứng gà ốp la', 60.0, 155.0, 13.0, 1.1, 11.0, 1.1, 0.0, 93, 7.8, 0.7, 6.6, 0.7, 0.0, NULL, NULL, 'SYSTEM', 56, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 7000, '[0.0, 0.8, 0.0, 0.0, 0.3, 0.0, 0.2, 0.6, 0.4, 0.0, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000039', 'Xôi', 'STAPLE', '1 gói xôi mặn', 200.0, 260.0, 8.5, 45.0, 5.2, 1.5, 1.8, 520, 17.0, 90.0, 10.4, 3.0, 3.6, NULL, NULL, 'SYSTEM', 57, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 0.6, 0.4, 0.0, 0.1, 0.4, 0.7, 0.1, 0.2, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000003a', 'Bánh bèo', 'SNACK', '1 mâm (6 chén)', 180.0, 135.0, 4.8, 22.0, 3.2, 0.8, 0.6, 243, 8.6, 39.6, 5.8, 1.4, 1.1, NULL, NULL, 'SYSTEM', 58, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 35000, '[0.0, 0.9, 0.0, 0.3, 0.0, 0.6, 0.1, 0.2, 0.9, 0.2, 0.2, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000003b', 'Cao lầu', 'NOODLE', '1 tô Hội An', 420.0, 115.0, 7.0, 16.0, 2.5, 1.0, 1.2, 483, 29.4, 67.2, 10.5, 4.2, 5.0, NULL, NULL, 'SYSTEM', 59, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.3, 0.8, 0.0, 0.9, 0.0, 0.0, 0.2, 0.4, 0.5, 0.2, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000003c', 'Mì Quảng', 'NOODLE', '1 tô tôm thịt', 450.0, 120.0, 7.2, 16.5, 2.8, 1.0, 1.0, 540, 32.4, 74.3, 12.6, 4.5, 4.5, NULL, NULL, 'SYSTEM', 60, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[0.4, 0.7, 0.0, 0.6, 0.2, 0.6, 0.1, 0.3, 0.7, 0.3, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000003d', 'Cơm chiên dương châu', 'RICE', '1 đĩa', 350.0, 175.0, 6.8, 26.0, 4.8, 1.2, 1.0, 613, 23.8, 91.0, 16.8, 4.2, 3.5, NULL, NULL, 'SYSTEM', 61, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.6, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.1, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000003e', 'Bún chả cá', 'NOODLE', '1 tô', 450.0, 88.0, 5.5, 12.0, 1.8, 0.8, 0.7, 396, 24.8, 54.0, 8.1, 3.6, 3.2, NULL, NULL, 'SYSTEM', 62, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-00000000003f', 'Cơm chiên gà', 'RICE', '1 đĩa', 350.0, 185.0, 9.2, 25.5, 5.1, 1.0, 0.9, 648, 32.2, 89.3, 17.9, 3.5, 3.2, NULL, NULL, 'SYSTEM', 63, '["LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.9, 0.0, 0.1, 0.1, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000040', 'Cháo lòng', 'SOUP', '1 tô', 450.0, 92.0, 5.8, 11.0, 2.5, 0.5, 0.4, 414, 26.1, 49.5, 11.3, 2.3, 1.8, NULL, NULL, 'SYSTEM', 64, '["BREAKFAST", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.0, 0.2, 0.9, 0.2, 0.0, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000041', 'Nộm hoa chuối', 'VEGETABLE', '1 đĩa tai heo', 200.0, 75.0, 4.2, 8.0, 2.8, 5.0, 3.5, 150, 8.4, 16.0, 5.6, 10.0, 7.0, NULL, NULL, 'SYSTEM', 65, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.4, 0.0, 0.4, 0.0, 0.0, 0.7, 0.0, 0.9, 0.3, 0.6, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000042', 'Nui xào bò', 'NOODLE', '1 đĩa', 300.0, 165.0, 8.5, 22.0, 4.8, 1.8, 1.2, 495, 25.5, 66.0, 14.4, 5.4, 3.6, NULL, NULL, 'SYSTEM', 66, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.2, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000043', 'Súp cua', 'SOUP', '1 chén', 250.0, 82.0, 5.2, 10.5, 1.9, 0.8, 0.6, 205, 13.0, 26.3, 4.8, 2.0, 1.5, NULL, NULL, 'SYSTEM', 67, '["BREAKFAST", "SNACK", "DINNER"]'::jsonb, 30000, '[1.0, 0.0, 0.0, 0.0, 0.3, 0.9, 0.1, 0.1, 0.9, 0.1, 0.1, 0.7]'::jsonb, true, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name                  = EXCLUDED.name,
    category              = EXCLUDED.category,
    default_serving_name  = EXCLUDED.default_serving_name,
    default_serving_grams = EXCLUDED.default_serving_grams,
    calories_per_100g     = EXCLUDED.calories_per_100g,
    protein_per_100g      = EXCLUDED.protein_per_100g,
    carbs_per_100g        = EXCLUDED.carbs_per_100g,
    fat_per_100g          = EXCLUDED.fat_per_100g,
    sugar_per_100g        = EXCLUDED.sugar_per_100g,
    fiber_per_100g        = EXCLUDED.fiber_per_100g,
    calories_per_serving  = EXCLUDED.calories_per_serving,
    protein_per_serving   = EXCLUDED.protein_per_serving,
    carbs_per_serving     = EXCLUDED.carbs_per_serving,
    fat_per_serving       = EXCLUDED.fat_per_serving,
    sugar_per_serving     = EXCLUDED.sugar_per_serving,
    fiber_per_serving     = EXCLUDED.fiber_per_serving,
    image_url             = COALESCE(EXCLUDED.image_url, foods.image_url),
    barcode               = COALESCE(EXCLUDED.barcode, foods.barcode),
    source                = EXCLUDED.source,
    vietfood_class_id     = EXCLUDED.vietfood_class_id,
    suitable_meal_types   = EXCLUDED.suitable_meal_types,
    reference_price_vnd   = EXCLUDED.reference_price_vnd,
    feature_vector        = EXCLUDED.feature_vector,
    is_verified           = EXCLUDED.is_verified,
    owner_user_id         = EXCLUDED.owner_user_id,
    deleted_at            = EXCLUDED.deleted_at,
    updated_at            = now();

-- Liên kết một số món ăn VietFood68 với nguyên liệu dị ứng nền
-- PEANUT = ...0001, DAIRY = ...0002, EGG = ...0003, TREE_NUTS = ...0004, SOY = ...0005, GLUTEN = ...0006, FISH = ...0007, CRUSTACEANS = ...0008, SESAME = ...0009, MOLLUSCS = ...0010
INSERT INTO food_ingredients (food_id, ingredient_id, quantity_grams) VALUES
-- Bánh mì chứa Gluten
('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000006', 70.0),
-- Bánh tráng trộn chứa Đậu phộng, Trứng
('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001', 15.0),
('10000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000003', 20.0),
-- Bún riêu chứa Giáp xác (Cua), Đậu nành (Đậu phụ)
('10000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000008', 50.0),
('10000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000005', 40.0),
-- Cá chứa Fish
('10000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000007', 150.0),
-- Cua chứa Crustaceans
('10000000-0000-0000-0000-00000000001d', '00000000-0000-0000-0000-000000000008', 250.0),
-- Đậu hũ chứa Soy
('10000000-0000-0000-0000-00000000001e', '00000000-0000-0000-0000-000000000005', 150.0),
-- Mì gói chứa Gluten
('10000000-0000-0000-0000-000000000029', '00000000-0000-0000-0000-000000000006', 75.0),
-- Mực chứa Molluscs
('10000000-0000-0000-0000-00000000002a', '00000000-0000-0000-0000-000000000010', 150.0),
-- Ốc chứa Molluscs
('10000000-0000-0000-0000-00000000002c', '00000000-0000-0000-0000-000000000010', 200.0),
-- Phô mai chứa Dairy
('10000000-0000-0000-0000-00000000002f', '00000000-0000-0000-0000-000000000002', 30.0),
-- Tôm chứa Crustaceans
('10000000-0000-0000-0000-000000000037', '00000000-0000-0000-0000-000000000008', 150.0),
-- Trứng chứa Egg
('10000000-0000-0000-0000-000000000038', '00000000-0000-0000-0000-000000000003', 60.0),
-- Bún chả cá chứa Fish
('10000000-0000-0000-0000-00000000003e', '00000000-0000-0000-0000-000000000007', 80.0),
-- Súp cua chứa Crustaceans và Egg
('10000000-0000-0000-0000-000000000043', '00000000-0000-0000-0000-000000000008', 40.0),
('10000000-0000-0000-0000-000000000043', '00000000-0000-0000-0000-000000000003', 25.0)
ON CONFLICT (food_id, ingredient_id) DO UPDATE SET
    quantity_grams = EXCLUDED.quantity_grams;

-- =============================================================================
-- MẪU THÊM MÓN MỚI (Bỏ comment /* ... */ để thêm các món ngoài bộ VietFood68)
-- =============================================================================
/*
INSERT INTO foods (
    id, name, category, default_serving_name, default_serving_grams,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    calories_per_serving, protein_per_serving, carbs_per_serving, fat_per_serving, sugar_per_serving, fiber_per_serving,
    image_url, barcode, source, vietfood_class_id, suitable_meal_types,
    reference_price_vnd, feature_vector, is_verified, owner_user_id, deleted_at
) VALUES
('10000000-0000-0000-0000-000000000101', 'Canh chua cá lóc', 'SOUP', '1 tô vừa', 300.0, 48.0, 4.5, 4.2, 1.4, 1.5, 0.8, 144, 13.5, 12.6, 4.2, 4.5, 2.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.3, 0.1, 0.9, 0.2, 0.3, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000102', 'Sữa chua không đường', 'SNACK', '1 hộp', 100.0, 62.0, 3.8, 4.8, 3.0, 4.5, 0.0, 62, 3.8, 4.8, 3.0, 4.5, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 8000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0, 0.8, 0.0, 0.2, 0.4]'::jsonb, true, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    name                  = EXCLUDED.name,
    category              = EXCLUDED.category,
    default_serving_name  = EXCLUDED.default_serving_name,
    default_serving_grams = EXCLUDED.default_serving_grams,
    calories_per_100g     = EXCLUDED.calories_per_100g,
    protein_per_100g      = EXCLUDED.protein_per_100g,
    carbs_per_100g        = EXCLUDED.carbs_per_100g,
    fat_per_100g          = EXCLUDED.fat_per_100g,
    sugar_per_100g        = EXCLUDED.sugar_per_100g,
    fiber_per_100g        = EXCLUDED.fiber_per_100g,
    calories_per_serving  = EXCLUDED.calories_per_serving,
    protein_per_serving   = EXCLUDED.protein_per_serving,
    carbs_per_serving     = EXCLUDED.carbs_per_serving,
    fat_per_serving       = EXCLUDED.fat_per_serving,
    sugar_per_serving     = EXCLUDED.sugar_per_serving,
    fiber_per_serving     = EXCLUDED.fiber_per_serving,
    image_url             = COALESCE(EXCLUDED.image_url, foods.image_url),
    barcode               = COALESCE(EXCLUDED.barcode, foods.barcode),
    suitable_meal_types   = EXCLUDED.suitable_meal_types,
    reference_price_vnd   = EXCLUDED.reference_price_vnd,
    feature_vector        = EXCLUDED.feature_vector,
    updated_at            = now();
*/

COMMIT;
