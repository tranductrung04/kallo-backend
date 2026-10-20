# Kallo – Backend API

> Backend RESTful API (Spring Boot) cho **Kallo** – ứng dụng di động hỗ trợ chăm sóc sức khỏe và dinh dưỡng tích hợp AI: theo dõi bữa ăn, cân nặng, vận động; nhận diện món ăn Việt Nam qua ảnh và ước lượng calo.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen)
![Gradle](https://img.shields.io/badge/build-Gradle-02303A)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)
![Flyway](https://img.shields.io/badge/migration-Flyway-CC0200)

## Mục lục

- [Giới thiệu](#giới-thiệu)
- [Tính năng chính](#tính-năng-chính)
- [Kiến trúc hệ thống](#kiến-trúc-hệ-thống)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt & chạy dự án](#cài-đặt--chạy-dự-án)
- [Cấu hình](#cấu-hình)
- [Database & Migration](#database--migration)
- [API Documentation](#api-documentation)
- [Testing](#testing)
- [Docker](#docker)
- [License](#license)

## Giới thiệu

Kallo đóng vai trò như một trợ lý sức khỏe cá nhân, giúp người dùng theo dõi dinh dưỡng, kiểm soát cân nặng và vận động thể chất một cách tiện lợi, tự động hoá. Hệ thống tích hợp pipeline nhận diện món ăn Việt Nam (YOLO, tập dữ liệu VietFood68) và ước lượng calo, được xây dựng và triển khai độc lập, không phụ thuộc API AI bên thứ ba.

Repository này chứa **Backend chính** (Spring Boot). Các thành phần khác của hệ thống:

| Thành phần         | Công nghệ                          | Repository                 |
|--------------------|------------------------------------|----------------------------|
| Mobile app         | React Native, Expo, TypeScript     | _(cập nhật link)_          |
| AI service         | Python, FastAPI, ONNX Runtime      | _(cập nhật link)_          |
| **Backend (repo này)** | Java, Spring Boot 4.x          | –                          |

## Tính năng chính

- **Tài khoản & hồ sơ sức khoẻ:** đăng ký/đăng nhập (Email, Google OAuth2), khôi phục mật khẩu, thể trạng – mục tiêu cân nặng – dị ứng; tự tính BMI, TDEE, mục tiêu calo hàng ngày.
- **Dinh dưỡng & bữa ăn:** nhật ký bữa ăn (Sáng/Trưa/Tối/Bữa phụ), nhận diện món ăn qua ảnh, tra cứu món, quét mã vạch, món ăn tuỳ chỉnh.
- **Kế hoạch bữa ăn & trợ lý AI:** gợi ý thực đơn cá nhân hoá theo calo/dị ứng/ngân sách, chatbot tư vấn dinh dưỡng.
- **Vận động & chỉ số cơ thể:** nhật ký tập luyện, đồng bộ Google Fit / Apple Health, lịch sử cân nặng, nước uống.
- **Báo cáo & thống kê:** dashboard, biểu đồ calo/macro/cân nặng theo ngày/tuần/tháng, dự đoán xu hướng cân nặng.
- **Streak & động lực:** chuỗi ngày, thử thách, huy hiệu, kết bạn, bảng xếp hạng thời gian thực (WebSocket).
- **Tiện ích:** thông báo đẩy (FCM), nhắc nhở thông minh, hỗ trợ đồng bộ khi offline.

## Kiến trúc hệ thống

```
┌──────────────┐   REST / WebSocket   ┌──────────────────┐   HTTP (JSON)   ┌──────────────────┐
│ Mobile App   │ ───────────────────▶ │ Spring Boot API  │ ──────────────▶ │ AI Service       │
│ (Expo / RN)  │ ◀─────────────────── │ (repo này)       │ ◀────────────── │ (FastAPI + ONNX) │
└──────────────┘                      └───────┬──────────┘                 └──────────────────┘
                                              │
                          ┌───────────────────┼────────────────────┐
                          ▼                   ▼                    ▼
                    PostgreSQL        Cloud Object Storage   Firebase Cloud
                    (Flyway)          (ảnh món ăn, avatar)   Messaging (FCM)
```

## Công nghệ sử dụng

| Thành phần      | Công nghệ                                              |
|-----------------|--------------------------------------------------------|
| Ngôn ngữ        | Java 21                                               |
| Framework       | Spring Boot 4.x                                        |
| Bảo mật         | Spring Security, JWT, Google OAuth2                    |
| ORM             | Spring Data JPA / Hibernate                            |
| Database        | PostgreSQL                                             |
| Migration       | Flyway                                                 |
| Build tool      | Gradle (Gradle Wrapper)                                |
| Real-time       | Spring WebSocket                                       |
| Thông báo đẩy   | Firebase Cloud Messaging (FCM)                         |
| Lưu trữ tệp     | MinIO              |
| API docs        | SpringDoc OpenAPI (Swagger UI)                         |
| Testing         | JUnit 5, Mockito, Testcontainers                       |
| Container       | Docker, Docker Compose                                 |

## Cấu trúc thư mục

```
kallo-backend/
├── src/
│   ├── main/
│   │   ├── java/com/healthcare/kallo/
│   │   │   ├── KalloBackendApplication.java
│   │   │   ├── common/            # Hằng số, response wrapper, tiện ích dùng chung
│   │   │   ├── config/            # Security, CORS, WebSocket, OpenAPI, Storage, FCM
│   │   │   ├── controller/        # REST controllers
│   │   │   ├── dto/               # Request / Response DTO
│   │   │   ├── model/            # JPA entities
│   │   │   ├── exception/         # Custom exception & global exception handler
│   │   │   ├── mapper/            # Chuyển đổi Entity <-> DTO
│   │   │   ├── repository/        # Spring Data JPA repositories
│   │   │   └── service/           # Business logic (interface)
│   │   │       └── impl/          # Cài đặt service
│   │   └── resources/
│   │       ├── db/migration/      # Flyway: V1__init_schema.sql, V2__...
│   │       ├── static/
│   │       ├── templates/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/com/healthcare/kallo/
├── gradle/wrapper/
├── .gitattributes
├── .gitignore
├── build.gradle
├── docker-compose.yml
├── gradlew
├── gradlew.bat
├── HELP.md
├── LICENSE.txt
├── README.md
└── settings.gradle
```

> Dự án tổ chức theo **layer** (controller → service → repository), các entity chính được liệt kê ở mục [Database & Migration](#database--migration).

## Yêu cầu hệ thống

- JDK 21
- PostgreSQL 17 (hoặc chạy qua Docker)
- Docker & Docker Compose (tuỳ chọn)
- Không cần cài Gradle riêng – dùng `./gradlew`

## Cài đặt & chạy dự án

### 1. Clone repository

```bash
git clone https://github.com/tranductrung04/kallo-backend.git
cd kallo-backend
```

### 2. Cấu hình môi trường

```bash
cp .env.example .env
```

Cập nhật thông tin database, JWT secret, Google OAuth2, FCM, storage trong file `.env` (xem [Cấu hình](#cấu-hình)).

### 3. Khởi động PostgreSQL (nếu chưa có)

```bash
docker compose up -d postgres
```

### 4. Build

```bash
./gradlew clean build
```

### 5. Chạy ứng dụng

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Ứng dụng chạy tại: http://localhost:8080  
Flyway sẽ tự động chạy migration khi ứng dụng khởi động.

## Cấu hình

Các biến môi trường chính:

| Biến                      | Mô tả                                              | Mặc định / Ví dụ                                  |
|---------------------------|----------------------------------------------------|---------------------------------------------------|
| `APP_NAME`                | Tên project, dùng đặt tên container (Docker)       | `kallo-backend`                                   |
| `SPRING_PROFILES_ACTIVE`  | Profile đang chạy                                  | `dev` (Docker Compose đặt `prod`)                 |
| `SERVER_PORT`             | Cổng ứng dụng (profile dev)                        | `8080`                                            |
| `PROD_SERVER_PORT`        | Cổng ứng dụng (profile prod)                       | `8080`                                            |
| `DB_NAME`                 | Tên database                                       | _(bắt buộc)_                                        |
| `DB_URL`                  | JDBC URL, chỉ dùng khi chạy local (tuỳ chọn)       | `jdbc:postgresql://localhost:5432/<db_name>`      |
| `DB_USERNAME`             | Tài khoản database                                 | _(bắt buộc)_                                        |
| `DB_PASSWORD`             | Mật khẩu database (bắt buộc với Docker)            | _(bắt buộc)_                                         |
| `PROD_REDIS_PASSWORD`     | Mật khẩu Redis (để trống nếu không đặt)            | _(trống)_                                         |
| `JWT_SECRET`              | Khoá ký JWT                                        | _(bắt buộc)_                                      |
| `JWT_ACCESS_EXPIRATION`   | Thời hạn access token (ms)                         | `900000`                                          |
| `JWT_REFRESH_EXPIRATION`  | Thời hạn refresh token (ms)                        | `604800000`                                       |
| `JWT_REFRESH_REUSE_GRACE` | Cửa sổ ân hạn khi refresh token bị gửi đồng thời (ms) | `3000`                                         |
| `GOOGLE_CLIENT_ID`        | Danh sách client ID Google, phân cách bằng dấu phẩy (Web/Android/iOS) | Bắt buộc khi khởi động ứng dụng |
| `GOOGLE_JWKS_URI`         | URL JWKS dùng để xác minh Google ID token          | _(bắt buộc)_   |
| `AI_SERVICE_URL`          | URL của AI service (FastAPI)                       | `http://localhost:8000`                           |
| `FCM_CREDENTIALS_PATH`    | Đường dẫn file service account FCM                 | _(bắt buộc nếu bật push)_                         |
| `STORAGE_ENDPOINT`        | Endpoint Object Storage (MinIO)                    | `http://localhost:9000`                           |
| `STORAGE_BUCKET`          | Tên bucket lưu ảnh                                 | _(bắt buộc)_                                |
| `STORAGE_ACCESS_KEY`      | Access key                                         | _(bắt buộc)_                                      |
| `STORAGE_SECRET_KEY`      | Secret key                                         | _(bắt buộc)_                                      |
| `MAIL_HOST`               | SMTP host gửi mail khôi phục mật khẩu              | _(bắt buộc)_                                      |
| `MAIL_USERNAME`           | Tài khoản SMTP                                     | _(bắt buộc)_                                      |
| `MAIL_PASSWORD`           | Mật khẩu SMTP                                      | _(bắt buộc)_                                      |

> Khi chạy bằng Docker Compose, các biến `PROD_DB_URL`, `PROD_DB_USERNAME`, `PROD_DB_PASSWORD`,
> `PROD_REDIS_HOST`, `PROD_ES_URIS`... đã được `docker-compose.yml` thiết lập sẵn, không cần khai trong `.env`.
>
> Spring Boot không tự đọc file `.env`: khi chạy `./gradlew bootRun`, hãy export biến vào shell
> hoặc dùng plugin EnvFile của IntelliJ.
> 
> **Lưu ý:** Không commit `.env`, file service account FCM hay bất kỳ thông tin nhạy cảm nào lên Git.

## Database & Migration

Dự án dùng **Flyway** để quản lý schema; Hibernate chỉ validate, không tự sinh bảng:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Quy ước đặt tên: `V<số phiên bản>__<mô_tả>.sql`, ví dụ `V1__init_schema.sql`, `V2__add_streak_tables.sql`.  
**Không sửa** file migration đã chạy – hãy tạo phiên bản mới.

Các nhóm thực thể chính:

| Nhóm            | Thực thể                                                         |
|-----------------|------------------------------------------------------------------|
| Người dùng      | `User`, `UserHealthProfile`                                      |
| Theo dõi        | `WeightLog`, `WaterLog`, `ActivityLog`, `HealthSyncLog`          |
| Dinh dưỡng      | `Food`, `UserFoodLog`, `MealPlan`, `MealPlanFood`, `DailySummary`|
| Chatbot         | `Conversation`, `ChatMessage`                                    |
| Động lực        | `Streak`, `Badge`, `UserBadge`, `Challenge`, `UserChallenge`     |
| Xã hội & hệ thống | `Friendship`, `Notification`                                   |

```bash
# Xem trạng thái migration (nếu cấu hình plugin Flyway cho Gradle)
./gradlew flywayInfo
```

## API Documentation

- Đặc tả API (OpenAPI): [docs/api-specs](docs/api-specs)
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Testing

```bash
# Chạy toàn bộ test
./gradlew test

# Build kèm kiểm tra
./gradlew check
```

Integration test dùng **Testcontainers** (PostgreSQL) nên cần Docker đang chạy.

## Docker

```bash
# Build image
docker compose build

# Chạy toàn bộ stack (backend + PostgreSQL)
docker compose up -d

# Xem log
docker compose logs -f app

# Dừng
docker compose down
```

## License

MIT. See [LICENSE](LICENSE) file.