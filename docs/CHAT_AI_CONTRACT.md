# Hợp đồng AI service cho Module Chat

> Trạng thái: bản nháp Phase 0, chờ owner AI service xác nhận trước khi bắt đầu Phase 1.
>
> Backend không gọi API AI bên thứ ba. AI service tự host, chạy nội bộ và không nhận dữ liệu định danh trực tiếp.

## 1. Endpoint

```http
POST {AI_SERVICE_URL}/v1/chat
Content-Type: application/json
X-Internal-Api-Key: <AI_SERVICE_API_KEY>
X-Request-Id: <UUID>
```

Backend đặt timeout kết nối 2 giây và timeout đọc 30 giây. Backend chỉ retry tối đa một lần với lỗi kết nối hoặc HTTP 5xx tức thời. AI service không tạo side effect nên request retry phải an toàn.

## 2. Request

```json
{
  "conversationId": "<conversation UUID>",
  "locale": "vi",
  "systemContext": {
    "profile": {
      "age": 28,
      "sex": "FEMALE",
      "heightCm": 160,
      "weightKg": 58,
      "goal": "LOSE_WEIGHT",
      "bmi": 22.66,
      "tdee": 2100,
      "dailyKcal": 1800,
      "allergies": ["tôm", "đậu phộng"]
    },
    "today": {
      "consumedKcal": 1350,
      "protein": 62,
      "carb": 170,
      "fat": 40,
      "meals": []
    }
  },
  "messages": [
    {"role": "user", "content": "Hôm nay tôi còn bao nhiêu calo?"},
    {"role": "assistant", "content": "Bạn còn khoảng 450 kcal."},
    {"role": "user", "content": "Tối nên ăn gì?"}
  ],
  "options": {
    "maxTokens": 512,
    "stream": false
  }
}
```

Quy tắc:

- `conversationId`: chuỗi UUID opaque; AI service không dùng để truy vấn dữ liệu người dùng.
- `locale`: bắt buộc, Phase 1 chỉ hỗ trợ `vi`.
- `systemContext`, `messages`: bắt buộc; backend chỉ gửi dữ liệu tối thiểu cần cho tư vấn.
- `messages`: tối đa 12 tin gần nhất ở Phase 1; tin cuối phải là `user`.
- `options.maxTokens`: backend kiểm soát, không nhận giá trị tuỳ ý từ mobile.
- Không gửi `userId`, email, tên, JWT, địa chỉ hoặc thông tin định danh khác.

## 3. Response thành công

HTTP `200`:

```json
{
  "message": {
    "role": "assistant",
    "content": "Bạn có thể chọn một bữa tối khoảng 450 kcal..."
  },
  "suggestedFoods": [
    {"name": "Canh chua cá", "kcal": 180}
  ],
  "usage": {
    "promptTokens": 640,
    "completionTokens": 210
  },
  "modelVersion": "kallo-chat-0.1"
}
```

Trường bắt buộc: `message.role = assistant`, `message.content` không rỗng. Các trường `suggestedFoods`, `usage`, `modelVersion` có thể vắng hoặc `null`.

## 4. Lỗi và mapping backend

| AI service | Ý nghĩa | Backend |
| --- | --- | --- |
| `400` | Request sai schema hoặc nội dung không hợp lệ | `CHAT_AI_INVALID_RESPONSE` (502) / lỗi tích hợp |
| `401`, `403` | API key không hợp lệ | `CHAT_AI_UNAVAILABLE` |
| `408` hoặc timeout | AI xử lý quá lâu | `CHAT_AI_TIMEOUT` (504) |
| `429` | AI service quá tải | `CHAT_AI_UNAVAILABLE` (503) |
| `5xx` | AI service lỗi | `CHAT_AI_UNAVAILABLE` (503) |

Khi AI lỗi, backend vẫn giữ tin nhắn `USER`, đánh dấu bản ghi `ASSISTANT` là `FAILED` và trả `userMessageId` để retry. AI service không trả câu trả lời dự phòng bịa nội dung.

## 5. Streaming Phase 3

Khi `options.stream = true`, response dùng `text/event-stream` với các event:

```text
event: delta
data: {"text":"Bạn có thể"}

event: done
data: {"usage":{"promptTokens":640,"completionTokens":210},"modelVersion":"kallo-chat-0.1"}
```

Event `error` có dạng `{"code":"...","message":"..."}`. Streaming không thuộc Phase 0/1.

## 6. Bảo mật và vận hành

- AI service chỉ mở mạng nội bộ; production bắt buộc `X-Internal-Api-Key`.
- `X-Request-Id` do backend sinh cho từng lần gọi và dùng để trace; không dùng làm idempotency của mobile.
- AI service không log payload ở mức INFO.
- Backend không ghi nội dung chat hoặc dữ liệu sức khoẻ vào log ứng dụng.
- Backend kiểm tra dị ứng và disclaimer sau khi nhận response; AI service không được xem đó là lớp bảo vệ duy nhất.

## 7. Cần owner AI service xác nhận

- [ ] Endpoint `/v1/chat` và schema trên.
- [ ] Tên model/version và giới hạn `maxTokens`.
- [ ] Danh sách mã lỗi thực tế và body lỗi.
- [ ] Cơ chế xác thực `X-Internal-Api-Key`.
- [ ] Hỗ trợ `stream=true` và format SSE ở Phase 3.
- [ ] Số lượng message/context tối đa AI service chấp nhận.
