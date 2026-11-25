-- Seed 10 nhóm nguyên liệu dị ứng nền chuẩn hệ thống
INSERT INTO ingredients (id, code, name, calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g, is_allergen, source, is_active) VALUES
('00000000-0000-0000-0000-000000000001', 'PEANUT', 'Đậu phụng (Lạc)', 567.0, 25.8, 16.1, 49.2, 4.7, 8.5, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000002', 'DAIRY', 'Sữa và Sản phẩm từ sữa', 61.0, 3.2, 4.8, 3.3, 5.1, 0.0, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000003', 'EGG', 'Trứng các loại', 155.0, 13.0, 1.1, 11.0, 1.1, 0.0, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000004', 'TREE_NUTS', 'Hạt cây (Hạnh nhân, Óc chó, Hạt điều)', 579.0, 21.0, 22.0, 50.0, 4.4, 12.5, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000005', 'SOY', 'Đậu nành (Đậu hũ, Tương)', 446.0, 36.0, 30.0, 20.0, 7.3, 9.3, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000006', 'GLUTEN', 'Gluten (Lúa mì, Bột mì)', 364.0, 10.0, 76.0, 1.0, 0.3, 2.7, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000007', 'FISH', 'Cá các loại', 206.0, 22.0, 0.0, 12.0, 0.0, 0.0, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000008', 'CRUSTACEANS', 'Giáp xác (Tôm, Cua, Ghẹ)', 99.0, 24.0, 0.2, 0.3, 0.0, 0.0, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000009', 'SESAME', 'Vừng (Mè)', 573.0, 17.7, 23.4, 49.7, 0.3, 11.8, true, 'SYSTEM', true),
('00000000-0000-0000-0000-000000000010', 'MOLLUSCS', 'Thân mềm (Ốc, Mực, Nghêu, Sò)', 92.0, 16.0, 3.0, 1.4, 0.0, 0.0, true, 'SYSTEM', true)
ON CONFLICT (id) DO NOTHING;
