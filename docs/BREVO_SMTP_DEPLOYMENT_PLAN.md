# Kế hoạch triển khai Email (prod) cho Kallo Backend bằng Brevo SMTP Relay

| Mục | Nội dung |
|---|---|
| Repo | `tranductrung04/kallo-backend` (Spring Boot 4.1.1, Java 21) |
| Phạm vi | Gửi email OTP (xác minh email, đặt lại mật khẩu) ở môi trường **prod** |
| Phương án | SMTP relay của dịch vụ transactional: **Brevo** |
| Ngày lập | 18/11/2026 |
| Trạng thái | Đề xuất, chưa triển khai |

> Các thông số Brevo trong tài liệu này lấy từ tài liệu chính thức của Brevo ngày 18/11/2026 (xem mục Nguồn). Những điểm **chưa xác nhận được** được đánh dấu ⚠️ và cần kiểm tra trực tiếp trên tài khoản Brevo.

---

## 1. Mục tiêu và tiêu chí thành công

**Mục tiêu:** người dùng thật nhận được thư OTP trong hộp thư **Inbox** (không vào Spam) trong vài giây, và đội vận hành biết ngay khi việc gửi thư gặp sự cố.

**Tiêu chí thành công:**
- Luồng đăng ký → nhận OTP → xác minh email chạy trọn vòng trên prod (AC5, AC7 trong SRS).
- Luồng quên mật khẩu → nhận OTP → đặt lại mật khẩu chạy trọn vòng.
- Thư tới Gmail, Outlook, Yahoo đều có `SPF=pass`, `DKIM=pass`, `DMARC=pass` và vào Inbox.
- Không còn Mailpit nào chạy hoặc mở cổng trên máy chủ prod.
- SMTP key không nằm trong Git; có quy trình xoay khoá.

---

## 2. Hiện trạng và các vấn đề cần xử lý

Cách gửi mail hiện tại trong code:

- `MailEmailService` dùng `JavaMailSender`, gửi **text thuần**, bất đồng bộ qua `mailTaskExecutor` (core 2, max 4, hàng đợi 100).
- Mỗi thư chỉ gửi **một lần**. Nếu lỗi, chỉ ghi `log.warn("Unable to send authentication email for user {}")`; người dùng vẫn thấy đăng ký thành công và phải bấm "gửi lại mã" (cách nhau ≥ 60 giây).
- Địa chỉ gửi đọc từ `MAIL_FROM`, mặc định `no-reply@kallo.local`.
- `application-prod.yml` đã đọc `MAIL_HOST`, `MAIL_PORT` (mặc định 587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH` (mặc định `true`), STARTTLS bật và bắt buộc theo mặc định. Timeout kế thừa từ `application.yml` (kết nối 3 giây, đọc/ghi 5 giây).
- Health check mail bị tắt (`management.health.mail.enabled: false`), nên không có tín hiệu tự động khi SMTP hỏng.

Các vấn đề phải sửa **trước khi** dùng Brevo:

| # | Vấn đề | Vị trí | Hậu quả | Mức độ |
|---|---|---|---|---|
| P1 | `MAIL_HOST: mailpit` bị đặt cứng cho service `app` (profile `prod`) | `docker-compose.yml` | Thư OTP không bao giờ ra ngoài; tới Mailpit | Chặn go-live |
| P2 | STARTTLS bị ép `false` | `docker-compose.yml` | Brevo yêu cầu xác thực; không dùng được cấu hình này | Chặn go-live |
| P3 | `MAIL_SMTP_AUTH` mặc định `false` | `docker-compose.yml` | Bị Brevo từ chối | Chặn go-live |
| P4 | `MAIL_FROM` không được truyền vào container | `docker-compose.yml` | Địa chỉ gửi là `no-reply@kallo.local`, Brevo sẽ từ chối vì miền chưa xác thực | Chặn go-live |
| P5 | Mailpit mở cổng `1025` và `8025` ra host | `docker-compose.yml` | Giao diện web 8025 hiện mọi OTP; ai truy cập được thì chiếm được tài khoản | Bảo mật nghiêm trọng |
| P6 | Rate limit theo IP (Phase 7 trong `docs/IMPLEMENT_MODULE_AUTH.md`) chưa được tick | tài liệu | ⚠️ Chưa kiểm tra mã nguồn. Nếu chưa có, có thể bị spam đăng ký để cạn hạn mức gửi hoặc hỏng uy tín tên miền | Cao |
| P7 | Lỗi gửi chỉ ghi log, không có metric hay cảnh báo | `MailEmailService` | Sự cố gửi thư không ai biết | Trung bình |

---

## 3. Kiến trúc đích

```
Người dùng ──► Kallo API (container app, profile prod)
                 │  register / forgot-password / resend-verification
                 ▼
          MailEmailService (@Async, mailTaskExecutor)
                 │  SMTP, STARTTLS, cổng 587, xác thực bằng SMTP key
                 ▼
          smtp-relay.brevo.com ──► máy chủ thư của người nhận (Gmail, Outlook...)
                                          ▲
        DNS của tên miền gửi: brevo-code (TXT), DKIM, DMARC ┘ (người nhận kiểm tra)
```

**Thông số kết nối Brevo:**

| Tham số | Giá trị | Biến môi trường |
|---|---|---|
| Máy chủ | `smtp-relay.brevo.com` | `MAIL_HOST` |
| Cổng | `587` (STARTTLS). Dự phòng: `2525` | `MAIL_PORT` |
| Xác thực | Bật | `MAIL_SMTP_AUTH=true` |
| Tên đăng nhập | **SMTP login** lấy ở Brevo (Settings → SMTP & API), có dạng `...@smtp-brevo.com`. **Không** dùng email tài khoản | `MAIL_USERNAME` |
| Mật khẩu | **SMTP key** (không dùng API key, không dùng mật khẩu tài khoản) | `MAIL_PASSWORD` |
| Mã hoá | STARTTLS bắt buộc | `MAIL_SMTP_STARTTLS_ENABLE=true`, `MAIL_SMTP_STARTTLS_REQUIRED=true` |
| Địa chỉ gửi (From) | Địa chỉ thuộc miền đã xác thực, ví dụ `no-reply@mail.<DOMAIN>`. **Không** dùng SMTP login làm From | `MAIL_FROM` |

Lưu ý: Brevo hỗ trợ cổng 465 (SSL), nhưng `application-prod.yml` hiện **chưa có** `mail.smtp.ssl.enable`, nên dùng 587 hoặc 2525.

---

## 4. Tiền đề và thông tin cần chuẩn bị

| # | Hạng mục | Người phụ trách | Ghi chú |
|---|---|---|---|
| T1 | Tên miền riêng của dự án, có quyền sửa DNS | Chủ tên miền | Không thể xác thực miền Gmail, Yahoo... |
| T2 | Quyết định miền gửi: subdomain `mail.<DOMAIN>` (khuyến nghị, cách ly uy tín) hoặc miền gốc | Nhóm kỹ thuật | Kiểm tra Brevo có cho thêm subdomain ở bước thêm domain |
| T3 | Tài khoản Brevo (email của nhóm, không dùng email cá nhân) | Quản trị dự án | Bật xác thực hai lớp cho tài khoản |
| T4 | Thông tin máy chủ prod: nhà cung cấp, IP đầu ra (outbound) cố định hay động | DevOps | Cần cho Giai đoạn 2, mục 2.3 và 2.4 |
| T5 | Mailbox thử nghiệm: 1 Gmail, 1 Outlook, 1 Yahoo | Nhóm kiểm thử | Dùng ở Giai đoạn 5 |

---

## 5. Lộ trình tổng thể

Thời gian bên dưới là **ước tính** của người lập kế hoạch, chưa có cam kết từ Brevo hay nhà cung cấp DNS.

| Giai đoạn | Nội dung | Ước tính công | Phụ thuộc / thời gian chờ |
|---|---|---|---|
| 0 | Mở tài khoản Brevo, **gửi yêu cầu kích hoạt SMTP transactional** | 0,25 ngày | Brevo xử lý ticket, ⚠️ chưa rõ thời gian |
| 1 | Thêm domain, cấu hình DNS, xác thực | 0,5 ngày | DNS lan truyền tối đa 48 giờ |
| 2 | Tạo SMTP key, cấu hình secrets, ủy quyền IP | 0,25 ngày | Cần xong GĐ 0 |
| 3 | Sửa `docker-compose.yml`, `.env`, tài liệu (PR) | 0,5 ngày | Độc lập, làm song song GĐ 1 |
| 4 | Củng cố code gửi thư (tuỳ chọn, PR riêng) | 1 ngày | Sau GĐ 3 |
| 5 | Kiểm thử trên staging/prod | 0,5 ngày | Cần xong GĐ 1–3 |
| 6 | Go-live | 0,25 ngày | Cần xong GĐ 5 |
| 7 | Giám sát và siết DMARC | liên tục | Sau go-live 2–4 tuần |

Đường găng: **GĐ 0 (kích hoạt SMTP)** và **GĐ 1 (DNS)**. Nên bắt đầu cả hai ngay trong ngày đầu.

---

## 6. Chi tiết từng giai đoạn

### Giai đoạn 0. Tài khoản Brevo và kích hoạt SMTP transactional

1. Tạo tài khoản Brevo bằng email của nhóm; bật xác thực hai lớp.
2. Theo Brevo, tài khoản mới có thể cần kích hoạt riêng cho gửi transactional. Nếu thử SMTP mà nhận lỗi `450 Your SMTP account is not yet activated`, hãy mở ticket hỗ trợ từ tài khoản. **Mở ticket sớm** để không chờ ở bước cuối.
3. Ghi lại hạn mức gói đang dùng. Gói Free: **tối đa 300 thư/ngày** (sau khi tài khoản được duyệt). ⚠️ Trang giá không nêu rõ gói Free có gắn logo Brevo vào thư hay không; kiểm tra khi gửi thư thử.

### Giai đoạn 1. Xác thực tên miền gửi

Vào **Settings → Senders, Domains, IPs → Domains**, thêm miền gửi (chỉ nhập tên miền, ví dụ `mail.<DOMAIN>`, không nhập địa chỉ email). Chọn một trong hai cách:

- **Automatic** (Brevo khuyến nghị): Brevo kết nối nhà cung cấp DNS và tự thêm bản ghi.
- **Manual:** sao chép từng bản ghi từ Brevo vào DNS, rồi bấm "Authenticate this email domain".

Các bản ghi DNS cần có (tên và giá trị lấy **nguyên văn từ màn hình Brevo**, vì khác nhau theo tài khoản):

| Mục đích | Loại | Ghi chú |
|---|---|---|
| Brevo code | TXT | Chứng minh quyền sở hữu miền |
| DKIM | 2 bản ghi CNAME (khoá 2048-bit) hoặc 1 bản ghi TXT (khoá 1024-bit mặc định) | Ưu tiên dạng 2 CNAME nếu Brevo cho chọn |
| DMARC | TXT tại `_dmarc.<miền>` | Chỉ được có **một** bản ghi DMARC, phải có thẻ `rua`. Ví dụ Brevo đưa ra: `v=DMARC1; p=none; rua=mailto:rua@dmarc.brevo.com` |

Lưu ý quan trọng:
- **SPF không bắt buộc** để xác thực miền với Brevo (Brevo chỉ cung cấp SPF/MX khi dùng IP riêng).
- Nếu miền đã có DMARC, chọn cách Manual để không bị ghi đè.
- Nếu dùng Cloudflare với DKIM dạng CNAME: tắt **CNAME flattening** và tắt **proxy** cho các bản ghi này, nếu không xác thực sẽ thất bại.
- DNS có thể mất tối đa 48 giờ; nếu bấm xác thực chưa được thì thử lại sau.
- Khi miền đã xác thực, không cần xác minh riêng từng địa chỉ gửi. Nếu chưa xác thực, phải nhập mã 6 chữ số gửi tới địa chỉ gửi.
- ⚠️ Tài liệu Brevo không nói rõ gói Free có xác thực được miền hay không. Nếu bị chặn, cân nhắc gói Starter (9 USD/tháng).

Kiểm tra bản ghi từ máy cá nhân (thay `<tên>` bằng tên bản ghi Brevo cung cấp):

```bash
dig +short TXT <DOMAIN-gui>                 # tìm giá trị brevo-code
dig +short CNAME <ten-ban-ghi-DKIM-1>.<DOMAIN-gui>
dig +short CNAME <ten-ban-ghi-DKIM-2>.<DOMAIN-gui>
dig +short TXT _dmarc.<DOMAIN-gui>
```

### Giai đoạn 2. SMTP key, secrets và IP

**2.1 Tạo SMTP key**
1. Vào **Settings → SMTP & API → tab SMTP** (`https://app.brevo.com/settings/keys/smtp`).
2. Ghi lại **Login** (dạng `...@smtp-brevo.com`) và tạo một **SMTP key** mới, đặt tên có ngữ cảnh, ví dụ `kallo-prod-2026-11`.
3. Lưu key ngay vào nơi quản lý bí mật của nhóm (key chỉ hiện đầy đủ một lần). Nếu client cắt ngắn mật khẩu dài, Brevo cho tạo key ngắn 15 ký tự.

**2.2 Lưu trữ secrets** (NFR-SEC-08)
- Chỉ đặt trong file `.env` trên máy chủ prod hoặc secret manager. `.env` phải nằm trong `.gitignore`; kiểm tra bằng `git ls-files .env` (kết quả phải rỗng).
- Không dán key vào chat, ticket, hay log.

**2.3 Ủy quyền IP (nếu Brevo bật chặn IP lạ)**
- Brevo có tính năng chặn IP lạ: trong "giai đoạn học" Brevo tự cho phép IP mới; chặn bật sau 30 ngày không phát hiện IP mới. Khi bị chặn, SMTP trả `525 5.7.1 Unauthorized IP address`.
- ⚠️ Trang tài liệu về tính năng này nói về API key; trang troubleshooting SMTP của Brevo vẫn liệt kê lỗi 525. Hãy kiểm tra trạng thái ở **Settings → Security → Authorized IPs** (Activated/Deactivated).
- Xác định **IP đầu ra thật** của máy chủ prod (trong Docker/cloud có thể khác IP bạn nghĩ):
  ```bash
  curl -s https://ifconfig.me
  ```
- Thêm IP đó (hoặc dải CIDR) vào **Authorized IPs**. Nếu IP đầu ra thay đổi theo lần triển khai, dùng IP tĩnh hoặc dải CIDR; chỉ tắt tính năng chặn như phương án cuối vì làm giảm bảo mật.

**2.4 Kiểm tra máy chủ prod ra được cổng SMTP**

Một số nhà cung cấp cloud (AWS, Azure, Google Cloud) hạn chế SMTP đi ra theo mặc định. Từ máy chủ prod:

```bash
openssl s_client -starttls smtp -crlf -connect smtp-relay.brevo.com:587
```

Nếu không kết nối được, thử cổng `2525` (đặt `MAIL_PORT=2525`) hoặc nhờ nhà cung cấp mở giới hạn.

### Giai đoạn 3. Thay đổi trong repo (PR 1)

**3.1 `docker-compose.yml`, service `app`, phần `environment`:**

```diff
-      MAIL_HOST: mailpit
-      MAIL_PORT: ${MAIL_PORT:-1025}
-      MAIL_USERNAME: ${MAIL_USERNAME:-}
-      MAIL_PASSWORD: ${MAIL_PASSWORD:-}
-      MAIL_SMTP_AUTH: ${MAIL_SMTP_AUTH:-false}
-      MAIL_SMTP_STARTTLS_ENABLE: false
-      MAIL_SMTP_STARTTLS_REQUIRED: false
+      MAIL_HOST: ${MAIL_HOST:?MAIL_HOST must be set}
+      MAIL_PORT: ${MAIL_PORT:-587}
+      MAIL_USERNAME: ${MAIL_USERNAME:?MAIL_USERNAME must be set}
+      MAIL_PASSWORD: ${MAIL_PASSWORD:?MAIL_PASSWORD must be set}
+      MAIL_FROM: ${MAIL_FROM:?MAIL_FROM must be set}
+      MAIL_SMTP_AUTH: "true"
+      MAIL_SMTP_STARTTLS_ENABLE: "true"
+      MAIL_SMTP_STARTTLS_REQUIRED: "true"
```

**3.2 `docker-compose.yml`, service `mailpit`:** chuyển sang profile `dev` để không chạy và không mở cổng trên prod.

```diff
   mailpit:
     image: axllent/mailpit:v1.21.8
     container_name: ${APP_NAME}-mailpit
     restart: unless-stopped
+    profiles: ["dev"]
     ports:
       - "1025:1025"
       - "8025:8025"
```

Hệ quả cho môi trường dev: muốn dùng Mailpit thì chạy `docker compose --profile dev up -d mailpit`. Cần cập nhật README. Nếu nhóm hay chạy service `app` của compose ở máy cá nhân, đặt `MAIL_HOST` trong `.env` cục bộ trỏ tới một SMTP dev khác.

**3.3 Tệp `.env` trên máy chủ prod** (không commit):

```dotenv
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=<SMTP login copy nguyên từ Brevo>
MAIL_PASSWORD=<SMTP key>
MAIL_FROM=no-reply@mail.<DOMAIN>

# Ngưỡng OTP theo SRS: KHÔNG dùng giá trị dev trong .env.example (5s, 20/giờ)
APP_AUTH_OTP_EXPIRATION=15m
APP_AUTH_RESEND_INTERVAL=60s
APP_AUTH_MAX_OTP_PER_HOUR=3
APP_AUTH_MAX_OTP_ATTEMPTS=5
```

**3.4 `.env.example`:** thêm khối chú thích hướng dẫn giá trị cho prod (không chứa bí mật thật) và nhắc không chép giá trị dev sang prod.

**3.5 Tài liệu:** cập nhật README (mục chạy Mailpit ở dev) và `docs/IMPLEMENT_MODULE_AUTH.md` nếu có mô tả hạ tầng mail.

**Tiêu chí hoàn thành PR 1:** `docker compose config` không báo lỗi khi đã có đủ biến; thiếu biến `MAIL_*` thì compose dừng với thông báo rõ.

### Giai đoạn 4. Củng cố code gửi thư (PR 2, tuỳ chọn nhưng nên làm)

Giải quyết P6/P7 và độ tin cậy. Mỗi mục độc lập:

| # | Thay đổi | Lý do |
|---|---|---|
| C1 | Đặt `RejectedExecutionHandler` cho `mailTaskExecutor` để ghi log rõ khi hàng đợi đầy (giống `pushTaskExecutor`) | Hiện tại thư bị bỏ khi hàng đợi 100 đầy, ngoại lệ chỉ lộ ra ở log chung |
| C2 | Bọc `action.run()` trong nhánh "không có transaction" của `dispatchAfterCommit` bằng `try/catch` (như nhánh afterCommit) | Tránh lỗi gửi thư làm hỏng request ở nhánh này |
| C3 | Retry 2–3 lần có backoff cho lỗi tạm thời (kết nối, 4xx) | Giảm số thư rơi mất do lỗi thoáng qua |
| C4 | Thêm bộ đếm Micrometer: gửi thành công, thất bại, bị từ chối; cảnh báo khi tỷ lệ lỗi tăng | Hiện không có tín hiệu tự động khi SMTP hỏng |
| C5 | (Tuỳ chọn) Thư HTML + text, có tên hiển thị (`Kallo <no-reply@...>`) | Giao diện chuyên nghiệp hơn, thân thiện với bộ lọc spam hơn |
| C6 | **Rate limit theo IP** cho register, forgot-password, resend-verification (Phase 7 của AUTH) nếu chưa có | Chặn spam đốt hạn mức 300 thư/ngày và hỏng uy tín miền |

Không đưa log chứa email người nhận hay mã OTP; code hiện tại chỉ log `user id`, giữ nguyên.

### Giai đoạn 5. Kiểm thử trước go-live

Thực hiện trên môi trường prod hoặc staging dùng chính cấu hình Brevo thật.

**5.1 Kiểm tra kết nối và xác thực**
- `openssl s_client` tới `smtp-relay.brevo.com:587` thành công (Giai đoạn 2, mục 2.4).
- Khởi động app với cấu hình mới, log không có lỗi cấu hình mail.

**5.2 Kiểm tra luồng nghiệp vụ** (thay `<API_HOST>` và mailbox thật của bạn):

```bash
# Đăng ký → phải nhận thư OTP xác minh
curl -X POST https://<API_HOST>/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"<mailbox-thu>","password":"Passw0rd123","displayName":"Test"}'

# Quên mật khẩu → phải nhận thư OTP đặt lại
curl -X POST https://<API_HOST>/api/auth/forgot-password \
  -H 'Content-Type: application/json' \
  -d '{"email":"<mailbox-thu>"}'
```

Sau đó dùng mã nhận được gọi `verify-email` (kèm Bearer token từ bước đăng ký) và `reset-password`.

**5.3 Danh sách kiểm tra**

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| K1 | Thư tới Gmail | Vào Inbox; header: `spf=pass`, `dkim=pass`, `dmarc=pass` |
| K2 | Thư tới Outlook | Như K1 |
| K3 | Thư tới Yahoo | Như K1 |
| K4 | Nội dung thư | Tiếng Việt hiển thị đúng dấu; có mã 6 chữ số, hạn 15 phút |
| K5 | Địa chỉ From | Đúng `MAIL_FROM`, thuộc miền đã xác thực |
| K6 | Độ trễ | Thư tới trong vài giây |
| K7 | OTP xác minh email | Nhập đúng mã → `emailVerified = true` |
| K8 | OTP đặt lại mật khẩu | Đổi mật khẩu thành công; dùng lại mã bị từ chối |
| K9 | Gửi lại mã | Gửi lại trước 60 giây trả 429 |
| K10 | Lỗi SMTP | Đặt tạm sai `MAIL_PASSWORD` trên staging: đăng ký vẫn thành công, log có `Unable to send authentication email`, bạn nhận biết được qua log/cảnh báo |
| K11 | Bảo mật | `curl http://<server>:8025` và cổng `1025` từ ngoài: **không** có dịch vụ nào phản hồi |
| K12 | Hạn mức | Ghi nhận hành vi khi gần chạm 300 thư/ngày (⚠️ tài liệu chưa nêu rõ khi vượt hạn mức ngày thì thư bị xếp hàng hay bị từ chối) |

Có thể dùng một công cụ chấm điểm thư (ví dụ mail-tester) để soát lỗi cấu hình trước khi mở rộng.

### Giai đoạn 6. Go-live

1. Thông báo cửa sổ thay đổi cho nhóm; chọn thời điểm ít người dùng.
2. Đảm bảo `.env` prod đã có đủ biến (Giai đoạn 3, mục 3.3) và key chưa hết hạn.
3. Triển khai bản có PR 1:
   ```bash
   docker compose pull    # nếu dùng image dựng sẵn
   docker compose up -d --build app
   docker compose ps
   docker compose logs -f app | grep -i -E "mail|Unable to send"
   ```
4. Gỡ Mailpit trên máy chủ nếu đang chạy: `docker compose rm -sf mailpit` (và đảm bảo cổng 1025/8025 đóng ở firewall).
5. Chạy lại K1, K7, K8 với mailbox thật.
6. Theo dõi sát 24 giờ đầu (Giai đoạn 7).

### Giai đoạn 7. Giám sát và vận hành

**Hàng ngày (2 tuần đầu), sau đó hàng tuần:**
- Dashboard Brevo: xem log thư giao dịch, số thư gửi, bị trả lại (bounce), khiếu nại spam (complaint). Mục "Real time" trong phần Transactional cho biết hàng đợi và mức sử dụng.
- Log ứng dụng: đếm dòng `Unable to send authentication email`. Tăng đột biến nghĩa là SMTP/khoá/IP có vấn đề.
- Mức dùng hạn mức: cảnh báo khi vượt ~70% hạn mức ngày.

**Cảnh báo đề xuất:**

| Điều kiện | Hành động |
|---|---|
| Có ≥ 1 lỗi gửi thư liên tục trong 10 phút | Kiểm tra key, IP, trạng thái Brevo |
| Bounce > 5% hoặc có complaint | Rà soát email đăng ký rác, siết rate limit |
| Dùng > 70% hạn mức ngày | Chuẩn bị nâng gói (Starter) hoặc siết rate limit |

**Xoay SMTP key (đề xuất mỗi 90 ngày hoặc khi nghi lộ):**
1. Tạo key mới trên Brevo (đừng xoá key cũ ngay).
2. Cập nhật `MAIL_PASSWORD` trong `.env` prod, chạy `docker compose up -d app`.
3. Chạy K1 để xác nhận; sau đó xoá key cũ trên Brevo.
Nếu Brevo thông báo cảnh báo bảo mật và vô hiệu key, tạo key mới và cập nhật ngay.

**Siết DMARC:** giữ `p=none` và đọc báo cáo `rua` 2–4 tuần; nếu mọi nguồn gửi hợp lệ đều pass, nâng lên `p=quarantine`, sau đó cân nhắc `p=reject`.

**Quản lý người nhận:** thư OTP gửi tới địa chỉ người dùng tự nhập, có thể sai chính tả hoặc không tồn tại. Theo dõi bounce để biết miền uy tín có bị ảnh hưởng không; đừng gửi lại liên tục tới địa chỉ lỗi (đã được hạn chế bởi giới hạn 3 mã/giờ).

---

## 7. Rủi ro và giảm thiểu

| # | Rủi ro | Khả năng | Ảnh hưởng | Giảm thiểu |
|---|---|---|---|---|
| R1 | Kích hoạt SMTP transactional chậm | Trung bình | Trễ go-live | Mở ticket ở ngày đầu (GĐ 0) |
| R2 | DNS/DKIM sai hoặc chưa lan truyền | Trung bình | Thư vào Spam hoặc bị từ chối | Kiểm tra bằng `dig`; xử lý Cloudflare (Giai đoạn 1); chờ tới 48 giờ |
| R3 | IP đầu ra bị Brevo chặn (`525`) | Trung bình | Không gửi được thư | Ủy quyền IP/CIDR (Giai đoạn 2, mục 2.3); dùng IP tĩnh |
| R4 | Cloud chặn cổng SMTP | Thấp–Trung bình | Không kết nối được | Dùng cổng 2525 hoặc xin mở giới hạn |
| R5 | Hết hạn mức 300 thư/ngày | Thấp (demo) – Trung bình (đông người) | OTP không tới, người dùng kẹt ở bước xác minh | Rate limit theo IP; cảnh báo 70%; nâng gói; có nhà cung cấp dự phòng |
| R6 | Spam đăng ký đốt hạn mức, hỏng uy tín miền | Trung bình | Thư thật bị chặn | PR 2 mục C6; quota/cảnh báo ở Brevo |
| R7 | Lộ SMTP key | Thấp | Kẻ xấu gửi thư mạo danh qua tài khoản của bạn | Chỉ lưu trong `.env`/secret manager; xoay khoá; bật 2FA cho tài khoản Brevo |
| R8 | Lỗi gửi bị nuốt, không ai biết | Cao nếu không làm GĐ 4 | Người dùng kẹt mà đội không biết | Cảnh báo theo log (tối thiểu); C4 metric |
| R9 | Gói Free không xác thực được miền hoặc gắn logo Brevo | ⚠️ Chưa rõ | Phải trả phí hoặc thư kém chuyên nghiệp | Kiểm tra ở GĐ 1; dự phòng gói Starter |

---

## 8. Kế hoạch quay lui (rollback)

Mailpit **không** phải phương án quay lui cho prod (thư sẽ không tới người dùng).

| Tình huống | Hành động |
|---|---|
| Brevo gián đoạn ngắn | Chờ; người dùng gửi lại mã sau 60 giây. Theo dõi trang trạng thái Brevo |
| Key/IP lỗi sau thay đổi | Khôi phục `.env` về bản trước, hoặc tạo key mới/ủy quyền IP đúng rồi `docker compose up -d app` |
| Brevo gián đoạn kéo dài hoặc bị khoá tài khoản | Chuyển sang nhà cung cấp dự phòng (ví dụ Amazon SES, Resend) chỉ bằng cách đổi `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`; **chuẩn bị sẵn** tài khoản và xác thực miền ở nhà cung cấp dự phòng từ trước nếu cần độ sẵn sàng cao |
| Thư vào Spam hàng loạt | Tạm dừng quảng bá đăng ký; kiểm tra DKIM/DMARC; xem bounce/complaint; xử lý nguồn spam |

Vì ứng dụng dùng SMTP chuẩn và `EmailService` đã là interface, đổi nhà cung cấp không cần sửa code.

---

## 9. Chi phí và hạn mức

Số liệu từ trang giá Brevo ngày 18/11/2026, đơn vị USD:

| Gói | Giá | Hạn mức |
|---|---|---|
| Free | 0 | Tối đa 300 thư/ngày (sau khi tài khoản được duyệt); không nêu hạn mức theo tháng |
| Starter | 9/tháng (97/năm) | Từ 5.000 thư/tháng; không gắn logo Brevo |
| Standard | 18/tháng (194/năm) | Trang giá không nêu số thư riêng |

Giả định mỗi người dùng mới tạo khoảng 2 thư OTP (giả định của người lập kế hoạch): 5.000 người dùng mới/tháng ≈ 10.000 thư/tháng, tương đương trung bình ~330 thư/ngày, tức **vượt** hạn mức Free 300 thư/ngày. Khi đó cần Starter trở lên hoặc đổi nhà cung cấp. Chi phí phụ: tên miền (chi phí hằng năm tuỳ nhà đăng ký, chưa tra cứu).

⚠️ Giá và hạn mức có thể thay đổi; xác nhận lại trên trang giá Brevo trước khi quyết định.

---

## 10. Checklist nghiệm thu (Definition of Done)

**Hạ tầng và cấu hình**
- [ ] Tài khoản Brevo có 2FA; SMTP transactional đã kích hoạt
- [ ] Miền gửi hiển thị "Authenticated" trong Brevo (Brevo code, DKIM, DMARC)
- [ ] IP đầu ra của prod đã được ủy quyền (nếu bật chặn IP)
- [ ] Máy chủ prod kết nối được `smtp-relay.brevo.com:587` (hoặc 2525)
- [ ] `docker-compose.yml` không còn `MAIL_HOST: mailpit`; Mailpit ở profile `dev`
- [ ] `MAIL_FROM`, `MAIL_USERNAME`, `MAIL_PASSWORD` được truyền vào container
- [ ] STARTTLS bắt buộc, xác thực SMTP bật
- [ ] `.env` prod dùng ngưỡng OTP theo SRS (60s, 3 mã/giờ)
- [ ] SMTP key không có trong Git (`git ls-files .env` rỗng)

**Chất lượng**
- [ ] K1–K12 đạt
- [ ] Cổng 1025/8025 không truy cập được từ ngoài
- [ ] Rate limit theo IP đã có hoặc có kế hoạch rõ ràng

**Vận hành**
- [ ] Cảnh báo theo log hoặc metric lỗi gửi thư
- [ ] Quy trình xoay SMTP key đã được ghi lại
- [ ] Có tài khoản nhà cung cấp dự phòng hoặc quyết định chấp nhận rủi ro
- [ ] Lịch xem báo cáo DMARC và siết chính sách đã đặt

---

## 11. Phụ lục: tra cứu lỗi thường gặp

| Thông báo | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `535 5.7.8 Authentication failed` | Dùng email tài khoản thay SMTP login; dùng API key hoặc mật khẩu tài khoản thay SMTP key; khoá bị cắt/thừa khoảng trắng; sai mã hoá so với cổng; khoá đã bị vô hiệu | Dùng đúng **Login** và **SMTP key**; tạo key mới; kiểm tra `.env` không có ký tự thừa |
| `450 Your SMTP account is not yet activated` / `Your sending platform is currently disabled` | Tài khoản mới chưa kích hoạt transactional; tài khoản bị khoá do bot hoặc bị xâm nhập; hết credit | Mở ticket hỗ trợ Brevo; bảo vệ form khỏi bot; kiểm tra hàng đợi và credit |
| `525 5.7.1 Unauthorized IP address` | Chặn IP lạ đang bật và IP đầu ra chưa được ủy quyền | Thêm IP/CIDR vào Authorized IPs; xác nhận IP đầu ra thật trong Docker/cloud |
| Thư bị từ chối do sender | Miền chưa xác thực; hoặc dùng SMTP login làm From | Hoàn tất xác thực miền; dùng `MAIL_FROM` thuộc miền đã xác thực |
| Kết nối treo, không có banner | Tường lửa hoặc nhà cung cấp cloud chặn SMTP | Thử cổng 2525; xin mở giới hạn cổng |
| Thư vào Spam | DKIM/DMARC chưa pass, miền mới, nội dung | Kiểm tra header; chờ danh tiếng miền tích luỹ; thêm bản HTML/text hợp lý |

---

## Nguồn

- Brevo, tích hợp SMTP relay: https://developers.brevo.com/docs/smtp-integration
- Brevo, xác thực tên miền: https://help.brevo.com/hc/en-us/articles/12163873383186
- Brevo, ủy quyền và chặn IP: https://help.brevo.com/hc/en-us/articles/5740111683858
- Brevo, xử lý sự cố SMTP: https://help.brevo.com/hc/en-us/articles/115000188150
- Brevo, bảng giá: https://www.brevo.com/pricing/
- Repo: `docker-compose.yml`, `.env.example`, `src/main/resources/application.yml`, `application-prod.yml`, `MailEmailService.java`, `AsyncConfig.java`, `EmailVerificationService.java`, `docs/IMPLEMENT_MODULE_AUTH.md`, `docs/REQUIREMENT.md`
