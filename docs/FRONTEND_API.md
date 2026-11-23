# Hướng dẫn tích hợp API Backend cho Frontend

Tài liệu tóm tắt hợp đồng REST hiện có trong source backend Kallo. Base URL lấy từ môi trường chạy; backend dùng cổng `8080` mặc định trong cấu hình triển khai. Ví dụ dưới đây dùng `http://localhost:8080`. Các endpoint bên dưới nối trực tiếp sau base URL.

## Quy ước chung

- JSON: gửi `Content-Type: application/json`; ngày là `YYYY-MM-DD`, thời gian timestamp là ISO-8601.
- Endpoint cần đăng nhập: `Authorization: Bearer <accessToken>`. Backend là stateless; access token mặc định hết hạn sau 15 phút, refresh token sau 7 ngày.
- Hầu hết response có dạng `ApiResponse<T>`:

```json
{ "success": true, "data": {}, "error": null }
```

- Lỗi nghiệp vụ thường có dạng `{ "success": false, "data": null, "error": { "code": "...", "message": "...", "details": null } }`. HTTP `401` khi thiếu/sai token; `403` khi không đủ quyền hoặc email chưa xác minh; `404` khi không tìm thấy; `400` khi request không hợp lệ. Nội dung lỗi cụ thể phụ thuộc endpoint.
- Một số thao tác trả `204 No Content` thay vì JSON (ví dụ logout/reset password/xóa tài khoản, một số xóa). Hãy xử lý theo status, không luôn parse JSON.
- Danh sách phân trang dùng `data: { items, page, size, totalElements, totalPages }`.
- Multipart upload dùng `multipart/form-data`; không tự đặt header `Content-Type` để client tự thêm boundary.

## Xác thực và tài khoản

| Method | Endpoint | Auth | Mục đích |
|---|---|---:|---|
| POST | `/api/auth/register` | Không | Đăng ký |
| POST | `/api/auth/login` | Không | Đăng nhập |
| POST | `/api/auth/google` | Không | Đăng nhập Google bằng ID token |
| POST | `/api/auth/refresh` | Không | Đổi refresh token lấy token mới |
| POST | `/api/auth/logout` | Không | Đăng xuất; có thể gửi refresh token, `allDevices` |
| POST | `/api/auth/forgot-password` | Không | Yêu cầu mã đặt lại mật khẩu |
| POST | `/api/auth/reset-password` | Không | Đặt mật khẩu bằng email, code, mật khẩu mới |
| POST | `/api/auth/change-password` | Có | Đổi mật khẩu hiện tại |
| POST | `/api/auth/verify-email` | Có | Xác minh email bằng code |
| POST | `/api/auth/resend-verification` | Có | Gửi lại mã xác minh |
| GET | `/api/users/me` | Có | Lấy thông tin tài khoản hiện tại |
| PUT | `/api/users/me` | Có | Cập nhật thông tin tài khoản |
| POST | `/api/users/me/avatar` | Có | Upload avatar (`file`) |
| DELETE | `/api/users/me` | Có | Xóa tài khoản |

Đăng nhập/đăng ký/refresh trả `data` kiểu `AuthResponse`: `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `user`, `profileCompleted`, `isNewUser`. Lưu token an toàn ở phía ứng dụng; khi refresh thành công thay cả access và refresh token theo response.

## Hồ sơ sức khỏe và dinh dưỡng

| Method | Endpoint | Auth | Mục đích |
|---|---|---:|---|
| GET | `/api/users/me/profile` | Có | Lấy hồ sơ, chỉ số và mục tiêu dinh dưỡng |
| PUT | `/api/users/me/profile` | Có | Tạo/cập nhật hồ sơ |
| POST | `/api/users/me/profile/recalculate` | Có | Áp dụng lại mục tiêu theo cân nặng mới nhất |
| GET | `/api/foods/search?q=&category=&page=0&size=20` | Có | Tìm món, có phân trang; `q` và `category` tùy chọn |
| GET | `/api/foods/{id}` | Có | Chi tiết món ăn |
| GET | `/api/foods/vietfood/{classId}` | Có | Tra món theo class ID VietFood (0–67) |
| POST | `/api/foods/custom` | Có | Tạo món ăn riêng |
| PUT | `/api/foods/custom/{id}` | Có | Sửa món ăn riêng |
| DELETE | `/api/foods/custom/{id}` | Có | Xóa món ăn riêng |
| GET | `/api/foods/barcode/{code}` | Có | Tra cứu mã vạch (backend có thể gọi Open Food Facts) |
| POST | `/api/food-logs` | Có | Ghi món vào nhật ký |
| GET | `/api/food-logs?date=YYYY-MM-DD` | Có | Lấy nhật ký món ăn theo ngày |
| GET | `/api/food-logs/{id}` | Có | Chi tiết mục nhật ký |
| PUT | `/api/food-logs/{id}` | Có | Sửa mục nhật ký |
| DELETE | `/api/food-logs/{id}` | Có | Xóa mục nhật ký |
| POST | `/api/food-logs/copy` | Có | Sao chép bữa ăn từ ngày khác |
| GET | `/api/food-logs/recent?limit=10` | Có | Món đã ghi gần đây |
| POST | `/api/food-logs/recognize` | Có | Nhận diện ảnh; multipart field `file` |
| POST | `/api/water-logs` | Có | Ghi lượng nước |
| GET | `/api/water-logs?date=YYYY-MM-DD` | Có | Lấy tổng và các lần uống nước trong ngày |
| DELETE | `/api/water-logs/{id}` | Có | Xóa lần ghi nước |
| GET | `/api/daily-summaries/today` | Có | Tóm tắt dinh dưỡng hôm nay |
| GET | `/api/daily-summaries?date=YYYY-MM-DD` | Có | Tóm tắt theo ngày |
| POST | `/api/daily-summaries/recalculate?date=YYYY-MM-DD` | Có | Tính lại summary; bỏ `date` để dùng ngày hiện tại |

### Ví dụ ghi món ăn

```http
POST /api/food-logs
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "foodId": "<uuid>",
  "mealType": "LUNCH",
  "servingName": "1 phần",
  "servingGrams": 250,
  "servingQuantity": 1,
  "loggedDate": "2026-11-21",
  "clientUuid": "<uuid>"
}
```

`foodId`, `mealType`, `loggedDate`, `clientUuid` bắt buộc; `servingGrams`/`servingQuantity` phải dương nếu gửi. Giá trị enum cần dùng đúng tên backend; ví dụ `mealType` thường là `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`.

### Ví dụ lấy daily summary

`GET /api/daily-summaries/today` trả về `data` với các trường gồm `summaryDate`, `caloriesConsumed`, `targetCalories`, `caloriesRemaining`, `proteinG`, `carbsG`, `fatG`, `waterMl`, `targetWaterMl`, `caloriesBurned`, `steps`, `goalAchieved` và các cờ/tổng khác. Nên dùng API này làm nguồn tổng hợp thay vì tự cộng các nhật ký ở frontend.

## Hoạt động, game và hệ thống

Các endpoint hoạt động, game và hệ thống cũng cần Bearer token, trừ khi backend nêu khác.

| Module | Endpoint |
|---|---|
| Hoạt động | `/api/activities` (GET/POST), `/api/activities/{id}` (GET/PUT/DELETE), `/api/activities/summary`, `/api/activity-types`, `/api/weight-logs` (GET/POST), `/api/weight-logs/{id}` (GET/PUT/DELETE), `/api/health-sync` (POST), `/api/health-sync/status` (GET) |
| Game | `/api/streak`, `/api/badges`, `/api/challenges`, `/api/challenges/{id}`, `/api/challenges/{id}/join`, `/api/friends`, `/api/friends/requests`, `/api/friends/requests/{id}`, `/api/leaderboard/friends`, `/api/users/search`, `/api/users/me/game-settings` |
| Hệ thống | `/api/notifications`, `/api/notifications/unread-count`, `/api/notifications/{id}/read`, `/api/notifications/read-all`, `/api/notification-settings`, `/api/devices`, `/api/sync/push`, `/api/sync/pull`, `/api/app/attributions` |
| Admin | `/api/admin/badges`, `/api/admin/challenges`; yêu cầu role ADMIN |

Với endpoint chỉ ghi trong nhóm tổng quan, xem OpenAPI hoặc controller để biết verb, query, body cụ thể. Không suy ra payload chỉ từ tên endpoint.

## Luồng frontend khuyến nghị

1. Đăng nhập/đăng ký → lưu `accessToken`, `refreshToken`; nếu `profileCompleted` là `false`, chuyển người dùng tạo hồ sơ.
2. Gửi access token cho API cần đăng nhập. Khi nhận `401`, thử refresh một lần; nếu refresh thất bại thì xóa phiên và yêu cầu đăng nhập lại.
3. Nếu đăng ký tài khoản yêu cầu xác minh email, gọi API xác minh trước khi dùng các API nghiệp vụ (đa số endpoint cần `email_verified = true`).
4. Tải danh mục/tìm món → ghi food log và water log → lấy `/api/daily-summaries/today` để hiển thị tổng ngày.
5. Gửi `clientUuid` ổn định cho thao tác ghi nhật ký để hỗ trợ xử lý idempotent và đồng bộ.

## Tài liệu chi tiết trong repository

- OpenAPI sẵn có: `docs/api-specs/auth.yaml`, `profile.yaml`, `food.yaml`, `act.yaml`, `game.yaml`, `sys.yaml`.
- Controller là nguồn chuẩn cho các module chưa có YAML và verb/body chính xác: `src/main/java/com/healthcare/kallo/controller/`.
- API docs runtime được mở công khai tại `/swagger-ui/**` và `/v3/api-docs/**` theo cấu hình security hiện tại.
