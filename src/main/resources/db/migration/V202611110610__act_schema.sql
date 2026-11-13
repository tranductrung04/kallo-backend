-- ============================================================
-- Activity module (act): weight, activity catalogue, activity logs,
-- health sync logs.
-- ============================================================

-- ============================================================
-- weight_logs
-- ============================================================
CREATE TABLE weight_logs (
                             id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                             client_uuid UUID          NOT NULL,
                             weight_kg   NUMERIC(5, 1) NOT NULL,
                             logged_at   TIMESTAMPTZ   NOT NULL,
                             note        VARCHAR(255),
                             created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                             updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                             deleted_at  TIMESTAMPTZ,
                             CONSTRAINT uq_weight_logs_client UNIQUE (user_id, client_uuid)
);

CREATE INDEX idx_weight_logs_user_logged
    ON weight_logs (user_id, logged_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_weight_logs_user_updated
    ON weight_logs (user_id, updated_at);

-- ============================================================
-- activity_types (catalogue)
-- MET source: SRS Appendix A.6 for brisk walking, 8 km/h running,
-- cycling, swimming, yoga, and badminton; remaining entries mapped to
-- Ainsworth et al., Compendium of Physical Activities (2011 update).
-- ============================================================
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
    ('SHOVELING_SNOW', 'Xúc tuyết', 'OTHER', 6.0, FALSE, 89);

-- ============================================================
-- activity_logs
-- ============================================================
CREATE TABLE activity_logs (
                               id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                               client_uuid     UUID NOT NULL,
                               activity_type   VARCHAR(40) NOT NULL REFERENCES activity_types (code),
                               intensity       VARCHAR(10) NOT NULL DEFAULT 'MODERATE',
                               met             NUMERIC(4, 1) NOT NULL,
                               weight_kg_used  NUMERIC(5, 1) NOT NULL,
                               duration_min    INTEGER NOT NULL CHECK (duration_min BETWEEN 1 AND 1440),
                               distance_km     NUMERIC(6, 2) CHECK (distance_km >= 0),
                               calories_burned NUMERIC(7, 1) NOT NULL CHECK (calories_burned >= 0),
                               source          VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
                               external_id     VARCHAR(100),
                               started_at      TIMESTAMPTZ NOT NULL,
                               ended_at        TIMESTAMPTZ NOT NULL,
                               superseded_by   UUID REFERENCES activity_logs (id) ON DELETE SET NULL,
                               note            VARCHAR(255),
                               route_polyline  TEXT,
                               created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                               deleted_at      TIMESTAMPTZ,
                               CONSTRAINT chk_activity_route_polyline_size
                                   CHECK (route_polyline IS NULL OR octet_length(route_polyline) <= 102400),
                               CONSTRAINT uq_activity_logs_client UNIQUE (user_id, client_uuid)
);

CREATE UNIQUE INDEX uq_activity_logs_external
    ON activity_logs (user_id, source, external_id) WHERE external_id IS NOT NULL;

CREATE INDEX idx_activity_logs_user_started
    ON activity_logs (user_id, started_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_activity_logs_user_updated
    ON activity_logs (user_id, updated_at);

-- ============================================================
-- health_sync_logs
-- ============================================================
CREATE TABLE health_sync_logs (
                                  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                  provider    VARCHAR(20)    NOT NULL,
                                  data_type   VARCHAR(20)    NOT NULL,
                                  external_id VARCHAR(100)   NOT NULL,
                                  value       NUMERIC(12, 2) NOT NULL,
                                  value_min   NUMERIC(12, 2),
                                  value_max   NUMERIC(12, 2),
                                  data_origin VARCHAR(100),
                                  start_time  TIMESTAMPTZ    NOT NULL,
                                  end_time    TIMESTAMPTZ    NOT NULL,
                                  synced_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  last_synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  CONSTRAINT chk_health_sync_range CHECK (end_time >= start_time),
                                  CONSTRAINT uq_health_sync UNIQUE (user_id, provider, data_type, external_id)
);

CREATE INDEX idx_health_sync_user_time
    ON health_sync_logs (user_id, data_type, start_time);
-- Keep the initial last_synced_at value aligned with synced_at.
UPDATE health_sync_logs
SET last_synced_at = synced_at
WHERE last_synced_at IS DISTINCT FROM synced_at;
