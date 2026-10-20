# Kế hoạch triển khai Module AUTH — Kallo Backend

Oct 19, 2026

Module AUTH gồm 9 phase (0–8), khoảng 14,5 ngày công cho một người. Xong Phase 2 các module khác dùng được xác thực JWT; xong Phase 3 mobile đăng nhập trọn vòng bằng email; xong Phase 5 có thêm xác minh email bằng OTP.

> **Cập nhật 20/10/2026 — thêm xác minh email bằng OTP (FR-AUTH-09).** Ngay sau khi đăng ký bằng email, hệ thống gửi mã OTP để người dùng chứng minh quyền sở hữu email, và việc xác minh là **bắt buộc** (SRS Q13): chưa xác minh thì mọi API nghiệp vụ trả 403 `EMAIL_NOT_VERIFIED`. Phần việc nằm gọn trong Phase 5 (+1,5 ngày công) vì dùng chung hạ tầng email/OTP với luồng quên mật khẩu. Phase 0–3 đã triển khai nên cần bốn chỉnh sửa trên phần đã làm: một migration mới (V1 đã chạy, không sửa), thêm `emailVerified` vào `AuthResponse`/`UserSummary`, thêm claim `email_verified` vào access token cùng bộ chặn 403, và gọi gửi OTP từ `register`. Các tài khoản thử đã đăng ký từ trước đang `email_verified = false` sẽ bị chặn: xác minh bằng OTP hoặc đặt `TRUE` trong DB dev. Chi tiết ở Phase 5, quyết định ở D10.

## Hiện trạng repo

Repo đang ở mức khung dự án: mình chưa xác nhận được dòng mã AUTH nào, và `build.gradle` còn thiếu thư viện JWT, mail, springdoc và Testcontainers. Trang GitHub chặn đọc thư mục `src/` tự động, nên bảng dưới đối chiếu theo [build.gradle](https://github.com/tranductrung04/kallo-backend/blob/main/build.gradle), [README](https://github.com/tranductrung04/kallo-backend) và ảnh cây thư mục bạn gửi; nếu đã có phần AUTH nào làm sẵn, hãy báo để mình chỉnh lại.

| Hạng mục | Hiện trạng | Hành động |
| --- | --- | --- |
| Nền tảng | Java 21, Spring Boot 4.1.1, Gradle | Giữ nguyên |
| Dependency đã có | security, security-oauth2-client, validation, webmvc, data-jpa, flyway (+ flyway-database-postgresql), postgresql, websocket, mapstruct, lombok, data-redis, data-elasticsearch | Dùng tiếp; Redis/ES xem quyết định D6 |
| Dependency còn thiếu | Thư viện JWT, `spring-boot-starter-mail`, springdoc-openapi, Testcontainers (README nói có dùng, `build.gradle` chưa khai) | Thêm ở Phase 0 |
| Cây thư mục | `common`, `dto`, `model` có nội dung; `config`, `controller`, `exception`, `mapper`, `repository`, `service.impl` trống; `db/migration` chưa có file SQL | Lấp dần theo Phase 0–1 |
| Lệch tên so với SRS/README | Repo dùng `model` (SRS và README ghi `entity`); chưa có package `service` chứa interface | Chốt ở D5 |
| Starter OAuth2 client | Không cần cho luồng “app gửi Google ID token lên server” | Quyết định ở D4 |

## Quyết định cần chốt trước khi code

Mười điểm dưới đây ảnh hưởng thiết kế ngay từ Phase 0; cột “Đề xuất” là lựa chọn mà toàn bộ kế hoạch phía sau giả định. Nếu bạn chọn khác, các phase liên quan sẽ phải chỉnh theo.

| # | Vấn đề | Đề xuất | Lý do |
| --- | --- | --- | --- |
| D1 | Thư viện JWT | `spring-boot-starter-oauth2-resource-server` (Nimbus), ký HS256 bằng `JWT_SECRET` | Có sẵn bộ lọc Bearer; Spring Boot 4 mặc định Jackson 3 còn `jjwt-jackson` dùng Jackson 2, dễ xung đột (cần thử nếu chọn jjwt) |
| D2 | Lưu refresh token | Bảng DB, token ngẫu nhiên 256-bit, chỉ lưu SHA-256; xoay vòng mỗi lần refresh và phát hiện dùng lại | NFR-SEC-03 yêu cầu thu hồi phía server; không cần thêm hạ tầng; dễ kiểm thử |
| D3 | Khôi phục mật khẩu | OTP 6 chữ số gửi qua email thay cho liên kết | App di động, liên kết cần cấu hình deep link; SRS cho phép cả hai |
| D4 | Đăng nhập Google | App gửi Google ID token tới `POST /api/auth/google`, server xác minh bằng JWKS của Google | `GOOGLE_CLIENT_SECRET` không cần cho luồng này; có thể bỏ starter oauth2-client; cần hỏi người làm mobile client ID nào làm `aud` |
| D5 | Tên package | Giữ `model` (đã có mã), thêm `service` (interface) và `security`; sửa SRS/README cho khớp | Đổi tên sau khi có entity tốn công hơn sửa tài liệu |
| D6 | Redis và Elasticsearch | Dùng Redis cho giới hạn tần suất (Phase 7); AUTH chưa dùng Elasticsearch | Trả lời Q12 của SRS; nếu chưa muốn dựng Redis ở máy dev thì dùng Caffeine tạm |
| D7 | Khoá chính và email | `users.id` kiểu UUID do DB sinh; email chuẩn hoá (trim, chữ thường), duy nhất theo `lower(email)` | UUID khó đoán, hợp với `client_uuid` của các bảng log |
| D8 | Kết quả đăng ký | Trả luôn cặp token, cờ `profileCompleted` và `emailVerified` | Bớt một lần gọi login; AC3 cần cờ để mobile chuyển sang thiết lập hồ sơ; có token ngay để gọi API xác minh email |
| D9 | Định dạng lỗi | `ApiResponse { success, data, error { code, message, details } }`; cập nhật mục 8.2 của SRS | SRS §3.3 yêu cầu wrapper chung, §8.2 lại mô tả lỗi dạng phẳng; cần một dạng duy nhất |
| D10 | Xác minh email sau đăng ký | OTP 6 chữ số gửi ngay sau `register`, bảng riêng `email_verification_tokens`; **bắt buộc**: claim `email_verified` trong access token, chưa xác minh thì mọi API nghiệp vụ trả 403 `EMAIL_NOT_VERIFIED`, trừ `/api/auth/**` và `GET /api/users/me`; `verify-email` trả access token mới; `verify-email` và `resend-verification` dùng Bearer | Dùng lại hạ tầng OTP của quên mật khẩu; claim trong token nên không truy vấn DB mỗi request (refresh đọc lại từ DB nên cờ luôn cập nhật); không nhận email từ client nên không lộ email nào đã đăng ký (SRS Q13) |

## Lộ trình

&#91;embedded content: lộ trình AUTH · 9 phase, 3 cột mốc, 14,5 ngày công\]

Các phase nối tiếp nhau vì cùng dùng `TokenService` và bảng `users`. M1 (ngày 4,5) cho các module khác dùng được người dùng hiện tại; M2 (ngày 6) cho mobile giữ phiên đăng nhập bằng email; M3 (ngày 14,5) là điểm nghiệm thu. Nếu bắt đầu từ thứ Hai 19/10/2026 và làm liên tục, M3 rơi vào khoảng 5–6/11/2026, xấp xỉ khung tuần 3–5 ở mục 10 của SRS.

## Thiết kế token và bảo mật

Access token là JWT sống 15 phút; refresh token là chuỗi ngẫu nhiên, chỉ lưu bản băm trong DB và sống 7 ngày. Mọi API ngoài nhóm `auth` yêu cầu header Bearer.

- **Access token.** JWT HS256, hạn theo `JWT_ACCESS_EXPIRATION` (mặc định 900000 ms). Claim gồm `sub` (user id), `role`, `email_verified` (boolean, D10), `jti`, `iat`, `exp`; không đưa email hay dữ liệu sức khoẻ vào token. Ứng dụng từ chối khởi động nếu `JWT_SECRET` ngắn hơn 32 byte.
- **Refresh token.** 32 byte ngẫu nhiên mã hoá base64url. DB lưu SHA-256, `family_id`, hạn dùng (`JWT_REFRESH_EXPIRATION`, mặc định 7 ngày) và thời điểm thu hồi. Mỗi lần refresh cấp token mới và thu hồi token cũ; nếu token đã thu hồi bị gửi lại thì thu hồi cả `family_id` vì nghi bị lộ.
- **Khi nào thu hồi.** Đăng xuất, đổi mật khẩu, đặt lại mật khẩu và xoá tài khoản đều thu hồi refresh token. Access token đã phát vẫn dùng được tối đa 15 phút vì bộ lọc không truy vấn DB mỗi request; chấp nhận độ trễ này và ghi vào tài liệu.
- **Mật khẩu.** BCrypt; tối thiểu 8 ký tự gồm chữ và số (SRS §4.1); không ghi mật khẩu hay token vào log.
- **Đăng nhập sai.** Một mã lỗi `INVALID_CREDENTIALS` chung cho email không tồn tại và sai mật khẩu; khoá tạm sau 5 lần sai trong 15 phút (Phase 7).
- **Google.** Server kiểm tra chữ ký bằng JWKS, `iss` là `accounts.google.com` hoặc `https://accounts.google.com`, `aud` thuộc danh sách client ID cấu hình, chưa hết hạn và `email_verified = true`. Email đã có trong DB thì liên kết vào tài khoản đó (FR-AUTH-03).
- **OTP đặt lại mật khẩu.** 6 chữ số sinh bằng `SecureRandom`, lưu HMAC-SHA256, hạn 15 phút, dùng một lần, nhập sai quá 5 lần thì vô hiệu. `forgot-password` luôn trả 200 dù email có tồn tại hay không.
- **OTP xác minh email.** Cùng đặc tính với OTP đặt lại mật khẩu (6 chữ số `SecureRandom`, HMAC-SHA256 gắn với mục đích và user id, hạn 15 phút, dùng một lần, sai quá 5 lần thì vô hiệu) nhưng nằm ở bảng riêng. Hai endpoint xác minh dùng Bearer và chỉ tác động lên user trong token, không nhận email hay id từ client. Chưa xác minh thì vẫn đăng nhập được nhưng bị chặn 403 ở mọi API nghiệp vụ (D10); `email_verified` cũng là căn cứ để liên kết Google an toàn (Phase 4).
- **Phân quyền.** Role `USER` hoặc `ADMIN` nằm trong claim. API tài khoản chỉ dùng đường dẫn `/users/me`, không nhận id từ client, để chống IDOR (NFR-SEC-04).

## Mô hình dữ liệu

Module AUTH cần bốn bảng: `users`, `refresh_tokens`, `password_reset_tokens` và `email_verification_tokens`. Khối SQL dưới đây là nội dung `V1__init_schema.sql` (đã chạy ở Phase 1) cho ba bảng đầu; bảng thứ tư thêm bằng migration mới nằm ngay sau khối này. Thuộc tính của `users` bám theo mục 6.1 của SRS, thêm `email_verified` (mặc định `FALSE`, bật khi xác minh xong) và `google_sub`.

```sql
CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash  VARCHAR(100),                  -- NULL nếu chỉ đăng nhập bằng Google
    google_sub     VARCHAR(64),
    auth_provider  VARCHAR(20)  NOT NULL,         -- LOCAL | GOOGLE (nguồn đăng ký ban đầu)
    display_name   VARCHAR(100) NOT NULL,
    avatar_url     VARCHAR(500),                  -- khoá object trong MinIO
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | DISABLED
    timezone       VARCHAR(50)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_credential CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,              -- HMAC-SHA256 của mã OTP
    attempts   INTEGER     NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id, created_at DESC);
```

Migration mới cho xác minh email. V1 đã chạy nên không sửa (SRS mục 6.3: không sửa migration đã chạy); dùng số phiên bản kế tiếp còn trống, ví dụ `V2__email_verification_tokens.sql`:

```sql
CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,              -- HMAC-SHA256 của mã OTP
    attempts   INTEGER     NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,                       -- đã dùng, hoặc đã bị mã mới thay thế
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id, created_at DESC);
```

- **Kiểu cột phải khớp entity.** Vì `ddl-auto: validate`, dùng `VARCHAR` (không dùng `CHAR`) cho chuỗi băm, `INTEGER` (không dùng `SMALLINT`) cho số đếm, `TIMESTAMPTZ` cho `Instant`; lệch kiểu sẽ làm ứng dụng không khởi động được.
- **Khoá ngoại xuyên module.** Mọi bảng module khác khai `user_id UUID REFERENCES users (id) ON DELETE CASCADE`, để FR-AUTH-07 xoá toàn bộ dữ liệu người dùng bằng một lệnh xoá `users`.
- **Số phiên bản Flyway.** `V1` chứa bảng AUTH vì mọi bảng khác tham chiếu `users`; các module khác bắt đầu từ `V2` và nhóm thống nhất số phiên bản trước khi gộp nhánh để tránh trùng. Bảng `email_verification_tokens` cần một số riêng: dùng `V2` nếu chưa module nào lấy, ngược lại lấy số kế tiếp.
- **Ảnh đại diện.** `avatar_url` lưu khoá object; API trả URL ký có thời hạn (Phase 6).

## Hợp đồng API

AUTH có 10 endpoint dưới `/api/auth` (công khai, trừ `change-password`, `verify-email` và `resend-verification` cần Bearer) và 4 endpoint dưới `/api/users/me` (cần Bearer). Mọi phản hồi theo `ApiResponse` (D9); mã lỗi là chuỗi cố định để mobile xử lý theo mã, không theo câu chữ. `GET /api/users/me/profile` thuộc module PROFILE, không nằm ở đây.

| Endpoint | FR | Phase | Body chính | Kết quả và lỗi chính |
| --- | --- | --- | --- | --- |
| `POST /api/auth/register` | FR-AUTH-01, 09 | 2, 5 | email, password, displayName | 201 + cặp token + user (`emailVerified = false`) + `profileCompleted`, đồng thời gửi OTP xác minh (từ Phase 5); 409 `EMAIL_ALREADY_EXISTS`; 400 `VALIDATION_ERROR` |
| `POST /api/auth/login` | FR-AUTH-02 | 2 | email, password | 200 + cặp token; 401 `INVALID_CREDENTIALS`; 403 `ACCOUNT_DISABLED`; 429 `TOO_MANY_REQUESTS` |
| `POST /api/auth/refresh` | FR-AUTH-06 | 3 | refreshToken | 200 + cặp token mới; 401 `REFRESH_TOKEN_INVALID` |
| `POST /api/auth/logout` | FR-AUTH-06 | 3 | refreshToken, allDevices (tuỳ chọn) | 204, luôn thành công (idempotent), không cần access token còn hạn |
| `POST /api/auth/change-password` (Bearer) | FR-AUTH-05 | 3 | currentPassword, newPassword | 200 + cặp token mới (các phiên khác bị thu hồi); 400 `INVALID_CURRENT_PASSWORD`; 400 `PASSWORD_NOT_SET` với tài khoản chỉ dùng Google |
| `POST /api/auth/google` | FR-AUTH-03 | 4 | idToken | 200 + cặp token + `isNewUser` + `profileCompleted`; 401 `GOOGLE_TOKEN_INVALID` |
| `POST /api/auth/forgot-password` | FR-AUTH-04 | 5 | email | 200 dù email có tồn tại hay không; 429 khi gửi quá dày |
| `POST /api/auth/reset-password` | FR-AUTH-04 | 5 | email, code, newPassword | 204; 400 `RESET_CODE_INVALID` (sai, hết hạn hoặc đã dùng) |
| `POST /api/auth/verify-email` (Bearer) | FR-AUTH-09 | 5 | code (6 chữ số) | 200 + access token mới (claim `email_verified = true`), cũng 200 nếu đã xác minh từ trước; 400 `VERIFICATION_CODE_INVALID` (sai, hết hạn, đã bị thay hoặc đã bị khoá) |
| `POST /api/auth/resend-verification` (Bearer) | FR-AUTH-09 | 5 | — | 204; 409 `EMAIL_ALREADY_VERIFIED`; 429 `TOO_MANY_REQUESTS` kèm `Retry-After` |
| `GET /api/users/me` | FR-AUTH-08 | 6 | — | 200 + thông tin tài khoản (gồm `emailVerified`) |
| `PUT /api/users/me` | FR-AUTH-08 | 6 | displayName, timezone | 200 + thông tin đã cập nhật |
| `POST /api/users/me/avatar` | FR-AUTH-08 | 6 | multipart `file` (JPEG/PNG, ≤ 2 MB) | 200 + `avatarUrl`; 400 `INVALID_FILE`; 413 |
| `DELETE /api/users/me` | FR-AUTH-07 | 6 | password, hoặc idToken mới với tài khoản Google | 204; 401 nếu xác nhận sai |

## Phase 0 — Nền chung (≈ 1 ngày, xong ở ngày 1)

Mục tiêu: có đủ thư viện, cấu hình và khung phản hồi/lỗi để các phase sau chỉ còn viết nghiệp vụ.

- [ ] Chốt D1–D9 với nhóm và ghi kết quả vào README hoặc mục “Quyết định” của doc này.
- [ ] `build.gradle`: thêm `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-mail`, springdoc-openapi (chọn bản hỗ trợ Spring Boot 4.1, kiểm tra bảng tương thích của springdoc) và Testcontainers (`postgresql`, `junit-jupiter`, `spring-boot-testcontainers`). Bỏ `spring-boot-starter-security-oauth2-client` nếu chốt D4 như đề xuất.
- [ ] Cấu hình YAML cho ba profile: khối `app.jwt` (secret, hạn access/refresh), `app.auth` (ngưỡng đăng nhập sai, hạn OTP, số lần nhập sai), `app.google` (danh sách client ID), `spring.mail.*`; mọi giá trị đọc từ biến môi trường.
- [ ] Cập nhật `.env.example` với `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `MAIL_*` và các biến `APP_AUTH_*`; không commit `.env`.
- [ ] `config/`: `JwtProperties`, `AuthProperties`, `GoogleProperties` dạng `@ConfigurationProperties` + `@Validated` (secret ≥ 32 byte, hạn > 0).
- [ ] `common/`: `ApiResponse<T>`, enum `ErrorCode` (mã lỗi + HTTP status), `BusinessException`.
- [ ] `exception/GlobalExceptionHandler`: lỗi validate → 400 kèm `details` theo từng trường; `BusinessException` → status của `ErrorCode`; lỗi không lường trước → 500, không lộ stacktrace.
- [ ] `docker-compose.yml`: thêm Mailpit làm SMTP giả cho máy dev (cổng 1025 nhận thư, 8025 xem thư) và Redis nếu theo D6.
- [ ] Kiểm tra ứng dụng khởi động được khi chưa chạy Redis và Elasticsearch; nếu không, bỏ starter chưa dùng (D6).

**Hoàn thành khi:** `./gradlew clean build` xanh; một endpoint cố tình lỗi trả JSON đúng `ApiResponse`; thiếu `JWT_SECRET` thì ứng dụng báo lỗi rõ ràng lúc khởi động.

## Phase 1 — Flyway, entity, repository (≈ 1,5 ngày, xong ở ngày 2,5)

Mục tiêu: lược đồ AUTH tồn tại trong PostgreSQL và khớp với entity, có test chứng minh điều đó.

- [ ] Tạo `src/main/resources/db/migration/V1__init_schema.sql` từ bản nháp ở mục “Mô hình dữ liệu”.
- [ ] `model/`: enum `Role`, `AuthProvider`, `UserStatus`; entity `User`, `RefreshToken`, `PasswordResetToken` với `@Enumerated(EnumType.STRING)` và `Instant` cho mọi cột thời gian. Dùng `@Getter`/`@Setter` thay vì `@Data` trên entity, `equals`/`hashCode` theo id.
- [ ] Bật JPA Auditing (`@EnableJpaAuditing`, `@CreatedDate`, `@LastModifiedDate`) cho `created_at` và `updated_at`.
- [ ] `repository/`: `UserRepository` (`findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, `findByGoogleSub`), `RefreshTokenRepository` (`findByTokenHash`, thu hồi theo user, thu hồi theo family, xoá bản ghi hết hạn), `PasswordResetTokenRepository` (lấy bản chưa dùng mới nhất của user).
- [ ] Hàm chuẩn hoá email (`trim` + `toLowerCase(Locale.ROOT)`) đặt ở `common/`, dùng ở mọi chỗ nhận email.
- [ ] Tuỳ chọn (FR-ADM-01, mức S): `ApplicationRunner` tạo tài khoản `ADMIN` khi có `ADMIN_EMAIL` và `ADMIN_PASSWORD`, bỏ qua nếu đã tồn tại.
- [ ] Test tích hợp bằng Testcontainers PostgreSQL: Flyway chạy sạch và Hibernate `validate` qua; email trùng khác hoa thường bị chặn bởi unique `lower(email)`; xoá `users` kéo theo xoá `refresh_tokens`.

**Hoàn thành khi:** `./gradlew test` xanh khi Docker đang chạy; ứng dụng khởi động trên DB trống và migration tự chạy, không còn lỗi `Schema-validation`.

## Phase 2 — Bảo mật, đăng ký, đăng nhập (≈ 2 ngày, xong ở ngày 4,5)

Mục tiêu: đăng ký và đăng nhập bằng email chạy được, mọi API khác được bảo vệ bằng JWT. Đây là cột mốc M1 — từ đây các module khác dùng được người dùng hiện tại và mobile bắt đầu tích hợp. Phase này bao phủ FR-AUTH-01 và FR-AUTH-02.

- [ ] `dto/auth/`: `RegisterRequest` (email hợp lệ, mật khẩu ≥ 8 ký tự có cả chữ và số, `displayName` 1–100 ký tự), `LoginRequest`, `AuthResponse` (accessToken, refreshToken, tokenType, expiresIn tính bằng giây, `user`, `profileCompleted`), `UserSummary`.
- [ ] `config/SecurityConfig`: chuỗi lọc không trạng thái, tắt CSRF, cho phép `/api/auth/**`, Swagger và `/error`, mọi đường dẫn còn lại yêu cầu xác thực, bật resource server JWT. Spring Security 7 đi kèm Boot 4: dùng lambda DSL và `requestMatchers` với chuỗi đường dẫn; nếu IDE báo API bị loại bỏ, đối chiếu ghi chú nâng cấp của Spring Security.
- [ ] `security/`: `CustomAuthenticationEntryPoint` (401 JSON), `CustomAccessDeniedHandler` (403 JSON), `JwtAuthenticationConverter` ánh xạ claim `role` sang `ROLE_USER`/`ROLE_ADMIN`, và tiện ích `CurrentUser` lấy user id từ `sub`.
- [ ] Bean `PasswordEncoder` BCrypt (strength 10–12).
- [ ] `TokenService` (interface ở `service/`, cài đặt ở `service/impl/`): `JwtEncoder`/`JwtDecoder` HS256 từ `JWT_SECRET`; cấp access token; cấp refresh token (32 byte `SecureRandom`, lưu SHA-256, `family_id` mới).
- [ ] `AuthService`: `register` chuẩn hoá email, trả 409 `EMAIL_ALREADY_EXISTS` nếu trùng, băm mật khẩu, lưu và trả cặp token. `login` trả cùng một lỗi `INVALID_CREDENTIALS` cho email lạ, sai mật khẩu và tài khoản chỉ dùng Google; tài khoản `DISABLED` trả 403.
- [ ] `profileCompleted`: đặt sau interface nhỏ `ProfileStatusProvider`, tạm trả `false`; module PROFILE sẽ cài đặt thật.
- [ ] `AuthController`: `POST /api/auth/register` (201) và `POST /api/auth/login` (200), có `@Valid` và chú thích Swagger. `UserMapper` (MapStruct) chuyển entity sang `UserSummary`.
- [ ] Test: đơn vị cho `TokenService` (claim, hạn, chữ ký sai bị từ chối) và `AuthService` (Mockito); `@WebMvcTest` cho validation và 401 khi thiếu token; tích hợp đăng ký → đăng nhập → gọi một endpoint bảo vệ tạm thời.

**Hoàn thành khi:** AC1 và AC2 của mục 4.1 SRS có test xanh; endpoint bảo vệ trả 200 với token hợp lệ và 401 khi thiếu hoặc sai token; lỗi validate trả 400 kèm `details` theo trường.

## Phase 3 — Refresh token, đăng xuất, đổi mật khẩu (≈ 1,5 ngày, xong ở ngày 6)

Mục tiêu: phiên đăng nhập kéo dài 7 ngày mà vẫn thu hồi được từ phía server. Xong phase này là cột mốc M2 — mobile có luồng email trọn vòng. Phase này bao phủ FR-AUTH-05 và FR-AUTH-06.

- [ ] `TokenService.rotate`: tìm token theo SHA-256; không có hoặc hết hạn → 401 `REFRESH_TOKEN_INVALID`; đã thu hồi ngoài cửa sổ ân hạn refresh đồng thời thì thu hồi cả `family_id` rồi trả 401; hợp lệ → thu hồi token cũ và cấp token mới cùng `family_id`, tất cả trong một transaction.
- [ ] Chặn đua khi hai lần refresh đồng thời dùng cùng một token: khoá bản ghi (`PESSIMISTIC_WRITE`) hoặc `UPDATE ... WHERE revoked_at IS NULL` rồi kiểm tra số dòng bị ảnh hưởng.
- [ ] `POST /api/auth/refresh` và `POST /api/auth/logout`. Logout thu hồi token được gửi lên, hoặc mọi token của user khi `allDevices = true`; luôn trả 204 dù token không tồn tại hoặc client không gửi refresh token.
- [ ] `POST /api/auth/change-password`: kiểm tra `currentPassword`, mật khẩu mới theo cùng quy tắc và phải khác mật khẩu cũ. Sau khi đổi, thu hồi mọi refresh token rồi cấp cặp token mới cho thiết bị đang dùng.
- [ ] Tài khoản chỉ dùng Google (không có `password_hash`) gọi đổi mật khẩu → 400 `PASSWORD_NOT_SET`; họ đặt mật khẩu qua luồng quên mật khẩu ở Phase 5.
- [ ] Báo nhóm mobile: lưu refresh token bằng `expo-secure-store`, và gộp các lần refresh đồng thời (nhiều request cùng gặp 401) thành đúng một lần gọi. Trong cửa sổ ân hạn `JWT_REFRESH_REUSE_GRACE`, request refresh thua vẫn có thể nhận 401; mobile không được xoá phiên chỉ vì một 401 đơn lẻ ngay sau refresh thành công.
- [ ] Test: dùng lại token đã xoay vòng làm thu hồi cả family; refresh hết hạn bị từ chối; đăng xuất rồi refresh → 401; đổi mật khẩu làm token cũ vô hiệu; tài khoản bị khóa và tài khoản Google chưa đặt mật khẩu bị từ chối; hai request refresh đồng thời chỉ chấp nhận một request; tích hợp đăng nhập → refresh → đăng xuất → refresh thất bại.

**Hoàn thành khi:** AC2 và quy tắc token của mục 4.1 SRS có test xanh; mỗi lần refresh trả refresh token khác lần trước; refresh token cũ gửi lại bị từ chối.

## Phase 4 — Đăng nhập Google (≈ 1,5 ngày, xong ở ngày 7,5)

Mục tiêu: người dùng đăng ký hoặc đăng nhập bằng Google, và email trùng được liên kết vào tài khoản cũ một cách an toàn. Phase này bao phủ FR-AUTH-03; có thể làm song song với Phase 5 sau khi Phase 3 xong.

- [ ] Hỏi nhóm mobile dùng thư viện nào để lấy Google ID token và client ID nào (Web, Android, iOS); khai trong `GOOGLE_CLIENT_ID` (một hoặc nhiều giá trị cách nhau bằng dấu phẩy) và đọc vào `GoogleProperties.clientIds`.
- [ ] `GoogleTokenVerifier`: `NimbusJwtDecoder` dựng từ JWKS của Google (`https://www.googleapis.com/oauth2/v3/certs`) kèm bộ kiểm tra `iss` (`accounts.google.com` hoặc `https://accounts.google.com`), `aud` thuộc danh sách client ID và thời hạn. Trả về `sub`, `email`, `email_verified`, `name`, `picture`; lỗi → 401 `GOOGLE_TOKEN_INVALID`. `email_verified = false` cũng bị từ chối.
- [ ] `AuthService.loginWithGoogle`: (1) tìm theo `google_sub` rồi đăng nhập; (2) chưa có nhưng email đã tồn tại → liên kết bằng cách ghi `google_sub` và `email_verified = true`; (3) chưa có gì → tạo user `GOOGLE` với `password_hash = NULL`, `display_name` lấy từ `name`, `isNewUser = true`.
- [ ] Chống chiếm tài khoản khi liên kết: nếu tài khoản email đang có `email_verified = false`, xoá `password_hash` và thu hồi mọi refresh token trước khi liên kết. Lý do: kẻ xấu có thể đăng ký bằng email của nạn nhân (người đăng ký chưa nhập OTP; kẻ xấu vẫn có thể đăng ký trước email của người khác, xem D10) rồi chờ nạn nhân đăng nhập Google; người dùng muốn có lại mật khẩu thì dùng luồng ở Phase 5.
- [ ] `POST /api/auth/google` trả cặp token, `isNewUser` và `profileCompleted`; mobile dựa vào hai cờ này để chuyển sang màn thiết lập hồ sơ (AC3).
- [ ] Test với `GoogleTokenVerifier` được mock: lần đầu tạo user mới, lần hai dùng lại user, email trùng thì liên kết, sai `aud` bị từ chối, trường hợp chiếm tài khoản làm mất mật khẩu cũ. Thêm một test riêng cho verifier bằng `JwtDecoder` dựng từ khoá thử để kiểm tra `iss`, `aud`, `exp`.

**Hoàn thành khi:** AC3 của mục 4.1 SRS có test xanh và đã thử tay một lần với ID token thật lấy từ app mobile ở môi trường dev.

## Phase 5 — OTP qua email: xác minh email, quên và đặt lại mật khẩu (≈ 3 ngày, xong ở ngày 10,5)

Mục tiêu: hai luồng dùng mã OTP gửi qua email, gồm xác minh email ngay sau đăng ký (FR-AUTH-09) và đặt lại mật khẩu (FR-AUTH-04), chạy trên cùng hạ tầng gửi thư và sinh/kiểm tra mã; mã không bị lạm dụng để dò mật khẩu hay dò email. Có thể làm song song với Phase 4. Làm theo thứ tự 5.1 → 5.2 → 5.3.

### 5.1 Hạ tầng dùng chung (≈ 0,75 ngày)

- [ ] `EmailService` (interface + cài đặt dùng `JavaMailSender`), gửi bất đồng bộ (`@EnableAsync`, executor nhỏ) để request không phải chờ SMTP. Lỗi gửi chỉ ghi log, không làm lộ email có tồn tại hay không.
- [ ] Hai mẫu email tiếng Việt, một cho xác minh email và một cho đặt lại mật khẩu; mỗi mẫu có mã OTP, hạn 15 phút và dòng “nếu không phải bạn, hãy bỏ qua thư này”. Đặt nội dung trong `resources/templates` hoặc chuỗi mẫu trong mã, tuỳ nhóm.
- [ ] Thêm biến `OTP_HMAC_SECRET` vào cấu hình và `.env.example`; tiêm `java.time.Clock` vào các service để test được hạn dùng.
- [ ] `OtpService` dùng chung: sinh mã 6 chữ số bằng `SecureRandom`; băm HMAC-SHA256 trên chuỗi `<purpose>:<userId>:<code>` (`purpose` là `EMAIL_VERIFY` hoặc `PASSWORD_RESET`, để mã của luồng này không dùng được ở luồng kia); so sánh bằng `MessageDigest.isEqual`.
- [ ] Bổ sung `AuthProperties`: hạn mã (15 phút), số lần nhập sai tối đa (5), khoảng cách gửi lại (60 giây), số mã tối đa mỗi giờ (3); hai luồng dùng chung giá trị trừ khi cần khác nhau.

### 5.2 Xác minh email bắt buộc, FR-AUTH-09 (≈ 1,5 ngày)

- [ ] Migration mới tạo `email_verification_tokens` (SQL ở mục “Mô hình dữ liệu”); entity `EmailVerificationToken`; `EmailVerificationTokenRepository` (lấy bản chưa dùng mới nhất của user, đếm số mã của user trong 1 giờ). Chạy ứng dụng để Hibernate `validate` qua.
- [ ] `EmailVerificationService.sendCode(userId)`: bỏ qua nếu `email_verified = true`; ngược lại vô hiệu các mã chưa dùng (đặt `used_at`), sinh mã mới, lưu bản băm, gửi email.
- [ ] Móc vào `register` (đã làm ở Phase 2): gửi OTP sau khi transaction commit (`@TransactionalEventListener(phase = AFTER_COMMIT)` hoặc tương đương); lỗi gửi chỉ ghi log, không làm đăng ký thất bại. Tài khoản tạo bằng Google (`email_verified = true`) không gửi.
- [ ] `verifyEmail(userId, code)`: nếu đã xác minh từ trước thì bỏ qua bước kiểm mã. Ngược lại lấy mã chưa dùng mới nhất; sai, hết hạn, đã bị thay hay đã khoá đều trả cùng một lỗi `VERIFICATION_CODE_INVALID`; mỗi lần sai tăng `attempts`, quá 5 lần thì vô hiệu mã; đúng thì ghi `used_at` và đặt `email_verified = true`. Cả hai trường hợp đều trả 200 kèm access token mới có claim `email_verified = true` (refresh token giữ nguyên).
- [ ] `resendVerification(userId)`: đã xác minh → 409 `EMAIL_ALREADY_VERIFIED`; vượt giới hạn (cách nhau < 60 giây hoặc quá 3 mã/giờ) → 429 `TOO_MANY_REQUESTS` kèm `Retry-After`; còn lại gọi `sendCode`. Phase này đếm theo bảng, Phase 7 chuyển sang `RateLimiter`.
- [ ] `POST /api/auth/verify-email` (body `code`, đúng 6 chữ số) và `POST /api/auth/resend-verification`. Lấy user id từ token bằng `CurrentUser`, không nhận email hay id từ client.
- [ ] `SecurityConfig`: hai endpoint này (giống `change-password`) phải yêu cầu xác thực. Khai báo `authenticated()` cho chúng **trước** dòng cho phép `/api/auth/**`, nếu không sẽ rơi vào nhóm công khai và `CurrentUser` trả rỗng.
- [ ] Bộ chặn xác minh (D10): sau khi xác thực JWT, nếu claim `email_verified` là false thì trả 403 `EMAIL_NOT_VERIFIED` (JSON `ApiResponse`, qua `CustomAccessDeniedHandler`) cho mọi đường dẫn trừ `/api/auth/**` và `GET /api/users/me`; làm bằng `AuthorizationManager` trong `SecurityConfig` hoặc một filter, không truy vấn DB mỗi request. Tài khoản `ADMIN` do `ApplicationRunner` tạo đặt `email_verified = true`.
- [ ] Sửa phần đã làm ở Phase 2 và 3: thêm `emailVerified` vào `UserSummary` (và `UserMapper`) và `AuthResponse`; `TokenService` đưa claim `email_verified` (đọc từ DB) vào access token ở đăng ký, đăng nhập, Google và `refresh`; `login` cũng trả cờ này.
- [ ] Báo nhóm mobile: sau `register` và mỗi lần đăng nhập mà `emailVerified = false`, chuyển thẳng tới màn nhập mã (6 ô, nút “Gửi lại mã” có đếm ngược 60 giây) và không cho vào app; gặp 403 `EMAIL_NOT_VERIFIED` ở bất kỳ API nào thì cũng quay về màn này. Sau `verify-email` thành công, thay access token đang giữ bằng token mới rồi vào app.

### 5.3 Quên và đặt lại mật khẩu, FR-AUTH-04 (≈ 0,75 ngày)

- [ ] `PasswordResetService.requestReset(email)`: chuẩn hoá email; nếu user tồn tại thì vô hiệu các mã chưa dùng, sinh OTP qua `OtpService` (`purpose = PASSWORD_RESET`), lưu bản băm, hạn 15 phút, gửi email. Luôn trả 200.
- [ ] Giới hạn gửi (3 mã/giờ/email, cách nhau ≥ 60 giây): Phase này đếm theo bảng `password_reset_tokens`; Phase 7 chuyển sang bộ đếm theo email + IP áp dụng cả với email không tồn tại, để mã 429 không lộ thông tin.
- [ ] `resetPassword(email, code, newPassword)`: lấy mã chưa dùng mới nhất; sai, hết hạn hay đã dùng đều trả cùng một lỗi `RESET_CODE_INVALID`; mỗi lần sai tăng `attempts`, quá 5 lần thì vô hiệu mã. Đúng mã thì đổi mật khẩu BCrypt, ghi `used_at`, thu hồi mọi refresh token và đặt `email_verified = true` (người dùng đã chứng minh sở hữu hộp thư).
- [ ] Tài khoản tạo bằng Google sau khi đặt lại sẽ có `password_hash`, đăng nhập được cả hai cách.
- [ ] `POST /api/auth/forgot-password` và `POST /api/auth/reset-password`.

### 5.4 Test và nghiệm thu

- [ ] Test xác minh (mock `EmailService`): đăng ký gửi đúng một thư tới đúng email và mã trong thư khớp bản băm đã lưu; mã đúng → `email_verified = true`; mã sai → `VERIFICATION_CODE_INVALID` và `attempts` tăng; sai 5 lần thì mã đúng cũng bị từ chối; mã hết hạn bị từ chối (đặt `Clock` cố định); gửi lại làm mã cũ vô hiệu; gửi lại trong 60 giây → 429; đã xác minh thì `verify-email` → 204 còn `resend-verification` → 409; thiếu Bearer → 401; không có tham số id nên không tác động được lên user khác; lỗi SMTP không làm `register` thất bại; tài khoản Google mới tạo có `email_verified = true` và không gửi thư; mã `EMAIL_VERIFY` không dùng được ở `reset-password` và ngược lại.
- [ ] Test chặn xác minh: tài khoản `email_verified = false` đăng nhập được nhưng gọi API nghiệp vụ → 403 `EMAIL_NOT_VERIFIED`; `GET /api/users/me` và nhóm `/api/auth/**` vẫn qua; sau `verify-email` access token mới gọi được, token cũ vẫn bị 403 đến khi refresh; `refresh` sau khi xác minh cho claim `true`; tài khoản Google và `ADMIN` mặc định `true`.
- [ ] Test quên mật khẩu (mock `EmailService`): mã đúng đổi được mật khẩu; dùng lại lần hai bị từ chối; mã hết hạn bị từ chối; sai 5 lần thì khoá mã; email không tồn tại vẫn trả 200 và không gửi thư; mã trong thư khớp với mã đã lưu băm; đặt lại thành công làm `email_verified = true`.
- [ ] Thử tay một lượt với Mailpit trên máy dev: đăng ký → thư OTP xác minh → nhập mã; quên mật khẩu → thư OTP đặt lại → đăng nhập bằng mật khẩu mới.

**Hoàn thành khi:** AC4 đến AC7 của mục 4.1 SRS có test xanh; cả hai loại thư OTP hiện trong Mailpit với đúng hạn 15 phút; thử trên mobile dev: đăng ký → nhập mã → `emailVerified` chuyển thành `true`.

## Phase 6 — Hồ sơ tài khoản, ảnh đại diện, xoá tài khoản (≈ 1,5 ngày, xong ở ngày 12)

Mục tiêu: người dùng xem và sửa thông tin tài khoản, đổi ảnh đại diện và xoá hẳn tài khoản cùng dữ liệu. Phase này bao phủ FR-AUTH-07 (mức S) và FR-AUTH-08 (mức M). Phần ảnh đại diện phụ thuộc lớp lưu trữ MinIO dùng chung với ảnh món ăn, nên cần thống nhất người viết `StorageService`.

- [ ] `UserService`: `getMe` (trả cả `emailVerified`), `updateMe` (đổi `displayName`, `timezone` — kiểm tra múi giờ bằng `ZoneId.getAvailableZoneIds()`). `UserController`: `GET` và `PUT /api/users/me`; user id luôn lấy từ token.
- [ ] `StorageService` (interface ở `service/`, cài đặt MinIO ở `service/impl/`; cấu hình `STORAGE_*`): `upload`, `delete`, `presignedUrl`. Nếu người khác đang viết lớp này cho ảnh món ăn thì dùng chung, không viết lại.
- [ ] `POST /api/users/me/avatar`: nhận `multipart` `file`, kiểm tra loại bằng nội dung (không tin đuôi tệp hay `Content-Type`), chỉ JPEG/PNG, tối đa 2 MB (413 nếu vượt), lưu khoá object dạng `avatars/{userId}/{uuid}.jpg`, xoá ảnh cũ, trả URL ký có thời hạn.
- [ ] `DELETE /api/users/me` (FR-AUTH-07): yêu cầu xác nhận lại bằng mật khẩu, hoặc Google ID token mới với tài khoản không có mật khẩu. Thứ tự xử lý: xoá ảnh trên MinIO, rồi xoá dòng `users` để `ON DELETE CASCADE` dọn dữ liệu của mọi module; ghi log sự kiện với user id (không ghi email).
- [ ] Đặt cơ chế phối hợp với module khác: mọi migration mới phải khai khoá ngoại `ON DELETE CASCADE` tới `users`; thêm một test kiểm tra bảng nào tham chiếu `users` mà thiếu `CASCADE` (truy vấn `information_schema`).
- [ ] Test: sửa thông tin hợp lệ và không hợp lệ; avatar đúng và sai định dạng, quá dung lượng; xoá tài khoản làm refresh token và ảnh biến mất, đăng nhập lại thất bại; không thể sửa hay xoá tài khoản của người khác (không có tham số id).

**Hoàn thành khi:** cả bốn endpoint `/api/users/me` chạy đúng trên môi trường dev với MinIO thật; sau khi xoá, không còn dòng nào mang user id đó ở bảng nào của module AUTH.

## Phase 7 — Giới hạn tần suất, log, dọn dẹp, tài liệu API (≈ 1,5 ngày, xong ở ngày 13,5)

Mục tiêu: AUTH chịu được dò mật khẩu, spam và lỗi vận hành, đáp ứng NFR-SEC-02 đến 06, NFR-SEC-08 và mục 9.7 của SRS.

- [ ] `RateLimiter` (interface; cài đặt Redis bằng `INCR` + `EXPIRE`, hoặc Caffeine nếu D6 chọn khác). Đăng nhập: 5 lần sai trong 15 phút theo cặp email + IP, trả 429 kèm `Retry-After`, đăng nhập đúng thì xoá bộ đếm. Quên mật khẩu: 3 mã/giờ/email và 10 yêu cầu/giờ/IP, áp dụng cả email không tồn tại. Đăng ký: tối đa 10/giờ/IP. Gửi lại mã xác minh email: 3 mã/giờ/user và cách nhau ≥ 60 giây (thay bộ đếm theo bảng của Phase 5), thêm 10 yêu cầu/giờ/IP.
- [ ] Đếm theo email + IP chứ không chỉ theo email, vì chỉ theo email cho phép kẻ xấu khoá tài khoản của người khác bằng cách cố tình đăng nhập sai.
- [ ] Lấy đúng IP phía sau reverse proxy: đặt `server.forward-headers-strategy` và chỉ tin `X-Forwarded-For` từ proxy của mình.
- [ ] Che độ trễ khi đăng nhập bằng email không tồn tại: so sánh với một bản băm BCrypt giả để thời gian phản hồi không lộ email có hay không.
- [ ] Bộ lọc `request_id`: nhận hoặc sinh `X-Request-Id`, đưa vào MDC và trả lại trong header phản hồi.
- [ ] Log sự kiện bảo mật: đăng ký, xác minh email (thành công, thất bại, gửi lại mã), đăng nhập thành công/thất bại, phát hiện dùng lại refresh token, đổi hoặc đặt lại mật khẩu, xoá tài khoản. Ghi `userId`, IP, `request_id`; không ghi mật khẩu, token, OTP hay email nguyên văn.
- [ ] Tác vụ dọn dẹp `@Scheduled` mỗi ngày (`@EnableScheduling`): xoá `refresh_tokens` đã hết hạn quá 7 ngày, `password_reset_tokens` và `email_verification_tokens` cũ hơn 24 giờ.
- [ ] CORS chỉ cho origin có trong cấu hình (app native không cần CORS, chỉ phục vụ Expo web/dev).
- [ ] Springdoc: khai báo security scheme Bearer để Swagger UI có nút Authorize; tắt hoặc giới hạn Swagger ở profile `prod` nếu triển khai công khai.
- [ ] Rà soát bí mật: `.env` có trong `.gitignore` và `git ls-files .env` rỗng; không có bí mật trong YAML; `JWT_SECRET` và `OTP_HMAC_SECRET` ở môi trường thật sinh ngẫu nhiên (ví dụ `openssl rand -base64 48`).
- [ ] Test: vượt ngưỡng trả 429 và hết cửa sổ thì dùng lại được; bộ đếm tách theo IP; log bắt được không chứa mật khẩu, token hay OTP.

**Hoàn thành khi:** NFR-SEC-06 có test xanh; Swagger UI gọi được API bảo vệ qua nút Authorize; danh sách rà soát bí mật được tick hết.

## Phase 8 — Kiểm thử tổng và nghiệm thu (≈ 1 ngày, xong ở ngày 14,5)

Mục tiêu: chứng minh toàn bộ AC của module AUTH đạt, trước khi các module sau dựa hẳn vào nó.

- [ ] Bộ test API đầu-cuối bằng Testcontainers (PostgreSQL), mock `EmailService` và `GoogleTokenVerifier`: đăng ký → đăng nhập → gọi API bảo vệ → refresh → đăng xuất; đăng ký → nhận OTP → xác minh email; Google → cờ `isNewUser`; quên mật khẩu → đặt lại → đăng nhập bằng mật khẩu mới; xoá tài khoản.
- [ ] Thêm JaCoCo; `./gradlew check` phải đạt độ phủ tầng service AUTH ≥ 60% (SRS §9.6).
- [ ] Nếu repo chưa có workflow GitHub Actions, thêm một workflow chạy `./gradlew check` (runner Ubuntu có sẵn Docker cho Testcontainers).
- [ ] Bộ yêu cầu mẫu cho nhóm mobile (Postman, Bruno hoặc tệp `.http`) cho các luồng chính.
- [ ] Kiểm thử bảo mật thủ công: token sai chữ ký, hết hạn hoặc bị sửa claim đều 401; gọi `/api/users/me` không token; thử chuỗi lạ (SQL, HTML) trong email và `displayName`; kích hoạt 429.
- [ ] Đo hiệu năng đăng nhập bằng k6 với vài chục request đồng thời: p95 ≤ 500 ms (NFR-PERF-01). BCrypt strength 12 có thể làm vượt ngưỡng; nếu vậy hạ về 10.
- [ ] Cập nhật tài liệu: README (endpoint AUTH thực tế, biến môi trường mới, cách chạy Mailpit) và SRS (D5, D9 và các mã lỗi mới như `VERIFICATION_CODE_INVALID`, `EMAIL_ALREADY_VERIFIED`, `EMAIL_NOT_VERIFIED`; `email_verified`, `google_sub` và FR-AUTH-09 đã có ở SRS 1.2).
- [ ] Thử trên thiết bị thật cùng nhóm mobile: đăng ký và nhập mã OTP, đăng nhập, tắt hẳn app rồi mở lại vẫn còn phiên, đăng xuất, đăng nhập Google.

**Hoàn thành khi:** mọi dòng trong bảng truy vết AC bên dưới xanh; `./gradlew check` xanh; nhóm mobile xác nhận năm luồng: email, xác minh email, Google, quên mật khẩu và đăng xuất chạy trên thiết bị thật.

## Truy vết tiêu chí chấp nhận

Mục 4.1 của SRS nêu bảy tiêu chí chấp nhận (AC1–AC7) cho AUTH. Mình bổ sung QT1–QT9 từ quy tắc nghiệp vụ và FR-AUTH-07/08 để mỗi yêu cầu đều có ít nhất một bài test.

| Mã | Yêu cầu | Phase | Bài test cần có |
| --- | --- | --- | --- |
| AC1 | Đăng ký email đã tồn tại báo lỗi rõ ràng, không tạo bản ghi mới | 2 | Đăng ký lặp (kể cả khác hoa thường) trả 409, số dòng `users` không đổi |
| AC2 | Đăng nhập trả token; gọi API bảo vệ thiếu hoặc sai token trả 401 | 2, 3 | Không token, chữ ký sai, hết hạn → 401; token đúng → 200 |
| AC3 | Google lần đầu tạo User mới và đưa mobile sang thiết lập hồ sơ | 4 | Lần đầu `isNewUser = true` và có dòng mới; lần hai `false`, không tạo thêm |
| AC4 | Mã đặt lại hết hạn hoặc đã dùng bị từ chối | 5 | Dùng mã hai lần; mã sau 15 phút (`Clock` cố định); sai 5 lần → `RESET_CODE_INVALID` |
| AC5 | Đăng ký bằng email gửi OTP; nhập đúng mã thì email được xác minh | 5 | Đăng ký → thư (mock `EmailService`) có mã khớp bản băm; `verify-email` đúng mã → `email_verified = true`; tài khoản Google mới tạo đã `true` |
| AC6 | Mã xác minh sai, hết hạn, bị thay hoặc bị khoá bị từ chối; gửi lại bị giới hạn | 5, 7 | Sai 5 lần thì mã đúng cũng bị từ chối; `Clock` sau 15 phút; gửi lại làm mã cũ vô hiệu; gửi lại trong 60 giây → 429; mọi trường hợp từ chối trả `VERIFICATION_CODE_INVALID` |
| AC7 | Tài khoản chưa xác minh gọi API nghiệp vụ nhận 403; sau xác minh gọi được | 5 | 403 `EMAIL_NOT_VERIFIED` với token cũ; `verify-email` → access token mới gọi được; `refresh` cho claim `true` |
| QT1 | Mật khẩu ≥ 8 ký tự gồm chữ và số, lưu BCrypt | 2 | Giá trị biên (7 ký tự, chỉ chữ, chỉ số); `password_hash` bắt đầu bằng `$2` |
| QT2 | Access token 15 phút, refresh token 7 ngày, cấu hình được | 2, 3 | `exp` của token khớp cấu hình; refresh hết hạn bị từ chối |
| QT3 | Refresh token lưu và thu hồi được phía server | 3 | Đăng xuất, đổi mật khẩu hoặc dùng lại token cũ làm refresh token vô hiệu |
| QT4 | Giới hạn đăng nhập sai 5 lần trong 15 phút | 7 | Lần sai thứ 6 trả 429; hết cửa sổ thì đăng nhập lại được |
| QT5 | Email duy nhất và đúng định dạng | 1, 2 | Unique `lower(email)` ở DB; email sai định dạng → 400 |
| QT6 | Email trùng khi đăng nhập Google thì liên kết vào tài khoản có sẵn | 4 | Cùng `users.id`, không tạo bản ghi mới; trường hợp email chưa xác minh làm mất mật khẩu cũ |
| QT7 | Xoá tài khoản xoá toàn bộ dữ liệu cá nhân (FR-AUTH-07) | 6 | Sau xoá: không còn refresh token, ảnh trên MinIO; đăng nhập lại thất bại |
| QT8 | Cập nhật tên hiển thị và ảnh đại diện (FR-AUTH-08) | 6 | Sửa hợp lệ, sai múi giờ → 400; ảnh sai định dạng hoặc quá 2 MB bị từ chối |
| QT9 | Chưa xác minh email bị chặn ở API nghiệp vụ; trạng thái xác minh lộ qua API | 2, 5, 6 | `email_verified = false` → 403 `EMAIL_NOT_VERIFIED`, trừ `/api/auth/**` và `GET /api/users/me`; `emailVerified` có trong `AuthResponse` và `GET /api/users/me` |

## Rủi ro và lưu ý kỹ thuật

Rủi ro lớn nhất là độ tương thích của Spring Boot 4 với thư viện bên thứ ba, và việc Phase 6 phụ thuộc lớp MinIO dùng chung. Nếu phải cắt phạm vi, giữ các yêu cầu mức M (FR-AUTH-01, 02, 03, 04, 06, 08, 09) và hoãn FR-AUTH-05, FR-AUTH-07 cùng cờ `allDevices`. Ước lượng 14,5 ngày công giả định một người làm liên tục; chia cho hai người thì Phase 4 và Phase 5 chạy song song sau Phase 3, tiết kiệm khoảng 1,5 ngày.

| Rủi ro | Mức | Cách xử lý |
| --- | --- | --- |
| Spring Boot 4, Spring Security 7 và Jackson 3 còn mới; springdoc, MapStruct, Lombok hoặc thư viện JWT có thể chưa tương thích | Cao | Thử build với đủ dependency ngay Phase 0; ghim phiên bản; nếu springdoc lỗi thì dời Swagger sang Phase 7 |
| Phase 6 chờ `StorageService` (MinIO) mà người khác có thể đang viết | Trung bình | Tách thành 6a (`/users/me`, xoá tài khoản) làm trước và 6b (ảnh đại diện) làm sau, để không chặn nhau |
| Access token vẫn dùng được tối đa 15 phút sau khi đăng xuất hoặc đổi mật khẩu | Thấp | Chấp nhận và ghi vào tài liệu; nếu cần chặt hơn thì thêm `token_version` trong claim hoặc danh sách `jti` bị thu hồi trong Redis |
| Đăng ký báo “email đã tồn tại” (theo AC1) nên lộ email nào đã có tài khoản | Thấp | Giới hạn tần suất đăng ký theo IP ở Phase 7; luồng quên mật khẩu và đăng nhập không lộ |
| Chiếm tài khoản khi liên kết Google vào email chưa xác minh | Thấp | Phase 5 xác minh email bằng OTP nên phần lớn tài khoản hợp lệ đã `email_verified = true` và liên kết bình thường; tài khoản chưa xác minh vẫn bị xoá mật khẩu cũ và thu hồi token ở Phase 4 |
| Chiếm chỗ email: kẻ xấu đăng ký trước bằng email của người khác, nạn nhân gặp 409 khi đăng ký | Thấp | Nạn nhân lấy lại tài khoản bằng Google hoặc quên mật khẩu (cả hai đều chứng minh quyền sở hữu hộp thư và vô hiệu mật khẩu của kẻ xấu). Muốn triệt để hơn, về sau cho người xác minh thành công “giành” email từ tài khoản chưa xác minh (ngoài phạm vi SRS 1.2) |
| Thư OTP không đến (SMTP lỗi, vào thư rác) làm người dùng kẹt ở màn nhập mã, hoặc bị lạm dụng làm spam | Trung bình | Vì xác minh là bắt buộc nên theo dõi log gửi thư và kiểm tra SMTP trước khi phát hành; nút gửi lại có đếm ngược; giới hạn 3 mã/giờ/tài khoản và đăng ký 10/giờ/IP ở Phase 7; gửi thư bất đồng bộ, lỗi chỉ ghi log |
| Nhiều người cùng thêm migration, trùng số phiên bản `V*` | Trung bình | Thống nhất bảng số phiên bản cho từng module trước khi gộp nhánh; Flyway chỉ báo lỗi khi đã trùng |
| BCrypt strength 12 làm đăng nhập chậm trên máy chủ yếu, vượt p95 500 ms | Thấp | Đo ở Phase 8, hạ về 10 nếu cần |
| Starter Redis và Elasticsearch có sẵn nhưng chưa có server ở máy dev | Trung bình | Kiểm tra khả năng khởi động ở Phase 0; bỏ starter chưa dùng theo D6 |