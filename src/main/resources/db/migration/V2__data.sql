-- ============================================================
-- Kallo Backend — Consolidated Seed Data (V2)
-- ============================================================

-- ============================================================
-- 1. ACT: activity_types (82 entries)
-- MET source: SRS Appendix A.6 for brisk walking, 8 km/h running,
-- cycling, swimming, yoga, and badminton; remaining entries mapped to
-- Ainsworth et al., Compendium of Physical Activities (2011 update).
-- ============================================================
INSERT INTO activity_types
(code, name_vi, category, met_moderate, has_distance, sort_order)
VALUES
    ('WALK_BRISK', 'Đi bộ nhanh', 'WALKING', 3.5, TRUE, 1),
    ('RUN_8KMH', 'Chạy bộ 8 km/h', 'RUNNING', 8.3, TRUE, 2),
    ('CYCLING_MODERATE', 'Đạp xe vừa', 'CYCLING', 6.8, TRUE, 3),
    ('SWIM_GENERAL', 'Bơi', 'SWIMMING', 6.0, FALSE, 4),
    ('YOGA_HATHA', 'Yoga', 'MIND_BODY', 2.5, FALSE, 5),
    ('BADMINTON', 'Cầu lông', 'SPORT', 5.5, FALSE, 6),
    ('WEIGHT_TRAINING', 'Tập tạ', 'GYM', 3.5, FALSE, 7),
    ('SOCCER', 'Bóng đá', 'SPORT', 7.0, FALSE, 8),
    ('HIKING', 'Leo núi, đi bộ đường dài', 'WALKING', 6.0, TRUE, 9),
    ('JUMP_ROPE', 'Nhảy dây', 'GYM', 11.8, FALSE, 10),
    ('TABLE_TENNIS', 'Bóng bàn', 'SPORT', 4.0, FALSE, 11),
    ('BASKETBALL', 'Bóng rổ', 'SPORT', 6.5, FALSE, 12),

    -- Catalogue synced from frontend (MET pending final Compendium verification)
    ('PICKLEBALL', 'Pickleball', 'SPORT', 6.0, FALSE, 20),
    ('GENERAL_WORKOUT', 'Hoạt động khác', 'MIND_BODY', 4.0, FALSE, 21),
    ('AUSTRALIAN_FOOTBALL', 'Bóng bầu dục Úc', 'SPORT', 8.0, FALSE, 22),
    ('BASEBALL', 'Bóng chày', 'SPORT', 5.0, FALSE, 23),
    ('BOWLING', 'Bowling', 'SPORT', 3.0, FALSE, 24),
    ('FUTSAL', 'Bóng đá trong nhà', 'SPORT', 8.0, FALSE, 25),
    ('CALISTHENICS', 'Bài tập thể chất', 'GYM', 6.0, FALSE, 26),
    ('HANDBALL', 'Bóng ném', 'SPORT', 8.0, FALSE, 27),
    ('CROQUET', 'Bóng cửa', 'SPORT', 2.5, FALSE, 28),
    ('BILLIARDS', 'Bi-da', 'SPORT', 2.5, FALSE, 29),
    ('RACQUETBALL', 'Bóng vợt', 'SPORT', 7.0, FALSE, 30),
    ('SQUASH', 'Bóng quần', 'SPORT', 9.0, FALSE, 31),
    ('WATER_POLO', 'Bóng nước', 'SPORT', 8.0, FALSE, 32),
    ('STRETCHING', 'Bài tập giãn cơ', 'MIND_BODY', 2.5, FALSE, 33),
    ('AMERICAN_FOOTBALL', 'Bóng bầu dục Mỹ', 'SPORT', 8.0, FALSE, 34),
    ('ARCHERY', 'Bắn cung', 'SPORT', 3.5, FALSE, 35),
    ('VOLLEYBALL', 'Bóng chuyền', 'SPORT', 4.0, FALSE, 36),
    ('RUGBY', 'Bóng bầu dục', 'SPORT', 8.0, FALSE, 37),
    ('LACROSSE', 'Bóng vợt', 'SPORT', 8.0, FALSE, 38),
    ('BARRE', 'Barre', 'MIND_BODY', 4.0, FALSE, 39),
    ('SOFTBALL', 'Bóng chày nữ', 'SPORT', 5.0, FALSE, 40),
    ('ELLIPTICAL', 'Máy elliptical', 'CARDIO', 7.0, FALSE, 41),
    ('ROWING', 'Chèo thuyền', 'CARDIO', 6.0, TRUE, 42),
    ('KAYAKING', 'Chèo thuyền kayak', 'SPORT', 5.0, TRUE, 43),
    ('WEIGHT_LIFTING', 'Cử tạ', 'GYM', 5.5, FALSE, 44),
    ('FISHING', 'Câu cá', 'OTHER', 3.0, FALSE, 45),
    ('RUNNING_FAST', 'Chạy nhanh', 'RUNNING', 9.0, TRUE, 46),
    ('JOGGING', 'Chạy chậm', 'RUNNING', 7.0, TRUE, 47),
    ('CANOEING', 'Chèo xuồng canoe', 'SPORT', 4.5, TRUE, 48),
    ('BICYCLING_STATIONARY', 'Đạp xe tại chỗ', 'CYCLING', 6.5, FALSE, 49),
    ('KICKBOXING', 'Đấm bốc tự do', 'SPORT', 8.5, FALSE, 50),
    ('FENCING', 'Đấu kiếm', 'SPORT', 6.0, FALSE, 51),
    ('SAILING', 'Đi thuyền buồm', 'SPORT', 3.5, FALSE, 52),
    ('WRESTLING', 'Đấu vật', 'SPORT', 7.0, FALSE, 53),
    ('TRACK_AND_FIELD', 'Điền kinh', 'SPORT', 8.0, TRUE, 54),
    ('SNOWSHOEING', 'Đi bộ trên tuyết', 'SPORT', 5.3, TRUE, 55),
    ('GOLF', 'Golf', 'SPORT', 4.0, FALSE, 56),
    ('HIIT', 'HIIT', 'GYM', 8.0, FALSE, 57),
    ('HOCKEY', 'Khúc côn cầu', 'SPORT', 7.5, FALSE, 58),
    ('SURFING', 'Lướt sóng', 'SPORT', 5.0, TRUE, 59),
    ('SKIN_DIVING', 'Lặn tự do', 'SWIMMING', 7.0, FALSE, 60),
    ('HOUSEWORK', 'Làm việc nhà', 'OTHER', 3.0, FALSE, 61),
    ('SNORKELING', 'Lặn có ống thở', 'SWIMMING', 5.0, FALSE, 62),
    ('CLIMBING', 'Leo núi', 'SPORT', 8.0, TRUE, 63),
    ('STAIR_CLIMBING', 'Leo cầu thang', 'CARDIO', 8.5, FALSE, 64),
    ('CURLING', 'Bi đá trên băng', 'SPORT', 4.0, FALSE, 65),
    ('MARATHON', 'Marathon', 'RUNNING', 10.0, TRUE, 66),
    ('FRISBEE', 'Ném đĩa', 'SPORT', 3.5, FALSE, 67),
    ('TRAMPOLINE', 'Nhảy bạt nhún', 'GYM', 4.5, FALSE, 68),
    ('PILATES', 'Pilates', 'MIND_BODY', 3.5, FALSE, 69),
    ('TENNIS', 'Quần vợt', 'SPORT', 7.3, TRUE, 70),
    ('BOXING', 'Quyền anh', 'SPORT', 8.5, FALSE, 71),
    ('HUNTING', 'Săn bắn', 'OTHER', 4.0, FALSE, 72),
    ('GYMNASTICS', 'Thể dục dụng cụ', 'SPORT', 5.5, FALSE, 73),
    ('SKATING_SPORTS', 'Trượt băng', 'SPORT', 7.0, FALSE, 74),
    ('SNOWMOBILING', 'Trượt tuyết bằng xe', 'SPORT', 3.5, FALSE, 75),
    ('WATER_FITNESS', 'Thể dục dưới nước', 'SWIMMING', 5.5, FALSE, 76),
    ('CROSS_COUNTRY_SKIING', 'Trượt tuyết việt dã', 'SPORT', 9.0, TRUE, 77),
    ('DOWNHILL_SKIING', 'Trượt tuyết đổ đèo', 'SPORT', 6.0, TRUE, 78),
    ('SKATEBOARDING', 'Trượt ván', 'SPORT', 5.0, FALSE, 79),
    ('EQUESTRIAN_SPORTS', 'Thể thao cưỡi ngựa', 'SPORT', 5.5, FALSE, 80),
    ('TAI_CHI', 'Thái cực quyền', 'MIND_BODY', 3.0, FALSE, 81),
    ('MEDITATION', 'Thiền', 'MIND_BODY', 1.5, FALSE, 82),
    ('AEROBIC', 'Thể dục nhịp điệu', 'CARDIO', 6.5, FALSE, 83),
    ('ROLLER_SKATING', 'Trượt patin', 'SPORT', 7.0, FALSE, 84),
    ('WATER_SKIING', 'Trượt nước', 'SPORT', 6.0, TRUE, 85),
    ('MARTIAL_ARTS', 'Võ thuật', 'SPORT', 9.0, FALSE, 86),
    ('WORKOUT', 'Workout', 'GYM', 6.0, FALSE, 87),
    ('CORE_TRAINING', 'Tập cơ trung tâm', 'GYM', 5.0, FALSE, 88),
    ('SHOVELING_SNOW', 'Xúc tuyết', 'OTHER', 6.0, FALSE, 89)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 2. GAME: badges (8 entries)
-- ============================================================
INSERT INTO badges (code, name, description, icon_key, rule_type, threshold, points, active, sort_order)
VALUES
    ('FIRST_LOG', 'Bước đầu tiên', 'Ghi nhật ký đầu tiên', 'first-log', 'FIRST_LOG', 1, 10, TRUE, 1),
    ('STREAK_7', 'Kiên trì 7 ngày', 'Hoàn thành mục tiêu 7 ngày liên tiếp', 'streak-7', 'STREAK_DAYS', 7, 20, TRUE, 2),
    ('STREAK_30', 'Kiên trì 30 ngày', 'Hoàn thành mục tiêu 30 ngày liên tiếp', 'streak-30', 'STREAK_DAYS', 30, 50, TRUE, 3),
    ('STREAK_100', 'Kiên trì 100 ngày', 'Hoàn thành mục tiêu 100 ngày liên tiếp', 'streak-100', 'STREAK_DAYS', 100, 150, TRUE, 4),
    ('FIRST_FRIEND', 'Người bạn đầu tiên', 'Kết nối với người bạn đầu tiên', 'first-friend', 'FRIENDS_COUNT', 1, 10, TRUE, 5),
    ('CHALLENGER_1', 'Người thử thách', 'Hoàn thành thử thách đầu tiên', 'challenger-1', 'CHALLENGES_COMPLETED', 1, 20, TRUE, 6),
    ('CHALLENGER_5', 'Bậc thầy thử thách', 'Hoàn thành 5 thử thách', 'challenger-5', 'CHALLENGES_COMPLETED', 5, 60, TRUE, 7),
    ('WEIGHT_GOAL_REACHED', 'Mục tiêu cân nặng', 'Đạt mục tiêu cân nặng', 'weight-goal-reached', 'WEIGHT_GOAL_REACHED', NULL, 100, FALSE, 8)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 3. PROFILE: chronic_conditions (5 entries)
-- ============================================================
INSERT INTO chronic_conditions (id, code, name, description, is_active) VALUES
('20000000-0000-0000-0000-000000000001', 'DIABETES_T2', 'Tiểu đường tuýp 2', 'Đái tháo đường tuýp 2 - kiểm soát đường tinh luyện và chỉ số đường huyết', true),
('20000000-0000-0000-0000-000000000002', 'DIABETES_T1', 'Tiểu đường tuýp 1', 'Đái tháo đường phụ thuộc insulin - kiểm soát lượng đường hấp thu', true),
('20000000-0000-0000-0000-000000000003', 'HYPERTENSION', 'Tăng huyết áp', 'Huyết áp cao - khuyến nghị hạn chế natri và chất béo bão hòa', true),
('20000000-0000-0000-0000-000000000004', 'GOUT', 'Bệnh Gút (Gout)', 'Tăng axit uric máu - khuyến nghị hạn chế thực phẩm giàu purine', true),
('20000000-0000-0000-0000-000000000005', 'CHRONIC_KIDNEY_DISEASE', 'Bệnh thận mạn tính', 'Hỗ trợ theo dõi đạm và hạn chế thực phẩm nhiều kali/natri/phốt pho', true)
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 4. FOOD: ingredients — 10 nhóm nguyên liệu dị ứng nền chuẩn hệ thống
-- ============================================================
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

-- ============================================================
-- 5a. FOOD: 67 món ăn VietFood68 (Lớp 27 'Con nguoi' bị loại bỏ) kèm 12D feature_vector
-- Thứ tự chiều feature_vector: [SOUP_BASED, DRY_BASED, BEEF, PORK, POULTRY, SEAFOOD, VEGETARIAN, FRIED_FATTY, STEAMED_BOILED, SPICY, SWEET, HIGH_PROTEIN]
-- ============================================================
INSERT INTO foods (
    id, name, category, default_serving_name, default_serving_grams,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    calories_per_serving, protein_per_serving, carbs_per_serving, fat_per_serving, sugar_per_serving, fiber_per_serving,
    vietfood_class_id, suitable_meal_types, reference_price_vnd, feature_vector, source, is_verified
) VALUES
('10000000-0000-0000-0000-000000000000', 'Bánh canh', 'NOODLE', '1 tô', 450.0, 89.0, 4.5, 14.2, 1.8, 0.5, 0.6, 400, 20.3, 63.9, 8.1, 2.3, 2.7, 0, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.7, 0.0, 0.3, 0.1, 0.1, 0.9, 0.1, 0.1, 0.5]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000001', 'Bánh chưng', 'STAPLE', '1 góc (1/8 bánh)', 150.0, 220.0, 7.8, 32.5, 6.8, 0.8, 1.5, 330, 11.7, 48.8, 10.2, 1.2, 2.3, 1, '["BREAKFAST", "LUNCH"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 0.8, 0.0, 0.0, 0.1, 0.4, 0.6, 0.1, 0.1, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000002', 'Bánh cuốn', 'STAPLE', '1 đĩa', 250.0, 145.0, 5.2, 22.0, 4.1, 0.8, 0.9, 363, 13.0, 55.0, 10.3, 2.0, 2.3, 2, '["BREAKFAST", "DINNER"]'::jsonb, 35000, '[0.0, 1.0, 0.0, 0.7, 0.0, 0.0, 0.2, 0.2, 0.9, 0.1, 0.1, 0.4]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000003', 'Bánh khọt', 'SNACK', '1 đĩa (8 cái)', 200.0, 185.0, 6.5, 23.0, 7.5, 1.2, 0.8, 370, 13.0, 46.0, 15.0, 2.4, 1.6, 3, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 45000, '[0.0, 1.0, 0.0, 0.2, 0.0, 0.8, 0.1, 0.9, 0.0, 0.1, 0.2, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000004', 'Bánh mì', 'STAPLE', '1 ổ thịt đầy đủ', 180.0, 250.0, 9.8, 35.2, 7.9, 2.5, 1.8, 450, 17.6, 63.4, 14.2, 4.5, 3.2, 4, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 0.8, 0.2, 0.0, 0.1, 0.4, 0.2, 0.2, 0.2, 0.4]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000005', 'Bánh tráng', 'SNACK', '1 cái bánh nướng', 50.0, 330.0, 3.5, 75.0, 1.2, 0.5, 1.0, 165, 1.8, 37.5, 0.6, 0.3, 0.5, 5, '["SNACK"]'::jsonb, 10000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.1, 0.1, 0.1, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000006', 'Bánh tráng trộn', 'SNACK', '1 bịch', 120.0, 280.0, 7.5, 42.0, 9.2, 4.5, 1.2, 336, 9.0, 50.4, 11.0, 5.4, 1.4, 6, '["SNACK"]'::jsonb, 20000, '[0.0, 0.9, 0.3, 0.0, 0.0, 0.2, 0.2, 0.5, 0.0, 0.7, 0.4, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000007', 'Bánh xèo', 'STAPLE', '1 cái lớn', 250.0, 190.0, 6.2, 22.5, 8.5, 1.0, 1.2, 475, 15.5, 56.3, 21.3, 2.5, 3.0, 7, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.6, 0.0, 0.7, 0.2, 0.9, 0.0, 0.1, 0.2, 0.4]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000008', 'Bò kho', 'SOUP', '1 tô vừa', 350.0, 120.0, 10.5, 7.5, 5.2, 1.5, 1.1, 420, 36.8, 26.3, 18.2, 5.3, 3.9, 8, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, '[1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.4, 0.6, 0.5, 0.2, 0.9]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000009', 'Bò lá lốt', 'MEAT', '1 đĩa (8 cuốn)', 200.0, 175.0, 14.5, 6.0, 10.2, 0.8, 1.4, 350, 29.0, 12.0, 20.4, 1.6, 2.8, 9, '["LUNCH", "DINNER"]'::jsonb, 60000, '[0.0, 0.8, 1.0, 0.1, 0.0, 0.0, 0.2, 0.6, 0.2, 0.2, 0.1, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000000a', 'Bông cải', 'VEGETABLE', '1 đĩa luộc', 150.0, 34.0, 2.8, 6.6, 0.4, 1.7, 2.6, 51, 4.2, 9.9, 0.6, 2.6, 3.9, 10, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.4]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000000b', 'Bún', 'STAPLE', '1 bát bún tươi', 150.0, 110.0, 1.7, 25.0, 0.2, 0.1, 0.5, 165, 2.6, 37.5, 0.3, 0.2, 0.8, 11, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 10000, '[0.2, 0.8, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.9, 0.0, 0.0, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000000c', 'Bún bò Huế', 'NOODLE', '1 tô', 500.0, 95.0, 6.2, 11.5, 2.8, 0.8, 0.7, 475, 31.0, 57.5, 14.0, 4.0, 3.5, 12, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, '[1.0, 0.0, 0.8, 0.4, 0.0, 0.0, 0.1, 0.3, 0.7, 0.8, 0.1, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000000d', 'Bún chả', 'NOODLE', '1 phần', 400.0, 135.0, 7.8, 16.5, 4.2, 2.5, 0.8, 540, 31.2, 66.0, 16.8, 10.0, 3.2, 13, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.3, 0.8, 0.0, 1.0, 0.0, 0.0, 0.2, 0.6, 0.3, 0.2, 0.5, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000000e', 'Bún đậu', 'NOODLE', '1 mẹt đầy đủ', 450.0, 150.0, 8.5, 15.2, 6.2, 1.0, 1.2, 675, 38.3, 68.4, 27.9, 4.5, 5.4, 14, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 1.0, 0.0, 0.9, 0.0, 0.2, 0.2, 0.8, 0.3, 0.3, 0.1, 0.7]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000000f', 'Bún mắm', 'NOODLE', '1 tô', 500.0, 105.0, 7.2, 13.0, 2.5, 1.2, 0.9, 525, 36.0, 65.0, 12.5, 6.0, 4.5, 15, '["LUNCH", "DINNER"]'::jsonb, 55000, '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.1, 0.2, 0.8, 0.4, 0.2, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000010', 'Bún riêu', 'NOODLE', '1 tô', 480.0, 92.0, 5.8, 12.0, 2.1, 0.9, 0.8, 442, 27.8, 57.6, 10.1, 4.3, 3.8, 16, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000011', 'Cá', 'SEAFOOD', '1 lát cá rán', 150.0, 180.0, 20.5, 0.5, 10.5, 0.0, 0.0, 270, 30.8, 0.8, 15.8, 0.0, 0.0, 17, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.8, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.1, 0.0, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000012', 'Cà chua', 'VEGETABLE', '1 quả vừa', 100.0, 18.0, 0.9, 3.9, 0.2, 2.6, 1.2, 18, 0.9, 3.9, 0.2, 2.6, 1.2, 18, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, '[0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.3, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000013', 'Cà pháo', 'VEGETABLE', '1 đĩa nhỏ muối', 80.0, 24.0, 1.2, 4.5, 0.1, 1.5, 1.8, 19, 1.0, 3.6, 0.1, 1.2, 1.4, 19, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.7, 0.2, 0.1, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000014', 'Cà rốt', 'VEGETABLE', '1 củ vừa', 100.0, 41.0, 0.9, 9.6, 0.2, 4.7, 2.8, 41, 0.9, 9.6, 0.2, 4.7, 2.8, 20, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 8000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.4, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000015', 'Canh', 'SOUP', '1 bát canh rau', 250.0, 28.0, 1.5, 3.2, 0.8, 0.6, 1.0, 70, 3.8, 8.0, 2.0, 1.5, 2.5, 21, '["LUNCH", "DINNER"]'::jsonb, 15000, '[1.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.2]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000016', 'Chả', 'MEAT', '1 khoanh chả lụa', 100.0, 215.0, 15.2, 3.5, 15.5, 0.5, 0.0, 215, 15.2, 3.5, 15.5, 0.5, 0.0, 22, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.3, 0.8, 0.2, 0.1, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000017', 'Chả giò', 'SNACK', '1 đĩa (5 cuốn)', 180.0, 260.0, 8.5, 22.0, 15.2, 1.2, 1.0, 468, 15.3, 39.6, 27.4, 2.2, 1.8, 23, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 45000, '[0.0, 1.0, 0.0, 0.8, 0.0, 0.3, 0.1, 1.0, 0.0, 0.1, 0.1, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000018', 'Chanh', 'VEGETABLE', '1 quả', 60.0, 29.0, 1.1, 9.3, 0.3, 2.5, 2.8, 17, 0.7, 5.6, 0.2, 1.5, 1.7, 24, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 3000, '[0.0, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.1, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000019', 'Cơm', 'RICE', '1 chén cơm trắng', 150.0, 130.0, 2.7, 28.2, 0.3, 0.1, 0.4, 195, 4.1, 42.3, 0.5, 0.2, 0.6, 25, '["LUNCH", "DINNER"]'::jsonb, 5000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000001a', 'Cơm tấm', 'RICE', '1 đĩa sườn bì chả', 400.0, 185.0, 8.2, 22.5, 6.8, 1.5, 1.0, 740, 32.8, 90.0, 27.2, 6.0, 4.0, 26, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.5]'::jsonb, 'SYSTEM', true),
-- Bỏ qua class_id 27 ('Con nguoi')
('10000000-0000-0000-0000-00000000001c', 'Củ kiệu', 'VEGETABLE', '1 đĩa nhỏ chua ngọt', 50.0, 65.0, 0.8, 15.0, 0.1, 12.0, 1.5, 33, 0.4, 7.5, 0.1, 6.0, 0.8, 28, '["LUNCH", "DINNER"]'::jsonb, 15000, '[0.0, 0.6, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.2, 0.6, 0.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000001d', 'Cua', 'SEAFOOD', '1 con cua hấp vừa', 250.0, 87.0, 18.2, 0.0, 1.1, 0.0, 0.0, 218, 45.5, 0.0, 2.8, 0.0, 0.0, 29, '["LUNCH", "DINNER"]'::jsonb, 150000, '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.1, 0.0, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000001e', 'Đậu hũ', 'STAPLE', '1 bìa đậu rán', 150.0, 140.0, 11.5, 3.2, 8.8, 0.5, 1.0, 210, 17.3, 4.8, 13.2, 0.8, 1.5, 30, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 0.9, 0.0, 0.0, 0.0, 0.0, 1.0, 0.7, 0.3, 0.0, 0.0, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000001f', 'Dưa chua', 'VEGETABLE', '1 chén nhỏ', 100.0, 19.0, 1.1, 3.2, 0.2, 1.0, 1.5, 19, 1.1, 3.2, 0.2, 1.0, 1.5, 31, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.1, 0.2, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000020', 'Dưa leo', 'VEGETABLE', '1 quả vừa', 120.0, 15.0, 0.7, 3.6, 0.1, 1.7, 0.5, 18, 0.8, 4.3, 0.1, 2.0, 0.6, 32, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.2, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000021', 'Gỏi cuốn', 'SNACK', '1 đĩa (4 cuốn)', 240.0, 130.0, 6.8, 19.5, 2.5, 1.5, 1.2, 312, 16.3, 46.8, 6.0, 3.6, 2.9, 33, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 35000, '[0.0, 0.8, 0.0, 0.5, 0.0, 0.6, 0.5, 0.0, 1.0, 0.1, 0.2, 0.5]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000022', 'Hamburger', 'STAPLE', '1 cái vừa', 200.0, 260.0, 13.5, 28.0, 10.5, 4.5, 1.8, 520, 27.0, 56.0, 21.0, 9.0, 3.6, 34, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 1.0, 1.0, 0.2, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.3, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000023', 'Heo quay', 'MEAT', '1 đĩa nhỏ', 150.0, 320.0, 16.0, 1.5, 28.0, 0.5, 0.0, 480, 24.0, 2.3, 42.0, 0.8, 0.0, 35, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.2, 0.2, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000024', 'Hủ tiếu', 'NOODLE', '1 tô Nam Vang', 450.0, 95.0, 5.5, 13.0, 2.1, 0.8, 0.6, 428, 24.8, 58.5, 9.5, 3.6, 2.7, 36, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.7, 0.0, 0.5, 0.1, 0.1, 0.9, 0.1, 0.2, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000025', 'Khổ qua thịt', 'SOUP', '1 quả nhồi thịt', 200.0, 75.0, 6.2, 4.0, 3.5, 1.0, 1.5, 150, 12.4, 8.0, 7.0, 2.0, 3.0, 37, '["LUNCH", "DINNER"]'::jsonb, 30000, '[1.0, 0.0, 0.0, 0.8, 0.0, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000026', 'Khoai tây chiên', 'SNACK', '1 phần vừa', 120.0, 312.0, 3.4, 41.5, 15.0, 0.3, 3.8, 374, 4.1, 49.8, 18.0, 0.4, 4.6, 38, '["SNACK"]'::jsonb, 30000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.8, 1.0, 0.0, 0.1, 0.1, 0.1]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000027', 'Lẩu', 'SOUP', '1 phần ăn kèm nước', 500.0, 85.0, 7.0, 6.5, 3.2, 1.2, 1.5, 425, 35.0, 32.5, 16.0, 6.0, 7.5, 39, '["LUNCH", "DINNER"]'::jsonb, 180000, '[1.0, 0.0, 0.6, 0.3, 0.2, 0.6, 0.4, 0.2, 0.8, 0.6, 0.2, 0.9]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000028', 'Lòng heo', 'MEAT', '1 đĩa luộc', 150.0, 165.0, 15.5, 0.5, 11.2, 0.0, 0.0, 248, 23.3, 0.8, 16.8, 0.0, 0.0, 40, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 0.9, 0.1, 0.0, 0.9]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000029', 'Mì', 'NOODLE', '1 tô mì gói xào/nước', 350.0, 130.0, 4.5, 18.5, 4.2, 1.2, 0.8, 455, 15.8, 64.8, 14.7, 4.2, 2.8, 41, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 30000, '[0.8, 0.3, 0.2, 0.5, 0.0, 0.2, 0.1, 0.6, 0.3, 0.4, 0.1, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000002a', 'Mực', 'SEAFOOD', '1 con mực xào/hấp', 150.0, 92.0, 17.5, 3.0, 1.4, 0.0, 0.0, 138, 26.3, 4.5, 2.1, 0.0, 0.0, 42, '["LUNCH", "DINNER"]'::jsonb, 70000, '[0.0, 0.7, 0.0, 0.0, 0.0, 1.0, 0.0, 0.4, 0.7, 0.2, 0.0, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000002b', 'Nấm', 'VEGETABLE', '1 đĩa nấm xào', 150.0, 45.0, 3.1, 6.2, 0.8, 1.5, 2.1, 68, 4.7, 9.3, 1.2, 2.3, 3.2, 43, '["LUNCH", "DINNER"]'::jsonb, 30000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.0, 0.1, 0.5]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000002c', 'Ốc', 'SEAFOOD', '1 đĩa ốc luộc/xào', 200.0, 85.0, 14.2, 3.5, 1.2, 0.5, 0.0, 170, 28.4, 7.0, 2.4, 1.0, 0.0, 44, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 55000, '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.3, 0.7, 0.5, 0.1, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000002d', 'Ớt chuông', 'VEGETABLE', '1 quả vừa', 120.0, 26.0, 1.0, 6.0, 0.3, 4.2, 2.1, 31, 1.2, 7.2, 0.4, 5.0, 2.5, 45, '["LUNCH", "DINNER"]'::jsonb, 12000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.1, 0.3, 0.2]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000002e', 'Phở', 'NOODLE', '1 tô phở bò', 500.0, 90.0, 5.8, 11.2, 2.2, 0.8, 0.6, 450, 29.0, 56.0, 11.0, 4.0, 3.0, 46, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, '[1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000002f', 'Phô mai', 'STAPLE', '1 miếng tam giác', 30.0, 350.0, 21.0, 2.0, 28.0, 1.5, 0.0, 105, 6.3, 0.6, 8.4, 0.5, 0.0, 47, '["BREAKFAST", "SNACK"]'::jsonb, 8000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.4, 0.6, 0.2, 0.0, 0.2, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000030', 'Rau', 'VEGETABLE', '1 đĩa rau muống xào', 150.0, 40.0, 2.5, 3.8, 1.8, 0.5, 2.2, 60, 3.8, 5.7, 2.7, 0.8, 3.3, 48, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.5, 0.1, 0.1, 0.4]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000031', 'Salad', 'VEGETABLE', '1 dĩa dầu giấm', 180.0, 55.0, 1.5, 5.2, 3.2, 2.5, 1.8, 99, 2.7, 9.4, 5.8, 4.5, 3.2, 49, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.9, 0.1, 0.9, 0.0, 0.2, 0.2]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000032', 'Thịt bò', 'MEAT', '1 phần xào/nướng', 150.0, 210.0, 26.0, 0.0, 11.5, 0.0, 0.0, 315, 39.0, 0.0, 17.3, 0.0, 0.0, 50, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 0.9, 1.0, 0.0, 0.0, 0.0, 0.0, 0.6, 0.3, 0.2, 0.0, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000033', 'Thịt gà', 'MEAT', '1 góc đùi luộc/rán', 160.0, 215.0, 24.5, 0.5, 12.0, 0.0, 0.0, 344, 39.2, 0.8, 19.2, 0.0, 0.0, 51, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.4, 0.7, 0.1, 0.0, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000034', 'Thịt heo', 'MEAT', '1 đĩa thịt luộc', 150.0, 240.0, 22.0, 0.0, 16.5, 0.0, 0.0, 360, 33.0, 0.0, 24.8, 0.0, 0.0, 52, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 0.9, 0.0, 0.0, 0.9]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000035', 'Thịt kho', 'MEAT', '1 bát thịt kho tàu', 200.0, 260.0, 16.5, 3.5, 20.0, 3.0, 0.0, 520, 33.0, 7.0, 40.0, 6.0, 0.0, 53, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.2, 0.7, 0.0, 1.0, 0.0, 0.0, 0.0, 0.6, 0.4, 0.1, 0.4, 0.7]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000036', 'Thịt nướng', 'MEAT', '1 xiên', 100.0, 280.0, 20.0, 4.0, 20.0, 3.5, 0.0, 280, 20.0, 4.0, 20.0, 3.5, 0.0, 54, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 30000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.7, 0.1, 0.3, 0.3, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000037', 'Tôm', 'SEAFOOD', '1 đĩa tôm luộc', 150.0, 99.0, 24.0, 0.2, 0.3, 0.0, 0.0, 149, 36.0, 0.3, 0.5, 0.0, 0.0, 55, '["LUNCH", "DINNER"]'::jsonb, 60000, '[0.0, 0.6, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000038', 'Trứng', 'STAPLE', '1 quả trứng gà ốp la', 60.0, 155.0, 13.0, 1.1, 11.0, 1.1, 0.0, 93, 7.8, 0.7, 6.6, 0.7, 0.0, 56, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 7000, '[0.0, 0.8, 0.0, 0.0, 0.3, 0.0, 0.2, 0.6, 0.4, 0.0, 0.1, 0.8]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000039', 'Xôi', 'STAPLE', '1 gói xôi mặn', 200.0, 260.0, 8.5, 45.0, 5.2, 1.5, 1.8, 520, 17.0, 90.0, 10.4, 3.0, 3.6, 57, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 25000, '[0.0, 1.0, 0.0, 0.6, 0.4, 0.0, 0.1, 0.4, 0.7, 0.1, 0.2, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000003a', 'Bánh bèo', 'SNACK', '1 mâm (6 chén)', 180.0, 135.0, 4.8, 22.0, 3.2, 0.8, 0.6, 243, 8.6, 39.6, 5.8, 1.4, 1.1, 58, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 35000, '[0.0, 0.9, 0.0, 0.3, 0.0, 0.6, 0.1, 0.2, 0.9, 0.2, 0.2, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000003b', 'Cao lầu', 'NOODLE', '1 tô Hội An', 420.0, 115.0, 7.0, 16.0, 2.5, 1.0, 1.2, 483, 29.4, 67.2, 10.5, 4.2, 5.0, 59, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.3, 0.8, 0.0, 0.9, 0.0, 0.0, 0.2, 0.4, 0.5, 0.2, 0.2, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000003c', 'Mì Quảng', 'NOODLE', '1 tô tôm thịt', 450.0, 120.0, 7.2, 16.5, 2.8, 1.0, 1.0, 540, 32.4, 74.3, 12.6, 4.5, 4.5, 60, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[0.4, 0.7, 0.0, 0.6, 0.2, 0.6, 0.1, 0.3, 0.7, 0.3, 0.2, 0.6]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000003d', 'Cơm chiên dương châu', 'RICE', '1 đĩa', 350.0, 175.0, 6.8, 26.0, 4.8, 1.2, 1.0, 613, 23.8, 91.0, 16.8, 4.2, 3.5, 61, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.6, 0.0, 0.4, 0.1, 0.9, 0.0, 0.1, 0.1, 0.4]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000003e', 'Bún chả cá', 'NOODLE', '1 tô', 450.0, 88.0, 5.5, 12.0, 1.8, 0.8, 0.7, 396, 24.8, 54.0, 8.1, 3.6, 3.2, 62, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.2, 0.8, 0.2, 0.1, 0.7]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-00000000003f', 'Cơm chiên gà', 'RICE', '1 đĩa', 350.0, 185.0, 9.2, 25.5, 5.1, 1.0, 0.9, 648, 32.2, 89.3, 17.9, 3.5, 3.2, 63, '["LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.9, 0.0, 0.1, 0.1, 0.5]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000040', 'Cháo lòng', 'SOUP', '1 tô', 450.0, 92.0, 5.8, 11.0, 2.5, 0.5, 0.4, 414, 26.1, 49.5, 11.3, 2.3, 1.8, 64, '["BREAKFAST", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.0, 0.2, 0.9, 0.2, 0.0, 0.7]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000041', 'Nộm hoa chuối', 'VEGETABLE', '1 đĩa tai heo', 200.0, 75.0, 4.2, 8.0, 2.8, 5.0, 3.5, 150, 8.4, 16.0, 5.6, 10.0, 7.0, 65, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.4, 0.0, 0.4, 0.0, 0.0, 0.7, 0.0, 0.9, 0.3, 0.6, 0.3]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000042', 'Nui xào bò', 'NOODLE', '1 đĩa', 300.0, 165.0, 8.5, 22.0, 4.8, 1.8, 1.2, 495, 25.5, 66.0, 14.4, 5.4, 3.6, 66, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.1, 0.8, 0.1, 0.1, 0.2, 0.5]'::jsonb, 'SYSTEM', true),
('10000000-0000-0000-0000-000000000043', 'Súp cua', 'SOUP', '1 chén', 250.0, 82.0, 5.2, 10.5, 1.9, 0.8, 0.6, 205, 13.0, 26.3, 4.8, 2.0, 1.5, 67, '["BREAKFAST", "SNACK", "DINNER"]'::jsonb, 30000, '[1.0, 0.0, 0.0, 0.0, 0.3, 0.9, 0.1, 0.1, 0.9, 0.1, 0.1, 0.7]'::jsonb, 'SYSTEM', true)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 5b. FOOD: 100 món ăn & đồ uống Việt Nam mở rộng (0101 - 0200)
-- ============================================================
INSERT INTO foods (
    id, name, category, default_serving_name, default_serving_grams,
    calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, sugar_per_100g, fiber_per_100g,
    calories_per_serving, protein_per_serving, carbs_per_serving, fat_per_serving, sugar_per_serving, fiber_per_serving,
    image_url, barcode, source, vietfood_class_id, suitable_meal_types,
    reference_price_vnd, feature_vector, is_verified, owner_user_id, deleted_at
) VALUES
-- 2 món mẫu mở rộng từ file gốc (0101 - 0102)
('10000000-0000-0000-0000-000000000101', 'Canh chua cá lóc', 'SOUP', '1 tô vừa', 300.0, 48.0, 4.5, 4.2, 1.4, 1.5, 0.8, 144, 13.5, 12.6, 4.2, 4.5, 2.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.3, 0.1, 0.9, 0.2, 0.3, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000102', 'Sữa chua không đường', 'SNACK', '1 hộp', 100.0, 62.0, 3.8, 4.8, 3.0, 4.5, 0.0, 62, 3.8, 4.8, 3.0, 4.5, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 8000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0, 0.8, 0.0, 0.2, 0.4]'::jsonb, true, NULL, NULL),
-- Nhóm MEAT: Món mặn từ thịt heo, gà, vịt, bò, chả trứng (22 món: 0103 - 0124)
('10000000-0000-0000-0000-000000000103', 'Sườn xào chua ngọt', 'MEAT', '1 đĩa vừa', 150.0, 245.0, 17.0, 11.5, 14.5, 8.5, 0.2, 368, 25.5, 17.2, 21.8, 12.8, 0.3, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 0.9, 0.0, 1.0, 0.0, 0.0, 0.0, 0.8, 0.1, 0.1, 0.6, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000104', 'Thịt ba chỉ rang cháy cạnh', 'MEAT', '1 đĩa vừa', 140.0, 310.0, 16.5, 3.5, 25.5, 2.8, 0.0, 434, 23.1, 4.9, 35.7, 3.9, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.2, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000105', 'Thịt kho tiêu', 'MEAT', '1 bát nhỏ', 150.0, 235.0, 18.5, 4.2, 16.0, 3.2, 0.1, 352, 27.8, 6.3, 24.0, 4.8, 0.1, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.1, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.6, 0.3, 0.5, 0.3, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000106', 'Thịt kho trứng cút', 'MEAT', '1 bát vừa', 200.0, 225.0, 16.0, 4.5, 15.8, 3.5, 0.0, 450, 32.0, 9.0, 31.6, 7.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 42000, '[0.2, 0.7, 0.0, 1.0, 0.2, 0.0, 0.0, 0.6, 0.4, 0.1, 0.4, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000107', 'Thịt bằm rang sả ớt', 'MEAT', '1 đĩa nhỏ', 120.0, 230.0, 19.0, 4.5, 15.0, 2.0, 0.4, 276, 22.8, 5.4, 18.0, 2.4, 0.5, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.6, 0.1, 0.7, 0.2, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000108', 'Sườn ram mặn ngọt', 'MEAT', '1 đĩa vừa', 150.0, 250.0, 18.0, 8.0, 16.2, 6.5, 0.0, 375, 27.0, 12.0, 24.3, 9.8, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 0.9, 0.0, 1.0, 0.0, 0.0, 0.0, 0.7, 0.2, 0.2, 0.5, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000109', 'Chả lá lốt thịt heo', 'MEAT', '1 đĩa (6 cuốn)', 160.0, 225.0, 15.5, 5.5, 15.5, 0.8, 1.2, 360, 24.8, 8.8, 24.8, 1.3, 1.9, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.2, 0.8, 0.0, 0.2, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000110', 'Giò thủ', 'MEAT', '1 đĩa nhỏ', 100.0, 265.0, 17.5, 2.5, 20.5, 0.5, 0.4, 265, 17.5, 2.5, 20.5, 0.5, 0.4, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 30000, '[0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.5, 0.6, 0.4, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000111', 'Thịt chân giò luộc', 'MEAT', '1 đĩa vừa', 150.0, 230.0, 20.5, 0.0, 16.2, 0.0, 0.0, 345, 30.8, 0.0, 24.3, 0.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 0.8, 0.0, 1.0, 0.0, 0.0, 0.0, 0.2, 1.0, 0.0, 0.0, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000112', 'Gà kho gừng', 'MEAT', '1 đĩa vừa', 160.0, 195.0, 21.0, 4.5, 10.2, 3.0, 0.3, 312, 33.6, 7.2, 16.3, 4.8, 0.5, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.1, 0.8, 0.0, 0.0, 1.0, 0.0, 0.0, 0.4, 0.5, 0.4, 0.3, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000113', 'Gà rang sả ớt', 'MEAT', '1 đĩa vừa', 160.0, 205.0, 20.5, 5.2, 11.2, 2.5, 0.5, 328, 32.8, 8.3, 17.9, 4.0, 0.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 40000, '[0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.6, 0.2, 0.7, 0.2, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000114', 'Gà chiên nước mắm', 'MEAT', '1 phần (2 cánh/1 đùi)', 160.0, 265.0, 19.5, 7.5, 17.5, 4.8, 0.0, 424, 31.2, 12.0, 28.0, 7.7, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.9, 0.0, 0.2, 0.4, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000115', 'Ức gà áp chảo', 'MEAT', '1 miếng vừa', 150.0, 165.0, 30.0, 0.5, 4.2, 0.0, 0.0, 248, 45.0, 0.8, 6.3, 0.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 0.9, 0.0, 0.0, 1.0, 0.0, 0.0, 0.2, 0.6, 0.1, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000116', 'Vịt kho gừng', 'MEAT', '1 đĩa vừa', 170.0, 240.0, 18.5, 4.0, 16.5, 2.8, 0.2, 408, 31.4, 6.8, 28.1, 4.8, 0.3, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.1, 0.8, 0.0, 0.0, 1.0, 0.0, 0.0, 0.6, 0.4, 0.4, 0.3, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000117', 'Vịt luộc chấm mắm gừng', 'MEAT', '1 đĩa chặt', 180.0, 225.0, 19.0, 0.5, 16.2, 0.2, 0.0, 405, 34.2, 0.9, 29.2, 0.4, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 0.8, 0.0, 0.0, 1.0, 0.0, 0.0, 0.3, 1.0, 0.3, 0.1, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000118', 'Bò xào cần tỏi', 'MEAT', '1 đĩa vừa', 180.0, 135.0, 14.5, 4.8, 6.2, 1.8, 1.6, 243, 26.1, 8.6, 11.2, 3.2, 2.9, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 0.7, 1.0, 0.0, 0.0, 0.0, 0.3, 0.4, 0.2, 0.2, 0.1, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000119', 'Bò xào lúc lắc', 'MEAT', '1 đĩa vừa', 180.0, 185.0, 17.0, 7.0, 9.8, 3.2, 1.2, 333, 30.6, 12.6, 17.6, 5.8, 2.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 0.9, 1.0, 0.0, 0.0, 0.0, 0.2, 0.6, 0.1, 0.2, 0.3, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000120', 'Bò sốt vang', 'MEAT', '1 bát vừa', 250.0, 130.0, 11.5, 6.8, 6.2, 2.0, 1.0, 325, 28.8, 17.0, 15.5, 5.0, 2.5, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 55000, '[0.8, 0.1, 1.0, 0.0, 0.0, 0.0, 0.1, 0.4, 0.7, 0.3, 0.2, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000121', 'Bò xào hành tây', 'MEAT', '1 đĩa vừa', 180.0, 145.0, 15.0, 6.5, 6.5, 2.8, 1.2, 261, 27.0, 11.7, 11.7, 5.0, 2.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 55000, '[0.0, 0.8, 1.0, 0.0, 0.0, 0.0, 0.2, 0.4, 0.2, 0.2, 0.2, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000122', 'Chả trứng hấp', 'MEAT', '1 miếng vuông', 120.0, 185.0, 13.5, 6.5, 11.8, 0.8, 0.5, 222, 16.2, 7.8, 14.2, 1.0, 0.6, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 25000, '[0.0, 0.9, 0.0, 0.8, 0.2, 0.0, 0.1, 0.2, 0.9, 0.2, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000123', 'Đậu hũ nhồi thịt sốt cà chua', 'MEAT', '1 đĩa (3 miếng)', 200.0, 155.0, 11.5, 6.2, 9.5, 2.2, 1.2, 310, 23.0, 12.4, 19.0, 4.4, 2.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.2, 0.7, 0.0, 0.7, 0.0, 0.0, 0.4, 0.6, 0.4, 0.1, 0.2, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000124', 'Trứng chiên thịt bằm', 'MEAT', '1 đĩa nhỏ', 140.0, 215.0, 15.2, 2.0, 16.2, 0.6, 0.1, 301, 21.3, 2.8, 22.7, 0.8, 0.1, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 30000, '[0.0, 1.0, 0.0, 0.6, 0.3, 0.0, 0.0, 0.8, 0.0, 0.1, 0.1, 0.8]'::jsonb, true, NULL, NULL),
-- Nhóm SEAFOOD: Món cá, tôm, mực, nghêu, lươn (14 món: 0125 - 0138)
('10000000-0000-0000-0000-000000000125', 'Cá kho tộ', 'SEAFOOD', '1 khúc vừa', 150.0, 175.0, 19.5, 5.2, 8.2, 4.0, 0.0, 262, 29.2, 7.8, 12.3, 6.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.1, 0.8, 0.0, 0.2, 0.0, 1.0, 0.0, 0.5, 0.4, 0.4, 0.3, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000126', 'Cá thu sốt cà chua', 'SEAFOOD', '1 lát vừa', 160.0, 170.0, 18.0, 4.8, 8.8, 2.5, 0.6, 272, 28.8, 7.7, 14.1, 4.0, 1.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.1, 0.8, 0.0, 0.0, 0.0, 1.0, 0.1, 0.6, 0.3, 0.2, 0.2, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000127', 'Cá diêu hồng chiên xù', 'SEAFOOD', '1 phần (1/2 con)', 180.0, 215.0, 19.5, 6.5, 12.2, 0.5, 0.2, 387, 35.1, 11.7, 22.0, 0.9, 0.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.1, 0.0, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000128', 'Cá nục kho cà chua', 'SEAFOOD', '1 đĩa (2 con nhỏ)', 150.0, 155.0, 18.5, 4.5, 6.8, 2.2, 0.5, 232, 27.8, 6.8, 10.2, 3.3, 0.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.2, 0.7, 0.0, 0.0, 0.0, 1.0, 0.1, 0.3, 0.6, 0.3, 0.2, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000129', 'Cá hồi áp chảo', 'SEAFOOD', '1 lát phi lê', 140.0, 215.0, 22.0, 0.5, 13.8, 0.0, 0.0, 301, 30.8, 0.7, 19.3, 0.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 75000, '[0.0, 0.9, 0.0, 0.0, 0.0, 1.0, 0.0, 0.4, 0.4, 0.1, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000130', 'Cá trắm kho riềng', 'SEAFOOD', '1 khúc vừa', 150.0, 165.0, 19.0, 4.2, 7.8, 2.2, 0.3, 248, 28.5, 6.3, 11.7, 3.3, 0.5, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.1, 0.8, 0.0, 0.2, 0.0, 1.0, 0.0, 0.4, 0.6, 0.3, 0.2, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000131', 'Cá rô phi rán giòn', 'SEAFOOD', '1 đĩa (1/2 con)', 160.0, 195.0, 21.0, 2.0, 11.2, 0.0, 0.0, 312, 33.6, 3.2, 17.9, 0.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.1, 0.0, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000132', 'Tôm rim mặn ngọt', 'SEAFOOD', '1 đĩa nhỏ', 130.0, 155.0, 19.5, 7.5, 5.2, 6.0, 0.0, 202, 25.4, 9.8, 6.8, 7.8, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 0.9, 0.0, 0.0, 0.0, 1.0, 0.0, 0.4, 0.2, 0.2, 0.5, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000133', 'Tôm rang thịt ba chỉ', 'SEAFOOD', '1 đĩa vừa', 150.0, 215.0, 18.0, 5.5, 13.5, 4.2, 0.0, 322, 27.0, 8.2, 20.2, 6.3, 0.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 0.9, 0.0, 0.6, 0.0, 0.7, 0.0, 0.7, 0.1, 0.2, 0.4, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000134', 'Mực xào cần tỏi', 'SEAFOOD', '1 đĩa vừa', 170.0, 105.0, 13.5, 4.5, 3.6, 1.5, 1.4, 178, 22.9, 7.7, 6.1, 2.5, 2.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 60000, '[0.0, 0.7, 0.0, 0.0, 0.0, 1.0, 0.3, 0.4, 0.3, 0.2, 0.1, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000135', 'Mực nhồi thịt sốt cà', 'SEAFOOD', '1 đĩa (2 con vừa)', 180.0, 145.0, 15.5, 5.0, 6.8, 2.0, 0.6, 261, 27.9, 9.0, 12.2, 3.6, 1.1, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.1, 0.8, 0.0, 0.5, 0.0, 0.8, 0.1, 0.4, 0.5, 0.2, 0.2, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000136', 'Chả mực chiên', 'SEAFOOD', '1 đĩa (4 miếng)', 140.0, 210.0, 16.5, 7.5, 12.5, 0.8, 0.2, 294, 23.1, 10.5, 17.5, 1.1, 0.3, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 65000, '[0.0, 1.0, 0.0, 0.2, 0.0, 1.0, 0.0, 0.9, 0.0, 0.2, 0.1, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000137', 'Nghêu hấp sả', 'SEAFOOD', '1 tô nhỏ (cả nước)', 250.0, 48.0, 7.5, 2.5, 0.8, 0.5, 0.2, 120, 18.8, 6.2, 2.0, 1.2, 0.5, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 45000, '[0.5, 0.4, 0.0, 0.0, 0.0, 1.0, 0.1, 0.0, 1.0, 0.5, 0.1, 1.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000138', 'Lươn xào sả ớt', 'SEAFOOD', '1 đĩa vừa', 150.0, 190.0, 17.5, 4.5, 11.2, 1.2, 0.6, 285, 26.2, 6.8, 16.8, 1.8, 0.9, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 0.8, 0.0, 0.0, 0.0, 1.0, 0.1, 0.6, 0.1, 0.8, 0.1, 1.0]'::jsonb, true, NULL, NULL),
-- Nhóm SOUP: Canh bữa cơm gia đình & cháo (13 món + 0101 = 14 món: 0139 - 0151)
('10000000-0000-0000-0000-000000000139', 'Canh cua rau đay mồng tơi', 'SOUP', '1 bát lớn', 280.0, 35.0, 3.2, 3.0, 1.1, 0.6, 1.4, 98, 9.0, 8.4, 3.1, 1.7, 3.9, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 25000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.7, 0.7, 0.0, 1.0, 0.0, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000140', 'Canh bí đỏ nấu xương', 'SOUP', '1 bát lớn', 280.0, 58.0, 3.5, 6.2, 2.2, 2.0, 1.1, 162, 9.8, 17.4, 6.2, 5.6, 3.1, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 25000, '[1.0, 0.0, 0.0, 0.5, 0.0, 0.0, 0.5, 0.1, 1.0, 0.0, 0.3, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000141', 'Canh bí đao nấu tôm', 'SOUP', '1 bát lớn', 280.0, 32.0, 3.4, 3.2, 0.6, 1.2, 0.9, 90, 9.5, 9.0, 1.7, 3.4, 2.5, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 25000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.6, 0.6, 0.0, 1.0, 0.0, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000142', 'Canh cải thảo thịt bằm', 'SOUP', '1 bát lớn', 280.0, 38.0, 3.2, 2.5, 1.6, 0.8, 1.0, 106, 9.0, 7.0, 4.5, 2.2, 2.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 22000, '[1.0, 0.0, 0.0, 0.5, 0.0, 0.0, 0.6, 0.1, 1.0, 0.0, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000143', 'Canh bầu nấu tôm', 'SOUP', '1 bát lớn', 280.0, 30.0, 3.2, 3.1, 0.5, 1.2, 0.8, 84, 9.0, 8.7, 1.4, 3.4, 2.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 25000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.6, 0.6, 0.0, 1.0, 0.0, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000144', 'Canh sườn nấu sấu', 'SOUP', '1 bát lớn', 300.0, 62.0, 4.2, 3.2, 3.6, 0.8, 0.7, 186, 12.6, 9.6, 10.8, 2.4, 2.1, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 30000, '[1.0, 0.0, 0.0, 0.7, 0.0, 0.0, 0.2, 0.2, 1.0, 0.0, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000145', 'Canh ngao nấu chua', 'SOUP', '1 bát lớn', 300.0, 34.0, 3.8, 2.8, 0.8, 1.0, 0.6, 102, 11.4, 8.4, 2.4, 3.0, 1.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 28000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.8, 0.3, 0.0, 1.0, 0.1, 0.1, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000146', 'Canh rau ngót thịt bằm', 'SOUP', '1 bát lớn', 280.0, 42.0, 3.8, 2.8, 1.6, 0.6, 1.5, 118, 10.6, 7.8, 4.5, 1.7, 4.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 22000, '[1.0, 0.0, 0.0, 0.5, 0.0, 0.0, 0.7, 0.1, 1.0, 0.0, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000147', 'Canh măng nấu xương', 'SOUP', '1 bát lớn', 300.0, 68.0, 4.5, 3.0, 4.2, 0.6, 1.4, 204, 13.5, 9.0, 12.6, 1.8, 4.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 35000, '[1.0, 0.0, 0.0, 0.7, 0.0, 0.0, 0.3, 0.2, 1.0, 0.1, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000148', 'Canh rong biển đậu phụ', 'SOUP', '1 bát vừa', 260.0, 32.0, 2.6, 2.8, 1.1, 0.5, 0.9, 83, 6.8, 7.3, 2.9, 1.3, 2.3, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 22000, '[1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000149', 'Canh cà chua trứng', 'SOUP', '1 bát lớn', 280.0, 45.0, 3.0, 3.2, 2.2, 1.6, 0.6, 126, 8.4, 9.0, 6.2, 4.5, 1.7, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 20000, '[1.0, 0.0, 0.0, 0.0, 0.2, 0.0, 0.5, 0.2, 0.9, 0.0, 0.2, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000150', 'Cháo gà xé phay', 'SOUP', '1 tô vừa', 420.0, 78.0, 5.8, 9.8, 1.6, 0.4, 0.4, 328, 24.4, 41.2, 6.7, 1.7, 1.7, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "DINNER"]'::jsonb, 35000, '[1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.1, 1.0, 0.1, 0.0, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000151', 'Cháo thịt bằm', 'SOUP', '1 tô vừa', 400.0, 82.0, 4.8, 10.5, 2.2, 0.4, 0.3, 328, 19.2, 42.0, 8.8, 1.6, 1.2, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "DINNER"]'::jsonb, 30000, '[1.0, 0.0, 0.0, 0.8, 0.0, 0.0, 0.1, 0.1, 1.0, 0.1, 0.0, 0.6]'::jsonb, true, NULL, NULL),
-- Nhóm VEGETABLE: Rau luộc, rau xào, nộm/gỏi, món chay (15 món: 0152 - 0166)
('10000000-0000-0000-0000-000000000152', 'Rau muống luộc chấm mắm tỏi', 'VEGETABLE', '1 đĩa vừa', 180.0, 22.0, 2.4, 2.8, 0.2, 0.4, 2.1, 40, 4.3, 5.0, 0.4, 0.7, 3.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 15000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.2, 0.0, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000153', 'Rau muống xào tỏi', 'VEGETABLE', '1 đĩa vừa', 170.0, 58.0, 2.5, 3.6, 3.8, 0.5, 2.1, 99, 4.2, 6.1, 6.5, 0.8, 3.6, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.3, 0.1, 0.0, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000154', 'Rau lang luộc', 'VEGETABLE', '1 đĩa vừa', 180.0, 24.0, 2.5, 3.2, 0.3, 0.5, 2.4, 43, 4.5, 5.8, 0.5, 0.9, 4.3, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 15000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.4]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000155', 'Cải thìa xào dầu hào', 'VEGETABLE', '1 đĩa vừa', 180.0, 52.0, 1.8, 4.5, 3.0, 1.8, 1.6, 94, 3.2, 8.1, 5.4, 3.2, 2.9, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.1, 0.9, 0.4, 0.4, 0.0, 0.2, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000156', 'Su su xào tỏi', 'VEGETABLE', '1 đĩa vừa', 180.0, 46.0, 1.0, 4.8, 2.6, 1.8, 1.7, 83, 1.8, 8.6, 4.7, 3.2, 3.1, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 18000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.4, 0.4, 0.0, 0.2, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000157', 'Đậu que xào tỏi', 'VEGETABLE', '1 đĩa vừa', 160.0, 58.0, 2.0, 6.8, 2.8, 2.4, 2.7, 93, 3.2, 10.9, 4.5, 3.8, 4.3, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 20000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.4, 0.4, 0.0, 0.2, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000158', 'Mướp xào giá đỗ', 'VEGETABLE', '1 đĩa vừa', 180.0, 48.0, 2.0, 4.6, 2.4, 1.6, 1.5, 86, 3.6, 8.3, 4.3, 2.9, 2.7, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 18000, '[0.1, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.4, 0.5, 0.0, 0.2, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000159', 'Cải ngọt luộc', 'VEGETABLE', '1 đĩa vừa', 180.0, 20.0, 1.8, 2.8, 0.2, 0.8, 1.8, 36, 3.2, 5.0, 0.4, 1.4, 3.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 15000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.1, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000160', 'Bí xanh luộc', 'VEGETABLE', '1 đĩa vừa', 200.0, 16.0, 0.6, 3.2, 0.1, 1.4, 1.2, 32, 1.2, 6.4, 0.2, 2.8, 2.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 15000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.2, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000161', 'Rau củ thập cẩm luộc', 'VEGETABLE', '1 đĩa đầy', 220.0, 34.0, 1.6, 6.2, 0.3, 2.4, 2.5, 75, 3.5, 13.6, 0.7, 5.3, 5.5, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 25000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.2, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000162', 'Bắp cải xào cà chua', 'VEGETABLE', '1 đĩa vừa', 180.0, 48.0, 1.3, 5.5, 2.4, 2.8, 2.0, 86, 2.3, 9.9, 4.3, 5.0, 3.6, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 18000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.4, 0.4, 0.0, 0.2, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000163', 'Đậu bắp luộc chấm chao', 'VEGETABLE', '1 đĩa vừa', 160.0, 42.0, 2.4, 6.8, 0.8, 1.5, 3.0, 67, 3.8, 10.9, 1.3, 2.4, 4.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 18000, '[0.0, 0.4, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.1, 0.1, 0.3]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000164', 'Nộm đu đủ bò khô', 'VEGETABLE', '1 đĩa vừa', 200.0, 88.0, 5.5, 11.2, 2.4, 6.5, 2.0, 176, 11.0, 22.4, 4.8, 13.0, 4.0, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER", "SNACK"]'::jsonb, 35000, '[0.0, 0.5, 0.5, 0.0, 0.0, 0.0, 0.6, 0.1, 0.7, 0.5, 0.6, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000165', 'Gỏi ngó sen tôm thịt', 'VEGETABLE', '1 đĩa vừa', 220.0, 92.0, 6.8, 10.5, 2.6, 6.0, 2.2, 202, 15.0, 23.1, 5.7, 13.2, 4.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.0, 0.5, 0.0, 0.5, 0.0, 0.6, 0.6, 0.0, 0.9, 0.3, 0.6, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000166', 'Cà tím om đậu phụ', 'VEGETABLE', '1 tô nhỏ', 220.0, 92.0, 4.8, 6.8, 5.2, 1.8, 2.2, 202, 10.6, 15.0, 11.4, 4.0, 4.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 28000, '[0.3, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.5, 0.6, 0.1, 0.1, 0.5]'::jsonb, true, NULL, NULL),
-- Nhóm RICE: Cơm các loại (4 món: 0167 - 0170)
('10000000-0000-0000-0000-000000000167', 'Cơm gạo lứt', 'RICE', '1 chén vừa', 150.0, 118.0, 2.6, 24.5, 0.9, 0.2, 1.8, 177, 3.9, 36.8, 1.4, 0.3, 2.7, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 10000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 1.0, 0.0, 0.1, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000168', 'Cơm rang dưa bò', 'RICE', '1 đĩa đầy', 380.0, 175.0, 8.2, 23.5, 5.4, 1.2, 1.2, 665, 31.2, 89.3, 20.5, 4.6, 4.6, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.9, 0.0, 0.0, 0.0, 0.2, 0.8, 0.0, 0.2, 0.1, 0.5]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000169', 'Cơm gà Hội An', 'RICE', '1 đĩa đầy', 380.0, 165.0, 9.5, 22.0, 4.2, 1.5, 1.0, 627, 36.1, 83.6, 16.0, 5.7, 3.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.2, 0.3, 0.8, 0.2, 0.2, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000170', 'Cơm gà xối mỡ', 'RICE', '1 đĩa đầy', 400.0, 198.0, 9.8, 22.8, 7.5, 1.2, 0.8, 792, 39.2, 91.2, 30.0, 4.8, 3.2, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 50000, '[0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.9, 0.0, 0.1, 0.1, 0.5]'::jsonb, true, NULL, NULL),
-- Nhóm STAPLE: Khoai, ngô, xôi, trứng, đậu phụ (7 món: 0171 - 0177)
('10000000-0000-0000-0000-000000000171', 'Khoai lang luộc', 'STAPLE', '1 củ vừa', 150.0, 86.0, 1.6, 20.1, 0.1, 4.2, 3.0, 129, 2.4, 30.1, 0.1, 6.3, 4.5, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK", "LUNCH"]'::jsonb, 10000, '[0.0, 0.9, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.5, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000172', 'Ngô nếp luộc', 'STAPLE', '1 bắp vừa (phần ăn được)', 140.0, 96.0, 3.2, 20.5, 1.2, 3.2, 2.4, 134, 4.5, 28.7, 1.7, 4.5, 3.4, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 12000, '[0.0, 0.9, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.3, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000173', 'Xôi xéo', 'STAPLE', '1 gói vừa', 200.0, 248.0, 6.2, 43.5, 5.4, 1.2, 2.4, 496, 12.4, 87.0, 10.8, 2.4, 4.8, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 20000, '[0.0, 1.0, 0.0, 0.0, 0.1, 0.0, 0.8, 0.4, 0.8, 0.0, 0.1, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000174', 'Xôi gấc', 'STAPLE', '1 gói vừa', 180.0, 235.0, 4.5, 46.5, 3.5, 6.5, 1.6, 423, 8.1, 83.7, 6.3, 11.7, 2.9, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 20000, '[0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.9, 0.0, 0.5, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000175', 'Trứng luộc', 'STAPLE', '1 quả vừa', 55.0, 155.0, 12.6, 1.1, 10.6, 0.8, 0.0, 85, 6.9, 0.6, 5.8, 0.4, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 6000, '[0.0, 0.8, 0.0, 0.0, 0.3, 0.0, 0.2, 0.0, 1.0, 0.0, 0.0, 0.9]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000176', 'Trứng đúc hành', 'STAPLE', '1 đĩa nhỏ (2 quả)', 120.0, 185.0, 11.2, 1.8, 14.8, 0.6, 0.3, 222, 13.4, 2.2, 17.8, 0.7, 0.4, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 18000, '[0.0, 0.9, 0.0, 0.0, 0.3, 0.0, 0.2, 0.8, 0.0, 0.1, 0.0, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000177', 'Đậu phụ sốt cà chua', 'STAPLE', '1 đĩa vừa', 200.0, 122.0, 7.8, 5.5, 7.6, 2.5, 1.2, 244, 15.6, 11.0, 15.2, 5.0, 2.4, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 22000, '[0.2, 0.7, 0.0, 0.0, 0.0, 0.0, 1.0, 0.6, 0.4, 0.0, 0.2, 0.8]'::jsonb, true, NULL, NULL),
-- Nhóm NOODLE: Phở, bún, miến, bánh đa (8 món: 0178 - 0185)
('10000000-0000-0000-0000-000000000178', 'Phở gà', 'NOODLE', '1 tô vừa', 480.0, 84.0, 5.6, 11.2, 1.8, 0.6, 0.5, 403, 26.9, 53.8, 8.6, 2.9, 2.4, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.1, 1.0, 0.1, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000179', 'Bún mọc', 'NOODLE', '1 tô vừa', 460.0, 88.0, 5.2, 11.8, 2.2, 0.6, 0.6, 405, 23.9, 54.3, 10.1, 2.8, 2.8, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.1, 0.1, 1.0, 0.1, 0.1, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000180', 'Bún thang', 'NOODLE', '1 tô vừa', 460.0, 86.0, 5.8, 11.0, 2.0, 0.6, 0.5, 396, 26.7, 50.6, 9.2, 2.8, 2.3, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.4, 0.7, 0.2, 0.1, 0.1, 1.0, 0.1, 0.1, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000181', 'Bún thịt nướng', 'NOODLE', '1 tô vừa', 400.0, 135.0, 7.2, 17.5, 4.0, 2.8, 1.4, 540, 28.8, 70.0, 16.0, 11.2, 5.6, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.3, 0.8, 0.0, 1.0, 0.0, 0.0, 0.3, 0.6, 0.2, 0.2, 0.4, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000182', 'Bún bò Nam Bộ', 'NOODLE', '1 tô vừa', 420.0, 122.0, 7.0, 16.2, 3.2, 2.8, 1.6, 512, 29.4, 68.0, 13.4, 11.8, 6.7, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 45000, '[0.3, 0.8, 1.0, 0.0, 0.0, 0.0, 0.3, 0.4, 0.3, 0.2, 0.4, 0.7]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000183', 'Miến gà', 'NOODLE', '1 tô vừa', 450.0, 74.0, 5.2, 10.2, 1.3, 0.5, 0.5, 333, 23.4, 45.9, 5.8, 2.2, 2.2, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 40000, '[1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.1, 0.1, 1.0, 0.1, 0.0, 0.8]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000184', 'Miến xào cua', 'NOODLE', '1 đĩa vừa', 320.0, 145.0, 7.5, 19.5, 4.0, 1.2, 1.2, 464, 24.0, 62.4, 12.8, 3.8, 3.8, NULL, NULL, 'SYSTEM', NULL, '["LUNCH", "DINNER"]'::jsonb, 65000, '[0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.7, 0.1, 0.2, 0.1, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000185', 'Bánh đa cua Hải Phòng', 'NOODLE', '1 tô đầy đủ', 480.0, 92.0, 5.6, 12.2, 2.3, 0.8, 1.0, 442, 26.9, 58.6, 11.0, 3.8, 4.8, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER"]'::jsonb, 45000, '[1.0, 0.0, 0.0, 0.4, 0.0, 0.9, 0.3, 0.2, 0.9, 0.2, 0.1, 0.7]'::jsonb, true, NULL, NULL),
-- Nhóm SNACK: Trái cây Việt Nam & tráng miệng (7 món + 0102 = 8 món: 0186 - 0192)
('10000000-0000-0000-0000-000000000186', 'Chuối tiêu chín', 'SNACK', '1 quả vừa (bỏ vỏ)', 100.0, 89.0, 1.1, 22.8, 0.3, 12.2, 2.6, 89, 1.1, 22.8, 0.3, 12.2, 2.6, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 5000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.7, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000187', 'Ổi tươi', 'SNACK', '1 quả vừa', 150.0, 68.0, 2.6, 14.3, 1.0, 8.9, 5.4, 102, 3.9, 21.4, 1.5, 13.3, 8.1, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 8000, '[0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.4, 0.2]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000188', 'Thanh long ruột trắng', 'SNACK', '1 đĩa (1/2 quả)', 180.0, 50.0, 1.1, 11.0, 0.4, 7.6, 3.0, 90, 2.0, 19.8, 0.7, 13.7, 5.4, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 10000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.5, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000189', 'Dưa hấu', 'SNACK', '1 đĩa (2 miếng vừa)', 200.0, 30.0, 0.6, 7.2, 0.2, 6.2, 0.4, 60, 1.2, 14.4, 0.4, 12.4, 0.8, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 10000, '[0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.6, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000190', 'Bưởi da xanh', 'SNACK', '3 múi vừa', 150.0, 38.0, 0.8, 9.6, 0.1, 6.8, 1.0, 57, 1.2, 14.4, 0.1, 10.2, 1.5, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 15000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.4, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000191', 'Xoài chín', 'SNACK', '1 đĩa (1/2 quả lớn)', 160.0, 60.0, 0.8, 15.0, 0.4, 13.7, 1.6, 96, 1.3, 24.0, 0.6, 21.9, 2.6, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 12000, '[0.0, 0.3, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.8, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000192', 'Chè đậu đen', 'SNACK', '1 ly vừa', 250.0, 95.0, 3.2, 19.5, 0.6, 11.5, 2.8, 238, 8.0, 48.8, 1.5, 28.8, 7.0, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 15000, '[0.7, 0.1, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.8, 0.2]'::jsonb, true, NULL, NULL),
-- Nhóm BEVERAGE: Đồ uống Việt Nam (8 món: 0193 - 0200)
('10000000-0000-0000-0000-000000000193', 'Sữa đậu nành ít đường', 'BEVERAGE', '1 ly vừa', 250.0, 46.0, 3.2, 4.2, 1.8, 3.0, 0.4, 115, 8.0, 10.5, 4.5, 7.5, 1.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 12000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.9, 0.0, 0.4, 0.6]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000194', 'Nước cam vắt tươi', 'BEVERAGE', '1 ly vừa', 250.0, 45.0, 0.7, 10.4, 0.2, 8.4, 0.2, 112, 1.8, 26.0, 0.5, 21.0, 0.5, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 20000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.6, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000195', 'Nước dừa tươi', 'BEVERAGE', '1 quả/1 ly lớn', 300.0, 19.0, 0.7, 3.7, 0.2, 2.6, 1.1, 57, 2.1, 11.1, 0.6, 7.8, 3.3, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 20000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.4, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000196', 'Trà đá không đường', 'BEVERAGE', '1 ly vừa', 250.0, 1.0, 0.0, 0.2, 0.0, 0.0, 0.0, 2, 0.0, 0.5, 0.0, 0.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "LUNCH", "DINNER", "SNACK"]'::jsonb, 5000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000197', 'Cà phê sữa đá', 'BEVERAGE', '1 ly vừa', 200.0, 72.0, 1.6, 12.2, 1.8, 11.5, 0.0, 144, 3.2, 24.4, 3.6, 23.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 25000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.1, 0.7, 0.0, 0.8, 0.1]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000198', 'Cà phê đen đá không đường', 'BEVERAGE', '1 ly vừa', 180.0, 2.0, 0.2, 0.3, 0.0, 0.0, 0.0, 4, 0.4, 0.5, 0.0, 0.0, 0.0, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 20000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.0, 0.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000199', 'Nước mía tươi', 'BEVERAGE', '1 ly vừa', 300.0, 58.0, 0.2, 14.5, 0.0, 13.8, 0.1, 174, 0.6, 43.5, 0.0, 41.4, 0.3, NULL, NULL, 'SYSTEM', NULL, '["SNACK"]'::jsonb, 12000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.8, 0.0, 0.9, 0.0]'::jsonb, true, NULL, NULL),
('10000000-0000-0000-0000-000000000200', 'Sinh tố bơ sữa đặc', 'BEVERAGE', '1 ly vừa', 280.0, 135.0, 2.2, 14.5, 8.2, 11.0, 2.8, 378, 6.2, 40.6, 23.0, 30.8, 7.8, NULL, NULL, 'SYSTEM', NULL, '["BREAKFAST", "SNACK"]'::jsonb, 30000, '[0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.7, 0.4, 0.6, 0.0, 0.7, 0.1]'::jsonb, true, NULL, NULL)
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

-- ============================================================
-- 6a. FOOD: food_ingredients — Liên kết món VietFood68 với nguyên liệu dị ứng nền
-- PEANUT = ...0001, DAIRY = ...0002, EGG = ...0003, TREE_NUTS = ...0004,
-- SOY = ...0005, GLUTEN = ...0006, FISH = ...0007, CRUSTACEANS = ...0008,
-- SESAME = ...0009, MOLLUSCS = ...0010
-- ============================================================
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
ON CONFLICT (food_id, ingredient_id) DO NOTHING;

-- ============================================================
-- 6b. FOOD: food_ingredients — Liên kết 100 món Việt Nam mở rộng với nguyên liệu dị ứng nền
-- ============================================================
INSERT INTO food_ingredients (food_id, ingredient_id, quantity_grams) VALUES
-- Canh chua cá lóc chứa Cá
('10000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000007', 80.0),
-- Sữa chua không đường chứa Sữa
('10000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000002', 100.0),
-- Thịt kho trứng cút chứa Trứng
('10000000-0000-0000-0000-000000000106', '00000000-0000-0000-0000-000000000003', 60.0),
-- Chả trứng hấp chứa Trứng
('10000000-0000-0000-0000-000000000122', '00000000-0000-0000-0000-000000000003', 45.0),
-- Đậu hũ nhồi thịt sốt cà chua chứa Đậu nành
('10000000-0000-0000-0000-000000000123', '00000000-0000-0000-0000-000000000005', 90.0),
-- Trứng chiên thịt bằm chứa Trứng
('10000000-0000-0000-0000-000000000124', '00000000-0000-0000-0000-000000000003', 80.0),
-- Cá kho tộ chứa Cá
('10000000-0000-0000-0000-000000000125', '00000000-0000-0000-0000-000000000007', 120.0),
-- Cá thu sốt cà chua chứa Cá
('10000000-0000-0000-0000-000000000126', '00000000-0000-0000-0000-000000000007', 120.0),
-- Cá diêu hồng chiên xù chứa Cá, Gluten
('10000000-0000-0000-0000-000000000127', '00000000-0000-0000-0000-000000000007', 140.0),
('10000000-0000-0000-0000-000000000127', '00000000-0000-0000-0000-000000000006', 15.0),
-- Cá nục kho cà chua chứa Cá
('10000000-0000-0000-0000-000000000128', '00000000-0000-0000-0000-000000000007', 120.0),
-- Cá hồi áp chảo chứa Cá
('10000000-0000-0000-0000-000000000129', '00000000-0000-0000-0000-000000000007', 140.0),
-- Cá trắm kho riềng chứa Cá
('10000000-0000-0000-0000-000000000130', '00000000-0000-0000-0000-000000000007', 120.0),
-- Cá rô phi rán giòn chứa Cá
('10000000-0000-0000-0000-000000000131', '00000000-0000-0000-0000-000000000007', 145.0),
-- Tôm rim mặn ngọt chứa Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000132', '00000000-0000-0000-0000-000000000008', 110.0),
-- Tôm rang thịt ba chỉ chứa Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000133', '00000000-0000-0000-0000-000000000008', 70.0),
-- Mực xào cần tỏi chứa Nhuyễn thể (Mực/Nghêu/Ngao/Hào)
('10000000-0000-0000-0000-000000000134', '00000000-0000-0000-0000-000000000010', 110.0),
-- Mực nhồi thịt sốt cà chứa Nhuyễn thể (Mực/Nghêu/Ngao/Hào)
('10000000-0000-0000-0000-000000000135', '00000000-0000-0000-0000-000000000010', 90.0),
-- Chả mực chiên chứa Nhuyễn thể (Mực/Nghêu/Ngao/Hào)
('10000000-0000-0000-0000-000000000136', '00000000-0000-0000-0000-000000000010', 110.0),
-- Nghêu hấp sả chứa Nhuyễn thể (Mực/Nghêu/Ngao/Hào)
('10000000-0000-0000-0000-000000000137', '00000000-0000-0000-0000-000000000010', 120.0),
-- Lươn xào sả ớt chứa Cá
('10000000-0000-0000-0000-000000000138', '00000000-0000-0000-0000-000000000007', 110.0),
-- Canh cua rau đay mồng tơi chứa Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000139', '00000000-0000-0000-0000-000000000008', 40.0),
-- Canh bí đao nấu tôm chứa Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000141', '00000000-0000-0000-0000-000000000008', 35.0),
-- Canh bầu nấu tôm chứa Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000143', '00000000-0000-0000-0000-000000000008', 35.0),
-- Canh ngao nấu chua chứa Nhuyễn thể (Mực/Nghêu/Ngao/Hào)
('10000000-0000-0000-0000-000000000145', '00000000-0000-0000-0000-000000000010', 50.0),
-- Canh rong biển đậu phụ chứa Đậu nành, Mè
('10000000-0000-0000-0000-000000000148', '00000000-0000-0000-0000-000000000005', 50.0),
('10000000-0000-0000-0000-000000000148', '00000000-0000-0000-0000-000000000009', 2.0),
-- Canh cà chua trứng chứa Trứng
('10000000-0000-0000-0000-000000000149', '00000000-0000-0000-0000-000000000003', 45.0),
-- Cải thìa xào dầu hào chứa Nhuyễn thể (Mực/Nghêu/Ngao/Hào), Đậu nành
('10000000-0000-0000-0000-000000000155', '00000000-0000-0000-0000-000000000010', 5.0),
('10000000-0000-0000-0000-000000000155', '00000000-0000-0000-0000-000000000005', 5.0),
-- Đậu bắp luộc chấm chao chứa Đậu nành
('10000000-0000-0000-0000-000000000163', '00000000-0000-0000-0000-000000000005', 15.0),
-- Nộm đu đủ bò khô chứa Đậu phộng
('10000000-0000-0000-0000-000000000164', '00000000-0000-0000-0000-000000000001', 15.0),
-- Gỏi ngó sen tôm thịt chứa Giáp xác (Tôm/Cua), Đậu phộng
('10000000-0000-0000-0000-000000000165', '00000000-0000-0000-0000-000000000008', 40.0),
('10000000-0000-0000-0000-000000000165', '00000000-0000-0000-0000-000000000001', 15.0),
-- Cà tím om đậu phụ chứa Đậu nành
('10000000-0000-0000-0000-000000000166', '00000000-0000-0000-0000-000000000005', 70.0),
-- Cơm rang dưa bò chứa Trứng
('10000000-0000-0000-0000-000000000168', '00000000-0000-0000-0000-000000000003', 30.0),
-- Trứng luộc chứa Trứng
('10000000-0000-0000-0000-000000000175', '00000000-0000-0000-0000-000000000003', 55.0),
-- Trứng đúc hành chứa Trứng
('10000000-0000-0000-0000-000000000176', '00000000-0000-0000-0000-000000000003', 100.0),
-- Đậu phụ sốt cà chua chứa Đậu nành
('10000000-0000-0000-0000-000000000177', '00000000-0000-0000-0000-000000000005', 120.0),
-- Bún thang chứa Trứng, Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000180', '00000000-0000-0000-0000-000000000003', 30.0),
('10000000-0000-0000-0000-000000000180', '00000000-0000-0000-0000-000000000008', 10.0),
-- Bún thịt nướng chứa Đậu phộng
('10000000-0000-0000-0000-000000000181', '00000000-0000-0000-0000-000000000001', 15.0),
-- Bún bò Nam Bộ chứa Đậu phộng
('10000000-0000-0000-0000-000000000182', '00000000-0000-0000-0000-000000000001', 15.0),
-- Miến xào cua chứa Giáp xác (Tôm/Cua), Trứng
('10000000-0000-0000-0000-000000000184', '00000000-0000-0000-0000-000000000008', 65.0),
('10000000-0000-0000-0000-000000000184', '00000000-0000-0000-0000-000000000003', 25.0),
-- Bánh đa cua Hải Phòng chứa Giáp xác (Tôm/Cua)
('10000000-0000-0000-0000-000000000185', '00000000-0000-0000-0000-000000000008', 50.0),
-- Sữa đậu nành ít đường chứa Đậu nành
('10000000-0000-0000-0000-000000000193', '00000000-0000-0000-0000-000000000005', 250.0),
-- Cà phê sữa đá chứa Sữa
('10000000-0000-0000-0000-000000000197', '00000000-0000-0000-0000-000000000002', 40.0),
-- Sinh tố bơ sữa đặc chứa Sữa
('10000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000002', 45.0)
ON CONFLICT (food_id, ingredient_id) DO UPDATE SET
    quantity_grams = EXCLUDED.quantity_grams;
