# Kế hoạch triển khai Module ACT — Kallo Backend

Nov 11, 2026

Module ACT (vận động và chỉ số cơ thể, SRS mục 4.5) gồm 7 phase (0–6), khoảng 12 ngày công cho một người, hoặc 11 ngày nếu hoãn Phase 5 (mức C). Xong Phase 2 mobile ghi được cân nặng và tập luyện thủ công có tính calo; xong Phase 3 đồng bộ được bước chân và nhịp tim từ Health Connect / HealthKit; xong Phase 4 có số liệu tổng hợp theo ngày không trùng lặp để dashboard và báo cáo dùng.

> **Trạng thái:** bản kế hoạch, chưa có dòng mã ACT nào. Mọi ô `[ ]` đều chưa làm. Các điểm ghi "Đề xuất" là lựa chọn của người viết kế hoạch, cần nhóm xác nhận ở Phase 0.

## Phạm vi

Module ACT phủ FR-ACT-01…08 của SRS 1.2. Bảng dưới gán từng yêu cầu vào phase.

| FR | Nội dung | Ưu tiên | Phase |
| --- | --- | --- | --- |
| FR-ACT-04 | Ghi, xem, sửa, xoá cân nặng theo ngày (WeightLog) | M | 1 |
| FR-ACT-01 | Ghi tập luyện thủ công, tính calo tiêu hao từ MET | M | 2 |
| FR-ACT-02 | Đồng bộ bước chân và nhịp tim, lưu vết đồng bộ (HealthSyncLog) | M | 3 |
| FR-ACT-03 | Xin và quản lý quyền sức khoẻ; app vẫn chạy khi bị từ chối | M | 3 (phía backend), phần còn lại ở mobile |
| FR-ACT-05 | Calo tiêu hao từ bước chân theo cân nặng, chiều cao | S | 4 |
| FR-ACT-08 | Khử trùng hoạt động đồng bộ và thủ công cùng khung giờ | S | 4 |
| FR-ACT-06 | Đếm bước bằng cảm biến thiết bị khi không có nguồn đồng bộ | C | 5 (backend chỉ nhận dữ liệu) |
| FR-ACT-07 | Lộ trình, quãng đường chạy/đi bộ bằng expo-location | C | 5 (backend chỉ nhận dữ liệu) |

**Ngoài kế hoạch này:** nhật ký nước uống (FR-NUT-11, thuộc NUTRI dù Phụ lục B của SRS xếp cạnh ACT; có thể dùng lại mẫu `client_uuid` ở Phase 1); ghi `DailySummary` và dự đoán xu hướng cân nặng (REPORT, FR-REP-05); đề xuất tính lại TDEE khi có cân nặng mới (PROFILE, FR-PRO-08); endpoint `/sync/push`, `/sync/pull` (SYS, FR-SYS-05/06). ACT chỉ cung cấp dữ liệu và sự kiện để các module đó dùng (xem Phase 4 và 6).

## Hiện trạng repo

Mình đã đọc `kallo-backend` (commit `03baf78`). Repo mới có AUTH; những điểm dưới đây quyết định cách làm ACT.

| Hạng mục | Hiện trạng | Hệ quả cho ACT |
| --- | --- | --- |
| Nền tảng | Java 21, Spring Boot 4.1.1, Gradle; có JPA, Flyway, MapStruct, springdoc 3.1.0, Redis, Elasticsearch | Không cần thêm dependency |
| Migration | Chỉ có `V1__init_schema.sql` (4 bảng AUTH, gồm cả `email_verification_tokens`) | Đánh số tuần tự (`V2`, `V3`…) sẽ trùng với PROFILE, NUTRI do người khác làm; ACT dùng số phiên bản theo thời gian (D11) |
| Cây gói | `controller/auth`, `dto/request/auth`, `dto/response/auth`, `model/auth`, `repository/auth`, `service/auth` + `service/auth/impl`; `mapper/` và `config/` phẳng | ACT dùng `.../act` cùng quy ước |
| Bảo mật | `SecurityConfig`: mọi đường dẫn ngoài `/api/auth/**` yêu cầu JWT có claim `email_verified = true` (trừ `GET /api/users/me`); `CurrentUser.id(authentication)` lấy user id | Endpoint ACT được chặn 403 `EMAIL_NOT_VERIFIED` tự động; user id chỉ lấy từ token |
| Lỗi | `ApiResponse`, `ErrorCode` (enum dùng chung), `BusinessException`, `GlobalExceptionHandler` | Thêm mã lỗi vào enum dùng chung (dễ xung đột khi gộp nhánh); handler **chỉ** map `DataIntegrityViolationException` cho `uq_users_email`, còn lại trả 500 |
| Xoá tài khoản | `AuthFlowIntegrationTest` truy vấn `information_schema` và đòi mọi khoá ngoại tới `users` có `ON DELETE CASCADE` | Bảng ACT thiếu `CASCADE` sẽ làm test này đỏ; đó cũng là cách FR-AUTH-07 xoá dữ liệu ACT |
| Mẫu mở rộng | `ProfileStatusProvider` + `@ConditionalOnMissingBean` làm cổng cho module chưa có; service nhận `Clock` | ACT dùng lại cách này cho dữ liệu hồ sơ (D1) |
| Chưa có | PROFILE (`user_health_profiles`), `daily_summaries`, `RateLimiter`, `@EnableScheduling`, `PageResponse`, `/sync/*` | Phải có cổng/sự kiện thay thế (D1, D2); rate limit và job dọn dẹp để Phase 6 |
| Cấu hình | `application.yml` đọc biến môi trường kèm mặc định; `application-test.yml` không có datasource (test dùng `DynamicPropertySource`); CI chạy `./gradlew build` | Thuộc tính `app.act.*` phải có mặc định để không phải thêm biến vào CI |
| Tài liệu | `docs/api-specs/auth.yaml`, `docs/tests/auth-test-cases.xlsx`, `docs/IMPLEMENT_MODULE_AUTH.md` | Phase 6 thêm `act.yaml` và bộ test-case tương ứng |

## Quyết định cần chốt trước khi code

Mười một điểm dưới đây ảnh hưởng thiết kế. Cột "Đề xuất" là lựa chọn mình dùng để ước lượng; nếu nhóm chọn khác thì phase liên quan thay đổi.

| # | Vấn đề | Đề xuất | Lý do |
| --- | --- | --- | --- |
| D1 | Lấy cân nặng và chiều cao để tính calo khi PROFILE chưa có | Cổng `ProfileBodyMetrics` (mặc định trả rỗng, PROFILE cài đặt sau). Cân nặng theo thứ tự: `weight_logs` gần nhất không muộn hơn thời điểm hoạt động → hồ sơ PROFILE → lỗi 422 `WEIGHT_REQUIRED`. Không tự bịa cân nặng mặc định | Calo sai âm thầm tệ hơn một thông báo rõ; onboarding bắt buộc có cân nặng nên lỗi hiếm |
| D2 | Ai sở hữu bảng `daily_summaries` | ACT **không** tạo bảng này. ACT tính tổng hợp theo ngày từ bảng gốc (`ActivityDailyAggregator`) và phát `ActivityDataChangedEvent(userId, localDate)` sau commit; module REPORT/NUTRI nghe sự kiện và ghi `steps`, `calories_burned`, `weight_kg` | Tránh hai nơi cùng ghi một bảng; bảng gốc luôn là nguồn đúng, tính lại được |
| D3 | Đơn vị đồng bộ bước chân và nhịp tim | Mobile gửi **bucket theo giờ địa phương** lấy từ API tổng hợp của Health Connect/HealthKit (đã khử trùng giữa các nguồn ghi). `external_id` là khoá xác định (ví dụ `STEPS:2026111108`), backend upsert theo `(user_id, provider, data_type, external_id)` | Đồng bộ lặp không cộng dồn (AC2); không tự cộng bước của đồng hồ và điện thoại |
| D4 | Có đồng bộ phiên tập (exercise session) vào `activity_logs` không | Có, mức S, làm trong Phase 3 nếu còn thời gian | FR-ACT-08 nói "hoạt động đồng bộ", tức đã có hoạt động đồng bộ ngoài bước chân; nếu bỏ, FR-ACT-08 chỉ còn đối chiếu bước chân với hoạt động đi bộ |
| D5 | Khử trùng hoạt động trùng giờ | Hai hoạt động của một user được coi là trùng khi cùng nhóm (đi bộ/chạy, đạp xe…) và giao nhau ≥ 80% thời lượng của hoạt động ngắn hơn. Giữ bản ưu tiên cao hơn theo nguồn: thiết bị (Health Connect, HealthKit) > cảm biến/GPS > thủ công. Bản thua ghi `superseded_by`, bị loại khỏi tổng, **không xoá**, khôi phục được khi bản thắng bị xoá | Dữ liệu từ thiết bị đo chính xác hơn nhập tay; không xoá để không mất dữ liệu người dùng nhập |
| D6 | Calo từ bước chân và tránh tính đôi với hoạt động | `calo = 3,5 × cân_nặng × giờ_đi_bộ`, trong đó `giờ_đi_bộ = (bước × 0,415 × chiều_cao_m / 1000) / 5 km/h`; thiếu chiều cao thì dùng nhịp 100 bước/phút. Bước thuộc bucket giao với hoạt động đi bộ/chạy đã ghi thì **không** tính calo bước (vẫn tính vào số bước hiển thị) | Dùng đúng MET đi bộ 3,5 của SRS Phụ lục A.6; hệ số 0,415 là xấp xỉ phổ biến cho độ dài sải; cần đối chiếu trước khi chốt |
| D7 | Xoá và xung đột đồng bộ | Xoá mềm (`deleted_at`), bản xoá luôn idempotent (204). Sửa theo "bản mới nhất thắng" so `updated_at`: nếu client gửi `clientUpdatedAt` cũ hơn bản trên server thì bỏ qua và trả bản server. Bản xoá mềm giữ tối thiểu 90 ngày để `/sync/pull` còn báo xoá | SRS 6.3 và FR-SYS-06; client ngoại tuyến cần biết bản nào đã bị xoá |
| D8 | Một hay nhiều lần cân mỗi ngày | Cho nhiều bản ghi mỗi ngày; "cân nặng của ngày" là bản có `logged_at` muộn nhất | Người dùng có thể cân sáng và tối; không buộc phải sửa bản cũ |
| D9 | Giới hạn thời gian của dữ liệu gửi lên | Tập luyện nhập tay: không quá 365 ngày về trước; đồng bộ: không quá 30 ngày về trước; mọi loại: không quá 5 phút về sau (cấu hình được). Cân nặng: `20 ≤ kg ≤ 300` (SRS AC2 của PROFILE) | Chặn dữ liệu rác và chặn làm sai thống kê; ngưỡng cân nặng lấy từ SRS |
| D10 | MET theo cường độ | Bảng `activity_types` có ba cột `met_light`, `met_moderate`, `met_vigorous`; cột light/vigorous để `NULL` nếu Compendium không có biến thể tương ứng và API từ chối cường độ không hỗ trợ. Không dùng hệ số nhân tự chế | Compendium gán MET theo từng hoạt động cụ thể; nhân hệ số sẽ cho số không có nguồn |
| D11 | Cách đặt số phiên bản migration Flyway | Dùng **thời gian tạo tệp** làm số phiên bản: `V<yyyyMMddHHmm>__<module>_<mô_tả>.sql` (giờ UTC), ví dụ `V202611121030__act_weight_logs.sql`. Không cấp số tuần tự trước cho phase nào. Bật `spring.flyway.out-of-order: true` ở profile `dev` (chi tiết ở mục "Mô hình dữ liệu") | Nhiều người làm nhiều module song song trên các nhánh riêng; số tuần tự (`V2`, `V3`…) chắc chắn trùng và phải đánh lại lúc gộp, còn số theo thời gian gần như không trùng. Quy ước này áp cho cả repo nên cần cả nhóm đồng ý |

**Lưu ý về calo gộp và calo ròng.** Công thức SRS (`MET × kg × giờ`) cho calo **gộp**, đã bao gồm phần chuyển hoá nghỉ. Nếu mục tiêu calo của người dùng là TDEE (đã có hệ số vận động) rồi lại cộng thêm calo tập luyện vào ngân sách thì bị tính đôi. ACT lưu số gộp theo SRS, kèm `met` và `weight_kg_used` nên REPORT tính được số ròng `(MET − 1) × kg × giờ` khi cần. Nhóm REPORT cần quyết định cách hiển thị trước khi làm FR-REP-02.

## Lộ trình

| Phase | Nội dung | Ước lượng | Tích luỹ | Cột mốc |
| --- | --- | --- | --- | --- |
| 0 | Chốt quyết định, dựng khung | 1 ngày | 1 | |
| 1 | Nhật ký cân nặng (FR-ACT-04) | 1,5 ngày | 2,5 | |
| 2 | Danh mục MET, tập luyện thủ công (FR-ACT-01) | 2 ngày | 4,5 | **M1**: mobile ghi được cân nặng và tập luyện |
| 3 | Đồng bộ Health Connect / HealthKit (FR-ACT-02, 03) | 2,5 ngày | 7 | **M2**: đồng bộ bước chân, nhịp tim chạy |
| 4 | Tổng hợp theo ngày, calo bước chân, khử trùng (FR-ACT-05, 08) | 2,5 ngày | 9,5 | **M3**: số liệu ngày đúng, không trùng |
| 5 | Cảm biến, lộ trình (FR-ACT-06, 07, mức C, tuỳ chọn) | 1 ngày | 10,5 | |
| 6 | Hoàn thiện, tài liệu, nghiệm thu | 1,5 ngày | 12 | **M4**: nghiệm thu |

Nếu bắt đầu thứ Năm 12/11/2026 và làm liên tục: M1 khoảng 18/11, M2 khoảng 20/11, M3 khoảng 25/11, M4 khoảng 27/11 (26/11 nếu bỏ Phase 5). Ước lượng chỉ tính phần backend và giả định một người làm liên tục. Phase 1 và Phase 3 có thể chạy song song nếu có hai người, vì chúng độc lập sau Phase 0; Phase 2 cần Phase 1 (lấy cân nặng).

## Mô hình dữ liệu

Bốn bảng mới, bám mục 6.1 của SRS và thêm các cột cần cho đồng bộ, xoá mềm và khử trùng. Tên tệp migration dưới đây dùng chỗ giữ `V<timestamp>` thay cho số phiên bản; cách đặt số ở mục "Quy ước đặt số phiên bản migration" ngay sau.

- **Khớp kiểu với entity** (vì `ddl-auto: validate`): `NUMERIC` ↔ `BigDecimal` (khai `precision`/`scale` trong `@Column`), `TIMESTAMPTZ` ↔ `Instant`, `UUID`, `VARCHAR`, `INTEGER` ↔ `int`/`Integer`. Lệch kiểu làm ứng dụng không khởi động được.
- **Khoá ngoại tới `users`** luôn có `ON DELETE CASCADE` (xem "Hiện trạng repo").
- **Không sửa migration đã chạy**; mỗi phase thêm một tệp mới.

### Quy ước đặt số phiên bản migration (D11)

Để migration của ACT không đụng migration của module khác do người khác làm, số phiên bản là thời gian tạo tệp thay vì số tuần tự.

- **Định dạng:** `V<yyyyMMddHHmm>__<module>_<mô_tả>.sql`, giờ UTC, ví dụ `V202611121030__act_weight_logs.sql`. Tiền tố `act_` giúp nhìn tên tệp biết module. Số này luôn lớn hơn `V1__init_schema.sql` nên thứ tự với AUTH không đổi. Lấy giờ bằng `date -u +%Y%m%d%H%M` **tại lúc tạo tệp**, không cấp trước; không đổi tên tệp sau khi đã gộp vào nhánh chính.
- **Thứ tự trong cùng module:** giữ đúng thứ tự phụ thuộc, cách nhau ít nhất một phút. Cụ thể `activity_types` phải có số nhỏ hơn `activity_logs` vì `activity_logs` có khoá ngoại tới nó. Tạo hai tệp theo đúng thứ tự đó là đủ.
- **Phụ thuộc chéo module:** bảng ACT chỉ tham chiếu `users` (đã có trong `V1`), không tham chiếu bảng của PROFILE hay NUTRI, nên thứ tự giữa ACT và các module kia không quan trọng. Nếu sau này có khoá ngoại sang bảng của module khác thì tệp ACT phải có số lớn hơn tệp tạo bảng đó, và cần báo trước cho người làm module kia.
- **Chạy lệch thứ tự (`out-of-order`):** nếu một nhánh có số nhỏ hơn được gộp **sau** khi một số lớn hơn đã chạy trên một CSDL dùng chung, Flyway mặc định báo lỗi `Detected resolved migration not applied to database` và ứng dụng không khởi động. Cách xử lý: thêm `spring.flyway.out-of-order: true` vào `application-dev.yml` (hiện `application.yml` chưa khai tuỳ chọn này). Với `prod`, giữ mặc định `false` và triển khai bản đã gộp lên CSDL sạch hoặc theo đúng thứ tự; nhóm quyết định lại nếu cần bật cho `prod`. Cách này an toàn vì các module tạo bảng độc lập nhau.
- **Áp dụng cho cả repo:** `V1` giữ nguyên; các module khác nên theo cùng quy ước. Ghi quy ước vào README ở Phase 6 để người làm PROFILE, NUTRI không đánh số tuần tự.

`V<timestamp>__act_weight_logs.sql` (Phase 1):

```sql
CREATE TABLE weight_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid UUID          NOT NULL,
    weight_kg   NUMERIC(5, 1) NOT NULL CHECK (weight_kg BETWEEN 20 AND 300),
    logged_at   TIMESTAMPTZ   NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_weight_logs_client UNIQUE (user_id, client_uuid)
);
CREATE INDEX idx_weight_logs_user_logged
    ON weight_logs (user_id, logged_at DESC) WHERE deleted_at IS NULL;
```

`V<timestamp>__act_activity_types.sql` (Phase 2, tạo **trước** `activity_logs`): danh mục MET, dữ liệu tham chiếu dùng chung cho mọi người dùng.

```sql
CREATE TABLE activity_types (
    code         VARCHAR(40)   PRIMARY KEY,
    name_vi      VARCHAR(100)  NOT NULL,
    category     VARCHAR(20)   NOT NULL,   -- WALKING | RUNNING | CYCLING | SWIMMING | GYM | MIND_BODY | SPORT
    met_light    NUMERIC(4, 1),            -- NULL = loại này không có mức nhẹ
    met_moderate NUMERIC(4, 1) NOT NULL CHECK (met_moderate > 0 AND met_moderate <= 25),
    met_vigorous NUMERIC(4, 1),
    has_distance BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order   INTEGER       NOT NULL DEFAULT 0,
    active       BOOLEAN       NOT NULL DEFAULT TRUE
);
```

Dữ liệu gieo ban đầu (cột `met_moderate`). Các giá trị có nguồn SRS Phụ lục A.6 là đi bộ nhanh 3,5; chạy 8 km/h 8,3; đạp xe 6–8; bơi 6–8; yoga 2,5–3; cầu lông 5,5. **Các dòng còn lại do mình đề xuất từ trí nhớ về Compendium of Physical Activities, người phụ trách phải đối chiếu từng dòng trước khi gộp**:

| code | Tên | category | MET |
| --- | --- | --- | --- |
| `WALK_BRISK` | Đi bộ nhanh | WALKING | 3,5 |
| `RUN_8KMH` | Chạy bộ 8 km/h | RUNNING | 8,3 |
| `CYCLING_MODERATE` | Đạp xe vừa | CYCLING | 6,8 |
| `SWIM_GENERAL` | Bơi | SWIMMING | 6,0 |
| `YOGA_HATHA` | Yoga | MIND_BODY | 2,5 |
| `BADMINTON` | Cầu lông | SPORT | 5,5 |
| `WEIGHT_TRAINING` | Tập tạ | GYM | 3,5 |
| `SOCCER` | Bóng đá | SPORT | 7,0 |
| `HIKING` | Leo núi, đi bộ đường dài | WALKING | 6,0 |
| `JUMP_ROPE` | Nhảy dây | GYM | 11,8 |
| `TABLE_TENNIS` | Bóng bàn | SPORT | 4,0 |
| `BASKETBALL` | Bóng rổ | SPORT | 6,5 |

`V<timestamp>__act_activity_logs.sql` (Phase 2, số lớn hơn `act_activity_types`):

```sql
CREATE TABLE activity_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_uuid     UUID          NOT NULL,
    activity_type   VARCHAR(40)   NOT NULL REFERENCES activity_types (code),
    intensity       VARCHAR(10)   NOT NULL DEFAULT 'MODERATE',   -- LIGHT | MODERATE | VIGOROUS
    met             NUMERIC(4, 1) NOT NULL,                       -- MET đã dùng (snapshot)
    weight_kg_used  NUMERIC(5, 1) NOT NULL,                       -- cân nặng đã dùng (snapshot)
    duration_min    INTEGER       NOT NULL CHECK (duration_min BETWEEN 1 AND 1440),
    distance_km     NUMERIC(6, 2) CHECK (distance_km >= 0),
    calories_burned NUMERIC(7, 1) NOT NULL CHECK (calories_burned >= 0),
    source          VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',      -- MANUAL | HEALTH_CONNECT | HEALTHKIT | SENSOR | GPS
    external_id     VARCHAR(100),                                 -- id phiên tập phía thiết bị (nguồn đồng bộ)
    started_at      TIMESTAMPTZ   NOT NULL,
    ended_at        TIMESTAMPTZ   NOT NULL,                       -- started_at + duration_min, để truy vấn giao nhau
    superseded_by   UUID REFERENCES activity_logs (id) ON DELETE SET NULL,
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_activity_logs_client UNIQUE (user_id, client_uuid)
);
CREATE UNIQUE INDEX uq_activity_logs_external
    ON activity_logs (user_id, source, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_activity_logs_user_started
    ON activity_logs (user_id, started_at DESC) WHERE deleted_at IS NULL;
```

`V<timestamp>__act_health_sync_logs.sql` (Phase 3):

```sql
CREATE TABLE health_sync_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(20)   NOT NULL,       -- HEALTH_CONNECT | HEALTHKIT (| DEVICE_SENSOR ở Phase 5)
    data_type   VARCHAR(20)   NOT NULL,       -- STEPS | HEART_RATE
    external_id VARCHAR(100)  NOT NULL,       -- khoá bucket xác định, ví dụ STEPS:2026111108
    value       NUMERIC(12, 2) NOT NULL,      -- số bước, hoặc nhịp tim trung bình (bpm)
    value_min   NUMERIC(12, 2),               -- chỉ dùng cho nhịp tim
    value_max   NUMERIC(12, 2),
    data_origin VARCHAR(100),                 -- app/thiết bị nguồn, chỉ để tra cứu
    start_time  TIMESTAMPTZ   NOT NULL,
    end_time    TIMESTAMPTZ   NOT NULL,
    synced_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_health_sync_range CHECK (end_time >= start_time),
    CONSTRAINT uq_health_sync UNIQUE (user_id, provider, data_type, external_id)
);
CREATE INDEX idx_health_sync_user_time
    ON health_sync_logs (user_id, data_type, start_time);
```

Khối lượng: khoảng 48 dòng mỗi người mỗi ngày (24 bucket bước + 24 bucket nhịp tim), tức khoảng 17.500 dòng mỗi người mỗi năm. Ở quy mô demo (NFR-PERF-04: 100 người dùng đồng thời) chỉ cần chỉ mục trên, chưa cần phân vùng.

Phase 5 thêm cột `route_polyline TEXT` vào `activity_logs` bằng migration riêng; Phase 6 thêm chỉ mục `(user_id, updated_at)` cho cả hai bảng log để phục vụ `/sync/pull`.

## Hợp đồng API

Tiền tố `/api`, tất cả cần Bearer và `email_verified = true`. Mọi phản hồi theo `ApiResponse` (trừ 204); mobile xử lý lỗi theo `error.code`. User id luôn lấy từ token, không có tham số id người dùng nào trong đường dẫn hay body.

| Endpoint | FR | Phase | Body / tham số chính | Kết quả và lỗi chính |
| --- | --- | --- | --- | --- |
| `GET /api/weight-logs` | FR-ACT-04 | 1 | `from`, `to` (ngày `yyyy-MM-dd`, mặc định 90 ngày gần nhất, tối đa 366 ngày) | 200 + danh sách tăng dần theo `loggedAt`; 400 `INVALID_TIME_RANGE` |
| `POST /api/weight-logs` | FR-ACT-04 | 1 | `clientUuid`, `weightKg`, `loggedAt` (mặc định bây giờ), `note` | 201 bản mới; 200 nếu `clientUuid` đã có (gửi lại); 409 `RECORD_DELETED` nếu bản đó đã bị xoá; 400 `VALIDATION_ERROR` |
| `PUT /api/weight-logs/{id}` | FR-ACT-04 | 1 | `weightKg`, `loggedAt`, `note`, `clientUpdatedAt` (tuỳ chọn) | 200 bản hiện tại; 404 `NOT_FOUND` |
| `DELETE /api/weight-logs/{id}` | FR-ACT-04 | 1 | — | 204, idempotent; 404 `NOT_FOUND` nếu không phải của mình |
| `GET /api/activity-types` | FR-ACT-01 | 2 | — | 200 danh mục + cường độ hỗ trợ; có `Cache-Control` vì dữ liệu tĩnh |
| `GET /api/activities` | FR-ACT-01 | 2 | `from`, `to` (ngày địa phương), `page`, `size` (mặc định 50, tối đa 200) | 200 + trang, sắp giảm dần theo `startedAt`, không gồm bản đã xoá hoặc bị khử trùng (có tham số `includeSuperseded` cho mobile cần hiển thị) |
| `POST /api/activities` | FR-ACT-01 | 2 | `clientUuid`, `activityType`, `intensity`, `startedAt`, `durationMin`, `distanceKm`, `note` | 201 / 200 như trên; 400 `ACTIVITY_TYPE_UNKNOWN`; 422 `WEIGHT_REQUIRED`; 400 `VALIDATION_ERROR` |
| `GET /api/activities/{id}` | FR-ACT-01 | 2 | — | 200; 404 |
| `PUT /api/activities/{id}` | FR-ACT-01 | 2 | như `POST` + `clientUpdatedAt` | 200, tính lại calo; 404 |
| `DELETE /api/activities/{id}` | FR-ACT-01 | 2 | — | 204, idempotent |
| `POST /api/health-sync` | FR-ACT-02 | 3 | `provider`, `records[]` (`dataType`, `externalId`, `startTime`, `endTime`, `value`, `min`, `max`, `dataOrigin`) | 200 + `{received, created, updated, unchanged, rejected[{index, reason}]}`; 413 `SYNC_BATCH_TOO_LARGE` (quá 500 bản ghi) |
| `GET /api/health-sync/status` | FR-ACT-02, 03 | 3 | — | 200 + mỗi `(provider, dataType)` có `lastEndTime`, `lastSyncedAt`; người chưa sync nhận danh sách rỗng, không phải lỗi |
| `GET /api/activities/summary` | FR-ACT-05, 08 | 4 | `date`, hoặc `from`+`to` (tối đa 31 ngày) | 200 + số bước, calo hoạt động, calo bước chân, tổng calo tiêu hao, phút vận động, nhịp tim trung bình/tối đa, cân nặng mới nhất |

Khi tính calo cho hoạt động, backend chỉ dùng bản ghi cân nặng có
`logged_at <= startedAt`; nếu không có thì mới hỏi cổng PROFILE. Vì vậy cân
được ghi sau thời điểm buổi tập không được dùng hồi tố cho buổi tập đó.

`POST /api/activities` cũng chấp nhận payload legacy của mobile: `id` được
dùng làm `clientUuid` ổn định, `type` ánh xạ sang mã catalog chuẩn,
`durationMinutes` ánh xạ sang `durationMin`, `timestamp` ánh xạ sang
`startedAt`, và thiếu `intensity` mặc định là `MODERATE`. `caloriesBurned`,
`name` và `date` do client gửi bị bỏ qua; calo trả về luôn do backend tính
từ MET và cân nặng snapshot. ID dạng `act-*` của payload legacy cũng được
chấp nhận ở endpoint xoá.

Quy ước chung của phần này:

- `POST` có `clientUuid` là **idempotent**: gửi lại cùng `clientUuid` trả bản đã có (200), không tạo thêm, bỏ qua phần còn lại của body; muốn đổi nội dung thì dùng `PUT`.
- Tài nguyên của người khác trả 404, không trả 403, để không lộ sự tồn tại của id (NFR-SEC-04).
- Ngày trong tham số là ngày địa phương theo `users.timezone`; thời điểm trong body và phản hồi là ISO-8601 UTC.
- Mã lỗi mới cần thêm vào `ErrorCode`: `ACTIVITY_TYPE_UNKNOWN` (400), `WEIGHT_REQUIRED` (422), `RECORD_DELETED` (409), `INVALID_TIME_RANGE` (400), `SYNC_BATCH_TOO_LARGE` (413), `TIMESTAMP_OUT_OF_RANGE` (400). Các lỗi còn lại dùng mã có sẵn (`VALIDATION_ERROR`, `NOT_FOUND`).

## Phase 0 — Chốt quyết định, dựng khung (≈ 1 ngày, xong ở ngày 1)

Mục tiêu: các phase sau chỉ còn viết nghiệp vụ; những câu hỏi liên module được trả lời trước khi viết migration.

- [ ] Chốt D1–D11 với nhóm; ghi kết quả vào mục "Quyết định" của doc này. D11 (số phiên bản migration theo thời gian) áp cho cả repo nên cần người làm PROFILE, NUTRI đồng ý.
- [ ] Hỏi nhóm: ai làm Health Connect/HealthKit phía mobile (Q6 của SRS còn mở), PROFILE và REPORT do ai làm và theo thứ tự nào (quyết định D1, D2); iOS có kiểm thử được không (Q7, rủi ro R4).
- [ ] Tạo nhánh; mỗi phase một PR, commit theo `feat(act): …` như cách repo đang dùng (`refactor(auth): …`).
- [ ] Áp dụng D11: thêm `spring.flyway.out-of-order: true` vào `application-dev.yml`; ghi quy ước đặt tên `V<yyyyMMddHHmm>__<module>_<mô_tả>.sql` vào README để module khác theo.
- [ ] Tạo gói `controller/act`, `dto/request/act`, `dto/response/act`, `model/act` (+ `enums`), `repository/act`, `service/act`, `service/act/impl`; mapper MapStruct đặt phẳng trong `mapper/` như `UserMapper`.
- [ ] `config/ActProperties` (`@ConfigurationProperties("app.act")`, `@Validated`): `maxSyncBatchSize` (500), `syncBackfillDays` (30), `manualBackfillDays` (365), `futureTolerance` (5m), `weightMinKg` (20), `weightMaxKg` (300), `walkingMet` (3,5), `walkingSpeedKmh` (5), `strideFactor` (0,415), `fallbackCadenceSpm` (100), `dedupOverlapRatio` (0,8), `tombstoneRetention` (90d). Khai trong `application.yml` dạng `${APP_ACT_…:mặc-định}`; không thêm biến bắt buộc vào CI hay `application-test.yml`. Thêm biến mới vào `.env.example`.
- [ ] `ErrorCode`: thêm sáu mã ở trên (tệp dùng chung, gộp sớm để giảm xung đột).
- [ ] `common/PageResponse<T>` (`items`, `page`, `size`, `totalElements`, `totalPages`) nếu chưa có người tạo; hỏi nhóm NUTRI để dùng chung.
- [ ] `UserTimeResolver`: `zoneOf(userId)` đọc `users.timezone`; `dayRange(LocalDate, ZoneId)` trả `[start, end)` dạng `Instant`; `localDate(Instant, ZoneId)`. Tiêm `Clock` như `EmailVerificationService`.
- [ ] Cổng `ProfileBodyMetrics` (`Optional<BigDecimal> weightKg(userId)`, `Optional<BigDecimal> heightCm(userId)`) kèm bean mặc định `@ConditionalOnMissingBean` trả rỗng, theo mẫu `ProfileStatusProvider`.
- [ ] Lớp nền cho test tích hợp: Testcontainers PostgreSQL dùng một container chung cho các test ACT (`AuthFlowIntegrationTest` khai container riêng trong từng lớp), `@MockitoBean` cho `StorageService`, `EmailService`, `GoogleTokenVerifier` như test AUTH để context khởi động được; tiện ích tạo user đã xác minh email và phát access token qua `TokenService.issue`.

**Hoàn thành khi:** `./gradlew clean build` xanh; ứng dụng khởi động với cấu hình mặc định; test của `UserTimeResolver` xanh, gồm giờ địa phương 23:30 rơi sang ngày UTC kế tiếp và một múi giờ khác `Asia/Ho_Chi_Minh`.

## Phase 1 — Nhật ký cân nặng, FR-ACT-04 (≈ 1,5 ngày, xong ở ngày 2,5)

Mục tiêu: có bảng và API đầu tiên của ACT. Làm cân nặng trước vì đơn giản nhất và dựng sẵn ba mẫu mà mọi bảng log sau dùng lại: tạo idempotent theo `client_uuid`, xoá mềm, "bản mới nhất thắng". Phase 2 cũng cần cân nặng để tính calo.

- [ ] Migration `V<timestamp>__act_weight_logs.sql`, số phiên bản lấy bằng `date -u +%Y%m%d%H%M` lúc tạo tệp (SQL ở mục "Mô hình dữ liệu"); entity `WeightLog` với `@Getter`/`@Setter`, `equals` theo id, `@EntityListeners(AuditingEntityListener.class)` như `User`; chạy ứng dụng để Hibernate `validate` qua.
- [ ] `WeightLogRepository`: `findByUserIdAndClientUuid`, `findByIdAndUserId`, lấy theo khoảng `logged_at` bỏ bản đã xoá, `findLatestAtOrBefore(userId, instant)`, bản cuối của một ngày.
- [ ] DTO: `CreateWeightLogRequest` (`clientUuid` bắt buộc, `weightKg` trong `[20; 300]` theo `ActProperties`, `loggedAt` tuỳ chọn, `note` ≤ 255), `UpdateWeightLogRequest`, `WeightLogResponse`; `WeightLogMapper`.
- [ ] `WeightLogService.create`: tạo idempotent. **Không** dựa vào việc bắt `DataIntegrityViolationException` rồi đọc lại trong cùng transaction (transaction đã hỏng, và `GlobalExceptionHandler` sẽ trả 500). Dùng `INSERT … ON CONFLICT (user_id, client_uuid) DO NOTHING` rồi đọc lại bản hiện có; bản đã xoá mềm trả 409 `RECORD_DELETED`.
- [ ] `loggedAt` không muộn hơn `now + futureTolerance` và không quá 10 năm về trước, sai thì 400 `TIMESTAMP_OUT_OF_RANGE`.
- [ ] `update` theo D7: `clientUpdatedAt` cũ hơn `updated_at` trên server thì không ghi và trả bản server. `delete` xoá mềm, gọi lại vẫn 204; bản của người khác 404.
- [ ] `GET` theo khoảng ngày địa phương (qua `UserTimeResolver`), tối đa 366 ngày, sắp tăng dần để vẽ biểu đồ.
- [ ] Phát `WeightChangedEvent(userId, localDate)` sau commit (`@TransactionalEventListener(AFTER_COMMIT)` ở phía nghe). Chưa có người nghe; PROFILE dùng cho FR-PRO-08 và REPORT dùng cho `DailySummary` (D2).
- [ ] `WeightLogController` tại `/api/weight-logs`, chú thích Swagger, `CurrentUser.id(authentication)` như `UserController`.
- [ ] Test: biên 19,9 / 20 / 300 / 300,1; `POST` lặp trả cùng `id` (201 rồi 200); hai luồng gửi cùng `clientUuid` đồng thời chỉ có một dòng; người dùng B sửa/xoá bản của A nhận 404 và dữ liệu không đổi; bản đã xoá không xuất hiện trong danh sách; `PUT` với `clientUpdatedAt` cũ bị bỏ qua; token `email_verified = false` nhận 403 `EMAIL_NOT_VERIFIED`; xoá tài khoản kéo theo xoá `weight_logs` (test cascade hiện có tự phủ bảng mới).

**Hoàn thành khi:** AC3 của mục 4.5 SRS có test xanh (thêm bản ghi rồi `GET` thấy ngay bản mới); các test ở trên xanh trên Testcontainers.

## Phase 2 — Danh mục MET và tập luyện thủ công, FR-ACT-01 (≈ 2 ngày, xong ở ngày 4,5)

Mục tiêu: người dùng ghi buổi tập, hệ thống tính calo tiêu hao. Xong phase này là cột mốc M1: mobile có màn Thêm hoạt động và Nhật ký cân nặng chạy trọn vòng.

- [ ] Hai migration `V<timestamp>__act_activity_types.sql` (bảng + dữ liệu gieo) rồi `V<timestamp>__act_activity_logs.sql`; tạo theo đúng thứ tự này, cách nhau ít nhất một phút, vì `activity_logs` có khoá ngoại tới `activity_types`. **Đối chiếu từng dòng MET với Compendium of Physical Activities trước khi gộp**; ghi nguồn vào mô tả PR.
- [ ] Entity `ActivityType` (khoá `code`), `ActivityLog`; enum `ActivityCategory`, `Intensity`, `ActivitySource` lưu bằng `@Enumerated(EnumType.STRING)`.
- [ ] `CalorieCalculator` (lớp thuần, không phụ thuộc Spring): `calories = MET × cân_nặng × thời_lượng_giờ`, làm tròn 1 chữ số thập phân `HALF_UP`. Test giá trị đã biết: `8,3 × 70 kg × 0,5 giờ = 290,5 kcal`; thời lượng 1 phút và 1440 phút; MET và cân nặng ở biên.
- [ ] `BodyMetricsService.weightAt(userId, instant)` theo D1: `weight_logs` gần nhất không muộn hơn `instant` → `ProfileBodyMetrics` → rỗng.
- [ ] `ActivityLogService.create`: tìm `ActivityType` (không có hoặc `active = false` → 400 `ACTIVITY_TYPE_UNKNOWN`); chọn MET theo cường độ (cường độ không hỗ trợ → 400 `VALIDATION_ERROR`); lấy cân nặng (rỗng → 422 `WEIGHT_REQUIRED`); tính calo; ghi `met`, `weight_kg_used`, `ended_at = started_at + duration`; chèn idempotent bằng `ON CONFLICT` như Phase 1.
- [ ] Kiểm tra đầu vào: `durationMin` 1–1440; `distanceKm` ≥ 0 và chỉ nhận khi loại hoạt động có `has_distance`; `startedAt` trong khoảng cho phép của D9 (`manualBackfillDays`, `futureTolerance`).
- [ ] `GET /api/activity-types` kèm `Cache-Control` (dữ liệu tĩnh, ví dụ `max-age=3600`).
- [ ] `GET /api/activities` phân trang theo khoảng ngày địa phương, sắp giảm dần; `GET /{id}`; `PUT` tính lại calo bằng cân nặng tại `startedAt` mới nhất; `DELETE` xoá mềm; cùng quy tắc D7 như cân nặng.
- [ ] Phát `ActivityDataChangedEvent(userId, localDate)` sau commit khi tạo, sửa, xoá. Khi `PUT` đổi `startedAt` sang ngày khác thì phát cho **cả ngày cũ và ngày mới**.
- [ ] `ActivityController` (`/api/activities`, `/api/activity-types`), `ActivityMapper`.
- [ ] Test: tính calo đúng qua HTTP; thiếu cân nặng → 422 `WEIGHT_REQUIRED`, ghi cân nặng rồi thử lại thì được; cường độ không hỗ trợ bị từ chối; `POST` lặp và đồng thời chỉ một dòng; đổi cân nặng sau đó **không** làm đổi calo của bản đã ghi (snapshot); `PUT` tính lại calo; IDOR giữa hai người dùng; hoạt động ghi lúc 23:30 giờ địa phương thuộc đúng ngày khi lọc; danh mục chỉ trả loại `active`.

**Hoàn thành khi:** buổi tập thủ công được ghi, sửa, xoá, liệt kê đúng calo; cột mốc M1 được báo cho nhóm mobile kèm hợp đồng ở mục "Việc cần phối hợp".

## Phase 3 — Đồng bộ Health Connect / HealthKit, FR-ACT-02, 03 (≈ 2,5 ngày, xong ở ngày 7)

Mục tiêu: nhận dữ liệu bước chân và nhịp tim từ điện thoại, đồng bộ lặp lại không tạo dữ liệu trùng. "Google Fit" trong đề cương được hiểu là dữ liệu đọc qua Health Connect (SRS mục 3.2).

- [ ] Migration `V<timestamp>__act_health_sync_logs.sql`; entity `HealthSyncLog`; enum `HealthProvider` (`HEALTH_CONNECT`, `HEALTHKIT`) và `HealthDataType` (`STEPS`, `HEART_RATE`).
- [ ] `HealthSyncService.sync(userId, request)`: kiểm tra cả lô trước (số bản ghi ≤ `maxSyncBatchSize`, quá thì 413 `SYNC_BATCH_TOO_LARGE`, thân yêu cầu hợp lệ), rồi kiểm từng bản ghi: số bước là số nguyên 0–100.000 mỗi bucket; nhịp tim 20–260 bpm, `min ≤ value ≤ max`; `endTime ≥ startTime` và bucket không dài quá 24 giờ; `startTime` trong `syncBackfillDays` và không muộn hơn `futureTolerance`. Bản ghi sai **không làm hỏng cả lô**: đưa vào `rejected[]` kèm chỉ số và lý do, các bản ghi còn lại vẫn được ghi.
- [ ] Ghi theo lô bằng `NamedParameterJdbcTemplate.batchUpdate` với `INSERT … ON CONFLICT (user_id, provider, data_type, external_id) DO UPDATE … WHERE` giá trị khác; đếm `created`, `updated`, `unchanged`. Bucket thuộc ngày địa phương của `start_time`.
- [ ] `GET /api/health-sync/status` (FR-ACT-03): với mỗi `(provider, dataType)` trả `lastEndTime` và `lastSyncedAt` bằng truy vấn trên chỉ mục. Mobile lấy `lastEndTime` làm mốc đồng bộ tiếp, lùi một giờ để tính lại bucket dở dang.
- [ ] Người dùng từ chối quyền hoặc chưa đồng bộ: backend không bắt buộc đồng bộ; mọi endpoint (kể cả `status` và `summary` ở Phase 4) trả rỗng hoặc số 0, không trả 404 hay lỗi.
- [ ] Nếu chốt D4: `dataType = EXERCISE_SESSION` tạo hoặc cập nhật `activity_logs` với `source` bằng `provider` và `external_id` của phiên; mobile ánh xạ loại bài tập của thiết bị sang `activityType` của danh mục trước khi gửi; calo vẫn do backend tính bằng MET để đồng nhất, không dùng calo do thiết bị báo.
- [ ] `HealthSyncController` (`/api/health-sync`). Giới hạn kích thước thân yêu cầu để lô 500 bản ghi vẫn nhận được; áp rate limit khi `RateLimiter` của AUTH có (Phase 6).
- [ ] Phát `ActivityDataChangedEvent` cho mọi ngày địa phương bị tác động bởi lô (gộp trùng, mỗi ngày một sự kiện).
- [ ] Không ghi giá trị bước hay nhịp tim vào log ứng dụng; chỉ ghi số bản ghi và `userId` (SRS 9.3: dữ liệu sức khoẻ là dữ liệu nhạy cảm).
- [ ] Test: gửi cùng một lô ba lần cho kết quả `created` rồi `unchanged` và số dòng không đổi (AC2); giá trị bucket thay đổi thì `updated`; nhịp tim 300 bị `rejected` còn các bản ghi hợp lệ trong lô vẫn được lưu; 501 bản ghi → 413; hai người dùng dùng cùng `externalId` không đè nhau; hai lô giống nhau gửi đồng thời không sinh dòng trùng; `status` rỗng với người dùng mới; token chưa xác minh email → 403.

**Hoàn thành khi:** AC2 của mục 4.5 SRS có test xanh; cột mốc M2 được báo cho nhóm mobile; một lượt thử tay với dữ liệu Health Connect thật trên máy Android.

## Phase 4 — Tổng hợp theo ngày, calo bước chân, khử trùng, FR-ACT-05, 08 (≈ 2,5 ngày, xong ở ngày 9,5)

Mục tiêu: một nguồn duy nhất cho "hôm nay bao nhiêu bước, đốt bao nhiêu calo", không tính đôi giữa bước chân và buổi tập, không trùng giữa nhập tay và thiết bị. Đây là phase khó nhất vì ba quy tắc ở D5, D6 đan vào nhau.

- [ ] `ActivityDailyAggregator.aggregate(userId, LocalDate)` trả `DailyActivityStats`: tổng số bước; tổng calo hoạt động (bỏ bản đã xoá và bản `superseded_by IS NOT NULL`); calo bước chân; tổng calo tiêu hao; phút vận động; nhịp tim trung bình và tối đa; cân nặng mới nhất trong ngày; `stepsSource`.
- [ ] Chọn nguồn bước theo từng giờ: nếu cùng một giờ có nhiều `provider` thì lấy nguồn ưu tiên cao nhất (`HEALTH_CONNECT`, `HEALTHKIT` trước `DEVICE_SENSOR`), không cộng dồn. Cấu hình thứ tự ưu tiên trong `ActProperties`.
- [ ] Calo bước chân theo D6: `bước × 0,415 × chiều_cao_m / 1000` ra km, chia 5 km/h ra giờ, nhân `3,5 × cân_nặng`; thiếu chiều cao thì dùng nhịp 100 bước/phút. Bucket giao với hoạt động nhóm đi bộ hoặc chạy đang tính thì loại khỏi calo bước nhưng vẫn cộng vào số bước. Ví dụ để kiểm tra: 10.000 bước, 170 cm, 70 kg ≈ 345,7 kcal.
- [ ] `ActivityDeduplicator` theo D5: chạy sau mỗi lần tạo hoặc sửa hoạt động và sau mỗi lô đồng bộ có phiên tập. Tìm hoạt động chưa xoá của cùng người dùng, cùng nhóm, giao nhau ≥ `dedupOverlapRatio` thời lượng của bản ngắn hơn; chọn bản thắng theo nguồn rồi theo `updated_at`; ghi `superseded_by` cho bản thua. Khi bản thắng bị xoá hoặc sửa hết còn trùng thì bản thua được khôi phục (`superseded_by = NULL`). Tách phần quyết định thành hàm thuần để test không cần DB.
- [ ] `GET /api/activities/summary` theo ngày và theo khoảng (≤ 31 ngày, tối đa 31 lần gọi `aggregate`; tối ưu bằng truy vấn nhóm theo ngày nếu đo thấy chậm).
- [ ] Hợp đồng sự kiện cho các module khác: `ActivityDataChangedEvent` và `WeightChangedEvent` (đã phát từ Phase 1–3) được ghi rõ trong doc này. Nếu D2 giao việc ghi `daily_summaries` cho REPORT/NUTRI: cung cấp phương thức `aggregate` để họ gọi từ bộ nghe; nếu bảng chưa tồn tại, thêm bộ nghe giả trong test để chứng minh sự kiện phát đủ và đúng ngày.
- [ ] Hợp đồng `WeightChangedEvent` với PROFILE: ghi trong tài liệu rằng bộ nghe của PROFILE (FR-PRO-08) phải dùng `@TransactionalEventListener(AFTER_COMMIT)` và không được làm lỗi giao dịch ghi cân nặng.
- [ ] Test: ngày có 24 bucket → tổng bước đúng bằng tổng bucket (dữ liệu mẫu đối chiếu thủ công cho AC1); đồng bộ lặp không đổi tổng (AC2); buổi chạy nhập tay và buổi chạy thiết bị giao nhau 90% chỉ tính một bản, xoá bản thiết bị thì bản nhập tay được tính lại; hai hoạt động khác nhóm cùng giờ (đi bộ và tập tạ) không bị khử trùng; bước trong giờ có buổi chạy không bị tính calo bước; hai nguồn cùng giờ chỉ lấy một; thiếu chiều cao dùng nhịp dự phòng; ranh giới ngày theo múi giờ (23:30 giờ địa phương); sự kiện phát cho cả ngày cũ và ngày mới khi dời giờ tập.

**Hoàn thành khi:** AC1 và AC2 của mục 4.5 SRS đạt ở mức dữ liệu mẫu trên Testcontainers; cột mốc M3: REPORT và dashboard lấy số liệu ngày từ endpoint này.

## Phase 5 — Cảm biến và lộ trình, FR-ACT-06, 07 (≈ 1 ngày, xong ở ngày 10,5, mức C, tuỳ chọn)

Mục tiêu: backend nhận dữ liệu từ `expo-sensors` và `expo-location`. Phần thu thập nằm hoàn toàn ở mobile; làm phase này chỉ khi mobile thực sự làm hai tính năng đó, nếu không thì bỏ.

- [ ] Thêm `DEVICE_SENSOR` vào `HealthProvider` (bước chân theo bucket giờ, cùng hợp đồng như Health Connect). Quy tắc nguồn ưu tiên ở Phase 4 đã loại trùng; thêm test: cùng giờ có cả Health Connect và cảm biến thì chỉ tính Health Connect.
- [ ] `activity_logs` nhận `source = SENSOR` hoặc `GPS`. Migration mới thêm `route_polyline TEXT`; chỉ nhận chuỗi polyline hợp lệ, tối đa 100 KB; kiểm tra quãng đường và thời lượng cho tốc độ trung bình hợp lý (cấu hình, mặc định ≤ 60 km/h) để loại dữ liệu GPS rác.
- [ ] Lộ trình là dữ liệu vị trí nhạy cảm: chỉ chủ sở hữu đọc được, không bao giờ nằm trong dữ liệu chia sẻ với bạn bè hay bảng xếp hạng (SRS 4.7), và bị xoá cùng tài khoản. Có test cho từng điểm.
- [ ] Test: GPS với tốc độ vô lý bị từ chối; polyline quá lớn → 413; người dùng khác không đọc được lộ trình.

**Hoàn thành khi:** hai nguồn dữ liệu mới đi qua cùng pipeline tổng hợp mà không tính đôi.

## Phase 6 — Hoàn thiện, tài liệu, nghiệm thu (≈ 1,5 ngày, xong ở ngày 12)

Mục tiêu: chứng minh mọi AC của module ACT đạt và để các module khác (SYS, REPORT) nối vào được.

- [ ] **Tương thích đồng bộ ngoại tuyến (FR-SYS-05/06):** thêm chỉ mục `(user_id, updated_at)` cho `weight_logs` và `activity_logs`; cung cấp `upsertFromClient` (dùng đúng quy tắc idempotent và "bản mới nhất thắng" ở trên) và `changedSince(userId, since)` (gồm bản xoá mềm, sắp theo `updated_at`) để SYS gọi từ `/sync/push` và `/sync/pull`.
- [ ] **Rate limit:** khi `RateLimiter` của AUTH Phase 7 có, giới hạn `POST /api/health-sync` (đề xuất 120 yêu cầu/giờ/người dùng, đủ cho đồng bộ mỗi 15 phút) và các `POST` tạo bản ghi (NFR-SEC-06). Nếu Phase 7 của AUTH chưa xong, ghi nợ kỹ thuật và vẫn giữ trần 500 bản ghi/lô.
- [ ] **Dọn dẹp:** tác vụ `@Scheduled` mỗi ngày xoá cứng bản xoá mềm cũ hơn `tombstoneRetention` (90 ngày) ở `weight_logs` và `activity_logs`. `@EnableScheduling` do AUTH Phase 7 thêm; thống nhất để không khai hai lần.
- [ ] **Bảo mật:** ma trận IDOR cho mọi endpoint ACT (người dùng B truy cập id của A: 404 và không đổi dữ liệu); kiểm tra log không chứa cân nặng, nhịp tim hay vị trí; chuỗi lạ (SQL, HTML) trong `note` được lưu nguyên và không phá phản hồi.
- [ ] **Xoá tài khoản:** test tích hợp tạo dữ liệu đủ bốn bảng rồi `DELETE /api/users/me` và kiểm tra cả bốn bảng không còn dòng nào của người dùng đó; test cascade hiện có vẫn xanh.
- [ ] **Hiệu năng:** gieo khoảng 100.000 bản ghi `health_sync_logs` cho một số người dùng; `EXPLAIN` các truy vấn khoảng thời gian và tổng hợp xác nhận dùng chỉ mục; k6 với vài chục request đồng thời: p95 ≤ 500 ms (NFR-PERF-01) cho `summary`, `activities`, `weight-logs`; lô đồng bộ 500 bản ghi hoàn tất trong khoảng 1 giây ở máy dev.
- [ ] **Tài liệu:** `docs/api-specs/act.yaml` cùng phong cách `auth.yaml`; `docs/tests/act-test-cases.xlsx`; README (endpoint ACT, biến `APP_ACT_*`); cập nhật SRS (cột mới `intensity`, `weight_kg_used`, `ended_at`, `superseded_by`, `deleted_at`, `value_min/max`, bảng `activity_types`, sáu mã lỗi mới, kết quả D1–D11); bộ yêu cầu mẫu (Postman, Bruno hoặc `.http`) cho nhóm mobile.
- [ ] **Chạy thật với mobile:** so số bước trong ứng dụng với ứng dụng sức khoẻ của hệ điều hành trên một máy Android thật, sai lệch ≤ 5% (AC1); thử đồng bộ lặp lại; thử từ chối quyền; iOS ở mức cố gắng nếu có thiết bị (R4).
- [ ] Nếu AUTH Phase 8 đã thêm JaCoCo, kiểm tra độ phủ tầng service ACT ≥ 60% (SRS 9.6).

**Hoàn thành khi:** mọi dòng trong bảng truy vết bên dưới xanh; `./gradlew check` xanh; nhóm mobile xác nhận ba luồng chạy trên thiết bị thật: ghi cân nặng, ghi tập luyện, đồng bộ bước chân và nhịp tim.

## Truy vết tiêu chí chấp nhận

Mục 4.5 của SRS nêu ba tiêu chí chấp nhận (AC1–AC3). Mình bổ sung QT1–QT10 từ quy tắc nghiệp vụ để mỗi yêu cầu đều có ít nhất một bài test.

| Mã | Yêu cầu | Phase | Bài test cần có |
| --- | --- | --- | --- |
| AC1 | Số bước trong ngày khớp ứng dụng sức khoẻ của hệ điều hành, sai lệch ≤ 5% | 4, 6 | Tổng bucket = số bước tổng hợp (tự động); so với Google Fit/Apple Health trên máy thật (thủ công) |
| AC2 | Đồng bộ lặp không làm tăng số bước hay tổng calo | 3, 4 | Gửi cùng lô nhiều lần: số dòng, `unchanged` và tổng bước không đổi |
| AC3 | Thêm bản ghi cân nặng cập nhật biểu đồ ngay | 1 | Sau `POST`, `GET /weight-logs` trả ngay bản mới; sự kiện phát đúng ngày |
| QT1 | Calo = MET × kg × giờ, làm tròn 1 chữ số | 2 | `8,3 × 70 × 0,5 = 290,5`; biên thời lượng 1 và 1440 phút |
| QT2 | Cân nặng và các số đo trong miền hợp lý | 1, 2, 3 | 19,9/20/300/300,1 kg; nhịp tim 300; số bước âm hoặc quá lớn |
| QT3 | Tạo bản ghi idempotent theo `client_uuid` | 1, 2 | Gửi lặp và gửi đồng thời hai luồng chỉ cho một dòng, lần lặp trả 200 |
| QT4 | Xoá mềm; "bản mới nhất thắng" khi sửa | 1, 2, 6 | `clientUpdatedAt` cũ bị bỏ qua; bản xoá không hiện trong danh sách; bản xoá vẫn có trong `changedSince` |
| QT5 | Mọi tài nguyên chỉ của chính chủ | 1–4, 6 | Ma trận IDOR cho từng endpoint, kết quả 404 và dữ liệu không đổi |
| QT6 | Cần cân nặng mới tính được calo; không bịa mặc định | 2 | 422 `WEIGHT_REQUIRED`; ghi cân nặng rồi tạo lại thành công; đổi cân nặng sau không đổi calo cũ |
| QT7 | Calo bước chân theo cân nặng, chiều cao; không tính đôi với buổi đi bộ/chạy | 4 | 10.000 bước, 170 cm, 70 kg ≈ 345,7 kcal; bước trong giờ có buổi chạy không tính calo bước |
| QT8 | Khử trùng hoạt động đồng bộ và thủ công | 4 | Giao nhau ≥ 80% chỉ tính một bản; xoá bản thắng khôi phục bản thua; khác nhóm không bị gộp |
| QT9 | Ứng dụng vẫn chạy khi người dùng từ chối quyền sức khoẻ | 3 | Người dùng không có dữ liệu đồng bộ nhận danh sách rỗng và số 0, không lỗi |
| QT10 | Chưa xác minh email bị chặn; xoá tài khoản xoá dữ liệu ACT | 1–6 | 403 `EMAIL_NOT_VERIFIED`; sau `DELETE /api/users/me` cả bốn bảng không còn dòng của người dùng |

## Việc cần phối hợp song song

**Nhóm mobile** (cần biết trước Phase 2 và Phase 3):

- Sinh `clientUuid` (UUID v4) lúc tạo bản ghi trên máy và giữ nguyên khi gửi lại; xếp hàng bản ghi chưa gửi khi ngoại tuyến.
- Gặp 409 `RECORD_DELETED` thì bỏ bản ghi cục bộ; gặp 422 `WEIGHT_REQUIRED` thì chuyển sang màn ghi cân nặng.
- Đồng bộ sức khoẻ: dùng API tổng hợp theo giờ **địa phương** của Health Connect/HealthKit, `externalId` dạng `STEPS:yyyyMMddHH` và `HR:yyyyMMddHH`, gửi từng lô ≤ 500 bản ghi; lấy `lastEndTime` từ `GET /api/health-sync/status` (lùi 1 giờ) làm mốc; lần đầu tối đa 30 ngày về trước.
- Khi người dùng từ chối quyền: không gọi `POST /api/health-sync`; màn hình vẫn hoạt động với dữ liệu nhập tay.
- Khi gặp 403 `EMAIL_NOT_VERIFIED`: quay về màn nhập mã (đã có từ AUTH).

**Nhóm PROFILE / REPORT / SYS:**

- PROFILE: cài `ProfileBodyMetrics` (cân nặng, chiều cao); nghe `WeightChangedEvent` cho FR-PRO-08.
- REPORT: nghe `ActivityDataChangedEvent`, gọi `ActivityDailyAggregator` để ghi `daily_summaries`; quyết định hiển thị calo gộp hay ròng (xem lưu ý ở mục "Quyết định").
- SYS: dùng `upsertFromClient` và `changedSince` cho `/sync/push` và `/sync/pull`.

## Rủi ro và lưu ý kỹ thuật

Rủi ro lớn nhất là phụ thuộc chéo (PROFILE, `daily_summaries`) chưa có, và độ chính xác của dữ liệu đồng bộ. Nếu phải cắt phạm vi, giữ các yêu cầu mức M (FR-ACT-01, 02, 03, 04), hoãn Phase 5 trước, rồi đến khử trùng (FR-ACT-08) và đồng bộ phiên tập (D4).

| Rủi ro | Mức | Cách xử lý |
| --- | --- | --- |
| PROFILE chưa có nên không có cân nặng dự phòng; người dùng mới chưa ghi cân nặng gặp 422 | Trung bình | Cổng `ProfileBodyMetrics` (D1); thống nhất với PROFILE rằng onboarding luôn ghi cân nặng đầu tiên vào `weight_logs` |
| Bảng `daily_summaries` chưa có hoặc hai module cùng cố tạo | Trung bình | D2: ACT chỉ phát sự kiện và cung cấp `aggregate`; chốt người sở hữu bảng ở Phase 0 |
| Cộng đôi bước chân với hoạt động, hoặc giữa hai thiết bị (điện thoại, đồng hồ) | Cao | Dùng bucket đã tổng hợp của hệ điều hành (D3), ưu tiên nguồn và loại bucket giao hoạt động (D5, D6); đối chiếu thủ công ở Phase 6 |
| Calo gộp bị tính đôi với TDEE trong ngân sách calo | Trung bình | ACT lưu `met` và `weight_kg_used` để tính số ròng; REPORT quyết định trước FR-REP-02 |
| Hệ số 0,415 và MET gieo ban đầu chưa được đối chiếu nguồn | Thấp | Đối chiếu Compendium từng dòng trước khi gộp; công thức và hằng số đều cấu hình được |
| `DataIntegrityViolationException` do trùng `client_uuid` rơi xuống handler chung và thành 500 | Trung bình | Chèn bằng `ON CONFLICT` thay vì bắt ngoại lệ; có test hai luồng đồng thời |
| `ErrorCode` là tệp dùng chung, nhiều nhánh cùng sửa | Trung bình | Gộp sớm sáu mã lỗi ở Phase 0 |
| Số phiên bản Flyway theo thời gian: nhánh có số nhỏ hơn gộp sau làm Flyway báo "resolved migration not applied" trên CSDL dùng chung | Thấp | Bật `out-of-order: true` ở `dev` (D11); `prod` triển khai bản đã gộp lên CSDL sạch hoặc theo thứ tự; các module tạo bảng độc lập nên chạy lệch thứ tự an toàn |
| Người làm module khác vẫn đánh số tuần tự (`V2`…) và chen vào giữa số theo thời gian | Thấp | Số tuần tự nhỏ hơn mọi số theo thời gian nên chỉ gây lệch thứ tự, không gây trùng; ghi quy ước vào README ở Phase 0 và Phase 6 |
| Lô đồng bộ lớn làm chậm hoặc tốn bộ nhớ | Thấp | Trần 500 bản ghi/lô, ghi theo lô bằng JDBC, đo ở Phase 6 |
| Dữ liệu nhịp tim, vị trí là dữ liệu sức khoẻ nhạy cảm | Trung bình | Không ghi giá trị vào log; chỉ chủ sở hữu đọc; lộ trình không bao giờ chia sẻ; xoá cùng tài khoản |
| Thiếu thiết bị iOS để thử HealthKit | Trung bình | Ưu tiên Android; backend không phân biệt hai nguồn nên chỉ rủi ro nằm ở mobile (R4 của SRS) |
| Ước lượng 12 ngày công chỉ tính backend, một người, làm liên tục; nhóm đang làm nhiều module song song | Cao | Phase 1 và 3 có thể chạy song song; cắt phạm vi như trên; xác nhận phân công (Q6) ở Phase 0 |
