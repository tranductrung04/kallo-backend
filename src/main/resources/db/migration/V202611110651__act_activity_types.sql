-- MET source: SRS Appendix A.6 for brisk walking, 8 km/h running,
-- cycling, swimming, yoga, and badminton; remaining entries mapped to
-- Ainsworth et al., Compendium of Physical Activities (2011 update).
CREATE TABLE activity_types (
    code         VARCHAR(40) PRIMARY KEY,
    name_vi      VARCHAR(100) NOT NULL,
    category     VARCHAR(20) NOT NULL,
    met_light    NUMERIC(4, 1),
    met_moderate NUMERIC(4, 1) NOT NULL CHECK (met_moderate > 0 AND met_moderate <= 25),
    met_vigorous NUMERIC(4, 1),
    has_distance BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE
);

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
    ('BASKETBALL', 'Bóng rổ', 'SPORT', 6.5, FALSE, 12);
