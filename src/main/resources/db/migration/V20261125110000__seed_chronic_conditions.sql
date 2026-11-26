-- V20261125110000__seed_chronic_conditions.sql
-- Seed danh mục bệnh mạn tính chuẩn cho PROFILE & MEAL_PLAN

INSERT INTO chronic_conditions (id, code, name, description, is_active) VALUES
('20000000-0000-0000-0000-000000000001', 'DIABETES_T2', 'Tiểu đường tuýp 2', 'Đái tháo đường tuýp 2 - kiểm soát đường tinh luyện và chỉ số đường huyết', true),
('20000000-0000-0000-0000-000000000002', 'DIABETES_T1', 'Tiểu đường tuýp 1', 'Đái tháo đường phụ thuộc insulin - kiểm soát lượng đường hấp thu', true),
('20000000-0000-0000-0000-000000000003', 'HYPERTENSION', 'Tăng huyết áp', 'Huyết áp cao - khuyến nghị hạn chế natri và chất béo bão hòa', true),
('20000000-0000-0000-0000-000000000004', 'GOUT', 'Bệnh Gút (Gout)', 'Tăng axit uric máu - khuyến nghị hạn chế thực phẩm giàu purine', true),
('20000000-0000-0000-0000-000000000005', 'CHRONIC_KIDNEY_DISEASE', 'Bệnh thận mạn tính', 'Hỗ trợ theo dõi đạm và hạn chế thực phẩm nhiều kali/natri/phốt pho', true)
ON CONFLICT (code) DO NOTHING;
