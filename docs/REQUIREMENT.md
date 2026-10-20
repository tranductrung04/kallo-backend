# TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM (REQUIREMENT)

## Dự án: Kallo — Ứng dụng hỗ trợ chăm sóc sức khỏe và dinh dưỡng tích hợp AI

| Mục | Nội dung |
|---|---|
| Loại tài liệu | Software Requirements Specification (SRS) |
| Tên ứng dụng | **Kallo** (theo đề cương) / **Kallo** (theo README, tên repo và package của backend) — cần thống nhất, xem Q11 |
| Phiên bản tài liệu | 1.2 (bản nháp, đã đối chiếu README của repository backend; đã chốt Q2 — tra cứu mã vạch theo cách lai; bổ sung xác minh email bằng OTP ở module AUTH) |

> **Quy ước đọc tài liệu**
> - Nội dung **không** có dấu `(*)` được lấy trực tiếp từ Đề cương đồ án.
> - Nội dung có dấu **`(*)`** là phần **đề xuất cụ thể hoá thêm** (con số, quy tắc, ràng buộc, giả định) để tài liệu đủ chi tiết cho thiết kế và cài đặt. Nhóm cần rà soát và xác nhận với giảng viên hướng dẫn.
> - Nội dung ghi **`[README]`** lấy từ README của repository backend (`kallo-backend`) và đã được đối chiếu với đề cương.
> - Mức ưu tiên theo MoSCoW: **M** (Must), **S** (Should), **C** (Could), **W** (Won't — ngoài phạm vi phiên bản này).

---

## Mục lục

1. [Giới thiệu](#1-giới-thiệu)
2. [Mô tả tổng quan](#2-mô-tả-tổng-quan)
3. [Kiến trúc và công nghệ](#3-kiến-trúc-và-công-nghệ)
4. [Yêu cầu chức năng](#4-yêu-cầu-chức-năng)
5. [Yêu cầu về AI và thị giác máy tính](#5-yêu-cầu-về-ai-và-thị-giác-máy-tính)
6. [Yêu cầu dữ liệu](#6-yêu-cầu-dữ-liệu)
7. [Yêu cầu giao diện](#7-yêu-cầu-giao-diện)
8. [Yêu cầu giao tiếp ngoài và API](#8-yêu-cầu-giao-tiếp-ngoài-và-api)
9. [Yêu cầu phi chức năng](#9-yêu-cầu-phi-chức-năng)
10. [Phân công công việc](#10-phân-công-công-việc)
11. [Kế hoạch và mốc tiến độ](#11-kế-hoạch-và-mốc-tiến-độ)
12. [Kiểm thử và tiêu chí nghiệm thu](#12-kiểm-thử-và-tiêu-chí-nghiệm-thu)
13. [Rủi ro, giả định, ràng buộc](#13-rủi-ro-giả-định-ràng-buộc)
14. [Câu hỏi mở cần chốt](#14-câu-hỏi-mở-cần-chốt)
15. [Phụ lục](#15-phụ-lục)

---

## 1. Giới thiệu

### 1.1. Mục đích tài liệu

Tài liệu này đặc tả đầy đủ yêu cầu của hệ thống **Kallo**, làm cơ sở cho các bước phân tích, thiết kế, cài đặt, kiểm thử và nghiệm thu đồ án tốt nghiệp. Đối tượng đọc gồm: các thành viên nhóm, giảng viên hướng dẫn và hội đồng chấm.

### 1.2. Mục tiêu dự án

Xây dựng ứng dụng di động **Kallo** đóng vai trò **trợ lý sức khỏe cá nhân**, giúp người dùng:

- Theo dõi dinh dưỡng, kiểm soát cân nặng và theo dõi vận động thể chất một cách tiện lợi, tự động hoá.
- Nhận diện **món ăn Việt Nam** qua ảnh chụp và ước lượng calo/macro bằng **pipeline AI do nhóm tự xây dựng và triển khai độc lập**, không phụ thuộc vào các dịch vụ API nhận diện bên thứ ba.

### 1.3. Phạm vi

**Trong phạm vi:**

1. Ứng dụng di động (React Native / Expo) cho Android và iOS.
2. Backend chính (Java Spring Boot) cung cấp RESTful API và WebSocket.
3. Dịch vụ AI (Python FastAPI) chạy mô hình YOLO định dạng ONNX.
4. Cơ sở dữ liệu PostgreSQL và lưu trữ tệp đối tượng (Object Storage — MinIO).
5. Quy trình thu thập dữ liệu, huấn luyện, đánh giá mô hình nhận diện món ăn trên tập VietFood68.
6. Tài khoản và xác thực (module AUTH): đăng ký bằng Email hoặc Google, **xác minh email bắt buộc bằng mã OTP ngay sau khi đăng ký bằng Email** `(*)`, khôi phục mật khẩu, quản lý phiên bằng JWT.

**Ngoài phạm vi (W) `(*)`:**

- Tư vấn y khoa, chẩn đoán hoặc kê đơn; ứng dụng chỉ mang tính tham khảo và hỗ trợ.
- Thanh toán, thương mại điện tử, giao đồ ăn.
- Phiên bản web hoặc trang quản trị đầy đủ (chỉ xem xét một công cụ quản trị tối thiểu, xem mục 4.10).
- Nhận diện món ăn ngoài 68 lớp của VietFood68 trong phiên bản đầu tiên.
- Thiết bị đeo chuyên dụng không đi qua Health Connect / HealthKit.
- Đổi địa chỉ email của tài khoản; nếu bổ sung về sau, email mới phải được xác minh lại bằng OTP. `(*)`

### 1.4. Định nghĩa và từ viết tắt

| Thuật ngữ | Giải nghĩa |
|---|---|
| BMI | Body Mass Index — chỉ số khối cơ thể |
| BMR | Basal Metabolic Rate — năng lượng chuyển hoá cơ bản |
| TDEE | Total Daily Energy Expenditure — tổng năng lượng tiêu hao mỗi ngày |
| Macro | Ba nhóm chất đa lượng: Protein, Carbohydrate (Carbs), Fat |
| Calo | Kilocalorie (kcal) |
| MET | Metabolic Equivalent of Task — đương lượng chuyển hoá của hoạt động |
| JWT | JSON Web Token |
| OAuth2 | Giao thức uỷ quyền, dùng cho đăng nhập Google |
| OTP | One-Time Password — mã dùng một lần gồm 6 chữ số, gửi qua email để xác minh email hoặc đặt lại mật khẩu |
| YOLO | You Only Look Once — họ mô hình phát hiện đối tượng |
| ONNX | Open Neural Network Exchange — định dạng mô hình trung gian |
| FCM | Firebase Cloud Messaging |
| VietFood68 | Tập dữ liệu 68 lớp món ăn Việt Nam |
| mAP | mean Average Precision — thước đo chất lượng phát hiện đối tượng |
| FR / NFR | Functional / Non-Functional Requirement |

### 1.5. Tài liệu tham khảo

- Hướng dẫn chẩn đoán và điều trị thừa cân, béo phì — Bộ Y tế.
- FAO/INFOODS Food Composition Databases.
- Bảng thành phần thực phẩm Việt Nam (Vietnamese Food Composition Table) — Viện Dinh dưỡng Quốc gia (NIN).
- Open Food Facts (dữ liệu Việt Nam) — dữ liệu theo giấy phép ODbL, yêu cầu ghi nguồn (xem FR-SYS-09).
- Ứng dụng tham khảo: WAO.
- README.md của repository `kallo-backend` (Backend Spring Boot): công nghệ, cấu trúc mã nguồn, cấu hình, migration, API, kiểm thử, Docker. `[README]`

---

## 2. Mô tả tổng quan

### 2.1. Bối cảnh và bài toán

Người dùng Việt Nam muốn kiểm soát cân nặng và ăn uống lành mạnh nhưng gặp khó khăn: ghi nhật ký ăn uống thủ công tốn thời gian; các ứng dụng quốc tế thiếu dữ liệu và khả năng nhận diện món Việt (phở, bún, cơm tấm, bánh mì…); việc ước lượng calo và lên thực đơn đòi hiểu biết dinh dưỡng. Kallo giải quyết bằng cách **chụp ảnh → nhận diện món → tự động tính calo/macro**, kết hợp lập kế hoạch bữa ăn và theo dõi vận động trong một ứng dụng.

### 2.2. Đối tượng người dùng

| Tác nhân (Actor) | Mô tả | Quyền chính |
|---|---|---|
| Khách (Guest) | Chưa đăng nhập | Đăng ký, đăng nhập, quên mật khẩu |
| Người dùng (User) | Người đã đăng ký tài khoản | Toàn bộ chức năng cá nhân hoá, kết bạn, thử thách |
| Quản trị viên (Admin) `(*)` | Người vận hành hệ thống | Quản lý dữ liệu món ăn chuẩn, huy hiệu, thử thách, người dùng vi phạm |
| Dịch vụ AI (System) | FastAPI nhận diện món ăn | Được Backend chính gọi nội bộ |
| Hệ thống ngoài | Google OAuth2, FCM, Health Connect / HealthKit, Open Food Facts | Tích hợp theo mục 8 |

### 2.3. Môi trường vận hành

- Thiết bị: điện thoại Android (Health Connect) và iOS (HealthKit); yêu cầu camera, kết nối mạng (có hỗ trợ ngoại tuyến một phần). `(*)` Phiên bản tối thiểu: Android 8.0+ (Health Connect cần Android 9+ hoặc ứng dụng Health Connect), iOS 15+.
- Máy chủ: Backend Spring Boot, dịch vụ AI FastAPI, PostgreSQL 15+, Object Storage (MinIO); đóng gói Docker / Docker Compose. `[README]`

### 2.4. Giả định và ràng buộc chính

- Ứng dụng là sản phẩm đồ án, có thời hạn hoàn thành 12/2026 và nhóm 3 người.
- Pipeline nhận diện món ăn phải tự huấn luyện và tự triển khai (YOLOv11m → ONNX), không gọi API nhận diện hình ảnh thương mại.
- Dữ liệu dinh dưỡng dựa trên nguồn chính thống (NIN, FAO/INFOODS); việc gán calo/macro cho từng món do nhóm thực hiện.
- Chi tiết các giả định và rủi ro xem mục 13.

---

## 3. Kiến trúc và công nghệ

### 3.1. Kiến trúc tổng thể

```
┌──────────────────────────┐
│  Mobile App (RN / Expo)  │
│  TypeScript              │
└───────┬─────────┬────────┘
        │ REST    │ WebSocket
        ▼         ▼
┌──────────────────────────────────────────┐        ┌────────────────────┐
│ Backend chính — Java Spring Boot         │───────▶│ FCM (push)         │
│ Spring Security + JWT + Google OAuth2    │        └────────────────────┘
│ Spring Data JPA / Hibernate              │        ┌────────────────────┐
│                                          │───────▶│ Google OAuth2      │
└───────┬───────────────┬──────────────────┘        └────────────────────┘
        │               │ HTTP nội bộ (JSON)        ┌────────────────────┐
        │               ▼                           │ Open Food Facts    │
        │      ┌───────────────────────────┐        │ (mã vạch, dự phòng)│
        │      │ AI Service — Python FastAPI│       └────────────────────┘
        │      │ YOLOv11m (ONNX Runtime)    │
        │      │ Tính khẩu phần + tra dinh  │
        │      │ dưỡng → trả JSON           │
        │      └───────────────────────────┘
        ▼
┌───────────────────┐   ┌──────────────────────────┐
│ PostgreSQL        │   │ Object Storage (MinIO)   │
│ (dữ liệu ứng dụng)│   │ (ảnh món ăn, avatar)     │
└───────────────────┘   └──────────────────────────┘
```

Luồng chính: Mobile gọi Backend chính; Backend chính xác thực, điều phối, lưu dữ liệu và gọi AI Service khi cần nhận diện. `(*)` Mobile **không** gọi trực tiếp AI Service để tập trung xác thực và kiểm soát tải tại một cổng. Tương tự, mobile **không** gọi trực tiếp Open Food Facts: khi quét mã vạch, Backend chính tra CSDL nội bộ trước, chỉ gọi Open Food Facts (HTTPS, có timeout) nếu chưa có và lưu đệm kết quả (xem FR-FD-07a/07b). `(*)`

### 3.2. Công nghệ sử dụng

| Lớp | Công nghệ |
|---|---|
| **Frontend** | TypeScript, React Native, Expo |
| Điều hướng & trạng thái | React Navigation, Zustand hoặc Redux Toolkit, React Query |
| Biểu đồ | Victory Native hoặc Recharts Native |
| Cảm biến & phần cứng | expo-sensors, expo-location, vision-camera-code-scanner |
| Đồng bộ sức khoẻ | react-native-health-connect (Android), react-native-health (iOS) |
| **Backend chính** | Java 21+, Spring Boot 4.x, Spring Security + JWT + Google OAuth2, Spring Data JPA/Hibernate, Spring WebSocket, SpringDoc OpenAPI (Swagger UI) `[README]` |
| Build & migration | Gradle (dùng Gradle Wrapper `./gradlew`), Flyway `[README]` |
| Gửi email | SMTP (`MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`) cho email khôi phục mật khẩu `[README]` và email xác minh email `(*)` |
| Kiểm thử & đóng gói | JUnit 5, Mockito, Testcontainers; Docker, Docker Compose `[README]` |
| **CSDL & lưu trữ** | PostgreSQL 15+; Object Storage tương thích S3 cho ảnh món ăn và ảnh đại diện, triển khai bằng **MinIO** (đề cương ghi chung là “Cloud Object Storage”) `[README]` |
| **AI** | YOLO (YOLOv11m) huấn luyện trên VietFood68; ONNX / ONNX Runtime; Python + FastAPI |
| **Thời gian thực** | WebSocket |
| **Thông báo đẩy** | Firebase Cloud Messaging + Expo Notifications |

> Lưu ý `(*)`: Google Fit REST API đang được Google ngừng hỗ trợ, nên phần đồng bộ trên Android dùng **Health Connect** như đề cương đã chọn (`react-native-health-connect`); “Google Fit” trong yêu cầu được hiểu là dữ liệu bước chân/nhịp tim đọc qua Health Connect.
>
> Lưu ý `[README]`: phần ghi chú cấu hình của README có nhắc `PROD_REDIS_HOST`, `PROD_REDIS_PASSWORD` và `PROD_ES_URIS` (Redis, Elasticsearch) nhưng bảng công nghệ chưa liệt kê hai thành phần này. Tài liệu này **chưa** coi chúng là bắt buộc — xem Q12.

### 3.3. Nguyên tắc kiến trúc `(*)`

1. API REST theo tài nguyên, trả JSON, tiền tố `/api` `[README]` (chưa phiên bản hoá; thêm `/v1` sau nếu cần). Mọi phản hồi dùng response wrapper chung; lỗi xử lý tập trung bằng global exception handler `[README]`.
2. Backend không trạng thái (stateless), xác thực bằng JWT (access token ngắn hạn + refresh token).
3. AI Service tách riêng để có thể mở rộng/huấn luyện lại mô hình độc lập với Backend.
4. Mọi dữ liệu phát sinh khi ngoại tuyến phải có khoá định danh phía client (UUID) để đồng bộ idempotent.
5. Tính toán tổng hợp hàng ngày (DailySummary) được lưu sẵn để dashboard và biểu đồ truy vấn nhanh.

### 3.4. Tổ chức mã nguồn, cấu hình và triển khai Backend `[README]`

**Repository:** `kallo-backend`; package gốc `com.healthcare.kallo`; kiến trúc phân lớp **controller → service → repository**.

| Thư mục / gói | Vai trò |
|---|---|
| `common/` | Hằng số, response wrapper, tiện ích dùng chung |
| `config/` | Cấu hình Security, CORS, WebSocket, OpenAPI, Storage, FCM |
| `controller/` | REST controller (chỉ nhận/ trả dữ liệu, không chứa nghiệp vụ) `(*)` |
| `dto/` | Request / Response DTO (không trả trực tiếp entity ra ngoài) `(*)` |
| `entity/` | JPA entity |
| `exception/` | Exception tuỳ chỉnh và global exception handler |
| `mapper/` | Chuyển đổi Entity ↔ DTO |
| `repository/` | Spring Data JPA repository |
| `service/` + `service/impl/` | Nghiệp vụ: interface và cài đặt |
| `resources/db/migration/` | Flyway: `V1__init_schema.sql`, `V2__...` |
| `resources/application*.yml` | Cấu hình `application.yml`, `application-dev.yml`, `application-prod.yml` |

**Profile và cách chạy:**

- Hai profile: `dev` (chạy local, mặc định) và `prod` (Docker Compose đặt `SPRING_PROFILES_ACTIVE=prod`).
- Yêu cầu: JDK 21+, PostgreSQL 15+ (hoặc qua Docker), Docker & Docker Compose (tuỳ chọn); không cần cài Gradle riêng.
- Lệnh chính: `./gradlew clean build`, `./gradlew bootRun --args='--spring.profiles.active=dev'`, `./gradlew test`, `./gradlew check`.
- Ứng dụng chạy tại `http://localhost:8080`; Flyway tự chạy migration khi khởi động.
- Docker Compose chạy stack backend + PostgreSQL (`docker compose up -d`); cấu hình `PROD_*` được `docker-compose.yml` thiết lập sẵn.

**Biến môi trường chính:**

| Biến | Mô tả | Mặc định / ví dụ |
|---|---|---|
| `APP_NAME` | Tên project, dùng đặt tên container | `kallo-backend` |
| `SPRING_PROFILES_ACTIVE` | Profile đang chạy | `dev` (Compose đặt `prod`) |
| `SERVER_PORT` / `PROD_SERVER_PORT` | Cổng ứng dụng (dev / prod) | `8080` |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Thông tin database | bắt buộc |
| `DB_URL` | JDBC URL khi chạy local (tuỳ chọn) | `jdbc:postgresql://localhost:5432/<db_name>` |
| `JWT_SECRET` | Khoá ký JWT | bắt buộc |
| `JWT_ACCESS_EXPIRATION` | Thời hạn access token (ms) | `900000` (15 phút) |
| `JWT_REFRESH_EXPIRATION` | Thời hạn refresh token (ms) | `604800000` (7 ngày) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_JWKS_URI` | Google ID token/JWKS | bắt buộc |
| `AI_SERVICE_URL` | URL của AI Service (FastAPI) | `http://localhost:8000` |
| `OFF_API_BASE_URL` | Địa chỉ API Open Food Facts `(*)` | `https://world.openfoodfacts.org` |
| `OFF_TIMEOUT_MS` | Timeout khi gọi Open Food Facts (ms) `(*)` | `2000` |
| `OFF_USER_AGENT` | User-Agent khai báo khi gọi Open Food Facts `(*)` | `Kallo/1.0 (<email liên hệ>)` |
| `OFF_MISS_TTL_HOURS` | Thời gian lưu đệm kết quả "không tìm thấy" (giờ) `(*)` | `24` |
| `FCM_CREDENTIALS_PATH` | Đường dẫn file service account FCM | bắt buộc nếu bật push |
| `STORAGE_ENDPOINT` | Endpoint Object Storage (MinIO) | `http://localhost:9000` |
| `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | Bucket và khoá truy cập | bắt buộc |
| `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP gửi email OTP (xác minh email, khôi phục mật khẩu) | bắt buộc |
| `OTP_HMAC_SECRET` `(*)` | Khoá HMAC dùng để băm mã OTP trước khi lưu | bắt buộc, ≥ 32 byte |
| `PROD_REDIS_PASSWORD` | Mật khẩu Redis (nếu dùng) | trống — xem Q12 |

**Quy tắc cấu hình:**

- Spring Boot **không** tự đọc file `.env`: khi chạy `bootRun`, phải export biến vào shell hoặc dùng plugin EnvFile của IDE.
- **Không commit** `.env`, file service account FCM hay bất kỳ thông tin nhạy cảm nào lên Git; repository cung cấp `.env.example` làm mẫu.
- API docs tự sinh bằng SpringDoc: Swagger UI tại `/swagger-ui.html`, OpenAPI JSON tại `/v3/api-docs`.

---

## 4. Yêu cầu chức năng

Mã yêu cầu có dạng `FR-<module>-<số>`. Mỗi module gồm: mô tả, danh sách yêu cầu, quy tắc nghiệp vụ và tiêu chí chấp nhận (AC).

### 4.1. Module AUTH — Tài khoản và xác thực

**Mô tả:** Đăng ký, xác minh email bằng OTP, đăng nhập bằng Email hoặc Google OAuth2, khôi phục mật khẩu.

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-AUTH-01 | Cho phép đăng ký tài khoản bằng Email + mật khẩu; sau khi đăng ký thành công, hệ thống gửi mã OTP xác minh email (FR-AUTH-09). | M |
| FR-AUTH-02 | Cho phép đăng nhập bằng Email + mật khẩu và nhận access token + refresh token (JWT). | M |
| FR-AUTH-03 | Cho phép đăng nhập/đăng ký bằng Google OAuth2; nếu email đã tồn tại thì liên kết vào tài khoản hiện có. | M |
| FR-AUTH-04 | Khôi phục mật khẩu: gửi liên kết/mã OTP đặt lại mật khẩu qua email. | M |
| FR-AUTH-05 | Đổi mật khẩu khi đã đăng nhập (yêu cầu mật khẩu cũ). | S |
| FR-AUTH-06 | Làm mới token bằng refresh token; đăng xuất vô hiệu hoá refresh token. | M |
| FR-AUTH-07 | Xoá tài khoản và toàn bộ dữ liệu cá nhân theo yêu cầu người dùng. `(*)` | S |
| FR-AUTH-08 | Cập nhật tên hiển thị, ảnh đại diện (lưu Object Storage). | M |
| FR-AUTH-09 | **Xác minh email bằng OTP (bắt buộc)** `(*)`: ngay sau khi đăng ký bằng Email, hệ thống gửi mã OTP 6 chữ số tới email đã đăng ký; người dùng phải nhập đúng mã để chứng minh quyền sở hữu email trước khi dùng các chức năng của ứng dụng, và có thể yêu cầu gửi lại mã. | M |

**Quy tắc nghiệp vụ `(*)`:**

- Email duy nhất, kiểm tra định dạng hợp lệ; mật khẩu tối thiểu 8 ký tự gồm chữ và số; lưu dạng băm (BCrypt).
- Access token hiệu lực mặc định **15 phút** (`JWT_ACCESS_EXPIRATION=900000` ms), refresh token **7 ngày** (`JWT_REFRESH_EXPIRATION=604800000` ms) `[README]`; cả hai cấu hình được. Refresh token lưu và có thể thu hồi phía server. `(*)`
- Mã OTP/liên kết đặt lại mật khẩu có hiệu lực tối đa 15 phút và chỉ dùng một lần. Email gửi qua SMTP cấu hình bằng `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` `[README]`.
- **Xác minh email `(*)`:** tài khoản đăng ký bằng Email có `email_verified = false` cho tới khi người dùng nhập đúng mã OTP gửi tới email đó. Mã gồm 6 chữ số sinh ngẫu nhiên an toàn, hiệu lực 15 phút, chỉ dùng một lần, lưu dạng băm (không lưu mã gốc); nhập sai quá 5 lần thì mã bị vô hiệu và phải gửi mã mới. Gửi lại mã cách nhau ≥ 60 giây, tối đa 3 mã/giờ cho mỗi tài khoản; mỗi mã mới vô hiệu mã cũ. Các ngưỡng này cấu hình được.
- **Trạng thái xác minh `(*)`:** xác minh email là **bắt buộc** (Q13). Tài khoản chưa xác minh vẫn đăng nhập và nhận token, nhưng mọi API nghiệp vụ trả 403 `EMAIL_NOT_VERIFIED` cho tới khi xác minh xong; chỉ nhóm `auth` và `GET /users/me` được phép. Trạng thái nằm trong access token (claim `email_verified`) và trong cờ `emailVerified` của API; sau khi xác minh, ứng dụng dùng access token mới do `verify-email` trả về. Ứng dụng di động chuyển thẳng tới màn nhập mã. `email_verified` chuyển thành `true` khi: nhập đúng mã xác minh; đăng ký hoặc đăng nhập bằng Google (Google đã xác minh email); hoặc đặt lại mật khẩu bằng OTP thành công. API xác minh chỉ tác động lên tài khoản trong token, không nhận email hoặc id từ client. Tài khoản `ADMIN` do cấu hình tạo có `email_verified = true`.
- **Liên kết Google an toàn `(*)`:** khi đăng nhập Google trùng email với tài khoản Email có `email_verified = false`, hệ thống xoá mật khẩu cũ và thu hồi mọi refresh token trước khi liên kết, vì mật khẩu đó có thể do người khác đặt bằng email không thuộc về họ; tài khoản đã xác minh được liên kết bình thường.
- Giới hạn đăng nhập sai (ví dụ 5 lần/15 phút) để chống dò mật khẩu.

**Tiêu chí chấp nhận:**

- AC1: Đăng ký với email đã tồn tại trả lỗi rõ ràng, không tạo bản ghi mới.
- AC2: Đăng nhập thành công trả token; gọi API bảo vệ không có/ sai token trả 401.
- AC3: Đăng nhập Google lần đầu tạo User mới và chuyển sang màn thiết lập hồ sơ sức khoẻ.
- AC4: Liên kết đặt lại mật khẩu hết hạn hoặc đã dùng bị từ chối.
- AC5: Đăng ký bằng Email thành công thì có thư chứa mã OTP gửi tới địa chỉ đó; nhập đúng mã trong 15 phút thì `email_verified` chuyển thành `true`; tài khoản tạo bằng Google có `email_verified = true` ngay từ đầu.
- AC6: Mã xác minh sai, hết hạn, đã bị thay bằng mã mới hoặc đã bị khoá sau 5 lần nhập sai đều bị từ chối với cùng một lỗi; gửi lại mã quá giới hạn bị từ chối (429).
- AC7: Tài khoản chưa xác minh gọi API nghiệp vụ nhận 403 `EMAIL_NOT_VERIFIED`; sau khi nhập đúng mã và dùng access token mới thì gọi được bình thường.

### 4.2. Module PROFILE — Hồ sơ sức khoẻ

**Mô tả:** Thiết lập thể trạng, mục tiêu cân nặng, dị ứng; tự động tính BMI, TDEE và mục tiêu calo hằng ngày.

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-PRO-01 | Nhập hồ sơ: giới tính, ngày sinh/tuổi, chiều cao, cân nặng hiện tại. | M |
| FR-PRO-02 | Chọn mức độ vận động (ít vận động, nhẹ, vừa, nhiều, rất nhiều). | M |
| FR-PRO-03 | Chọn mục tiêu: giảm cân / giữ cân / tăng cân; nhập cân nặng mục tiêu và (tuỳ chọn) thời hạn hoặc tốc độ thay đổi/tuần. | M |
| FR-PRO-04 | Khai báo thực phẩm dị ứng / kiêng kỵ (danh mục chuẩn + nhập tự do). | M |
| FR-PRO-05 | Tự động tính **BMI**, **BMR**, **TDEE** và **mục tiêu calo hằng ngày** khi hồ sơ thay đổi. | M |
| FR-PRO-06 | Tự động chia mục tiêu macro (Protein/Carbs/Fat) theo mục tiêu; cho phép người dùng chỉnh tay. | S |
| FR-PRO-07 | Đặt mục tiêu uống nước hằng ngày (mặc định theo cân nặng). `(*)` | S |
| FR-PRO-08 | Khi cân nặng mới được ghi nhận, đề xuất cập nhật lại TDEE/mục tiêu calo. `(*)` | S |

**Quy tắc nghiệp vụ (công thức chi tiết ở Phụ lục A):**

- BMI = cân nặng (kg) / chiều cao (m)². Phân loại BMI hiển thị cho người dùng; ngưỡng tham chiếu cho người châu Á `(*)`: thiếu cân < 18,5; bình thường 18,5–22,9; thừa cân 23–24,9; béo phì ≥ 25.
- BMR theo công thức Mifflin–St Jeor `(*)`; TDEE = BMR × hệ số vận động.
- Mục tiêu calo = TDEE ± mức điều chỉnh theo mục tiêu; **có giới hạn an toàn** (không thấp hơn ngưỡng tối thiểu, không giảm quá mức cho phép mỗi tuần). `(*)`
- Dị ứng được dùng làm bộ lọc cứng cho gợi ý thực đơn và cảnh báo khi ghi log món chứa chất dị ứng.

**Tiêu chí chấp nhận:**

- AC1: Nhập đủ hồ sơ hợp lệ thì hệ thống hiển thị BMI, TDEE, mục tiêu calo trong vòng 1 giây.
- AC2: Giá trị ngoài miền hợp lý (ví dụ chiều cao < 100 cm hoặc > 250 cm; cân nặng < 20 kg hoặc > 300 kg) bị từ chối với thông báo rõ. `(*)`
- AC3: Nếu mục tiêu do người dùng đặt vượt ngưỡng an toàn, hệ thống cảnh báo và đề xuất giá trị điều chỉnh.

### 4.3. Module NUTRI — Nhật ký dinh dưỡng và bữa ăn

**Mô tả:** Ghi nhật ký bữa ăn theo buổi, nhận diện món qua ảnh, tra cứu dữ liệu món ăn, quét mã vạch, quản lý món tuỳ chỉnh.

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-NUT-01 | Ghi nhật ký bữa ăn theo 4 buổi: **Sáng, Trưa, Tối, Bữa phụ**. | M |
| FR-NUT-02 | Chụp/chọn ảnh món ăn → gọi AI → hiển thị danh sách món nhận diện kèm độ tin cậy, khẩu phần ước lượng, calo và macro. | M |
| FR-NUT-03 | Người dùng xác nhận, sửa món/khẩu phần hoặc thêm/bỏ món trước khi lưu log. | M |
| FR-NUT-04 | Tra cứu món ăn theo tên (tìm kiếm không phân biệt dấu, gợi ý khi gõ). | M |
| FR-NUT-05 | Quét mã vạch thực phẩm đóng gói để tra cứu và ghi log. | M |
| FR-NUT-06 | Tạo, sửa, xoá **món ăn tuỳ chỉnh** (tên, khẩu phần chuẩn, calo, Protein/Carbs/Fat, ảnh). | M |
| FR-NUT-07 | Xem nhật ký theo ngày: danh sách món theo buổi, tổng calo/macro đã nạp, calo còn lại. | M |
| FR-NUT-08 | Sửa/xoá một mục log đã ghi; sao chép bữa ăn từ ngày khác. `(*)` | S |
| FR-NUT-09 | Món yêu thích và gần đây để ghi nhanh. `(*)` | S |
| FR-NUT-10 | Cảnh báo khi món/bữa ăn chứa thành phần người dùng dị ứng. `(*)` | S |
| FR-NUT-11 | Ghi nhận lượng nước uống (thêm nhanh theo ly/ml) và theo dõi so với mục tiêu ngày. | M |
| FR-NUT-12 | Lưu ảnh gốc của log ảnh lên Object Storage và liên kết với bản ghi log. | M |
| FR-NUT-13 | Gợi ý đóng góp ảnh sửa sai (người dùng sửa nhãn → lưu làm dữ liệu cải thiện mô hình, có sự đồng ý). `(*)` | C |

**Quy tắc nghiệp vụ:**

- Mỗi `UserFoodLog` ghi rõ: món (Food), khẩu phần (số lượng + đơn vị/gram), buổi ăn, thời điểm, **nguồn** (`AI_PHOTO`, `BARCODE`, `SEARCH`, `CUSTOM`, `MEAL_PLAN`). `(*)`
- Calo/macro của log = giá trị trên 100 g (hoặc trên khẩu phần chuẩn) × lượng ăn; **lưu snapshot** giá trị dinh dưỡng tại thời điểm ghi để thay đổi dữ liệu món về sau không làm sai lịch sử. `(*)`
- Khi ghi/sửa/xoá log, hệ thống cập nhật `DailySummary` của ngày tương ứng.
- Ảnh vượt dung lượng giới hạn được nén phía client trước khi tải lên (ví dụ cạnh dài ≤ 1280 px, ≤ 2 MB). `(*)`
- Quét mã vạch tra theo thứ tự: **CSDL nội bộ → Open Food Facts (do Backend gọi) →** nếu cả hai không có (hoặc thiếu calo/macro) thì cho phép người dùng tạo món mới từ mã vạch đó (liên kết với FR-NUT-06, FR-FD-07a/07b). Log tạo từ mã vạch có nguồn `BARCODE`. `(*)`

**Tiêu chí chấp nhận:**

- AC1: Chụp ảnh một món thuộc 68 lớp → nhận kết quả đề xuất; người dùng lưu được log chỉ với tối đa 3 thao tác chạm sau khi chụp. `(*)`
- AC2: Ảnh không nhận diện được món nào → hiển thị thông báo và cho phép tìm kiếm thủ công.
- AC3: Tổng calo/macro trên màn nhật ký khớp tổng các mục log của ngày.
- AC4: Quét mã vạch hợp lệ **có trong CSDL nội bộ** trả kết quả trong ≤ 1 giây. `(*)`
- AC5: Mã vạch chưa có ở CSDL nội bộ nhưng có ở Open Food Facts trả kết quả trong ≤ 3 giây (timeout gọi OFF mặc định 2 giây); lần quét sau cùng mã đó lấy từ CSDL nội bộ. `(*)`
- AC6: Open Food Facts lỗi, quá thời gian hoặc không có sản phẩm → ứng dụng không bị treo, hiển thị thông báo và nút "Tạo món từ mã vạch này". `(*)`

### 4.4. Module PLAN — Lập kế hoạch bữa ăn và trợ lý AI

**Mô tả:** Gợi ý thực đơn cá nhân hoá; chatbot tư vấn dinh dưỡng dựa trên lịch sử ăn uống.

#### 4.4.1. Gợi ý thực đơn (MealPlan)

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-PLAN-01 | Tạo thực đơn cho 1 ngày (và tuỳ chọn 7 ngày `(*)`) theo **mục tiêu calo**, **dị ứng**, **ngân sách** người dùng nhập. | M |
| FR-PLAN-02 | Thực đơn chia theo 4 buổi, mỗi buổi gồm danh sách món và khẩu phần; hiển thị tổng calo/macro và chi phí ước tính. | M |
| FR-PLAN-03 | Đổi món (swap) bằng món tương đương về calo/macro; tạo lại toàn bộ thực đơn. | S |
| FR-PLAN-04 | Lưu, xem lại và xoá thực đơn; áp dụng thực đơn vào nhật ký ngày (tạo log hàng loạt). | M |
| FR-PLAN-05 | Thực đơn tôn trọng dị ứng tuyệt đối: không chứa món/thành phần bị loại trừ. | M |
| FR-PLAN-06 | Tính đến lịch sử ăn uống gần đây để tránh lặp món quá nhiều. `(*)` | C |

**Quy tắc nghiệp vụ `(*)`:**

- Tổng calo thực đơn nằm trong ±5% mục tiêu calo ngày (sai số cấu hình được).
- Ngân sách tính theo trường giá tham khảo của món (VNĐ/khẩu phần); tổng chi phí ≤ ngân sách. Cần bổ sung dữ liệu giá cho bảng món ăn.
- Phân bổ calo gợi ý mặc định: Sáng 25%, Trưa 35%, Tối 30%, Phụ 10% (cấu hình được).
- Phương pháp sinh thực đơn (quy tắc/tối ưu hoá ràng buộc, có hoặc không kết hợp mô hình ngôn ngữ) — xem câu hỏi mở Q3.

#### 4.4.2. Chatbot tư vấn dinh dưỡng

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-CHAT-01 | Người dùng trò chuyện với chatbot qua giao diện hội thoại; hội thoại được lưu (Conversation / ChatMessage). | M |
| FR-CHAT-02 | Chatbot trả lời **dựa trên hồ sơ và lịch sử ăn uống** của chính người dùng (mục tiêu, dị ứng, calo đã nạp, macro, cân nặng gần đây). | M |
| FR-CHAT-03 | Hỗ trợ truyền phản hồi theo thời gian thực (streaming) qua WebSocket. | S |
| FR-CHAT-04 | Hiển thị lịch sử hội thoại, tạo cuộc hội thoại mới, xoá hội thoại. | M |
| FR-CHAT-05 | Câu hỏi gợi ý nhanh (ví dụ “Hôm nay tôi còn bao nhiêu calo?”, “Gợi ý bữa tối”). `(*)` | C |
| FR-CHAT-06 | Giới hạn phạm vi: từ chối/chuyển hướng câu hỏi y khoa chuyên sâu, kèm lời nhắc tham khảo bác sĩ. `(*)` | M |

**Quy tắc `(*)`:**

- Ngữ cảnh gửi cho mô hình chỉ gồm dữ liệu của chính người dùng; không lộ dữ liệu người dùng khác.
- Có giới hạn tần suất (rate limit) theo người dùng.
- Chatbot không đưa ra lời khuyên vượt ngưỡng an toàn (ví dụ ăn dưới mức calo tối thiểu).

**Tiêu chí chấp nhận:**

- AC1: Hỏi “hôm nay tôi ăn bao nhiêu calo” → trả lời đúng với DailySummary hiện tại.
- AC2: Người dùng khai báo dị ứng tôm → gợi ý của chatbot và thực đơn không chứa tôm.

### 4.5. Module ACT — Vận động và chỉ số cơ thể

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-ACT-01 | Ghi nhận hoạt động tập luyện thủ công: loại hoạt động, thời lượng, (tuỳ chọn) cường độ/quãng đường; tính **calo tiêu hao**. | M |
| FR-ACT-02 | Đồng bộ tự động **bước chân** và **nhịp tim** từ Health Connect (Android) / HealthKit (iOS); lưu vết đồng bộ (HealthSyncLog). | M |
| FR-ACT-03 | Xin và quản lý quyền truy cập dữ liệu sức khoẻ; ứng dụng vẫn hoạt động khi người dùng từ chối. | M |
| FR-ACT-04 | Ghi nhận cân nặng theo ngày và xem lịch sử (WeightLog); sửa/xoá bản ghi. | M |
| FR-ACT-05 | Tính calo tiêu hao từ bước chân (ước lượng theo cân nặng, chiều cao). `(*)` | S |
| FR-ACT-06 | Theo dõi bước chân trực tiếp bằng cảm biến thiết bị khi không có nguồn đồng bộ (expo-sensors). `(*)` | C |
| FR-ACT-07 | Ghi lộ trình/quãng đường cho chạy bộ, đi bộ bằng expo-location. `(*)` | C |
| FR-ACT-08 | Tránh trùng lặp: hoạt động đồng bộ và thủ công cùng khung thời gian phải được khử trùng. `(*)` | S |

**Quy tắc nghiệp vụ `(*)`:**

- Calo tiêu hao = MET × cân nặng (kg) × thời lượng (giờ); bảng MET cho các loại hoạt động phổ biến (đi bộ, chạy, đạp xe, bơi, gym, yoga, bóng đá, cầu lông…) lưu trong cơ sở dữ liệu (Phụ lục A).
- Cân nặng mới nhất cập nhật vào hồ sơ và kích hoạt đề xuất tính lại mục tiêu (FR-PRO-08).
- Mỗi bản ghi đồng bộ có `source`, `external_id` và khoảng thời gian để đồng bộ lặp lại không tạo bản ghi trùng.

**Tiêu chí chấp nhận:**

- AC1: Sau khi cấp quyền, số bước trong ngày trên ứng dụng khớp (sai lệch ≤ 5%) với ứng dụng sức khoẻ của hệ điều hành.
- AC2: Đồng bộ lặp lại nhiều lần không làm tăng số bước/tổng calo tiêu hao.
- AC3: Thêm bản ghi cân nặng cập nhật biểu đồ cân nặng ngay lập tức.

### 4.6. Module REPORT — Báo cáo và thống kê

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-REP-01 | **Dashboard** tổng quan tiến độ dinh dưỡng, nước uống, vận động của ngày hiện tại, cập nhật gần thời gian thực. | M |
| FR-REP-02 | Biểu đồ **calo nạp vào / tiêu hao** theo Ngày / Tuần / Tháng. | M |
| FR-REP-03 | Biểu đồ **macro** (Protein/Carbs/Fat) theo Ngày / Tuần / Tháng. | M |
| FR-REP-04 | Biểu đồ **cân nặng** theo thời gian so với cân nặng mục tiêu. | M |
| FR-REP-05 | **Dự đoán xu hướng cân nặng** và **thời điểm dự kiến đạt mục tiêu**. | S |
| FR-REP-06 | Thống kê nước uống và bước chân theo kỳ. `(*)` | S |
| FR-REP-07 | Hiển thị trạng thái “vượt/thiếu” so với mục tiêu calo, macro. `(*)` | S |
| FR-REP-08 | Xuất báo cáo tuần/tháng dạng ảnh hoặc PDF để chia sẻ. `(*)` | C |

**Quy tắc nghiệp vụ `(*)`:**

- Số liệu lấy từ bảng `DailySummary` (calo nạp, calo tiêu hao, macro, nước, bước chân, cân nặng cuối ngày) để truy vấn nhanh; `DailySummary` được cập nhật khi log thay đổi.
- Dự đoán xu hướng cân nặng: dùng ít nhất 14 ngày dữ liệu cân nặng, ước lượng bằng hồi quy tuyến tính hoặc trung bình động hàm mũ; khi dữ liệu không đủ, hiển thị thông báo thay vì con số.
- Thời gian đạt mục tiêu = (cân nặng hiện tại − cân nặng mục tiêu) / tốc độ thay đổi ước lượng; chỉ hiển thị nếu xu hướng đi đúng hướng mục tiêu.
- Múi giờ ngày được xác định theo múi giờ người dùng (mặc định Asia/Ho_Chi_Minh).

**Tiêu chí chấp nhận:**

- AC1: Dashboard tải xong trong ≤ 2 giây với mạng ổn định.
- AC2: Tổng calo tuần = tổng calo 7 ngày trong tuần đó.
- AC3: Khi chưa đủ dữ liệu, màn dự đoán hiển thị trạng thái hướng dẫn thay vì kết quả sai.

### 4.7. Module GAME — Streak và tạo động lực

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-GAM-01 | **Streak**: theo dõi số ngày liên tiếp hoàn thành mục tiêu; hiển thị chuỗi hiện tại và chuỗi dài nhất. | M |
| FR-GAM-02 | **Huy hiệu (Badge)**: cấp tự động khi đạt điều kiện (ví dụ streak 7/30/100 ngày, lần đầu ghi log, đạt mục tiêu cân nặng…). | M |
| FR-GAM-03 | **Thử thách (Challenge)**: người dùng tham gia thử thách có thời hạn (ví dụ “uống đủ nước 7 ngày”), theo dõi tiến độ, hoàn thành nhận huy hiệu. | M |
| FR-GAM-04 | **Kết bạn**: gửi/chấp nhận/từ chối lời mời, tìm người dùng, huỷ kết bạn. | M |
| FR-GAM-05 | **Bảng xếp hạng bạn bè thời gian thực** (WebSocket) theo tiêu chí như streak, điểm thử thách, bước chân tuần. | M |
| FR-GAM-06 | Thông báo khi đạt huy hiệu, bạn bè vượt thứ hạng, thử thách sắp hết hạn. `(*)` | S |
| FR-GAM-07 | Cho phép bật/tắt hiển thị thông tin của mình với bạn bè (quyền riêng tư). `(*)` | S |

**Quy tắc nghiệp vụ `(*)`:**

- Điều kiện “hoàn thành mục tiêu ngày” phải được định nghĩa rõ và cấu hình được: ví dụ có ghi log ≥ 2 bữa **và** calo nạp nằm trong ±10% mục tiêu. Có thể có tiêu chí khác (nước, vận động) cho thử thách riêng.
- Streak tính theo ngày theo múi giờ người dùng; có thể có cơ chế “đóng băng streak” (Could).
- Chỉ bạn bè đã chấp nhận mới thấy thứ hạng của nhau; chỉ hiển thị chỉ số được người dùng cho phép chia sẻ, **không** hiển thị cân nặng tuyệt đối.
- Xếp hạng cập nhật qua WebSocket với độ trễ ≤ 3 giây từ khi dữ liệu thay đổi.

**Tiêu chí chấp nhận:**

- AC1: Bỏ lỡ một ngày không đạt mục tiêu → streak đặt lại về 0 vào đầu ngày kế tiếp (trừ khi có cơ chế bảo vệ).
- AC2: Huy hiệu chỉ cấp một lần cho mỗi người dùng.
- AC3: Người dùng không là bạn bè không xem được dữ liệu của nhau.

### 4.8. Module SYS — Tiện ích và hệ thống

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-SYS-01 | **Thông báo đẩy** qua FCM + Expo Notifications; đăng ký/huỷ token thiết bị. | M |
| FR-SYS-02 | **Nhắc nhở thông minh**: nhắc uống nước, nhắc ghi log bữa ăn theo giờ thói quen, **cảnh báo vượt calo**. | M |
| FR-SYS-03 | Trung tâm thông báo trong ứng dụng (Notification): danh sách, đánh dấu đã đọc, xoá. | M |
| FR-SYS-04 | Người dùng tuỳ chỉnh loại/khung giờ nhận thông báo; chế độ không làm phiền. `(*)` | S |
| FR-SYS-05 | **Hoạt động ngoại tuyến**: xem dữ liệu đã tải và ghi log (bữa ăn, nước, cân nặng, hoạt động) khi mất mạng; **tự động đồng bộ** khi có mạng trở lại. | M |
| FR-SYS-06 | Giải quyết xung đột đồng bộ: dùng khoá UUID phía client và quy tắc “bản ghi mới nhất theo `updated_at` thắng” cho dữ liệu chỉnh sửa. `(*)` | S |
| FR-SYS-07 | Hỗ trợ ngôn ngữ Tiếng Việt (mặc định); chuẩn bị i18n cho Tiếng Anh. `(*)` | C |
| FR-SYS-08 | Giao diện sáng/tối. `(*)` | C |
| FR-SYS-09 | Hiển thị ghi nguồn và giấy phép dữ liệu bên thứ ba (Open Food Facts — ODbL) trong màn Giới thiệu/Cài đặt; món có `source = OFF` hiển thị nhãn nguồn. `(*)` | M |

**Quy tắc nghiệp vụ `(*)`:**

- **Cảnh báo vượt calo:** gửi khi tổng calo nạp trong ngày vượt mục tiêu quá ngưỡng (mặc định 100%); tối đa một lần/ngày cho mỗi mức cảnh báo.
- **Nhắc nhở thông minh:** không nhắc nếu người dùng vừa thực hiện hành động đó; giới hạn số thông báo/ngày (ví dụ ≤ 6).
- **Ngoại tuyến:** nhận diện ảnh bằng AI **cần mạng**; khi ngoại tuyến, ảnh được xếp hàng chờ và xử lý khi có mạng, hoặc cho phép nhập tay. Tra cứu món trong bộ nhớ đệm cục bộ.
- Hàng đợi đồng bộ có cơ chế thử lại với backoff, và đồng bộ idempotent.

**Tiêu chí chấp nhận:**

- AC1: Tắt mạng → ghi 3 log nước → bật mạng: cả 3 log xuất hiện trên server, không trùng.
- AC2: Vượt calo ngày → người dùng nhận đúng một thông báo cảnh báo.

### 4.9. Module FOODDATA — Dữ liệu món ăn (Backend)

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-FD-01 | Lưu danh mục món ăn: tên (VN/không dấu), nhóm món, khẩu phần chuẩn, calo, Protein/Carbs/Fat trên khẩu phần và/hoặc 100 g, ảnh, mã vạch (nếu có), nguồn dữ liệu. | M |
| FR-FD-02 | Phân biệt món **hệ thống** (chuẩn, 68 món VietFood68 + dữ liệu thu thập) và món **tuỳ chỉnh** (thuộc về một người dùng). | M |
| FR-FD-03 | Tìm kiếm món: theo tên (không dấu), theo nhóm, theo mã vạch; phân trang. | M |
| FR-FD-04 | Truy xuất chất gây dị ứng/nhãn dị ứng của món để lọc thực đơn và cảnh báo. `(*)` | M |
| FR-FD-05 | Giá tham khảo (VNĐ/khẩu phần) phục vụ gợi ý thực đơn theo ngân sách. `(*)` | S |
| FR-FD-06 | Quy trình nhập/ cập nhật dữ liệu dinh dưỡng hàng loạt (CSV/JSON) cho nhóm phát triển. `(*)` | S |
| FR-FD-07a | Tra cứu mã vạch trong CSDL nội bộ (món hệ thống và món người dùng có mã vạch); trả `source` của món. `(*)` | M |
| FR-FD-07b | Nếu CSDL nội bộ chưa có, Backend gọi Open Food Facts theo mã vạch, chuẩn hoá dữ liệu và lưu đệm vào `Food` (`source = OFF`); nếu vẫn không có thì trả 404 để mobile gợi ý tạo món từ mã vạch. `(*)` | S |

**Quy tắc nghiệp vụ cho FR-FD-07a/07b `(*)`** (quyết định Q2, cách C — lai):

- Thứ tự tra: CSDL nội bộ → Open Food Facts → 404.
- Chỉ Backend gọi Open Food Facts; có timeout (`OFF_TIMEOUT_MS`, mặc định 2000 ms), khai báo User-Agent riêng (`OFF_USER_AGENT`) và giới hạn tần suất gọi ra ngoài.
- Kiểm tra định dạng mã vạch (chỉ chữ số, độ dài hợp lệ EAN-8/UPC-A/EAN-13/GTIN-14) trước khi tra.
- Kết quả từ Open Food Facts lưu với `source = OFF` và không bị người dùng sửa đè; muốn chỉnh thì tạo món tuỳ chỉnh riêng.
- Nếu Open Food Facts thiếu calo hoặc macro thì không lưu như món hoàn chỉnh; yêu cầu người dùng bổ sung rồi lưu thành món tuỳ chỉnh.
- Lưu đệm cả kết quả "không tìm thấy" trong `OFF_MISS_TTL_HOURS` (mặc định 24 giờ) để không gọi lặp lại cho cùng một mã.
- Khi Open Food Facts không khả dụng, chức năng quét vẫn chạy với dữ liệu nội bộ và tạo món tay (không phụ thuộc dịch vụ ngoài).
- Tuân thủ giấy phép ODbL: ghi nguồn dữ liệu (FR-SYS-09).

### 4.10. Quản trị tối thiểu `(*)`

| Mã | Yêu cầu | Ưu tiên |
|---|---|---|
| FR-ADM-01 | Vai trò `ADMIN` được cấp bằng cấu hình/CSDL, không đăng ký công khai. | S |
| FR-ADM-02 | Quản lý món ăn hệ thống, huy hiệu, thử thách (CRUD) qua API được bảo vệ bằng vai trò. | S |
| FR-ADM-03 | Giao diện quản trị web hoặc công cụ dòng lệnh/Swagger cho các thao tác trên. | C |

---

## 5. Yêu cầu về AI và thị giác máy tính

### 5.1. Mục tiêu

Pipeline nhận diện món ăn Việt Nam và ước lượng calo **được xây dựng, triển khai độc lập**, không dùng API nhận diện hình ảnh bên thứ ba.

### 5.2. Dữ liệu huấn luyện

- **Tập VietFood68:** 68 lớp món ăn Việt Nam, dùng để huấn luyện mô hình phát hiện món ăn.
- **Bảng dinh dưỡng:** calo, Protein/Carbs/Fat cho từng món (theo nguồn NIN, FAO/INFOODS, Bảng thành phần thực phẩm Việt Nam), dùng tra cứu sau nhận diện.
- Công việc dữ liệu: thu thập, gán nhãn (bounding box), kiểm tra chất lượng, chia tập train/val/test `(*)` (đề xuất 70/20/10, chia theo ảnh gốc để tránh rò rỉ dữ liệu).
- Tăng cường dữ liệu `(*)`: lật, xoay nhẹ, đổi sáng/tương phản, mosaic, mixup.

### 5.3. Mô hình và huấn luyện

| Hạng mục | Yêu cầu |
|---|---|
| Kiến trúc | YOLOv11m (phát hiện đối tượng) |
| Đầu vào | Ảnh RGB, kích thước suy luận cố định `(*)` 640×640 |
| Đầu ra | Danh sách đối tượng: lớp món, độ tin cậy, hộp giới hạn (bbox) |
| Xuất mô hình | ONNX, chạy bằng ONNX Runtime trong FastAPI |
| Đánh giá | Precision, Recall, mAP@0.5, mAP@0.5:0.95, ma trận nhầm lẫn theo lớp, phân tích lớp yếu |
| Mục tiêu chất lượng `(*)` | mAP@0.5 ≥ 0,80 trên tập kiểm thử (chốt sau khi có kết quả thực nghiệm đầu tiên); mỗi lớp có AP đủ dùng hoặc được liệt kê rõ lớp yếu |
| Tái lập | Lưu cấu hình huấn luyện, phiên bản dữ liệu, seed, trọng số tốt nhất `(*)` |

### 5.4. Luồng nhận diện và tính calo

```
Ảnh người dùng
   │ (client nén ảnh)
   ▼
Backend chính ── xác thực, lưu ảnh lên Object Storage ──▶ AI Service (FastAPI)
                                                              │
                              1. Tiền xử lý (resize/letterbox, chuẩn hoá)
                              2. Suy luận ONNX Runtime
                              3. Hậu xử lý (NMS, ngưỡng độ tin cậy)
                              4. Ước lượng khẩu phần
                              5. Tra bảng dinh dưỡng → calo + macro
                              ▼
                         JSON kết quả ──▶ Backend ──▶ Mobile (người dùng xác nhận/sửa)
```

**Đặc tả bước ước lượng khẩu phần `(*)`:**

- Mỗi món có **khẩu phần chuẩn (gram)** trong bảng dinh dưỡng. Khẩu phần ước lượng = khẩu phần chuẩn × hệ số kích thước, trong đó hệ số kích thước suy ra từ **tỷ lệ diện tích bbox so với ảnh** hoặc so với diện tích tham chiếu của lớp đó.
- Hệ số kích thước bị chặn trong khoảng hợp lý (ví dụ 0,5–2,0) và **luôn cho phép người dùng chỉnh tay**.
- Hiển thị rõ đây là **giá trị ước lượng**.

### 5.5. Hợp đồng JSON của AI Service `(*)`

Yêu cầu: `POST /ai/v1/detect` — `multipart/form-data` (trường `image`), tham số tuỳ chọn `conf_threshold`. Backend chính gọi AI Service theo địa chỉ cấu hình `AI_SERVICE_URL` (mặc định `http://localhost:8000`) `[README]`.

Phản hồi mẫu:

```json
{
  "request_id": "c1f2...",
  "model_version": "yolo11m-vietfood68-v1",
  "inference_ms": 180,
  "items": [
    {
      "class_id": 12,
      "class_name": "Phở bò",
      "confidence": 0.91,
      "bbox": { "x": 0.12, "y": 0.20, "w": 0.55, "h": 0.60 },
      "portion": { "grams": 420, "size_factor": 1.1, "unit": "tô" },
      "nutrition": {
        "calories": 450,
        "protein_g": 22,
        "carbs_g": 60,
        "fat_g": 12
      }
    }
  ]
}
```

- Trả mảng rỗng khi không phát hiện món (không phải lỗi).
- Lỗi chuẩn hoá: ảnh sai định dạng/quá lớn (400/413), quá tải (503), lỗi nội bộ (500) với mã lỗi và thông điệp.

### 5.6. Yêu cầu hiệu năng và vận hành AI `(*)`

| Chỉ tiêu | Yêu cầu |
|---|---|
| Thời gian suy luận một ảnh (CPU) | ≤ 1,5 giây (mục tiêu), tổng thời gian tính từ lúc chụp đến lúc có kết quả ≤ 5 giây trên mạng 4G |
| Dung lượng ảnh tối đa | 5 MB; định dạng JPEG/PNG/HEIC (HEIC chuyển đổi phía client) |
| Khả dụng | Có endpoint `/health`; nạp mô hình một lần khi khởi động |
| Giám sát | Ghi nhật ký độ tin cậy trung bình, tỉ lệ “không nhận diện”, độ trễ |
| Bảo mật | Chỉ nhận yêu cầu từ Backend chính (mạng nội bộ/API key nội bộ) |
| Quản lý phiên bản | Tên và phiên bản mô hình trả trong mỗi phản hồi |

### 5.7. Chatbot và thực đơn AI

Yêu cầu chức năng ở mục 4.4. Về kỹ thuật, nguồn mô hình cho chatbot (tự lưu trữ hay dịch vụ ngoài) và thuật toán sinh thực đơn **chưa được đề cương quy định** — xem Q1 và Q3 ở mục 14. Dù chọn cách nào, đầu vào bắt buộc gồm hồ sơ, mục tiêu calo/macro, dị ứng và lịch sử ăn uống của người dùng.

---

## 6. Yêu cầu dữ liệu

### 6.1. Thực thể chính

Lược đồ dữ liệu ban đầu theo đề cương gồm các thực thể sau. Cột “Thuộc tính chính” là đề xuất `(*)`.

| Nhóm | Thực thể | Thuộc tính chính `(*)` |
|---|---|---|
| Tài khoản | **User** | id, email (unique), email_verified, password_hash, google_sub, auth_provider, display_name, avatar_url, role, timezone, created_at, updated_at, status |
| | **RefreshToken** `(*)` | id, user_id, family_id, token_hash, device_info, expires_at, revoked_at, created_at |
| | **PasswordResetToken** `(*)` | id, user_id, code_hash, attempts, expires_at, used_at, created_at |
| | **EmailVerificationToken** `(*)` | id, user_id, code_hash, attempts, expires_at, used_at, created_at |
| | **UserHealthProfile** | user_id, gender, birth_date, height_cm, weight_kg, activity_level, goal_type, target_weight_kg, weekly_rate_kg, bmi, bmr, tdee, daily_calorie_goal, protein/carbs/fat goal, water_goal_ml, allergies[] |
| Theo dõi | **WeightLog** | id, user_id, weight_kg, logged_at, note, client_uuid |
| | **WaterLog** | id, user_id, amount_ml, logged_at, client_uuid |
| Dinh dưỡng | **Food** | id, name, name_unaccented, category, serving_desc, serving_grams, calories, protein_g, carbs_g, fat_g, barcode (unique, có chỉ mục), image_url, price_vnd, allergens[], source (`SYSTEM`/`NIN`/`OFF`/`USER`), off_synced_at, verified, owner_user_id (null nếu món hệ thống), vietfood_class_id |
| | **BarcodeMiss** `(*)` | barcode (PK), checked_at, expires_at — đệm kết quả "không tìm thấy" từ Open Food Facts |
| | **UserFoodLog** | id, user_id, food_id, meal_type, quantity/grams, calories, protein_g, carbs_g, fat_g (snapshot), source, image_url, ai_confidence, logged_at, client_uuid |
| Thực đơn | **MealPlan** | id, user_id, plan_date, target_calories, budget_vnd, total_calories, total_cost, status |
| | **MealPlanFood** | id, meal_plan_id, food_id, meal_type, grams, calories |
| Tổng hợp | **DailySummary** | user_id, date, calories_in, calories_burned, protein_g, carbs_g, fat_g, water_ml, steps, weight_kg, goal_met (bool) — khoá (user_id, date) |
| Vận động | **ActivityLog** | id, user_id, activity_type, met, duration_min, distance_km, calories_burned, source, started_at, client_uuid |
| | **HealthSyncLog** | id, user_id, provider, data_type, external_id, value, start_time, end_time, synced_at |
| Thông báo | **Notification** | id, user_id, type, title, body, data(JSON), is_read, created_at |
| | **DeviceToken** `(*)` | id, user_id, token, platform, last_seen |
| Chatbot | **Conversation** | id, user_id, title, created_at, updated_at |
| | **ChatMessage** | id, conversation_id, role (user/assistant), content, created_at |
| Động lực | **Streak** | user_id, current_streak, longest_streak, last_completed_date |
| | **Badge** | id, code, name, description, icon_url, condition |
| | **UserBadge** | user_id, badge_id, earned_at |
| | **Challenge** | id, title, description, metric, target_value, start_date, end_date |
| | **UserChallenge** | user_id, challenge_id, progress, status, joined_at, completed_at |
| Xã hội | **Friendship** | id, requester_id, addressee_id, status (pending/accepted/declined), created_at |

> Lưu ý: đề cương nêu **UserFoodLog–Food** là thực thể ghi log qua ảnh AI hoặc quét mã vạch; thực đơn dùng **MealPlan/MealPlanFood**.

### 6.2. Quan hệ chính

- User 1–1 UserHealthProfile; User 1–N RefreshToken, PasswordResetToken, EmailVerificationToken, WeightLog, WaterLog, UserFoodLog, ActivityLog, HealthSyncLog, Notification, MealPlan, Conversation, UserChallenge, UserBadge.
- Food 1–N UserFoodLog; Food 1–N MealPlanFood; MealPlan 1–N MealPlanFood.
- Conversation 1–N ChatMessage.
- User N–N User qua Friendship; User N–N Badge qua UserBadge; User N–N Challenge qua UserChallenge.
- DailySummary: duy nhất theo (user_id, date).

### 6.3. Quy tắc dữ liệu `(*)`

- Khoá chính UUID hoặc bigint; các bảng log có `client_uuid` duy nhất theo user để đồng bộ idempotent.
- Thời gian lưu UTC; ngày thống kê tính theo múi giờ người dùng.
- Xoá mềm (soft delete) cho log để phục vụ đồng bộ; xoá cứng khi người dùng xoá tài khoản.
- Chỉ mục: (user_id, logged_at) cho các bảng log; (user_id, date) cho DailySummary; chỉ mục tìm kiếm không dấu cho Food (ví dụ `pg_trgm`); chỉ mục duy nhất cho `Food.barcode` và khoá chính `barcode` của `BarcodeMiss`.
- Lược đồ quản lý bằng **Flyway** (`classpath:db/migration`, tên tệp `V<số phiên bản>__<mô_tả>.sql`); Hibernate đặt `ddl-auto: validate` (chỉ kiểm tra, không tự sinh bảng); **không sửa** migration đã chạy, chỉ tạo phiên bản mới `[README]`.
- Ràng buộc kiểm tra: lượng calo/macro không âm; cân nặng, chiều cao nằm trong miền hợp lý.

### 6.4. Lưu trữ tệp

- Object Storage (**MinIO**, tương thích S3; cấu hình qua `STORAGE_*`) lưu ảnh món ăn (log ảnh, ảnh món) và ảnh đại diện; CSDL chỉ lưu URL/khoá đối tượng `[README]`.
- Truy cập ảnh riêng tư qua URL ký có thời hạn `(*)`; đặt vòng đời xoá ảnh khi xoá tài khoản.

### 6.5. Dữ liệu ban đầu (seed)

- 68 món VietFood68 kèm calo, Protein, Carbs, Fat, khẩu phần chuẩn, nhóm món.
- Danh mục hoạt động và hệ số MET `(*)`.
- Danh mục chất dị ứng chuẩn `(*)`.
- (Tuỳ chọn, mức C) Tập con Open Food Facts Việt Nam nhập sẵn: chỉ sản phẩm có mã vạch và đủ calo/macro; ghi `source = OFF`. `(*)`
- Bộ huy hiệu và thử thách mẫu `(*)`.

---

## 7. Yêu cầu giao diện

### 7.1. Danh sách màn hình `(*)`

| Nhóm | Màn hình |
|---|---|
| Khởi đầu | Splash, Giới thiệu (onboarding), Đăng nhập, Đăng ký, Xác minh email (nhập mã OTP, gửi lại mã), Quên mật khẩu |
| Thiết lập | Nhập thông tin thể trạng, Mục tiêu cân nặng, Dị ứng, Kết quả BMI/TDEE/mục tiêu calo |
| Trang chủ | Dashboard (calo, macro, nước, bước chân, streak) |
| Dinh dưỡng | Nhật ký ngày, Chụp ảnh nhận diện, Kết quả nhận diện (xác nhận/sửa), Tìm món, Quét mã vạch, Chi tiết món, Tạo/sửa món tuỳ chỉnh, Ghi nước |
| Kế hoạch | Tạo thực đơn, Chi tiết thực đơn, Danh sách thực đơn đã lưu |
| Trợ lý | Danh sách hội thoại, Màn chat |
| Vận động | Danh sách hoạt động, Thêm hoạt động, Đồng bộ sức khoẻ, Nhật ký cân nặng |
| Thống kê | Biểu đồ calo, macro, cân nặng (Ngày/Tuần/Tháng), Dự đoán mục tiêu |
| Động lực | Streak, Huy hiệu, Thử thách, Bạn bè, Bảng xếp hạng |
| Hệ thống | Thông báo, Cài đặt nhắc nhở, Hồ sơ cá nhân, Cài đặt chung |

### 7.2. Điều hướng

Thanh tab dưới gồm: **Trang chủ — Nhật ký — (nút chụp ảnh nổi bật) — Thống kê — Cá nhân**; các mục Trợ lý, Vận động, Thử thách truy cập từ Trang chủ/Cá nhân. `(*)`

### 7.3. Nguyên tắc UI/UX `(*)`

- Ghi một bữa ăn bằng ảnh tối đa 3 thao tác sau khi chụp.
- Trạng thái rõ ràng: đang tải, rỗng, lỗi, ngoại tuyến (biểu ngữ “Chưa đồng bộ”).
- Dùng màu và biểu tượng nhất quán cho 3 macro; vòng tiến độ calo trên dashboard.
- Hỗ trợ cỡ chữ hệ thống, độ tương phản đủ (WCAG AA), nhãn truy cập cho thành phần chính.
- Nhập liệu có kiểm tra tức thì và thông báo lỗi bằng tiếng Việt dễ hiểu.

---

## 8. Yêu cầu giao tiếp ngoài và API

### 8.1. Giao diện phần cứng/phần mềm ngoài

| Đối tác | Mục đích | Ghi chú |
|---|---|---|
| Camera thiết bị | Chụp ảnh món ăn, quét mã vạch | Xin quyền camera; xử lý từ chối quyền |
| Health Connect / HealthKit | Bước chân, nhịp tim | Xin quyền theo từng loại dữ liệu |
| Google OAuth2 | Đăng nhập Google | Xác minh ID token phía server |
| FCM + Expo Notifications | Thông báo đẩy | Quản lý token thiết bị; Backend dùng service account qua `FCM_CREDENTIALS_PATH` `[README]` |
| Open Food Facts `(*)` | Nguồn dự phòng cho tra cứu mã vạch (Q2, cách C) | **Chỉ Backend gọi** (mobile không gọi); có timeout, giới hạn tần suất, lưu đệm kết quả vào `Food`; chỉ gửi mã vạch; ghi nguồn theo ODbL; cấu hình `OFF_*` |
| Object Storage (MinIO) | Lưu ảnh | URL ký có thời hạn; cấu hình `STORAGE_ENDPOINT/BUCKET/ACCESS_KEY/SECRET_KEY` `[README]` |
| SMTP | Gửi email OTP (xác minh email, khôi phục mật khẩu) | `MAIL_HOST/USERNAME/PASSWORD` `[README]` |

### 8.2. API Backend chính (tổng quan)

Tiền tố `/api` `[README]`; xác thực `Authorization: Bearer <JWT>` cho mọi API trừ nhóm `auth` (riêng `change-password`, `verify-email`, `resend-verification` trong nhóm này vẫn cần Bearer). Tài liệu API: Swagger UI `/swagger-ui.html`, OpenAPI JSON `/v3/api-docs` `[README]`.

Ký hiệu: **✔** = endpoint đã nêu trong README (đường dẫn chính thức, đầy đủ prefix `/api`); không có ký hiệu = đề xuất `(*)`, cần cập nhật khi cài đặt thực tế.

| Nhóm | Endpoint (sau tiền tố `/api`) |
|---|---|
| auth | `POST /auth/register` ✔, `POST /auth/login` ✔, `POST /auth/google` ✔, `POST /auth/forgot-password` ✔, `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/reset-password`, `POST /auth/change-password`, `POST /auth/verify-email`, `POST /auth/resend-verification` |
| users / profile | `GET /users/me/profile` ✔ (BMI, TDEE, mục tiêu calo), `PUT /users/me/profile`, `GET/PUT /users/me`, `POST /users/me/avatar`, `DELETE /users/me` |
| foods | `GET /foods/barcode/{code}` ✔ (tra CSDL nội bộ → Open Food Facts → 404; response có `source`; 404 thì mobile hiện nút "Tạo món từ mã vạch này"), `GET /foods?query=&category=&page=`, `GET /foods/{id}`, `POST/PUT/DELETE /foods/custom` |
| food-logs | `POST /food-logs` ✔, `POST /food-logs/recognize` ✔ (ảnh → AI), `GET /food-logs?date=`, `PUT/DELETE /food-logs/{id}` |
| water | `GET /water-logs?date=`, `POST /water-logs`, `DELETE /water-logs/{id}` |
| weight | `GET /weight-logs?from=&to=`, `POST /weight-logs`, `PUT/DELETE /weight-logs/{id}` |
| activities | `GET/POST /activities`, `PUT/DELETE /activities/{id}`, `POST /health-sync` |
| meal-plans | `POST /meal-plans/generate`, `GET /meal-plans`, `GET/DELETE /meal-plans/{id}`, `POST /meal-plans/{id}/apply`, `POST /meal-plans/{id}/swap` |
| chat | `POST /chat/messages` ✔, `GET /chat/conversations`, `GET /chat/conversations/{id}/messages`, `DELETE /chat/conversations/{id}` |
| reports | `GET /reports/daily` ✔ (tổng hợp dinh dưỡng theo ngày), `GET /reports/dashboard`, `GET /reports/summary?range=day\|week\|month`, `GET /reports/weight-forecast` |
| gamification | `GET /streak`, `GET /badges`, `GET /challenges`, `POST /challenges/{id}/join`, `GET /leaderboard/friends` |
| friends | `GET /friends`, `POST /friends/requests`, `PUT /friends/requests/{id}` (accept/decline), `DELETE /friends/{id}`, `GET /users/search?q=` |
| notifications | `GET /notifications`, `PUT /notifications/{id}/read`, `POST /devices` (đăng ký token), `PUT /notification-settings` |
| sync | `POST /sync/push`, `GET /sync/pull?since=` (đồng bộ ngoại tuyến) |

> README ghi rõ danh sách endpoint chỉ là ví dụ và cần cập nhật theo API thực tế; bảng trên là điểm xuất phát cho thiết kế API. AI Service có API riêng (`/ai/v1/...`, xem mục 5.5) và không được gọi trực tiếp từ mobile.

**WebSocket `(*)`:** kênh `/ws` (xác thực JWT) cho: bảng xếp hạng bạn bè, luồng trả lời chatbot, thông báo trực tiếp.

**Quy ước API `(*)`:** mã trạng thái HTTP chuẩn; lỗi dạng `{ "code": "...", "message": "...", "details": [...] }`; phân trang `page/size` hoặc `cursor`; tài liệu OpenAPI/Swagger tự sinh.

---

## 9. Yêu cầu phi chức năng

### 9.1. Hiệu năng `(*)`

| Mã | Yêu cầu |
|---|---|
| NFR-PERF-01 | 95% API thông thường phản hồi ≤ 500 ms (không tính nhận diện ảnh và chatbot) với tải mục tiêu. |
| NFR-PERF-02 | Nhận diện ảnh: tổng thời gian từ lúc gửi đến lúc có kết quả ≤ 5 giây (mạng 4G). |
| NFR-PERF-03 | Dashboard hiển thị ≤ 2 giây; mở ứng dụng đến màn hình dùng được ≤ 3 giây (khởi động nguội trên thiết bị tầm trung). |
| NFR-PERF-04 | Hệ thống đáp ứng tối thiểu 100 người dùng đồng thời trong môi trường thử nghiệm. |
| NFR-PERF-05 | Cập nhật bảng xếp hạng qua WebSocket ≤ 3 giây. |

### 9.2. Bảo mật `(*)`

| Mã | Yêu cầu |
|---|---|
| NFR-SEC-01 | Toàn bộ giao tiếp qua HTTPS/WSS. |
| NFR-SEC-02 | Mật khẩu băm BCrypt (hoặc Argon2); không ghi log dữ liệu nhạy cảm. |
| NFR-SEC-03 | JWT ký bằng khoá bí mật cấu hình ngoài mã nguồn; refresh token có thể thu hồi. |
| NFR-SEC-04 | Phân quyền theo vai trò; mỗi API chỉ truy cập dữ liệu của chính người dùng (kiểm tra quyền sở hữu tài nguyên, chống IDOR). |
| NFR-SEC-05 | Kiểm tra và làm sạch dữ liệu đầu vào; tham số hoá truy vấn (JPA) chống SQL injection; giới hạn kích thước và kiểm tra loại tệp tải lên. |
| NFR-SEC-06 | Giới hạn tần suất (rate limit) cho đăng nhập, đăng ký, quên mật khẩu, gửi lại mã xác minh email, nhận diện ảnh và chatbot. |
| NFR-SEC-07 | AI Service chỉ truy cập được từ mạng nội bộ/Backend. |
| NFR-SEC-08 | Bí mật (khoá Google, FCM, Object Storage, DB, JWT, SMTP) quản lý bằng biến môi trường/secret manager; **không** commit `.env`, file service account FCM hay thông tin nhạy cảm lên Git `[README]`. |
| NFR-SEC-09 | Mã vạch được kiểm tra định dạng trước khi gọi ra ngoài; lời gọi Open Food Facts chỉ do Backend thực hiện, có timeout và giới hạn tần suất; không gửi dữ liệu định danh người dùng. `(*)` |

### 9.3. Quyền riêng tư và dữ liệu sức khoẻ `(*)`

- Thu thập tối thiểu cần thiết; hiển thị rõ mục đích khi xin quyền (camera, vị trí, sức khoẻ, thông báo).
- Người dùng có thể xuất và xoá toàn bộ dữ liệu của mình.
- Dữ liệu sức khoẻ không chia sẻ cho bên thứ ba; dữ liệu bạn bè chỉ hiển thị theo thiết lập quyền riêng tư.
- Khi tra Open Food Facts, Backend chỉ gửi mã vạch (cùng User-Agent của ứng dụng); không gửi định danh hay dữ liệu cá nhân của người dùng. `(*)`
- Ảnh người dùng chỉ dùng để cải thiện mô hình khi có sự đồng ý rõ ràng (FR-NUT-13).
- Có điều khoản sử dụng và miễn trừ: ứng dụng không thay thế tư vấn y khoa.

### 9.4. Độ tin cậy và khả dụng `(*)`

- Không mất dữ liệu người dùng ghi khi ngoại tuyến (lưu cục bộ bền vững đến khi đồng bộ).
- Mục tiêu khả dụng 99% trong môi trường demo; có `health check` cho Backend và AI Service.
- Sao lưu CSDL định kỳ; có thể khôi phục.
- Xử lý lỗi nhã nhặn khi AI Service hoặc dịch vụ ngoài không khả dụng (thông báo + cho phép nhập tay).

### 9.5. Khả năng sử dụng và tương thích `(*)`

- Giao diện tiếng Việt, dễ dùng cho người không chuyên; học cách dùng chính trong ≤ 5 phút.
- Chạy ổn định trên Android 8.0+ và iOS 15+; hỗ trợ màn hình 5–7 inch, chế độ dọc.
- Hỗ trợ truy cập cơ bản (cỡ chữ động, nhãn đọc màn hình, độ tương phản).

### 9.6. Khả năng bảo trì và mở rộng `(*)`

- Mã nguồn tổ chức theo layer controller → service → repository (kèm dto, entity, mapper, exception, config, common) như mục 3.4 `[README]`; có quy ước đặt tên và định dạng mã. `(*)`
- Kiểm thử tự động cho lõi nghiệp vụ bằng JUnit 5 + Mockito (độ phủ mục tiêu ≥ 60% cho tầng service quan trọng `(*)`); integration test dùng Testcontainers (PostgreSQL) nên cần Docker đang chạy `[README]`.
- Cấu hình theo profile `dev`/`prod`; build bằng Gradle Wrapper; đóng gói Docker/Docker Compose `[README]`; CI chạy `./gradlew check` `(*)`.
- Mô hình AI có thể thay phiên bản mà không đổi mã Backend (đổi file ONNX + khai báo phiên bản).

### 9.7. Quan sát và ghi nhật ký `(*)`

- Nhật ký có cấu trúc kèm `request_id`/`trace_id` xuyên suốt Mobile → Backend → AI Service.
- Chỉ số cơ bản: số yêu cầu, lỗi, độ trễ, tỉ lệ nhận diện thất bại.

### 9.8. Địa phương hoá `(*)`

- Tiếng Việt là ngôn ngữ chính; đơn vị mét (cm, kg), kcal, ml; tiền tệ VNĐ; múi giờ mặc định Asia/Ho_Chi_Minh.

---

## 10. Kế hoạch và mốc tiến độ

Mốc dự kiến `(*)`; đề cương chỉ xác định giao đề tài 09/2026 và hoàn thiện 12/2026.

| Giai đoạn | Thời gian đề xuất | Kết quả bàn giao |
|---|---|---|
| 1. Khởi động, đặc tả, thiết kế | Tuần 1–3 (cuối 09 – đầu 10/2026) | Tài liệu yêu cầu, ERD, thiết kế API, wireframe, kiến trúc |
| 2. Nền tảng | Tuần 3–5 | Dự án Spring Boot + CSDL + migration; AUTH; khung ứng dụng Expo; CI cơ bản |
| 3. Dữ liệu và AI | Tuần 2–8 (song song) | Dữ liệu VietFood68 + bảng dinh dưỡng; mô hình YOLOv11m, ONNX; FastAPI nhận diện |
| 4. Chức năng lõi | Tuần 5–9 | PROFILE, NUTRI (ghi log, tra cứu, mã vạch, tuỳ chỉnh), nước, cân nặng, ACT, DailySummary |
| 5. Thông minh và xã hội | Tuần 8–11 | Thực đơn AI, chatbot, REPORT, GAME, thông báo, WebSocket |
| 6. Ngoại tuyến, tích hợp, hoàn thiện | Tuần 11–13 | Đồng bộ ngoại tuyến, kiểm thử tích hợp, tối ưu hiệu năng, sửa lỗi |
| 7. Báo cáo và bảo vệ | Tuần 13–15 (đến 12/2026) | Báo cáo đồ án, demo, bản build ứng dụng |

**Đề xuất cắt phạm vi nếu chậm tiến độ `(*)`:** ưu tiên giữ các yêu cầu **M**; hoãn các yêu cầu **C** (xuất báo cáo, lộ trình GPS, i18n, giao diện tối) trước, sau đó đến một số yêu cầu **S** (thực đơn 7 ngày, dự đoán xu hướng nâng cao).

---

## 11. Kiểm thử và tiêu chí nghiệm thu

### 11.1. Chiến lược kiểm thử `(*)`

| Mức | Nội dung | Công cụ gợi ý |
|---|---|---|
| Đơn vị | Công thức BMI/BMR/TDEE, tính calo/macro, tính streak, sinh thực đơn, tính xu hướng cân nặng | JUnit 5, Mockito, pytest, Jest |
| Tích hợp | API + CSDL; Backend ↔ AI Service; xác thực JWT/Google | Spring Test, Testcontainers |
| Giao diện/E2E | Luồng đăng ký → thiết lập hồ sơ → ghi log → xem thống kê | Detox hoặc Maestro |
| Mô hình AI | Đánh giá trên tập test; kiểm thử với ảnh chụp thực tế bằng điện thoại | Script đánh giá riêng |
| Hiệu năng | Tải API và nhận diện | k6 hoặc JMeter |
| Bảo mật | Kiểm tra phân quyền, injection, rate limit | Kiểm thử thủ công + công cụ quét |
| Ngoại tuyến | Mất mạng/ có mạng lại, đồng bộ lặp lại | Kịch bản thủ công + tự động |
| Chấp nhận | Người dùng thử nghiệm (UAT) | Bảng khảo sát ngắn |

### 11.2. Tiêu chí nghiệm thu tổng thể `(*)`

Sản phẩm được coi là đạt khi:

1. 100% yêu cầu mức **M** hoạt động đúng trên thiết bị Android thật (và iOS nếu có điều kiện thiết bị), không có lỗi nghiêm trọng (blocker/critical) còn mở.
2. Luồng chính chạy trọn vẹn từ đầu đến cuối: đăng ký → hồ sơ → chụp ảnh nhận diện → lưu log → dashboard/thống kê → thực đơn → chatbot → streak/thử thách → thông báo.
3. Mô hình nhận diện đạt chỉ tiêu ở mục 5.3 và hoạt động qua pipeline độc lập (không gọi API nhận diện bên thứ ba).
4. Đồng bộ ngoại tuyến chạy đúng, không mất/trùng dữ liệu trong kịch bản kiểm thử.
5. Đạt các chỉ tiêu hiệu năng và bảo mật chính ở mục 9.
6. Có tài liệu: SRS, thiết kế (kiến trúc, ERD, API), hướng dẫn cài đặt/triển khai, báo cáo huấn luyện/đánh giá mô hình, báo cáo kiểm thử.

---

## 12. Rủi ro, giả định, ràng buộc

### 12.1. Rủi ro và biện pháp `(*)`

| # | Rủi ro | Mức | Biện pháp |
|---|---|---|---|
| R1 | Chất lượng mô hình thấp với một số món (nhiều món nhìn giống nhau, ánh sáng kém) | Cao | Tăng cường dữ liệu, thu thêm ảnh chụp thực tế, phân tích lớp yếu; luôn cho người dùng sửa kết quả |
| R2 | Ước lượng khẩu phần từ ảnh 2D thiếu chính xác | Cao | Dùng khẩu phần chuẩn × hệ số bị chặn, hiển thị là ước lượng, cho chỉnh tay |
| R3 | Dữ liệu dinh dưỡng thiếu/không đồng nhất giữa nguồn | Trung bình | Ưu tiên bảng NIN, ghi rõ nguồn; kiểm tra chéo với FAO/INFOODS |
| R4 | Thiếu thiết bị iOS để kiểm thử HealthKit | Trung bình | Ưu tiên Android; iOS ở mức “cố gắng hỗ trợ” nếu không có thiết bị |
| R5 | Thay đổi chính sách/API Google (Google Fit, Health Connect) | Trung bình | Dùng Health Connect; tách lớp trừu tượng cho nguồn dữ liệu sức khoẻ |
| R6 | Phạm vi lớn so với nhóm 3 người, 3 tháng | Cao | Ưu tiên MoSCoW, làm song song AI–Backend–Mobile, cắt phạm vi theo mục 11 |
| R7 | Phụ thuộc dịch vụ ngoài (Open Food Facts, nguồn LLM) không ổn định; Open Food Facts có thể thiếu hoặc thiếu calo/macro với sản phẩm Việt Nam | Trung bình | Bộ nhớ đệm (kể cả "không tìm thấy"), timeout, dữ liệu dự phòng, thông báo lỗi nhã nhặn, cho phép tạo món từ mã vạch |
| R8 | Chi phí/tài nguyên hạ tầng hạn chế khi suy luận AI, chatbot | Trung bình | Dùng ONNX Runtime CPU, mô hình nhỏ/tối ưu, giới hạn tần suất |
| R9 | Rủi ro an toàn sức khoẻ khi gợi ý calo cực đoan | Trung bình | Ngưỡng calo tối thiểu, giới hạn tốc độ giảm cân, lời miễn trừ |
| R10 | Lộ dữ liệu cá nhân/sức khoẻ | Cao | Mục 9.2 và 9.3 |
| R11 | Chiếm email: kẻ xấu đăng ký trước bằng email của người khác (chưa xác minh), rồi chờ nạn nhân dùng Google hoặc khiến nạn nhân gặp lỗi “email đã tồn tại” | Trung bình | Xác minh email bằng OTP (FR-AUTH-09); liên kết Google vào tài khoản chưa xác minh thì xoá mật khẩu cũ và thu hồi token; nạn nhân lấy lại tài khoản bằng Google hoặc quên mật khẩu |

### 12.2. Giả định `(*)`

- Người dùng có điện thoại có camera và kết nối mạng khi dùng tính năng AI.
- Nhóm có đủ quyền sử dụng/ gán nhãn bộ dữ liệu VietFood68 cho mục đích học thuật.
- Có môi trường máy chủ/đám mây đủ để triển khai demo (Backend, AI, CSDL, Object Storage).
- Người dùng chủ yếu ăn món Việt thuộc phạm vi 68 lớp.

### 12.3. Ràng buộc

- Pipeline nhận diện và ước lượng calo do nhóm tự xây dựng, triển khai độc lập.
- Sử dụng bộ công nghệ đã nêu ở mục 3.2.
- Hoàn thiện trong 12/2026; ba thành viên.
- Tuân thủ chính sách của Google (OAuth, Health Connect), Apple (HealthKit) và các cửa hàng ứng dụng nếu phát hành.

---

## 13. Câu hỏi mở cần chốt

| # | Câu hỏi | Vì sao cần chốt |
|---|---|---|
| Q1 | Chatbot dùng mô hình ngôn ngữ nào (tự lưu trữ mô hình mã nguồn mở hay gọi dịch vụ LLM ngoài)? | Mục tiêu nêu “không phụ thuộc API bên thứ ba” cho nhận diện món ăn; chưa rõ có áp dụng cho chatbot không. Ảnh hưởng chi phí, độ trễ, quyền riêng tư. |
| Q2 | ~~Quét mã vạch dùng dữ liệu Open Food Facts bằng cách nhập sẵn vào CSDL (offline) hay gọi API khi chạy?~~ | **Đã chốt (20/10/2026): cách C — lai.** Tra CSDL nội bộ trước; chưa có thì Backend gọi Open Food Facts rồi lưu đệm; vẫn không có thì người dùng tạo món từ mã vạch. Xem FR-FD-07a/07b, FR-SYS-09, mục 8.1–8.2. Chờ giảng viên hướng dẫn xác nhận. |
| Q3 | Thực đơn AI sinh bằng thuật toán ràng buộc/tối ưu hoá, mô hình học máy, hay LLM? | Quyết định thiết kế, dữ liệu cần có (giá, nhãn dị ứng, nhóm món) và cách đánh giá. |
| Q4 | Định nghĩa chính xác “hoàn thành mục tiêu ngày” cho streak? | Ảnh hưởng logic streak, huy hiệu, xếp hạng. |
| Q5 | Bảng xếp hạng dựa trên tiêu chí nào (streak, bước chân, điểm thử thách)? Có chu kỳ đặt lại không? | Ảnh hưởng thiết kế dữ liệu và WebSocket. |
| Q6 | Ai phụ trách: đồng bộ ngoại tuyến, Health Connect/HealthKit phía mobile, WebSocket, triển khai/CI-CD, và khung ứng dụng mobile chung? | Đề cương chưa phân công các hạng mục này. |
| Q7 | Có hỗ trợ iOS không (HealthKit cần thiết bị/ tài khoản phát triển Apple)? | Ảnh hưởng phạm vi và kiểm thử. |
| Q8 | Có cần trang/ công cụ quản trị cho Admin không? | Ảnh hưởng phạm vi (mục 4.10). |
| Q9 | Tập món có mở rộng ngoài 68 lớp, và người dùng có thể “đóng góp ảnh” để huấn luyện lại không? | Ảnh hưởng vòng đời mô hình và yêu cầu pháp lý/riêng tư. |
| Q10 | Đề cương ghi ngày giao “09/20226”; xác nhận là **09/2026**? | Sai sót đánh máy trong đề cương gốc. |
| Q11 | Tên ứng dụng là **Kallo** (đề cương) hay **Kallo** (README, repo `kallo-backend`, package `com.healthcare.kallo`)? | Cần một tên duy nhất trong tài liệu, mã nguồn và giao diện. |
| Q12 | README nhắc `PROD_REDIS_HOST`, `PROD_REDIS_PASSWORD`, `PROD_ES_URIS` (Redis, Elasticsearch) nhưng bảng công nghệ không có hai thành phần này. Có dùng không? | Nếu có (ví dụ Redis cho cache/rate limit/thu hồi token, Elasticsearch cho tìm món không dấu) thì phải bổ sung vào kiến trúc và yêu cầu; nếu không thì bỏ khỏi cấu hình/README. |
| Q13 | ~~Có bắt buộc xác minh email (chặn dùng ứng dụng hoặc một số tính năng) cho tới khi nhập đúng OTP không?~~ | **Đã chốt (20/10/2026): bắt buộc.** Chưa xác minh thì mọi API nghiệp vụ trả 403 `EMAIL_NOT_VERIFIED` (trừ nhóm `auth` và `GET /users/me`). Xem FR-AUTH-09, AC7. |

---

## 14. Phụ lục

### Phụ lục A — Công thức tính toán `(*)`

**A.1. BMI**

```
BMI = cân_nặng_kg / (chiều_cao_m)^2
```

**A.2. BMR — Mifflin–St Jeor**

```
Nam:  BMR = 10·W + 6.25·H − 5·A + 5
Nữ:   BMR = 10·W + 6.25·H − 5·A − 161
(W: kg, H: cm, A: tuổi)
```

**A.3. TDEE**

```
TDEE = BMR × hệ_số_vận_động
```

| Mức vận động | Hệ số |
|---|---|
| Ít vận động (ngồi nhiều) | 1,2 |
| Nhẹ (1–3 buổi/tuần) | 1,375 |
| Vừa (3–5 buổi/tuần) | 1,55 |
| Nhiều (6–7 buổi/tuần) | 1,725 |
| Rất nhiều (lao động nặng/ tập 2 lần/ngày) | 1,9 |

**A.4. Mục tiêu calo hằng ngày**

```
Giữ cân:  calo_mục_tiêu = TDEE
Giảm cân: calo_mục_tiêu = TDEE − (tốc_độ_kg_tuần × 7700 / 7)
Tăng cân: calo_mục_tiêu = TDEE + (tốc_độ_kg_tuần × 7700 / 7)
```

Ràng buộc an toàn mặc định (cấu hình được): tốc độ thay đổi tối đa khoảng 0,5–1 kg/tuần; mức thâm hụt tối đa khoảng 1000 kcal/ngày; calo mục tiêu không thấp hơn BMR hoặc ngưỡng tối thiểu (khoảng 1200 kcal với nữ, 1500 kcal với nam). Các ngưỡng cần đối chiếu với “Hướng dẫn chẩn đoán và điều trị thừa cân, béo phì” của Bộ Y tế trước khi chốt.

**A.5. Chia macro mặc định**

| Mục tiêu | Protein | Carbs | Fat |
|---|---|---|---|
| Giảm cân | 25–30% | 40–45% | 25–30% |
| Giữ cân | 20% | 50% | 30% |
| Tăng cân | 20–25% | 50–55% | 25% |

Quy đổi: Protein 4 kcal/g, Carbs 4 kcal/g, Fat 9 kcal/g.

**A.6. Calo tiêu hao hoạt động**

```
calo = MET × cân_nặng_kg × thời_lượng_giờ
```

Ví dụ MET tham chiếu: đi bộ nhanh ≈ 3,5; chạy bộ (8 km/h) ≈ 8,3; đạp xe vừa ≈ 6–8; bơi ≈ 6–8; yoga ≈ 2,5–3; cầu lông ≈ 5,5 (giá trị cuối cùng theo Compendium of Physical Activities, lưu trong CSDL).

**A.7. Nước uống mặc định**

```
mục_tiêu_nước_ml ≈ 30–35 × cân_nặng_kg  (cấu hình được)
```

**A.8. Xu hướng cân nặng**

- Làm mịn bằng trung bình động hàm mũ (EMA) hoặc hồi quy tuyến tính trên cửa sổ 14–28 ngày gần nhất.
- Tốc độ thay đổi (kg/tuần) = hệ số góc × 7.
- Ngày dự kiến đạt mục tiêu = hôm nay + (cân_hiện_tại − cân_mục_tiêu) / tốc_độ; chỉ hiển thị khi dấu của tốc độ hướng tới mục tiêu và cửa sổ đủ dữ liệu.

### Phụ lục B — Ma trận truy vết yêu cầu ↔ mục tiêu đề cương

| Chức năng chính trong đề cương | Yêu cầu tương ứng |
|---|---|
| Quản lý tài khoản & hồ sơ sức khoẻ | FR-AUTH-01…09, FR-PRO-01…08 |
| Quản lý dinh dưỡng & bữa ăn | FR-NUT-01…13, FR-FD-01…07b |
| Lập kế hoạch bữa ăn & Trợ lý AI | FR-PLAN-01…06, FR-CHAT-01…06 |
| Theo dõi vận động & chỉ số cơ thể | FR-ACT-01…08, FR-NUT-11 |
| Báo cáo & thống kê | FR-REP-01…08 |
| Streak & tạo động lực | FR-GAM-01…07 |
| Tiện ích & hệ thống | FR-SYS-01…09 |
| Mô hình nhận diện món Việt (mục tiêu 2.1) | Mục 5; FR-NUT-02, FR-NUT-03 |

### Phụ lục C — Danh sách 68 lớp món ăn VietFood68

Danh sách lớp (nhãn) sẽ được điền theo tệp `data.yaml` của tập VietFood68 do thành viên phụ trách dữ liệu cung cấp; **đề cương chưa liệt kê** nên không điền ở bản nháp này.

| class_id | Tên món | Khẩu phần chuẩn | Calo | Protein (g) | Carbs (g) | Fat (g) | Nguồn |
|---|---|---|---|---|---|---|---|
| 0 | … | … | … | … | … | … | … |

### Phụ lục D — Lịch sử thay đổi tài liệu

| Phiên bản | Ngày | Tác giả | Nội dung |
|---|---|---|---|
| 1.0 | 15/10/2026 | Nhóm Kallo | Bản nháp đầu tiên, dựng từ Đề cương đồ án tốt nghiệp |
| 1.1 | 15/10/2026 | Nhóm Kallo | Đối chiếu README repository backend: chốt Java 21+/Spring Boot 4.x/Gradle/Flyway/MinIO, thêm mục 3.4 (cấu trúc mã nguồn, biến môi trường), đổi tiền tố API sang `/api` và căn chỉnh endpoint, đặt thời hạn token mặc định 15 phút / 7 ngày, thêm Q11–Q12 |
| 1.2 | 20/10/2026 | Nhóm Kallo | Chốt Q2 = cách C (tra mã vạch lai: CSDL nội bộ → Open Food Facts → tạo món tay): tách FR-FD-07 thành 07a/07b; thêm FR-SYS-09 (ghi nguồn ODbL), NFR-SEC-09; cập nhật 3.1, 3.4 (biến `OFF_*`), 4.3 (quy tắc, AC4–AC6), 6.1 (`Food.source`, `BarcodeMiss`), 6.5, 8.1, 8.2, R7 và Q2; sửa ghi chú v1.1 (Q11–Q12). Module AUTH: bổ sung xác minh email bằng OTP ngay sau đăng ký (FR-AUTH-09, AC5–AC6, quy tắc `email_verified` và liên kết Google an toàn); cập nhật phạm vi (1.3), thuật ngữ OTP, biến `OTP_HMAC_SECRET`, dữ liệu (6.1–6.2: `email_verified`, `google_sub`, bảng token), màn hình (7.1), API (8.2), NFR-SEC-06, rủi ro R11, câu hỏi Q13 (đã chốt: bắt buộc xác minh, AC7, lỗi 403 `EMAIL_NOT_VERIFIED`) |