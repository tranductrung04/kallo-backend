# Kế hoạch triển khai Module SYS — Kallo Backend

Nov 12, 2026

Module SYS (SRS mục 4.8, FR-SYS-01…09) gồm 9 phase (0–8), khoảng 15 ngày công cho một người. Xong Phase 2 các module khác gọi được `NotificationService` và mobile có trung tâm thông báo kèm push; xong Phase 6 mọi yêu cầu mức **M** của SYS đã chạy (push, nhắc nhở, trung tâm thông báo, đồng bộ ngoại tuyến, ghi nguồn ODbL); Phase 8 là điểm nghiệm thu.

> **Phạm vi tài liệu.** Đây là kế hoạch cho **repository backend**. Phần việc ở mobile (hàng đợi ngoại tuyến, xin quyền thông báo, giao diện sáng/tối, i18n phía app) chỉ xuất hiện dưới dạng mục “Báo nhóm mobile” vì Q6 của SRS (ai phụ trách đồng bộ ngoại tuyến, WebSocket, CI/CD) chưa chốt.

## Phạm vi và cách đọc

| Mã | Yêu cầu | Ưu tiên | Việc ở backend | Phase |
| --- | --- | --- | --- | --- |
| FR-SYS-01 | Thông báo đẩy FCM + Expo Notifications; đăng ký/huỷ token thiết bị | M | Bảng `device_tokens`, API thiết bị, gửi FCM qua `PushSender` | 1 |
| FR-SYS-02 | Nhắc nhở thông minh: nước, ghi log bữa ăn, cảnh báo vượt calo | M | Bộ lập lịch nhắc, trình nghe sự kiện cảnh báo calo, trần thông báo/ngày | 4, 5 |
| FR-SYS-03 | Trung tâm thông báo: danh sách, đã đọc, xoá | M | Bảng `notifications`, 5 endpoint | 2 |
| FR-SYS-04 | Tuỳ chỉnh loại/khung giờ nhận; không làm phiền | S | Bảng `notification_settings`, `NotificationPolicy` | 3 |
| FR-SYS-05 | Hoạt động ngoại tuyến, tự đồng bộ khi có mạng | M | `POST /sync/push`, `GET /sync/pull`, khung `SyncEntityHandler` | 6 |
| FR-SYS-06 | Xung đột đồng bộ: UUID client + `updated_at` mới nhất thắng | S | Quy tắc trong khung đồng bộ | 6 |
| FR-SYS-07 | Tiếng Việt mặc định, chuẩn bị i18n tiếng Anh | C | Mẫu thông báo vi/en phía server | 3 |
| FR-SYS-08 | Giao diện sáng/tối | C | **Không có việc ở backend** (thuần phía app) | — |
| FR-SYS-09 | Ghi nguồn và giấy phép Open Food Facts (ODbL) | M | Endpoint công khai trả danh sách ghi nguồn, nhãn `source` | 0 |

## Hiện trạng repo

Bảng dưới phản ánh nhánh hiện tại (commit `03baf78`, “reorganize authentication components into auth packages”). Đây là các điểm ảnh hưởng trực tiếp đến SYS.

| Hạng mục | Hiện trạng | Hành động |
| --- | --- | --- |
| Migration | Chỉ có `V1__init_schema.sql`, chứa cả 4 bảng AUTH (gồm `email_verification_tokens`; kế hoạch AUTH ghi `V3` nhưng repo đã gộp vào V1) | SYS bắt đầu từ `V2`; thống nhất số phiên bản với các module khác trước khi gộp nhánh |
| Cấu trúc package | Theo tính năng: `controller/auth`, `service/auth` + `service/auth/impl`, `model/auth`, `repository/auth`, `dto/request/auth`, `dto/response/auth`, `security/auth` | SYS dùng cùng kiểu với gói `sys` (quyết định S1) |
| Dependency | Có `websocket`, `data-redis`, `mail`, `validation`, `oauth2-resource-server`, Testcontainers; **chưa có** thư viện FCM, Caffeine, ShedLock | Thêm thư viện FCM ở Phase 0 (S2); không thêm ShedLock (S7) |
| FCM, WebSocket | README nhắc `FCM_CREDENTIALS_PATH` và mục WebSocket nhưng chưa có mã hay cấu hình nào | Làm mới ở Phase 1 và 7 |
| Redis, Elasticsearch | Có starter và container trong `docker-compose.yml`, chưa có dòng mã nào dùng | SYS không dùng Elasticsearch; Redis chỉ qua `RateLimiter` của AUTH (Phase 8) |
| AUTH Phase 7 | Chưa làm: không có `RateLimiter`, `@EnableScheduling`, `@Scheduled`, bộ lọc `request_id` | **Điều kiện tiên quyết** cho Phase 8 của SYS; `@EnableScheduling` thì SYS Phase 0 thêm nếu AUTH chưa thêm, chỉ một lớp `SchedulingConfig` |
| Cổng xác minh email | `SecurityConfig` dùng `anyRequest().access(emailVerifiedManager())` | Mọi API SYS tự động trả 403 `EMAIL_NOT_VERIFIED` khi chưa xác minh; không cần mã thêm |
| Endpoint công khai | `PUBLIC_AUTH_ENDPOINTS` là danh sách matcher từng đường dẫn chính xác; `BearerTokenResolver` trả `null` cho các đường dẫn này | Thêm `/api/app/attributions` (Phase 0) và `/ws/**` (Phase 7) |
| Thời gian | Không có bean `Clock`; service AUTH nhận `Clock` qua constructor thứ hai, mặc định `Clock.systemUTC()` | SYS làm cùng kiểu để test được hạn dùng, không thêm bean `Clock` mới |
| Docker Compose | Service `app` liệt kê **từng biến môi trường** một | Mỗi biến mới của SYS phải thêm vào `docker-compose.yml`, nếu không container không nhận được |
| Module khác | Chưa có `food_logs`, `water_logs`, `weight_logs`, `activity_logs`, `daily_summary` | SYS định nghĩa hợp đồng sự kiện và cổng (Phase 4–6); module sở hữu bảng cài đặt sau |

## Quyết định cần chốt trước khi code

Mười hai điểm dưới đây ảnh hưởng thiết kế. Cột “Đề xuất” là lựa chọn mình đề nghị; nhóm cần xác nhận, nhất là S2–S3 (cần người làm mobile) và S9 (ảnh hưởng schema các module khác).

| # | Vấn đề | Đề xuất | Lý do |
| --- | --- | --- | --- |
| S1 | Tên package | `sys`: `controller/sys`, `service/sys`, `service/sys/impl`, `model/sys`, `repository/sys`, `dto/{request,response}/sys`; sự kiện dùng chung ở `common/event` | Giữ quy ước theo tính năng của AUTH; sự kiện đặt ở `common` để module khác phát mà không phụ thuộc gói `sys` |
| S2 | Kênh gửi push | Backend gửi **FCM trực tiếp** qua cổng `PushSender`; dev/test dùng `LoggingPushSender`, bật FCM bằng `PUSH_ENABLED=true` | SRS 8.1 chọn FCM + service account (`FCM_CREDENTIALS_PATH`); cổng trừu tượng giúp đổi sang Expo Push Service nếu S3 vướng |
| S3 | Loại token mobile gửi lên | Cần xác nhận với nhóm mobile: token FCM gốc (`getDevicePushTokenAsync`) hay Expo push token | Theo tài liệu Expo, trên iOS token gốc là token APNs chứ không phải token FCM; muốn gửi FCM tới iOS phải cấu hình Firebase phía iOS hoặc đổi sang Expo Push Service. Android không bị vướng |
| S4 | Đường gửi duy nhất | Mọi thông báo đi qua `NotificationService.notify(...)`: kiểm tra chính sách → ghi `notifications` (khoá chống trùng) → sau commit mới gửi push và WebSocket. Loại thông báo bị tắt chặn cả trung tâm lẫn push; **không làm phiền chỉ chặn push**, mục vẫn vào trung tâm (riêng nhắc nhở bị chặn do DND thì bỏ hẳn, S6) | Một cổng giúp kiểm soát trần/ngày, i18n và chống trùng ở một chỗ |
| S5 | Chống gửi trùng | Cột `dedupe_key` + chỉ mục duy nhất `(user_id, dedupe_key)`; `INSERT … ON CONFLICT DO NOTHING`; xoá mục trong trung tâm là **xoá mềm** (`deleted_at`) | AC2 của SRS “đúng một thông báo”; xoá cứng sẽ làm mất khoá và cho phép cảnh báo lặp trong ngày |
| S6 | Trần thông báo/ngày | Trần ≤ 6 (cấu hình được) chỉ áp cho nhóm **REMINDER** (nhắc nước, nhắc bữa ăn); cảnh báo calo, huy hiệu, bạn bè không tính. Nhắc nhở gặp DND hoặc hết trần thì **bỏ qua**, không dời giờ | SRS chỉ nêu trần ở quy tắc nhắc nhở; nhắc dồn sau DND dễ thành thông báo lỗi thời |
| S7 | Bộ lập lịch nhắc | `@Scheduled` mỗi phút; an toàn khi chạy nhiều instance nhờ `dedupe_key`, không thêm ShedLock | Demo chạy một instance; khoá duy nhất đã đủ để “nhiều nơi cùng bắn” vẫn ra đúng một thông báo |
| S8 | Hợp đồng với module khác | Sự kiện Spring `@TransactionalEventListener(AFTER_COMMIT)` cho thay đổi (calo, nước, bữa ăn) và cổng `UserActivityPort` để SYS **hỏi** lịch sử (lần uống nước gần nhất, đã có log bữa ăn chưa, giờ ăn quen thuộc). Cổng có bản `Noop` mặc định | SYS không phụ thuộc bảng của NUTRI/ACT; module kia cài cổng khi tới lượt |
| S9 | Con trỏ đồng bộ | `sync_seq` — một sequence toàn cục, gán bằng cột mặc định và trigger mỗi lần ghi; bản ghi chỉ được trả về sau khoảng chờ `settle-delay` (2 giây) để transaction chưa commit kịp xong | Sequence cấp số theo lúc ghi chứ không theo lúc commit nên có thể bỏ sót dòng; `updated_at` của client không dùng làm con trỏ vì lệch đồng hồ. Khoảng chờ là giải pháp thực dụng, giới hạn đã ghi ở mục Rủi ro |
| S10 | Quy tắc xung đột | Client gửi `updatedAt`; server kẹp về `now` nếu client lệch tương lai quá 5 phút; bản ghi có `updated_at` lớn hơn thắng, hoà thì server thắng; xoá là tombstone cũng tuân theo quy tắc này | Đúng FR-SYS-06; tombstone ngăn bản `UPSERT` cũ đến muộn “sống lại” dữ liệu đã xoá |
| S11 | Kênh thời gian thực | STOMP trên WebSocket (`/ws`), broker đơn giản trong bộ nhớ, xác thực JWT ở khung `CONNECT` bằng header `Authorization` | Tránh đưa token lên URL; STOMP cho sẵn khái niệm đích/đăng ký để GAME và CHAT dùng lại; mở rộng nhiều instance nằm ngoài phạm vi |
| S12 | Nội dung push và dữ liệu sức khoẻ | Push và `data` đi qua máy chủ của FCM nên **không chứa số liệu sức khoẻ** (calo, cân nặng); push dùng câu chung chung, chi tiết chỉ nằm trong mục trung tâm lấy qua API | SRS 9.3: dữ liệu sức khoẻ không chia sẻ cho bên thứ ba; số liệu trên màn hình khoá cũng lộ ra người xung quanh |

## Lộ trình

| Phase | Nội dung | Công | Cộng dồn | FR |
| --- | --- | --- | --- | --- |
| 0 | Nền chung, cấu hình, hợp đồng sự kiện, endpoint ghi nguồn | 1 | 1 | FR-SYS-09 |
| 1 | Token thiết bị và gửi push FCM | 1,5 | 2,5 | FR-SYS-01 |
| 2 | Lõi thông báo và trung tâm thông báo | 2 | 4,5 | FR-SYS-03 |
| 3 | Cài đặt thông báo, không làm phiền, mẫu vi/en | 1,5 | 6 | FR-SYS-04, 07 |
| 4 | Cảnh báo vượt calo | 1 | 7 | FR-SYS-02 |
| 5 | Nhắc nhở thông minh | 2 | 9 | FR-SYS-02 |
| 6 | Đồng bộ ngoại tuyến | 3 | 12 | FR-SYS-05, 06 |
| 7 | Thông báo trực tiếp qua WebSocket | 1,5 | 13,5 | mục 8.2 SRS |
| 8 | Giới hạn tần suất, dọn dẹp, tài liệu, nghiệm thu | 1,5 | 15 | NFR |

Các mốc: **M1 (ngày 4,5)** — module khác gọi được `NotificationService`, mobile đăng ký thiết bị và xem trung tâm thông báo; **M2 (ngày 12)** — mọi yêu cầu mức M của SYS chạy được (còn WebSocket và rà soát); **M3 (ngày 15)** — nghiệm thu. Bắt đầu thứ Hai 16/11/2026 và làm liên tục thì M3 rơi vào khoảng thứ Sáu 4/12/2026, nằm trong khung giai đoạn 5–6 ở mục 10 của SRS.

Phase 6 chỉ phụ thuộc Phase 0, nên nếu có hai người thì một người làm Phase 1→2→3→4→5→7 còn người kia làm Phase 6, tiết kiệm khoảng 2,5 ngày. Phase 4–6 cần module NUTRI/ACT/PROFILE phát sự kiện và cài cổng; SYS tự chứng minh bằng sự kiện giả và handler giả, khi module thật có thì chạy lại các bài test hợp đồng (xem mục Phụ thuộc).

## Kiến trúc và luồng

```
 Module khác (NUTRI, GAME, ACT…)          Bộ lập lịch nhắc (mỗi phút)
   │ phát sự kiện / gọi notify()                  │
   ▼                                              ▼
 ┌────────────────────────── NotificationService ───────────────────────────┐
 │ 1. NotificationPolicy (loại bật/tắt, DND, trần/ngày)                      │
 │ 2. INSERT notifications … ON CONFLICT (user_id, dedupe_key) DO NOTHING    │
 └───────────────┬───────────────────────────────────────────────────────────┘
                 │ sau commit (AFTER_COMMIT, bất đồng bộ)
      ┌──────────┴───────────┬──────────────────────┐
      ▼                      ▼                      ▼
  PushSender (FCM)     WebSocket /user/queue    Trung tâm thông báo
  mọi thiết bị của     /notifications           (GET /api/notifications)
  user, gỡ token hỏng
```

**Hợp đồng sự kiện** (đặt ở `common/event`, module sở hữu dữ liệu phát sau khi commit):

```java
public record DailySummaryChangedEvent(UUID userId, LocalDate date, double caloriesIn, double calorieGoal) {}
public record WaterLoggedEvent(UUID userId, Instant loggedAt) {}
public record MealLoggedEvent(UUID userId, String mealType /* BREAKFAST|LUNCH|DINNER|SNACK */, Instant loggedAt) {}
```

**Cổng truy vấn lịch sử** (`service/sys`, module sở hữu dữ liệu cài đặt; mặc định `Noop` trả “không có dữ liệu”):

```java
public interface UserActivityPort {
  Optional<Instant> lastWaterLogAt(UUID userId);
  boolean hasMealLog(UUID userId, LocalDate date, String mealType);
  List<LocalTime> recentMealLogTimes(UUID userId, String mealType, int days);
}
```

**API cho module khác** (hợp đồng công khai của SYS):

```java
public interface NotificationService {
  NotifyOutcome notify(NotifyCommand cmd);   // SENT | SUPPRESSED_DISABLED | SUPPRESSED_DND_PUSH_ONLY | DUPLICATE | CAP_REACHED
}
// NotifyCommand: userId, type, params (Map<String,Object>), dedupeKey (nullable), data (Map<String,String> chỉ chứa id)
```

## Mô hình dữ liệu

SYS cần bốn nhóm migration. Mỗi migration một tệp để các nhánh khác nhau của nhóm không đụng nhau; số phiên bản dưới đây là đề xuất, dùng số kế tiếp còn trống tại thời điểm gộp nhánh. Mọi bảng khai `user_id UUID REFERENCES users (id) ON DELETE CASCADE` để FR-AUTH-07 dọn sạch bằng một lệnh xoá `users`, và bài test `information_schema` của AUTH Phase 6 sẽ bắt lỗi nếu thiếu. Vì `ddl-auto: validate`, dùng `VARCHAR`, `INTEGER`, `TIMESTAMPTZ`, `TIME` khớp entity (`LocalTime` cho `TIME`).

**`V2__device_tokens.sql`**

```sql
CREATE TABLE device_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(10)  NOT NULL,            -- ANDROID | IOS
    locale       VARCHAR(10),
    app_version  VARCHAR(20),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_device_tokens_token ON device_tokens (token);
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
```

**`V3__notifications.sql`**

```sql
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,            -- đã dựng theo ngôn ngữ người dùng, dạng chi tiết cho trung tâm
    body        VARCHAR(1000) NOT NULL,
    data        JSONB,                             -- chỉ id/tham số hiển thị, không số liệu nhạy cảm cho push
    dedupe_key  VARCHAR(120),
    is_read     BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMPTZ,
    pushed_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,                       -- xoá mềm để khoá chống trùng còn hiệu lực (S5)
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_notifications_dedupe ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_user_unread  ON notifications (user_id) WHERE is_read = FALSE AND deleted_at IS NULL;
```

**`V4__notification_settings.sql`** (dòng được tạo lười lần đầu `GET`/`PUT`, không cần móc vào luồng đăng ký của AUTH)

```sql
CREATE TABLE notification_settings (
    user_id                     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    language                    VARCHAR(5) NOT NULL DEFAULT 'vi',
    push_enabled                BOOLEAN    NOT NULL DEFAULT TRUE,
    water_reminder_enabled      BOOLEAN    NOT NULL DEFAULT TRUE,
    water_start_time            TIME       NOT NULL DEFAULT '10:00',
    water_end_time              TIME       NOT NULL DEFAULT '18:00',
    water_interval_minutes      INTEGER    NOT NULL DEFAULT 240,   -- mặc định 3 lần/ngày: 10:00, 14:00, 18:00
    meal_reminder_enabled       BOOLEAN    NOT NULL DEFAULT TRUE,
    meal_breakfast_time         TIME,                              -- NULL = giờ thói quen hoặc mặc định
    meal_lunch_time             TIME,
    meal_dinner_time            TIME,
    calorie_alert_enabled       BOOLEAN    NOT NULL DEFAULT TRUE,
    calorie_alert_threshold_pct INTEGER    NOT NULL DEFAULT 100,
    social_enabled              BOOLEAN    NOT NULL DEFAULT TRUE,  -- huy hiệu, bạn bè, thử thách
    dnd_enabled                 BOOLEAN    NOT NULL DEFAULT FALSE,
    dnd_start_time              TIME       NOT NULL DEFAULT '22:00',
    dnd_end_time                TIME       NOT NULL DEFAULT '07:00',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_water_interval CHECK (water_interval_minutes BETWEEN 30 AND 720),
    CONSTRAINT chk_calorie_threshold CHECK (calorie_alert_threshold_pct BETWEEN 100 AND 200)
);
```

**`V5__sync_infrastructure.sql`** (hạ tầng đồng bộ; không tạo bảng dữ liệu, chỉ sequence và hàm trigger)

```sql
CREATE SEQUENCE sync_seq;

CREATE FUNCTION touch_sync() RETURNS trigger AS $$
BEGIN
    NEW.sync_seq := nextval('sync_seq');
    NEW.server_updated_at := clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Mẫu cột bắt buộc cho bảng log tham gia đồng bộ** (module sở hữu bảng — NUTRI, ACT — khai trong migration của mình, đây là điều kiện để cắm handler ở Phase 6):

```sql
client_uuid       UUID        NOT NULL,                         -- khoá định danh phía client (SRS 3.3, mục 4)
updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),           -- thời điểm client gửi, đã kẹp: dùng cho quy tắc xung đột
deleted_at        TIMESTAMPTZ,                                  -- tombstone (SRS 6.3: xoá mềm)
sync_seq          BIGINT      NOT NULL DEFAULT nextval('sync_seq'),
server_updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
CONSTRAINT uq_<bảng>_client UNIQUE (user_id, client_uuid)
-- CREATE INDEX idx_<bảng>_sync ON <bảng> (user_id, sync_seq);
-- CREATE TRIGGER trg_<bảng>_sync BEFORE UPDATE ON <bảng> FOR EACH ROW EXECUTE FUNCTION touch_sync();
```

`updated_at` (giờ client, phục vụ xung đột) khác `server_updated_at` (giờ server, phục vụ con trỏ); đừng nhập hai cột làm một.

## Hợp đồng API

SYS có 12 endpoint HTTP (cộng một endpoint chỉ có ở profile `dev`) và một kênh WebSocket. Mọi phản hồi theo `ApiResponse` (D9 của AUTH); mã lỗi là chuỗi cố định để mobile xử lý theo mã. Tất cả cần Bearer đã xác minh email, trừ `GET /api/app/attributions` (công khai) và `/ws` (xác thực ở khung `CONNECT`).

| Endpoint | FR | Phase | Body / tham số chính | Kết quả và lỗi chính |
| --- | --- | --- | --- | --- |
| `GET /api/app/attributions` | FR-SYS-09 | 0 | — | 200 + danh sách nguồn dữ liệu bên thứ ba (tên, giấy phép, URL, câu ghi nguồn) và nhãn hiển thị cho `Food.source` |
| `POST /api/devices` | FR-SYS-01 | 1 | token, platform (`ANDROID`\|`IOS`), locale, appVersion | 200 + `id` thiết bị; đăng ký lại cùng token chỉ cập nhật `last_seen_at`; token đang thuộc user khác thì **chuyển chủ**; 400 `VALIDATION_ERROR` |
| `DELETE /api/devices/{id}` | FR-SYS-01 | 1 | — | 204, idempotent; không phải thiết bị của mình cũng trả 204 (không lộ sự tồn tại) |
| `GET /api/notifications` | FR-SYS-03 | 2 | `cursor`, `limit` (≤ 50), `unreadOnly` | 200 + `items`, `nextCursor`, `unreadCount` |
| `GET /api/notifications/unread-count` | FR-SYS-03 | 2 | — | 200 + `count` |
| `PUT /api/notifications/{id}/read` | FR-SYS-03 | 2 | — | 204; 404 `NOTIFICATION_NOT_FOUND` (kể cả của người khác) |
| `PUT /api/notifications/read-all` | FR-SYS-03 | 2 | — | 204 |
| `DELETE /api/notifications/{id}` | FR-SYS-03 | 2 | — | 204 (xoá mềm); 404 `NOTIFICATION_NOT_FOUND` |
| `GET /api/notification-settings` | FR-SYS-04 | 3 | — | 200 + cài đặt (tạo mặc định nếu chưa có) |
| `PUT /api/notification-settings` | FR-SYS-04 | 3 | các trường cài đặt (giờ dạng `HH:mm`) | 200 + cài đặt mới; 400 `VALIDATION_ERROR` kèm `details` theo trường |
| `POST /api/sync/push` | FR-SYS-05, 06 | 6 | `operations[]`: `entity`, `clientUuid`, `op` (`UPSERT`\|`DELETE`), `updatedAt`, `data` | 200 + `results[]` từng thao tác (`APPLIED`, `UNCHANGED`, `CONFLICT_SERVER_WINS`, `REJECTED` kèm mã lý do) + `serverTime`; 413 `SYNC_BATCH_TOO_LARGE`; 429 `TOO_MANY_REQUESTS` |
| `GET /api/sync/pull` | FR-SYS-05 | 6 | `cursor` (rỗng = từ đầu), `limit` (mặc định 500, tối đa 1000) | 200 + `changes[]`, `nextCursor`, `hasMore`, `serverTime`; 409 `FULL_RESYNC_REQUIRED` khi con trỏ quá hạn giữ tombstone |
| `POST /api/devices/test-push` | dev | 1 | — | Chỉ có ở profile `dev` (`@Profile("dev")`), gửi một push thử tới thiết bị của mình để nhóm mobile kiểm tra |
| WebSocket `/ws` (STOMP) | mục 8.2 | 7 | khung `CONNECT` kèm header `Authorization` | đích `/user/queue/notifications` |

Hai điểm cần báo nhóm mobile ngay khi chốt hợp đồng: thực thể đồng bộ ở v1 là `WATER_LOG`, `WEIGHT_LOG`, `FOOD_LOG`, `ACTIVITY_LOG` (đúng bốn nhóm SRS 4.8 nêu); và nhận diện ảnh bằng AI luôn cần mạng, hàng đợi ảnh nằm ở phía app, không đi qua `sync` (SRS 4.8).

## Phase 0 — Nền chung, cấu hình, ghi nguồn (≈ 1 ngày, xong ở ngày 1)

Mục tiêu: có thư viện, cấu hình, hợp đồng sự kiện và khung lỗi để các phase sau chỉ còn viết nghiệp vụ; đồng thời xong FR-SYS-09 vì nó nhỏ và là mức M.

- [ ] Chốt S1–S12 với nhóm; ghi kết quả vào mục “Quyết định” của tài liệu này. S3 cần câu trả lời của người làm mobile trước Phase 1.
- [ ] `build.gradle`: thêm thư viện gửi FCM (`firebase-admin`; chọn bản mới nhất, kiểm tra bằng `./gradlew dependencies` và khởi động ứng dụng vì thư viện này kéo Jackson 2 trong khi Spring Boot 4 dùng Jackson 3). Nếu xung đột không gỡ được, chuyển sang gọi FCM HTTP v1 bằng `RestClient` kèm thư viện xác thực Google; cổng `PushSender` giữ nguyên.
- [ ] `config/SchedulingConfig` với `@EnableScheduling` (bỏ qua nếu AUTH Phase 7 đã thêm; chỉ một lớp duy nhất).
- [ ] `@ConfigurationProperties` + `@Validated`: `PushProperties` (`app.push.enabled`, `credentials-path`), `NotificationProperties` (`retention-days` = 30, `reminder-daily-cap` = 6, `max-devices-per-user` = 10, `reminder-catch-up` = 10m), `SyncProperties` (`max-operations` = 200, `settle-delay` = 2s, `tombstone-retention` = 30d, `default-page` = 500, `max-page` = 1000, `max-future-skew` = 5m), `AttributionProperties`. Thêm khối `app.push`, `app.notification`, `app.sync` vào `application.yml`; giá trị đọc từ biến môi trường.
- [ ] `.env.example` và `docker-compose.yml` (khối `environment` của service `app`): `PUSH_ENABLED`, `FCM_CREDENTIALS_PATH`, `APP_NOTIFICATION_*`, `APP_SYNC_*`. Compose liệt kê từng biến nên thiếu dòng nào thì container không nhận. Không commit tệp service account FCM (NFR-SEC-08).
- [ ] `common/ErrorCode`: thêm `NOTIFICATION_NOT_FOUND` (404), `DEVICE_NOT_FOUND` (404, dùng nội bộ), `SYNC_BATCH_TOO_LARGE` (413), `FULL_RESYNC_REQUIRED` (409).
- [ ] `common/event`: ba bản ghi sự kiện ở mục Kiến trúc; `service/sys/UserActivityPort` và `NoopUserActivityPort` (`@ConditionalOnMissingBean`).
- [ ] Khung package `sys` rỗng (controller, service, impl, model, repository, dto) và `NotificationType` kèm nhóm (`REMINDER`: `WATER_REMINDER`, `MEAL_REMINDER`; `ALERT`: `CALORIE_OVER`; `SOCIAL`: `BADGE_EARNED`, `FRIEND_REQUEST`, `FRIEND_OVERTAKE`, `CHALLENGE_EXPIRING`, `CHALLENGE_COMPLETED`; `SYSTEM`). Nhóm GAME thêm loại mới ở đây khi cần.
- [ ] FR-SYS-09: `GET /api/app/attributions` công khai (thêm đường dẫn vào `PUBLIC_AUTH_ENDPOINTS`). Dữ liệu lấy từ cấu hình, gồm Open Food Facts với giấy phép ODbL, URL và câu ghi nguồn có thể cấu hình (nhóm đối chiếu câu chữ với hướng dẫn ghi nguồn của Open Food Facts trước khi phát hành), cùng nguồn NIN và FAO/INFOODS; kèm bảng nhãn hiển thị cho `Food.source` (`SYSTEM`, `NIN`, `OFF`, `USER`) bằng vi/en để món `source = OFF` hiện nhãn nguồn (FOODDATA dùng bảng này).
- [ ] Test: ngữ cảnh Spring khởi động khi `PUSH_ENABLED=false`; `GET /api/app/attributions` trả 200 không cần token và có mục ODbL; thiếu cấu hình bắt buộc thì khởi động lỗi rõ ràng.

**Hoàn thành khi:** `./gradlew clean build` xanh; ứng dụng khởi động trên DB trống; endpoint ghi nguồn gọi được không cần đăng nhập; QS11 có test xanh.

## Phase 1 — Token thiết bị và gửi push FCM (≈ 1,5 ngày, xong ở ngày 2,5)

Mục tiêu: mobile đăng ký/huỷ thiết bị; backend gửi được push tới mọi thiết bị của một người dùng và tự dọn token hỏng. Phase này bao phủ FR-SYS-01.

- [ ] `V2__device_tokens.sql` (mục Mô hình dữ liệu); entity `DeviceToken` (`@Getter`/`@Setter`, `equals`/`hashCode` theo id, `Instant`); `DeviceTokenRepository` (`findByToken`, `findByUserId`, đếm theo user, xoá theo id và user).
- [ ] `DeviceService.register(userId, request)`: chuẩn hoá và cắt khoảng trắng token; nếu token đã có thì cập nhật `last_seen_at`, `locale`, `app_version` và **chuyển `user_id` sang người đang gọi** (máy dùng chung: người trước đăng xuất rồi người sau đăng nhập không được nhận thông báo của người trước); vượt `max-devices-per-user` thì gỡ thiết bị có `last_seen_at` cũ nhất. `unregister(userId, id)` idempotent.
- [ ] Cổng `PushSender` (`send(PushMessage, tokens)` → `PushResult` theo từng token: `SENT`, `INVALID_TOKEN`, `RETRYABLE_FAILURE`); `LoggingPushSender` (mặc định, chỉ ghi log loại thông báo và 6 ký tự cuối token); `FcmPushSender` (`@ConditionalOnProperty app.push.enabled=true`, khởi tạo từ `FCM_CREDENTIALS_PATH`). Thông điệp gồm `notification` (tiêu đề, nội dung chung chung theo S12) và `data` (mọi giá trị là chuỗi, chỉ `type`, `notificationId`).
- [ ] Xử lý lỗi: token không còn hợp lệ (`UNREGISTERED`, `INVALID_ARGUMENT`) thì xoá khỏi `device_tokens`; lỗi tạm (`UNAVAILABLE`, `INTERNAL`, quota) thử lại tối đa 3 lần với backoff luỹ tiến; hết lần thử chỉ ghi log, không làm hỏng luồng gọi.
- [ ] `PushDispatcher` gửi bất đồng bộ trên `pushTaskExecutor` (thêm vào `AsyncConfig` cạnh `mailTaskExecutor`), nhận danh sách thiết bị theo user và gọi `PushSender`.
- [ ] `DeviceController`: `POST /api/devices` (200 + `id`), `DELETE /api/devices/{id}` (204), và `POST /api/devices/test-push` chỉ ở profile `dev`. User id lấy từ `CurrentUser`, không nhận từ client.
- [ ] Báo nhóm mobile: gọi `POST /api/devices` mỗi lần mở app sau khi có token (ít nhất mỗi khi token đổi), `DELETE /api/devices/{id}` **trước khi** đăng xuất; xin quyền thông báo kèm lời giải thích; Android 13+ cần quyền `POST_NOTIFICATIONS`; kênh thông báo riêng cho nhắc nhở và cảnh báo; xác nhận S3.
- [ ] Test: đăng ký hai lần cùng token chỉ có một dòng; token chuyển chủ khi người khác đăng ký; vượt trần thiết bị gỡ thiết bị cũ nhất; thiếu Bearer → 401, chưa xác minh email → 403 `EMAIL_NOT_VERIFIED`; xoá thiết bị của người khác không ảnh hưởng và vẫn 204; `PushSender` giả trả `INVALID_TOKEN` thì token bị xoá; `RETRYABLE_FAILURE` được thử lại đủ số lần; log không chứa token đầy đủ; xoá tài khoản kéo theo xoá `device_tokens` (bài test cascade của AUTH).
- [ ] Thử tay: `PUSH_ENABLED=true` với dự án Firebase thật, gọi `test-push` trên thiết bị Android thật (và iOS nếu có điều kiện, xem R4 của SRS).

**Hoàn thành khi:** QS1 và QS2 có test xanh; push thử tới được thiết bị Android thật; `./gradlew test` xanh khi Docker đang chạy.

## Phase 2 — Lõi thông báo và trung tâm thông báo (≈ 2 ngày, xong ở ngày 4,5)

Mục tiêu: một cổng `notify` duy nhất và trung tâm thông báo dùng được. Đây là cột mốc M1: từ đây GAME, NUTRI và các module khác gọi được `NotificationService`, mobile hiển thị danh sách thông báo. Phase này bao phủ FR-SYS-03.

- [ ] `V3__notifications.sql`; entity `Notification` (`data` là `JSONB`, ánh xạ bằng `@JdbcTypeCode(SqlTypes.JSON)`); `NotificationRepository` có truy vấn phân trang theo con trỏ `(created_at, id)`, đếm chưa đọc, đánh dấu đã đọc hàng loạt, xoá mềm. Mọi truy vấn đều có `user_id = :me` và `deleted_at IS NULL`.
- [ ] `NotificationService` (interface ở `service/sys`, cài đặt ở `service/sys/impl`) theo mục Kiến trúc. Phase này `NotificationPolicy` còn là bản cho phép tất cả; Phase 3 gắn cài đặt thật. Ghi bằng `INSERT … ON CONFLICT (user_id, dedupe_key) DO NOTHING` để trả `DUPLICATE` mà không ném lỗi và không làm hỏng transaction của người gọi.
- [ ] Hậu xử lý sau commit: `@TransactionalEventListener(AFTER_COMMIT)` gọi `PushDispatcher` (Phase 1) bất đồng bộ; cập nhật `pushed_at`. Trình nghe nào **ghi DB** sau AFTER_COMMIT phải mở transaction mới (`REQUIRES_NEW`), nếu không thay đổi bị bỏ qua mà không báo lỗi.
- [ ] `NotificationController`: `GET /api/notifications` (con trỏ không trong suốt, `limit` ≤ 50, `unreadOnly`), `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all`, `DELETE /api/notifications/{id}`. Thông báo của người khác hoặc đã xoá trả 404 `NOTIFICATION_NOT_FOUND` (chống IDOR, NFR-SEC-04).
- [ ] Tác vụ dọn dẹp hằng ngày xoá cứng thông báo cũ hơn `retention-days` (gộp vào `SysMaintenanceJob` ở Phase 8, tạm viết riêng nếu cần).
- [ ] Báo nhóm mobile: huy hiệu số chưa đọc lấy từ `unread-count`, danh sách cuộn vô hạn theo `nextCursor`; khi nhận push thì mở màn hình theo `data.type` và `data.notificationId`.
- [ ] Test: gọi `notify` hai lần cùng `dedupeKey` chỉ ra một dòng và một lần gọi `PushSender`; xoá mềm rồi `notify` lại với cùng khoá vẫn là `DUPLICATE`; hai người dùng không thấy thông báo của nhau (cả danh sách, đã đọc, xoá, đếm); `read-all` chỉ động tới người gọi; phân trang theo con trỏ ổn định khi có thông báo mới chen vào giữa các lần gọi; thông báo rollback cùng transaction của người gọi thì không có push; trình nghe sau commit không chạy khi transaction rollback.

**Hoàn thành khi:** QS3 có test xanh; module khác chỉ cần một dòng `notificationService.notify(...)` là có thông báo; mobile xem được trung tâm thông báo trên dev.

## Phase 3 — Cài đặt thông báo, không làm phiền, mẫu vi/en (≈ 1,5 ngày, xong ở ngày 6)

Mục tiêu: người dùng tắt/bật từng loại, đặt khung giờ và chế độ không làm phiền; nội dung thông báo có tiếng Việt và sẵn khung tiếng Anh. Phase này bao phủ FR-SYS-04 và phần phía server của FR-SYS-07.

- [ ] `V4__notification_settings.sql`; entity `NotificationSettings`, repository; `NotificationSettingsService.getOrCreate` (tạo dòng mặc định khi chưa có, cạnh tranh tạo đồng thời xử lý bằng khoá chính).
- [ ] `GET` và `PUT /api/notification-settings`; kiểm tra: giờ dạng `HH:mm`, `water_end_time > water_start_time`, `interval` 30–720, ngưỡng calo 100–200, DND được qua nửa đêm (`start > end`) nhưng `start = end` không hợp lệ; lỗi trả `VALIDATION_ERROR` kèm `details` theo trường.
- [ ] `NotificationPolicy.evaluate(settings, type, localNow)` thay bản cho-phép-tất-cả: loại bị tắt → chặn cả trung tâm và push; `push_enabled = false` hoặc đang trong DND → chỉ chặn push (mục vẫn vào trung tâm, S4); riêng `REMINDER` gặp DND thì bỏ hẳn (S6). Giờ địa phương tính bằng `users.timezone`, múi giờ không hợp lệ thì rơi về `Asia/Ho_Chi_Minh`.
- [ ] i18n phía server: `messages/notifications_vi.properties` và `_en.properties` (`ResourceBundleMessageSource`, UTF-8); `NotificationMessageFactory.render(type, locale, params)` trả hai biến thể: **chi tiết** cho mục trung tâm và **chung chung** cho push (S12, ví dụ push “Bạn đã vượt mục tiêu calo hôm nay”, trung tâm có số liệu). Ngôn ngữ lấy từ `notification_settings.language` (mặc định `vi`), rơi về `vi` nếu khoá thiếu.
- [ ] FR-SYS-08 (giao diện sáng/tối): không có việc ở backend; ghi vào tài liệu giao diện để nhóm mobile lưu lựa chọn cục bộ.
- [ ] Test: DND qua nửa đêm với `Clock` cố định tại 21:59, 22:00, 23:30, 06:59, 07:00; loại bị tắt không tạo mục và không gọi push; DND tạo mục nhưng không gọi push; cài đặt của người này không đọc/sửa được bởi người kia; đầu vào sai trả 400 theo trường; bài test duyệt toàn bộ `NotificationType` và khẳng định mỗi loại có đủ khoá ở cả hai tệp vi/en, cả biến thể push; không biến thể push nào chứa tham số số liệu.

**Hoàn thành khi:** QS4, QS9 (phần mẫu) và QS10 có test xanh; đổi `language` sang `en` thì thông báo mới ra tiếng Anh.

## Phase 4 — Cảnh báo vượt calo (≈ 1 ngày, xong ở ngày 7)

Mục tiêu: tổng calo nạp trong ngày vượt ngưỡng thì người dùng nhận đúng một thông báo cho mỗi mức cảnh báo. Phase này bao phủ phần cảnh báo của FR-SYS-02 và AC2 của SRS mục 4.8.

- [ ] `CalorieAlertListener` nghe `DailySummaryChangedEvent` bằng `@TransactionalEventListener(AFTER_COMMIT)` kèm `@Transactional(REQUIRES_NEW)`.
- [ ] Quy tắc: bỏ qua nếu `calorieGoal ≤ 0` hoặc ngày của sự kiện khác “hôm nay” theo múi giờ người dùng (đồng bộ log cũ hoặc sửa ngày trước không được báo); bỏ qua khi `calorie_alert_enabled = false`; điều kiện `caloriesIn > calorieGoal × ngưỡng/100`; khoá chống trùng `CALORIE_OVER:{yyyy-MM-dd}:{ngưỡng}` nên mỗi ngày mỗi mức chỉ một lần, kể cả khi người dùng xoá log rồi nạp lại. Mục trung tâm ghi số liệu cụ thể, push chỉ nói chung chung (S12).
- [ ] Hỏi nhóm NUTRI: sự kiện phải phát **sau commit** của giao dịch ghi log/sửa/xoá và sau khi `DailySummary` đã cập nhật; `calorieGoal` lấy từ mục tiêu hiện hành. Đồng bộ ngoại tuyến cũng phải đi qua dịch vụ của NUTRI để phát sự kiện (Phase 6, mục 6.5).
- [ ] Test (phát sự kiện giả bằng `ApplicationEventPublisher`): ba sự kiện liên tiếp vượt ngưỡng chỉ ra một thông báo và một lần gọi push (AC2); dưới ngưỡng không báo; sau ngày mới báo lại; sự kiện cho ngày hôm qua không báo; hai luồng cùng vượt ngưỡng một lúc vẫn chỉ một thông báo; nâng ngưỡng 120% thì mức 100% không còn được tính là mức mới; ngày được tính theo múi giờ người dùng (người ở `Asia/Ho_Chi_Minh` lúc 00:30 giờ địa phương là ngày mới dù UTC vẫn là ngày cũ).

**Hoàn thành khi:** AC2 của mục 4.8 SRS có test xanh với sự kiện giả; checklist hợp đồng gửi cho chủ module NUTRI; khi NUTRI cài FR-NUT-01 thì chạy lại luồng thật “ghi log → nhận đúng một thông báo”.

## Phase 5 — Nhắc nhở thông minh (≈ 2 ngày, xong ở ngày 9)

Mục tiêu: nhắc uống nước và nhắc ghi bữa ăn đúng giờ địa phương, không nhắc thừa, không vượt trần. Đây là cột mốc hoàn thiện FR-SYS-02. Làm theo thứ tự 5.1 → 5.2 → 5.3; 5.3 có thể cắt.

### 5.1 Khung bộ lập lịch và nhắc nước (≈ 1 ngày)

- [ ] `ReminderScheduler` với `@Scheduled(fixedDelay = 60s)` gọi `ReminderEngine.run(Instant now)` (nhận `Clock`); mỗi người dùng xử lý trong `try/catch` riêng để một lỗi không dừng cả lượt.
- [ ] Chọn ứng viên theo trang (200 dòng/lượt): người dùng `ACTIVE`, `email_verified = true`, có dòng cài đặt, bật ít nhất một loại nhắc và có **ít nhất một thiết bị** (không có thiết bị thì không tạo mục trung tâm, vì nhắc không có push chỉ là rác).
- [ ] Tính khe giờ nhắc nước từ `water_start_time`, `water_end_time`, `water_interval_minutes` theo giờ địa phương của người dùng; một khe “đến hạn” khi `now` nằm trong `[khe, khe + reminder-catch-up]` (mặc định 10 phút) để lượt bị trễ hoặc khởi động lại vẫn bắn, quá cửa sổ thì bỏ, không bù. Khoá chống trùng `WATER_REMINDER:{date}:{HH:mm}`.
- [ ] Quy tắc bỏ qua: gặp DND (S6); lần uống nước gần nhất (`UserActivityPort.lastWaterLogAt`) cách chưa tới nửa `water_interval_minutes`; đã chạm trần nhắc nhở trong ngày (đếm thông báo nhóm `REMINDER` được tạo trong ngày địa phương, `reminder-daily-cap` = 6).
- [ ] Test (`Clock` cố định): khe tính đúng cho cửa sổ mặc định (10:00, 14:00, 18:00) và cửa sổ tuỳ chỉnh; cùng một khoảnh khắc ở hai múi giờ cho kết quả khác nhau; cửa sổ catch-up; khởi động lại không bắn lại; chạy hai `ReminderEngine` cùng lúc cho một khe chỉ ra một thông báo; người có ba thiết bị nhận một mục trung tâm và ba lần gửi; không thiết bị thì bỏ qua; loại bị tắt thì bỏ qua; vừa uống nước thì bỏ qua; chạm trần thì bỏ qua; một người lỗi không ảnh hưởng người sau.

### 5.2 Nhắc ghi bữa ăn theo giờ cố định (≈ 0,5 ngày)

- [ ] Giờ nhắc mặc định (cấu hình được): sáng 08:30, trưa 12:30, tối 19:30; giờ do người dùng đặt (`meal_*_time`) thắng. Khoá `MEAL_REMINDER:{date}:{MEAL}`. Chỉ nhắc khi `UserActivityPort.hasMealLog` trả `false`, tức là chưa có log của buổi đó; bữa phụ không nhắc.
- [ ] Cùng quy tắc DND, trần và thiết bị như 5.1. Mặc định 3 nhắc ăn + 3 nhắc nước đúng bằng trần 6, nên trần chỉ cắt khi người dùng tự thêm khe.
- [ ] Test: có log bữa trưa lúc 12:00 thì không nhắc 12:30; chưa có log thì nhắc; chỉ nhắc một lần mỗi buổi mỗi ngày; giờ tuỳ chỉnh ghi đè mặc định.

### 5.3 Giờ thói quen (≈ 0,5 ngày, có thể cắt)

- [ ] Từ `UserActivityPort.recentMealLogTimes` (14 ngày gần nhất), nếu có ≥ 5 mẫu thì giờ nhắc = trung vị giờ ghi log + 30 phút, kẹp trong 05:00–22:00; ít mẫu hơn thì dùng mặc định. Tính một lần mỗi người mỗi ngày và lưu đệm (bảng nhỏ `reminder_habits` ở migration kế tiếp, hoặc cột trong `notification_settings`); không truy vấn lịch sử ở mỗi lượt một phút.
- [ ] Test: ≥ 5 mẫu dùng trung vị, < 5 mẫu dùng mặc định; giờ kẹp biên; giờ người dùng đặt vẫn thắng.

**Hoàn thành khi:** QS5 và QS6 có test xanh; đo một lượt cho 100 người dùng giả < 1 giây; với `Noop` port, nhắc nước vẫn chạy còn nhắc bữa ăn dùng mặc định (nhắc luôn khi chưa biết đã ăn).

## Phase 6 — Đồng bộ ngoại tuyến (≈ 3 ngày, xong ở ngày 12)

Mục tiêu: dữ liệu ghi khi mất mạng được đẩy lên đúng một lần, kéo về được trên thiết bị khác, và xung đột được giải quyết theo một quy tắc duy nhất. Đây là hạng mục rủi ro cao nhất; cột mốc M2 đạt ở cuối phase. Phase này bao phủ FR-SYS-05, FR-SYS-06 và AC1 của SRS mục 4.8.

### 6.1 Hạ tầng và khung (≈ 0,75 ngày)

- [ ] `V5__sync_infrastructure.sql` (sequence và hàm `touch_sync`); soạn tài liệu “mẫu cột bắt buộc” ở mục Mô hình dữ liệu và gửi chủ NUTRI/ACT **trước khi** họ viết migration bảng log, vì thêm cột sau tốn công hơn.
- [ ] Khung: `SyncEntityType` (`WATER_LOG`, `WEIGHT_LOG`, `FOOD_LOG`, `ACTIVITY_LOG`), `SyncOperation`, `SyncApplyResult`, `SyncChange` và SPI:

```java
public interface SyncEntityHandler {
  SyncEntityType type();
  SyncApplyResult apply(UUID userId, SyncOperation op);          // idempotent theo (userId, clientUuid); gọi qua dịch vụ nghiệp vụ của module
  List<SyncChange> changesSince(UUID userId, long sinceSeq, Instant settledBefore, int limit);
}
```

- [ ] `SyncRegistry` gom các bean `SyncEntityHandler` theo `type()`; thực thể chưa có handler trả `REJECTED` với lý do `UNKNOWN_ENTITY`, không ném lỗi cho cả lô.

### 6.2 Đẩy dữ liệu lên (≈ 1 ngày)

- [ ] `POST /api/sync/push`: lô quá `max-operations` (200) hoặc thân yêu cầu quá 1 MB trả 413 `SYNC_BATCH_TOO_LARGE`; trong lô, các thao tác cùng `clientUuid` xử lý theo thứ tự `updatedAt` tăng dần.
- [ ] Mỗi thao tác chạy trong **transaction riêng** (`TransactionTemplate`), một thao tác lỗi không làm hỏng cả lô; kết quả từng thao tác `APPLIED`, `UNCHANGED` (đã áp dụng từ trước, gửi lại cùng nội dung), `CONFLICT_SERVER_WINS` (kèm bản của server để client ghi đè cục bộ), `REJECTED` (kèm mã lý do: `VALIDATION_ERROR`, `FORBIDDEN_REFERENCE`, `UNKNOWN_ENTITY`).
- [ ] Quy tắc S10: `updatedAt` của client lớn hơn `now + max-future-skew` bị kẹp về `now`; bản ghi hiện có có `updated_at` lớn hơn hoặc bằng thì server thắng. `DELETE` ghi tombstone (`deleted_at`) kể cả khi chưa có bản ghi, để `UPSERT` cũ đến muộn không làm sống lại dữ liệu đã xoá.
- [ ] Tham chiếu chéo phải kiểm tra quyền sở hữu (ví dụ `FOOD_LOG` trỏ tới `food_id` của món tuỳ chỉnh thuộc người khác) và trả `REJECTED`/`FORBIDDEN_REFERENCE`.

### 6.3 Kéo dữ liệu về (≈ 0,75 ngày)

- [ ] `GET /api/sync/pull`: con trỏ không trong suốt dạng base64url của `v1:<sync_seq>:<epochGiây phát hành>`; mỗi handler trả các thay đổi có `sync_seq > con trỏ` và `server_updated_at <= now - settle-delay` (S9); SYS trộn kết quả các handler theo `sync_seq` toàn cục, cắt theo `limit`, đặt `nextCursor` là `sync_seq` cuối và `hasMore`.
- [ ] Con trỏ phát hành quá `tombstone-retention` (30 ngày) trả 409 `FULL_RESYNC_REQUIRED`; mobile phải tải lại bằng các API `GET` thường và bắt đầu con trỏ mới. Không cần bảng trạng thái vì thời điểm phát hành nằm ngay trong con trỏ.
- [ ] Công việc dọn dẹp tombstone cũ hơn `tombstone-retention` do handler của từng module phụ trách, SYS chỉ gọi qua `SysMaintenanceJob` (Phase 8).

### 6.4 Bộ test hợp đồng và handler giả (≈ 0,5 ngày)

- [ ] Handler giả trong bộ nhớ (chỉ ở test) để chứng minh khung: lô nhiều thao tác, `UNCHANGED` khi gửi lại, thua xung đột, tombstone chặn `UPSERT` cũ, kẹp giờ tương lai, con trỏ liên tục không bỏ sót hay lặp vô hạn, hết hạn con trỏ, quá 200 thao tác, thao tác lỗi không phá lô, hai thiết bị cùng đẩy một `clientUuid` chỉ có một bản ghi.
- [ ] Lớp trừu tượng `SyncHandlerContractTest`: module sở hữu bảng kế thừa và chạy cùng bộ kiểm tra cho handler thật (idempotent theo `clientUuid`, xung đột theo `updated_at`, tombstone, thứ tự `sync_seq`, từ chối tham chiếu chéo người dùng, đi qua dịch vụ nghiệp vụ để `DailySummary` và sự kiện được cập nhật).

### 6.5 Cắm handler thật (làm khi module sở hữu bảng sẵn sàng)

- [ ] `WATER_LOG` và `FOOD_LOG` do NUTRI cài (FR-NUT-11, FR-NUT-01); `WEIGHT_LOG` và `ACTIVITY_LOG` do ACT cài (FR-ACT-04, FR-ACT-01). Mỗi handler gọi **cùng dịch vụ** mà REST dùng để `DailySummary` được cập nhật và các sự kiện của Phase 4–5 được phát; ghi thẳng vào repository sẽ làm cảnh báo calo và nhắc nhở im lặng sai.
- [ ] Ảnh của `FOOD_LOG` không đi qua đồng bộ: mobile tải ảnh lên bằng luồng nhận diện/`POST /food-logs/recognize` khi có mạng rồi mới gửi log kèm khoá ảnh; log ngoại tuyến không có ảnh dùng nguồn `SEARCH`/`CUSTOM`.
- [ ] Chạy bộ test hợp đồng cho từng handler thật và kịch bản AC1 của SRS: “tắt mạng → ghi 3 log nước → bật mạng: cả 3 log xuất hiện trên server, không trùng”, kể cả gửi lô hai lần để mô phỏng mất phản hồi giữa chừng.

### 6.6 Báo nhóm mobile

- [ ] Hàng đợi gửi (outbox) lưu bền trên máy (SQLite/MMKV), mỗi mục có `clientUuid` UUID v4 sinh một lần lúc ghi; gửi khi có mạng trở lại và khi app vào foreground; thử lại với backoff từ 1 giây đến 60 giây; **không bỏ mục khỏi hàng đợi cho tới khi nhận kết quả rõ ràng** (`APPLIED`/`UNCHANGED`/`CONFLICT_SERVER_WINS`/`REJECTED`); mục `REJECTED` hiển thị cho người dùng thay vì thử lại mãi.
- [ ] Lưu `nextCursor` sau mỗi lần kéo thành công; nhận 409 `FULL_RESYNC_REQUIRED` thì xoá bộ nhớ đệm cục bộ và tải lại.
- [ ] Giới hạn đã biết ở v1: tạo **món tuỳ chỉnh** khi ngoại tuyến chưa nằm trong phạm vi (món cần tồn tại trên server trước khi log tham chiếu tới nó); nếu cần, thêm thực thể `CUSTOM_FOOD` vào danh sách đồng bộ.

**Hoàn thành khi:** QS7 và QS8 có test xanh; AC1 của mục 4.8 SRS có test xanh với handler nước thật (hoặc ghi rõ “chờ NUTRI” nếu NUTRI chưa kịp); bộ test hợp đồng sẵn trong repo cho module kia dùng.

## Phase 7 — Thông báo trực tiếp qua WebSocket (≈ 1,5 ngày, xong ở ngày 13,5)

Mục tiêu: người dùng đang mở app thấy thông báo mới ngay mà không chờ push hay tải lại. Đây là phần **S** (mục 8.2 SRS nêu kênh `/ws` cho thông báo trực tiếp); nếu thiếu thời gian thì cắt phase này, mobile dùng `unread-count` khi app vào foreground cùng push là đủ. Hạ tầng `/ws` cũng là nền cho bảng xếp hạng (GAME) và luồng chatbot (CHAT), nên chốt ai sở hữu ở Q6.

- [ ] `config/WebSocketConfig`: STOMP trên `/ws` (`registerStompEndpoints`), broker đơn giản `/queue`, tiền tố người dùng `/user`, heartbeat 10 giây hai chiều, `setAllowedOriginPatterns` theo cấu hình (app native không cần CORS).
- [ ] `SecurityConfig`: cho phép bắt tay `/ws/**` không cần Bearer (khung `CONNECT` mới là nơi xác thực), thêm vào danh sách công khai.
- [ ] `StompAuthChannelInterceptor`: ở `CONNECT`, đọc header `Authorization`, giải mã bằng `JwtDecoder` chính, kiểm tra `email_verified = true`, đặt principal là user id; thiếu hoặc sai thì từ chối khung. Chặn `SUBSCRIBE` tới mọi đích ngoài `/user/queue/**` (đích của GAME/CHAT thêm khi cần); tối đa 5 phiên mỗi người; ngắt phiên khi access token hết hạn (15 phút) và mobile kết nối lại bằng token mới.
- [ ] `NotificationPublisher`: sau commit gửi `{ "type": "NOTIFICATION_CREATED", "id", "unreadCount" }` tới `/user/queue/notifications`; chỉ id và đếm, không số liệu sức khoẻ (S12), mobile gọi `GET /api/notifications` để lấy nội dung.
- [ ] Test (dùng `WebSocketStompClient`): `CONNECT` không token bị từ chối; token hết hạn hoặc `email_verified = false` bị từ chối; hai người dùng chỉ nhận thông báo của mình; nhận được khung sau khi `notify`; đăng ký đích lạ bị từ chối; phiên thứ sáu của cùng người dùng bị từ chối; ngắt khi token hết hạn.
- [ ] Giới hạn đã biết: broker trong bộ nhớ chỉ đúng với một instance; chạy nhiều instance cần broker ngoài hoặc relay Redis, nằm ngoài phạm vi đồ án.

**Hoàn thành khi:** QS12 có test xanh; thử tay với mobile dev: tạo thông báo từ backend thì chuông cập nhật ngay.

## Phase 8 — Giới hạn tần suất, dọn dẹp, tài liệu, nghiệm thu (≈ 1,5 ngày, xong ở ngày 15)

Mục tiêu: SYS chịu được lạm dụng và lỗi vận hành, đáp ứng NFR-PERF-01, NFR-SEC-04/05/06/08 và mục 9.7 SRS, rồi chứng minh mọi AC đạt. **Điều kiện tiên quyết:** `RateLimiter` của AUTH Phase 7 đã có trong repo; nếu chưa, hoàn thành phần đó trước hoặc tạm thời làm bản Redis tối thiểu dùng chung.

- [ ] Giới hạn tần suất (dùng `RateLimiter` của AUTH, 429 kèm `Retry-After`): `POST /api/devices` 20/giờ/user, `POST /api/sync/push` 30/phút/user, `GET /api/sync/pull` 60/phút/user, `GET /api/notifications` 120/phút/user, `PUT /api/notification-settings` 30/phút/user, `/ws` 10 kết nối mới/phút/IP. Giới hạn theo user lấy từ token, không từ nội dung yêu cầu.
- [ ] Nhật ký: mỗi lần gửi push ghi một dòng có `userId`, loại, kết quả (`SENT`/`INVALID_TOKEN`/`RETRY`) và độ trễ, kèm `request_id` (bộ lọc của AUTH Phase 7); không ghi nội dung thông báo, `data`, token đầy đủ hay số liệu sức khoẻ.
- [ ] `SysMaintenanceJob` `@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")`: xoá cứng thông báo cũ hơn `retention-days`, thiết bị có `last_seen_at` cũ hơn 60 ngày, và gọi dọn tombstone của các handler.
- [ ] Rà soát bảo mật: kiểm thử IDOR cho `notifications`, `devices`, `notification-settings`; chuyển chủ token thiết bị không lộ ai từng sở hữu; thân yêu cầu `sync/push` có chuỗi lạ (HTML, SQL) không gây lỗi; tệp service account FCM nằm ngoài Git (`git ls-files` không chứa), không có bí mật trong YAML.
- [ ] Hiệu năng: k6 với ~100 người dùng đồng thời gọi `GET /api/notifications` p95 ≤ 500 ms (NFR-PERF-01); `POST /api/sync/push` lô 100 thao tác; lượt `ReminderEngine` cho 100 người dùng.
- [ ] Tài liệu: `docs/api-specs/sys.yaml` (OpenAPI cùng kiểu `auth.yaml`); `docs/tests/sys-test-cases.xlsx` cùng kiểu `auth-test-cases.xlsx`; README (biến `PUSH_ENABLED`, `FCM_CREDENTIALS_PATH`, `APP_NOTIFICATION_*`, `APP_SYNC_*`, endpoint SYS, cách dùng `test-push`); SRS (mã lỗi mới `NOTIFICATION_NOT_FOUND`, `SYNC_BATCH_TOO_LARGE`, `FULL_RESYNC_REQUIRED`; chốt S1–S12; bổ sung thực thể `NotificationSettings`, `DeviceToken` theo thiết kế; hình dạng cuối của `sync/push` và `sync/pull` ở mục 8.2; endpoint `GET /api/app/attributions`).
- [ ] Thử trên thiết bị thật cùng nhóm mobile: đăng ký thiết bị và nhận push (Android, và iOS nếu có), bật DND, nhận nhắc nước đúng giờ địa phương, vượt calo nhận đúng một thông báo, tắt mạng ghi 3 log nước rồi bật mạng, đổi ngôn ngữ thông báo.

**Hoàn thành khi:** mọi dòng trong bảng truy vết bên dưới xanh (dòng phụ thuộc module khác được ghi rõ trạng thái “đã chạy với module thật” hoặc “đã chạy với giả lập, chờ module”); `./gradlew check` xanh; nhóm mobile xác nhận bốn luồng: push, trung tâm thông báo, nhắc nhở và đồng bộ ngoại tuyến chạy trên thiết bị thật.

## Truy vết tiêu chí chấp nhận

Mục 4.8 của SRS nêu hai tiêu chí chấp nhận (AC1, AC2) cho SYS. Mình bổ sung QS1–QS13 từ quy tắc nghiệp vụ và các FR để mỗi yêu cầu có ít nhất một bài test.

| Mã | Yêu cầu | Phase | Bài test cần có |
| --- | --- | --- | --- |
| AC1 | Tắt mạng → ghi 3 log nước → bật mạng: 3 log trên server, không trùng | 6 | Đẩy lô 3 `WATER_LOG`, gửi lại lô lần hai: vẫn 3 dòng; `UNCHANGED` ở lần hai |
| AC2 | Vượt calo ngày → nhận đúng một thông báo cảnh báo | 4 | Ba sự kiện vượt ngưỡng → một dòng `CALORIE_OVER`, một lần gọi push; hai luồng đồng thời vẫn một dòng |
| QS1 | Đăng ký/huỷ token thiết bị; token chuyển chủ khi người khác đăng ký (FR-SYS-01) | 1 | Đăng ký lặp một dòng; người B đăng ký token của A thì token thuộc B; `DELETE` idempotent |
| QS2 | Push tới mọi thiết bị của người dùng, token hỏng bị gỡ (FR-SYS-01) | 1 | `INVALID_TOKEN` xoá token; `RETRYABLE_FAILURE` được thử lại; ba thiết bị nhận ba lần gửi |
| QS3 | Trung tâm thông báo: danh sách, đã đọc, xoá; cô lập giữa người dùng (FR-SYS-03) | 2 | Truy cập chéo → 404; xoá mềm giữ khoá chống trùng; phân trang con trỏ ổn định |
| QS4 | Tuỳ chỉnh loại và khung giờ, không làm phiền (FR-SYS-04) | 3 | DND qua nửa đêm ở các giờ biên; loại bị tắt chặn cả trung tâm lẫn push; đầu vào sai → 400 theo trường |
| QS5 | Không nhắc nếu vừa thực hiện hành động; trần ≤ 6/ngày (FR-SYS-02) | 5 | Vừa uống nước thì bỏ qua; đã có log bữa ăn thì bỏ qua; chạm trần thì bỏ qua |
| QS6 | Nhắc theo giờ địa phương của người dùng; không bắn trùng khi nhiều instance (FR-SYS-02) | 5 | Hai múi giờ, cửa sổ catch-up, hai engine đồng thời một thông báo |
| QS7 | Đồng bộ idempotent, lỗi một thao tác không phá cả lô (FR-SYS-05) | 6 | Gửi lại lô; thao tác lỗi lẫn thao tác tốt; 413 khi quá 200 thao tác |
| QS8 | Xung đột: `updated_at` mới nhất thắng, tombstone chặn bản cũ (FR-SYS-06) | 6 | Thua xung đột trả `CONFLICT_SERVER_WINS`; `UPSERT` cũ sau `DELETE` không sống lại; kẹp giờ tương lai |
| QS9 | Push không chứa số liệu sức khoẻ chi tiết (S12, SRS 9.3) | 3, 8 | Duyệt mọi biến thể push: không tham số số liệu; `data` chỉ có `type`, `notificationId` |
| QS10 | Mẫu thông báo đủ khoá tiếng Việt và tiếng Anh (FR-SYS-07) | 3 | Duyệt toàn bộ `NotificationType` với cả hai ngôn ngữ |
| QS11 | Ghi nguồn và giấy phép Open Food Facts (FR-SYS-09) | 0 | `GET /api/app/attributions` công khai, có mục ODbL và nhãn `source = OFF` |
| QS12 | Kết nối thời gian thực phải xác thực (mục 8.2 SRS) | 7 | `CONNECT` không token, token hết hạn, chưa xác minh email đều bị từ chối; không nhận thông báo của người khác |
| QS13 | Giới hạn tần suất và chống lạm dụng (NFR-SEC-06) | 8 | Vượt ngưỡng → 429 kèm `Retry-After`; hết cửa sổ dùng lại được |

## Phụ thuộc vào module khác

SYS chạy được trước khi các module khác hoàn tất nhờ sự kiện giả và handler giả, nhưng **nghiệm thu cuối** cần các phần sau từ module sở hữu. Gửi bảng này cho chủ từng module ngay khi chốt S8–S10.

| Module | Việc cần làm | Cần cho | Khi nào |
| --- | --- | --- | --- |
| NUTRI | Phát `DailySummaryChangedEvent`, `WaterLoggedEvent`, `MealLoggedEvent` sau commit; cài `UserActivityPort` | Phase 4, 5 | Khi cài FR-NUT-01, FR-NUT-11 |
| NUTRI, ACT | Bảng log có đủ cột mẫu đồng bộ (`client_uuid`, `updated_at`, `deleted_at`, `sync_seq`, `server_updated_at`, trigger); cài `SyncEntityHandler` đi qua dịch vụ nghiệp vụ; kế thừa `SyncHandlerContractTest` | Phase 6.5 | Cùng migration bảng log |
| PROFILE | `calorieGoal` hiện hành có sẵn để NUTRI đưa vào sự kiện | Phase 4 | Khi cài FR-PRO-05 |
| FOODDATA | Dùng bảng nhãn `source` của `GET /api/app/attributions` để hiển thị nhãn nguồn món | FR-SYS-09 | Khi cài FR-FD-07b |
| GAME | Gọi `NotificationService.notify` cho huy hiệu, bạn bè vượt hạng, thử thách sắp hết hạn (FR-GAM-06); thêm loại vào `NotificationType`; dùng `/ws` | Sau Phase 2, 7 | Khi cài FR-GAM-06 |
| AUTH | Hoàn thành Phase 7: `RateLimiter`, bộ lọc `request_id`, `@EnableScheduling` | Phase 0, 8 | Trước Phase 8 của SYS |

## Rủi ro và lưu ý kỹ thuật

Rủi ro lớn nhất là đồng bộ ngoại tuyến (Phase 6) vì nó đòi mọi module log tuân theo cùng một mẫu cột, và gửi FCM tới iOS còn phụ thuộc quyết định S3. Nếu phải cắt phạm vi, giữ các yêu cầu mức M (FR-SYS-01, 02, 03, 05, 09) và hoãn theo thứ tự: Phase 7 (WebSocket), Phase 5.3 (giờ thói quen), FR-SYS-07 (phía server), rồi tới FR-SYS-06 ở mức tối giản (chỉ “server thắng khi hoà”). Ước lượng 15 ngày công giả định một người làm liên tục.

| Rủi ro | Mức | Cách xử lý |
| --- | --- | --- |
| Thư viện FCM kéo Jackson 2, xung đột với Spring Boot 4 và Jackson 3 | Cao | Thử build và khởi động ngay Phase 0; nếu xung đột, gọi FCM HTTP v1 bằng `RestClient`; cổng `PushSender` giữ nguyên |
| Token iOS gốc là APNs, không gửi FCM trực tiếp được | Trung bình | Chốt S3 trước Phase 1; phương án dự phòng là Expo Push Service sau cùng cổng `PushSender`; iOS để “cố gắng hỗ trợ” theo R4 của SRS |
| Module khác chưa sẵn sàng nên Phase 4–6 chỉ chứng minh được bằng sự kiện/handler giả | Cao | Hợp đồng sự kiện, cổng và bộ test hợp đồng nằm trong repo từ sớm; bảng “Phụ thuộc” gửi chủ module; nghiệm thu ghi rõ dòng nào đã chạy với module thật |
| Con trỏ đồng bộ có thể bỏ sót dòng nếu một transaction ghi kéo dài hơn `settle-delay` | Trung bình | Giữ giao dịch ghi ngắn; `settle-delay` 2 giây cấu hình được; client kéo lại định kỳ; có bài test mô phỏng; chấp nhận giới hạn và ghi vào tài liệu |
| Đồng hồ thiết bị lệch làm quy tắc “mới nhất thắng” sai | Trung bình | Kẹp thời điểm client lệch tương lai quá 5 phút; hoà thì server thắng; mobile dùng giờ server từ `serverTime` để hiệu chỉnh |
| Nhắc nhở bị coi là spam nên người dùng tắt thông báo hệ điều hành | Trung bình | Trần 6/ngày, DND, không nhắc khi vừa làm hành động, bỏ qua thay vì bù; mặc định chỉ 3 nhắc nước + 3 nhắc ăn |
| Push bị trễ hoặc không đến do Doze/tiết kiệm pin hoặc người dùng tắt quyền | Trung bình | Trung tâm thông báo và `unread-count` là nguồn sự thật; push chỉ là gợi nhắc, không ghi trong AC là “đến trong N giây” |
| Nhiều instance chạy cùng bộ lập lịch gây gửi trùng | Thấp | Khoá chống trùng duy nhất ở DB (S5, S7); có bài test hai engine đồng thời |
| Lộ dữ liệu sức khoẻ qua nội dung push (màn hình khoá, máy chủ FCM) | Trung bình | S12: push chung chung, chi tiết chỉ qua API; test duyệt mọi mẫu push |
| Máy dùng chung: người sau nhận thông báo của người trước | Trung bình | Chuyển chủ token khi đăng ký; mobile `DELETE` thiết bị trước khi đăng xuất; test chuyển chủ |
| Log ngoại tuyến tham chiếu món tuỳ chỉnh chưa tồn tại trên server | Trung bình | Ghi là giới hạn của v1; thêm thực thể `CUSTOM_FOOD` vào đồng bộ nếu cần; trong lúc đó `REJECTED`/`FORBIDDEN_REFERENCE` và hiển thị cho người dùng |
| Handler đồng bộ ghi thẳng repository bỏ qua `DailySummary` và sự kiện | Cao | Bộ test hợp đồng bắt buộc kiểm tra `DailySummary` và sự kiện sau `apply`; review chéo handler |
| Token WebSocket hết hạn giữa phiên (access token 15 phút) | Thấp | Ngắt phiên khi token hết hạn, mobile kết nối lại với token mới; ghi trong tài liệu cho nhóm mobile |
| Trùng số phiên bản migration `V*` với nhóm khác | Trung bình | Thống nhất bảng số phiên bản trước khi gộp nhánh; Flyway chỉ báo lỗi khi đã trùng |
| `RateLimiter` của AUTH Phase 7 chưa xong khi tới Phase 8 | Trung bình | Làm AUTH Phase 7 trước hoặc tách lớp `RateLimiter` ra làm sớm; Redis đã có sẵn trong `docker-compose.yml` |
