-- ============================================================
-- Seed 68 món Việt Nam chuẩn hóa (VietFood68) khớp mô hình YOLO AI
-- ============================================================

INSERT INTO foods (
    vietfood_class_id, name, name_unaccented, category,
    default_serving_name, default_serving_grams,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    calories_per_serving, protein_per_serving, carbs_per_serving, fat_per_serving, sugar_per_serving, fiber_per_serving,
    allergens, suitable_meal_types, reference_price_vnd, source, is_verified
) VALUES
-- 0: Banh canh
(0, 'Bánh canh', 'banh canh', 'NOODLES_SOUP', '1 tô vừa', 450.0,
 88.0, 4.2, 14.5, 1.5, 0.6, 0.4,
 396, 18.9, 65.3, 6.8, 2.5, 1.8,
 '["GLUTEN", "CRUSTACEANS"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 1: Banh chung
(1, 'Bánh chưng', 'banh chung', 'TRADITIONAL_CAKE', '1 miếng', 150.0,
 225.0, 6.5, 35.0, 6.8, 0.8, 1.7,
 338, 9.8, 52.5, 10.2, 1.2, 2.5,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 25000, 'SYSTEM', true),

-- 2: Banh cuon
(2, 'Bánh cuốn', 'banh cuon', 'ROLLS_CAKE', '1 đĩa', 250.0,
 135.0, 5.2, 21.0, 3.4, 0.7, 0.6,
 338, 13.0, 52.5, 8.5, 1.8, 1.5,
 '[]'::jsonb, '["BREAKFAST", "DINNER"]'::jsonb, 35000, 'SYSTEM', true),

-- 3: Banh khot
(3, 'Bánh khọt', 'banh khot', 'PANCAKE', '1 đĩa 6 cái', 200.0,
 210.0, 7.0, 26.0, 8.5, 1.0, 0.6,
 420, 14.0, 52.0, 17.0, 2.0, 1.2,
 '["CRUSTACEANS"]'::jsonb, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 40000, 'SYSTEM', true),

-- 4: Banh mi
(4, 'Bánh mì thịt', 'banh mi thit', 'BREAD_SANDWICH', '1 ổ', 180.0,
 250.0, 9.5, 33.0, 8.8, 1.9, 1.6,
 450, 17.1, 59.4, 15.8, 3.5, 2.8,
 '["GLUTEN", "DAIRY"]'::jsonb, '["BREAKFAST", "LUNCH", "SNACK"]'::jsonb, 25000, 'SYSTEM', true),

-- 5: Banh trang
(5, 'Bánh tráng', 'banh trang', 'SNACK', '1 cái', 50.0,
 280.0, 2.5, 65.0, 0.8, 1.0, 1.6,
 140, 1.3, 32.5, 0.4, 0.5, 0.8,
 '[]'::jsonb, '["SNACK"]'::jsonb, 10000, 'SYSTEM', true),

-- 6: Banh trang tron
(6, 'Bánh tráng trộn', 'banh trang tron', 'SNACK', '1 bịch', 150.0,
 260.0, 6.0, 42.0, 7.5, 3.7, 1.3,
 390, 9.0, 63.0, 11.3, 5.5, 2.0,
 '["PEANUT", "EGG", "CRUSTACEANS"]'::jsonb, '["SNACK"]'::jsonb, 25000, 'SYSTEM', true),

-- 7: Banh xeo
(7, 'Bánh xèo', 'banh xeo', 'PANCAKE', '1 cái vừa', 250.0,
 195.0, 7.5, 22.0, 8.5, 1.0, 1.2,
 488, 18.8, 55.0, 21.3, 2.5, 3.0,
 '["CRUSTACEANS"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 8: Bo kho
(8, 'Bò kho', 'bo kho', 'STEW', '1 tô vừa', 350.0,
 125.0, 12.0, 6.0, 5.8, 1.2, 0.7,
 438, 42.0, 21.0, 20.3, 4.2, 2.5,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 9: Bo la lot
(9, 'Bò lá lốt', 'bo la lot', 'GRILLED', '1 phần', 200.0,
 185.0, 14.5, 6.5, 11.2, 1.4, 1.1,
 370, 29.0, 13.0, 22.4, 2.8, 2.2,
 '["PEANUT", "FISH"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 10: Bong cai
(10, 'Bông cải luộc', 'bong cai luoc', 'VEGETABLES', '1 đĩa', 150.0,
 35.0, 2.8, 5.0, 0.4, 1.4, 2.5,
 53, 4.2, 7.5, 0.6, 2.1, 3.8,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 20000, 'SYSTEM', true),

-- 11: Bun
(11, 'Bún tươi', 'bun tuoi', 'STAPLE', '1 bát', 150.0,
 110.0, 1.8, 25.0, 0.2, 0.1, 0.5,
 165, 2.7, 37.5, 0.3, 0.2, 0.8,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 5000, 'SYSTEM', true),

-- 12: Bun bo Hue
(12, 'Bún bò Huế', 'bun bo hue', 'NOODLES_SOUP', '1 tô vừa', 500.0,
 126.0, 8.2, 14.0, 4.1, 0.6, 0.4,
 630, 41.0, 70.0, 20.5, 2.8, 2.0,
 '["CRUSTACEANS"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 13: Bun cha
(13, 'Bún chả', 'bun cha', 'NOODLES_DISH', '1 phần', 450.0,
 132.0, 7.8, 16.5, 3.9, 1.9, 0.6,
 594, 35.1, 74.3, 17.6, 8.5, 2.5,
 '["FISH"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 14: Bun dau
(14, 'Bún đậu mắm tôm', 'bun dau mam tom', 'NOODLES_DISH', '1 mẹt', 400.0,
 165.0, 8.5, 18.0, 6.5, 0.8, 0.8,
 660, 34.0, 72.0, 26.0, 3.2, 3.0,
 '["CRUSTACEANS", "SOY"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 15: Bun mam
(15, 'Bún mắm', 'bun mam', 'NOODLES_SOUP', '1 tô', 550.0,
 115.0, 7.5, 13.5, 3.4, 0.8, 0.6,
 633, 41.3, 74.3, 18.7, 4.5, 3.5,
 '["FISH", "CRUSTACEANS", "MOLLUSCS"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 60000, 'SYSTEM', true),

-- 16: Bun rieu
(16, 'Bún riêu cua', 'bun rieu cua', 'NOODLES_SOUP', '1 tô', 500.0,
 98.0, 6.5, 12.0, 2.6, 0.6, 0.5,
 490, 32.5, 60.0, 13.0, 3.0, 2.5,
 '["CRUSTACEANS", "SOY"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 17: Ca
(17, 'Cá hấp / chiên', 'ca hap chien', 'FISH_SEAFOOD', '1 khúc', 150.0,
 145.0, 18.5, 0.5, 7.2, 0.0, 0.0,
 218, 27.8, 0.8, 10.8, 0.0, 0.0,
 '["FISH"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 40000, 'SYSTEM', true),

-- 18: Ca chua
(18, 'Cà chua tươi', 'ca chua tuoi', 'FRUIT_VEG', '1 quả', 100.0,
 18.0, 0.9, 3.9, 0.2, 2.6, 1.2,
 18, 0.9, 3.9, 0.2, 2.6, 1.2,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, 'SYSTEM', true),

-- 19: Ca phao
(19, 'Cà pháo muối', 'ca phao muoi', 'PICKLED', '1 đĩa nhỏ', 50.0,
 28.0, 1.2, 5.5, 0.1, 2.4, 2.0,
 14, 0.6, 2.8, 0.1, 1.2, 1.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 5000, 'SYSTEM', true),

-- 20: Ca rot
(20, 'Cà rốt luộc', 'ca rot luoc', 'VEGETABLES', '1 củ', 100.0,
 41.0, 0.9, 9.6, 0.2, 4.7, 2.8,
 41, 0.9, 9.6, 0.2, 4.7, 2.8,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 5000, 'SYSTEM', true),

-- 21: Canh
(21, 'Canh rau thịt', 'canh rau thit', 'SOUP', '1 tô', 250.0,
 48.0, 3.5, 2.5, 2.6, 0.6, 0.7,
 120, 8.8, 6.3, 6.5, 1.5, 1.8,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 15000, 'SYSTEM', true),

-- 22: Cha
(22, 'Chả lụa', 'cha lua', 'PROCESSED_MEAT', '1 khoanh', 100.0,
 230.0, 17.5, 2.5, 16.5, 1.0, 0.0,
 230, 17.5, 2.5, 16.5, 1.0, 0.0,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 25000, 'SYSTEM', true),

-- 23: Cha gio
(23, 'Chả giò (Nem rán)', 'cha gio nem ran', 'FRIED', '3 cuốn', 120.0,
 285.0, 10.5, 22.0, 17.0, 2.1, 1.3,
 342, 12.6, 26.4, 20.4, 2.5, 1.5,
 '["EGG", "CRUSTACEANS"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 35000, 'SYSTEM', true),

-- 24: Chanh
(24, 'Quả chanh', 'qua chanh', 'FRUIT_VEG', '1 quả', 50.0,
 29.0, 1.1, 9.3, 0.3, 2.5, 2.8,
 15, 0.6, 4.7, 0.2, 1.3, 1.4,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 2000, 'SYSTEM', true),

-- 25: Com
(25, 'Cơm trắng', 'com trang', 'STAPLE', '1 chén', 150.0,
 130.0, 2.7, 28.2, 0.3, 0.1, 0.4,
 195, 4.1, 42.3, 0.5, 0.1, 0.6,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 5000, 'SYSTEM', true),

-- 26: Com tam
(26, 'Cơm tấm sườn bì chả', 'com tam suon bi cha', 'RICE_DISH', '1 dĩa', 400.0,
 182.0, 8.8, 22.0, 6.5, 1.6, 0.6,
 728, 35.2, 88.0, 26.0, 6.5, 2.2,
 '["EGG", "FISH"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 27: Con nguoi (Lớp nhận diện đối tượng người trong ảnh AI - Đánh dấu không phải thực phẩm)
(27, 'Người (Đối tượng không phải thực phẩm)', 'nguoi doi tuong khong phai thuc pham', 'NON_FOOD', 'Không áp dụng', 1.0,
 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
 0, 0.0, 0.0, 0.0, 0.0, 0.0,
 '[]'::jsonb, '[]'::jsonb, 0, 'SYSTEM', false),

-- 28: Cu kieu
(28, 'Củ kiệu muối', 'cu kieu muoi', 'PICKLED', '1 đĩa nhỏ', 50.0,
 68.0, 1.0, 15.5, 0.1, 13.0, 1.6,
 34, 0.5, 7.8, 0.1, 6.5, 0.8,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 10000, 'SYSTEM', true),

-- 29: Cua
(29, 'Cua biển hấp', 'cua bien hap', 'FISH_SEAFOOD', '1 con', 250.0,
 95.0, 19.5, 0.5, 1.5, 0.0, 0.0,
 238, 48.8, 1.3, 3.8, 0.0, 0.0,
 '["CRUSTACEANS"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 120000, 'SYSTEM', true),

-- 30: Dau hu
(30, 'Đậu hũ', 'dau hu', 'SOY_PRODUCT', '1 miếng', 100.0,
 90.0, 10.5, 2.0, 5.0, 0.6, 1.2,
 90, 10.5, 2.0, 5.0, 0.6, 1.2,
 '["SOY"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 10000, 'SYSTEM', true),

-- 31: Dua chua
(31, 'Dưa cải chua', 'dua cai chua', 'PICKLED', '1 đĩa', 100.0,
 22.0, 1.4, 3.8, 0.2, 1.0, 1.8,
 22, 1.4, 3.8, 0.2, 1.0, 1.8,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 5000, 'SYSTEM', true),

-- 32: Dua leo
(32, 'Dưa leo', 'dua leo', 'VEGETABLES', '1 trái', 100.0,
 15.0, 0.7, 3.6, 0.1, 1.7, 0.5,
 15, 0.7, 3.6, 0.1, 1.7, 0.5,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, 'SYSTEM', true),

-- 33: Goi cuon
(33, 'Gỏi cuốn tôm thịt', 'goi cuon tom thit', 'ROLLS_CAKE', '2 cuốn', 160.0,
 148.0, 8.5, 21.0, 3.2, 1.6, 1.3,
 237, 13.6, 33.6, 5.1, 2.5, 2.0,
 '["CRUSTACEANS", "PEANUT"]'::jsonb, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 25000, 'SYSTEM', true),

-- 34: Hamburger
(34, 'Bánh burger', 'banh burger', 'FASTFOOD', '1 cái', 200.0,
 260.0, 13.0, 25.0, 12.0, 3.4, 1.2,
 520, 26.0, 50.0, 24.0, 6.8, 2.4,
 '["GLUTEN", "DAIRY"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 35: Heo quay
(35, 'Thịt heo quay', 'thit heo quay', 'MEAT', '1 phần', 150.0,
 280.0, 16.5, 1.0, 23.5, 0.3, 0.0,
 420, 24.8, 1.5, 35.3, 0.5, 0.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 60000, 'SYSTEM', true),

-- 36: Hu tieu
(36, 'Hủ tiếu Nam Vang', 'hu tieu nam vang', 'NOODLES_SOUP', '1 tô', 450.0,
 105.0, 6.8, 14.5, 2.1, 0.5, 0.3,
 473, 30.6, 65.3, 9.5, 2.2, 1.5,
 '["CRUSTACEANS", "EGG"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 37: Kho qua thit
(37, 'Canh khổ qua nhồi thịt', 'canh kho qua nhoi thit', 'SOUP', '1 khúc', 150.0,
 110.0, 8.0, 3.5, 7.0, 1.2, 1.9,
 165, 12.0, 5.3, 10.5, 1.8, 2.8,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 25000, 'SYSTEM', true),

-- 38: Khoai tay chien
(38, 'Khoai tây chiên', 'khoai tay chien', 'SNACK', '1 phần', 120.0,
 312.0, 3.4, 41.0, 15.0, 0.7, 3.2,
 374, 4.1, 49.2, 18.0, 0.8, 3.8,
 '[]'::jsonb, '["SNACK"]'::jsonb, 30000, 'SYSTEM', true),

-- 39: Lau
(39, 'Lẩu thập cẩm', 'lau thap cam', 'HOTPOT', '1 phần', 350.0,
 135.0, 11.0, 8.5, 6.2, 1.0, 1.1,
 473, 38.5, 29.8, 21.7, 3.5, 4.0,
 '["CRUSTACEANS", "FISH"]'::jsonb, '["DINNER"]'::jsonb, 80000, 'SYSTEM', true),

-- 40: Long heo
(40, 'Lòng heo luộc', 'long heo luoc', 'MEAT', '1 đĩa', 150.0,
 190.0, 15.5, 1.5, 13.5, 0.0, 0.0,
 285, 23.3, 2.3, 20.3, 0.0, 0.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 41: Mi
(41, 'Mì tôm / Mì gói', 'mi tom mi goi', 'NOODLES', '1 tô', 120.0,
 380.0, 8.5, 54.0, 14.5, 2.1, 1.5,
 456, 10.2, 64.8, 17.4, 2.5, 1.8,
 '["GLUTEN"]'::jsonb, '["BREAKFAST", "SNACK"]'::jsonb, 15000, 'SYSTEM', true),

-- 42: Muc
(42, 'Mực hấp / xào', 'muc hap xao', 'FISH_SEAFOOD', '1 con', 150.0,
 92.0, 16.0, 3.0, 1.4, 0.0, 0.0,
 138, 24.0, 4.5, 2.1, 0.0, 0.0,
 '["MOLLUSCS"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 65000, 'SYSTEM', true),

-- 43: Nam
(43, 'Nấm tươi các loại', 'nam tuoi cac loai', 'VEGETABLES', '1 đĩa', 100.0,
 32.0, 3.1, 4.3, 0.3, 1.8, 2.2,
 32, 3.1, 4.3, 0.3, 1.8, 2.2,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 20000, 'SYSTEM', true),

-- 44: Oc
(44, 'Thịt ốc luộc', 'thit oc luoc', 'FISH_SEAFOOD', '1 đĩa', 150.0,
 85.0, 14.0, 4.0, 0.8, 0.0, 0.0,
 128, 21.0, 6.0, 1.2, 0.0, 0.0,
 '["MOLLUSCS"]'::jsonb, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 45000, 'SYSTEM', true),

-- 45: Ot chuong
(45, 'Ớt chuông', 'ot chuong', 'VEGETABLES', '1 quả', 100.0,
 26.0, 1.0, 6.0, 0.3, 4.2, 2.1,
 26, 1.0, 6.0, 0.3, 4.2, 2.1,
 '[]'::jsonb, '["LUNCH", "DINNER", "SALAD"]'::jsonb, 10000, 'SYSTEM', true),

-- 46: Pho
(46, 'Phở bò', 'pho bo', 'NOODLES_SOUP', '1 tô', 500.0,
 105.0, 7.5, 13.2, 2.3, 0.5, 0.3,
 525, 37.5, 66.0, 11.5, 2.5, 1.5,
 '[]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 47: Pho mai
(47, 'Phô mai lát', 'pho mai lat', 'DAIRY', '1 lát', 25.0,
 380.0, 22.0, 2.0, 31.0, 2.0, 0.0,
 95, 5.5, 0.5, 7.8, 0.5, 0.0,
 '["DAIRY"]'::jsonb, '["BREAKFAST", "SNACK"]'::jsonb, 12000, 'SYSTEM', true),

-- 48: Rau
(48, 'Rau sống tổng hợp', 'rau song tong hop', 'VEGETABLES', '1 đĩa', 100.0,
 20.0, 1.5, 3.5, 0.2, 1.2, 2.5,
 20, 1.5, 3.5, 0.2, 1.2, 2.5,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 10000, 'SYSTEM', true),

-- 49: Salad
(49, 'Salad sốt mè', 'salad sot me', 'SALAD', '1 đĩa', 150.0,
 75.0, 1.8, 6.5, 4.5, 3.0, 2.0,
 113, 2.7, 9.8, 6.8, 4.5, 3.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 35000, 'SYSTEM', true),

-- 50: Thit bo
(50, 'Thịt bò xào', 'thit bo xao', 'MEAT', '1 phần', 150.0,
 185.0, 22.0, 1.5, 10.5, 0.3, 0.0,
 278, 33.0, 2.3, 15.8, 0.5, 0.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 65000, 'SYSTEM', true),

-- 51: Thit ga
(51, 'Thịt gà luộc', 'thit ga luoc', 'POULTRY', '1 đùi/phần', 150.0,
 195.0, 24.0, 0.0, 10.5, 0.0, 0.0,
 293, 36.0, 0.0, 15.8, 0.0, 0.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 52: Thit heo
(52, 'Thịt heo luộc', 'thit heo luoc', 'MEAT', '1 đĩa', 150.0,
 245.0, 20.0, 0.0, 18.0, 0.0, 0.0,
 368, 30.0, 0.0, 27.0, 0.0, 0.0,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 53: Thit kho
(53, 'Thịt kho hột vịt', 'thit kho hot vit', 'STEW', '1 phần', 200.0,
 235.0, 13.5, 4.5, 18.0, 2.8, 0.3,
 470, 27.0, 9.0, 36.0, 5.5, 0.5,
 '["EGG"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 54: Thit nuong
(54, 'Thịt heo xiên nướng', 'thit heo xien nuong', 'GRILLED', '2 xiên', 120.0,
 260.0, 17.5, 3.0, 19.5, 2.3, 0.2,
 312, 21.0, 3.6, 23.4, 2.8, 0.2,
 '[]'::jsonb, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 30000, 'SYSTEM', true),

-- 55: Tom
(55, 'Tôm luộc / hấp', 'tom luoc hap', 'FISH_SEAFOOD', '1 phần', 120.0,
 99.0, 21.0, 0.2, 1.1, 0.0, 0.0,
 119, 25.2, 0.2, 1.3, 0.0, 0.0,
 '["CRUSTACEANS"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 56: Trung
(56, 'Trứng ốp la / luộc', 'trung op la luoc', 'EGG', '1 quả', 55.0,
 155.0, 13.0, 1.1, 11.0, 0.7, 0.0,
 85, 7.2, 0.6, 6.1, 0.4, 0.0,
 '["EGG"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 7000, 'SYSTEM', true),

-- 57: Xoi
(57, 'Xôi mặn', 'xoi man', 'STAPLE', '1 gói', 200.0,
 245.0, 6.5, 42.0, 5.2, 0.8, 1.0,
 490, 13.0, 84.0, 10.4, 1.5, 2.0,
 '[]'::jsonb, '["BREAKFAST", "SNACK"]'::jsonb, 20000, 'SYSTEM', true),

-- 58: Banh beo
(58, 'Bánh bèo', 'banh beo', 'ROLLS_CAKE', '1 mâm', 180.0,
 155.0, 5.5, 24.0, 3.8, 1.0, 0.7,
 279, 9.9, 43.2, 6.8, 1.8, 1.2,
 '["CRUSTACEANS", "FISH"]'::jsonb, '["BREAKFAST", "SNACK"]'::jsonb, 35000, 'SYSTEM', true),

-- 59: Cao lau
(59, 'Cao lầu Hội An', 'cao lau hoi an', 'NOODLES_DISH', '1 tô', 400.0,
 125.0, 7.8, 16.5, 3.0, 0.8, 0.7,
 500, 31.2, 66.0, 12.0, 3.2, 2.8,
 '["GLUTEN", "SOY"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 60: Mi Quang
(60, 'Mì Quảng', 'mi quang', 'NOODLES_DISH', '1 tô', 450.0,
 128.0, 8.0, 17.0, 3.2, 0.8, 0.7,
 576, 36.0, 76.5, 14.4, 3.5, 3.0,
 '["PEANUT", "CRUSTACEANS"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 61: Com chien duong chau
(61, 'Cơm chiên Dương Châu', 'com chien duong chau', 'RICE_DISH', '1 đĩa', 300.0,
 195.0, 6.8, 28.5, 5.8, 0.8, 0.6,
 585, 20.4, 85.5, 17.4, 2.5, 1.8,
 '["EGG"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 62: Bun cha ca
(62, 'Bún chả cá', 'bun cha ca', 'NOODLES_SOUP', '1 tô', 500.0,
 95.0, 7.2, 12.5, 1.8, 0.6, 0.4,
 475, 36.0, 62.5, 9.0, 3.0, 2.2,
 '["FISH"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, 'SYSTEM', true),

-- 63: Com chien ga
(63, 'Cơm chiên gà xối mỡ', 'com chien ga xoi mo', 'RICE_DISH', '1 đĩa', 350.0,
 205.0, 9.5, 27.0, 6.5, 0.3, 0.4,
 718, 33.3, 94.5, 22.8, 1.2, 1.5,
 '[]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 55000, 'SYSTEM', true),

-- 64: Chao long
(64, 'Cháo lòng', 'chao long', 'PORRIDGE', '1 tô', 450.0,
 90.0, 6.2, 11.5, 2.0, 0.2, 0.3,
 405, 27.9, 51.8, 9.0, 1.0, 1.2,
 '[]'::jsonb, '["BREAKFAST", "DINNER"]'::jsonb, 40000, 'SYSTEM', true),

-- 65: Nom hoa chuoi
(65, 'Nộm hoa chuối', 'nom hoa chuoi', 'SALAD', '1 đĩa', 250.0,
 105.0, 4.8, 11.5, 4.2, 2.1, 1.8,
 263, 12.0, 28.8, 10.5, 5.2, 4.5,
 '["PEANUT"]'::jsonb, '["LUNCH", "DINNER"]'::jsonb, 35000, 'SYSTEM', true),

-- 66: Nui xao bo
(66, 'Nui xào bò', 'nui xao bo', 'PASTA_DISH', '1 đĩa', 300.0,
 175.0, 9.0, 23.5, 4.8, 1.3, 0.8,
 525, 27.0, 70.5, 14.4, 3.8, 2.5,
 '["GLUTEN"]'::jsonb, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, 'SYSTEM', true),

-- 67: Sup cua
(67, 'Súp cua', 'sup cua', 'SOUP', '1 chén', 250.0,
 92.0, 6.8, 11.5, 2.0, 0.9, 0.4,
 230, 17.0, 28.8, 5.0, 2.2, 1.0,
 '["CRUSTACEANS", "EGG"]'::jsonb, '["BREAKFAST", "SNACK", "DINNER"]'::jsonb, 35000, 'SYSTEM', true);
