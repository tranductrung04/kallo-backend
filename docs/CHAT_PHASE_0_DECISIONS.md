# Quyết định Phase 0 — Module Chat

Ngày: 16/11/2026  
Trạng thái: đã đối chiếu code hiện tại; các mục cần owner AI service xác nhận nằm trong `docs/CHAT_AI_CONTRACT.md`.

## Checklist hiện trạng

| Hạng mục | Kết quả |
| --- | --- |
| Kiểu khoá chính | `UUID`, theo `users.id` trong `V1__init_schema.sql` và `User.id` |
| Response wrapper | `ApiResponse<T>` với `success`, `data`, `error` |
| User từ JWT | `CurrentUser.id(Authentication)`; không nhận user ID từ request |
| Bảng chat hiện có | Chưa có `conversations` hoặc `chat_messages` trong migration hiện tại |
| Redis | Đã có dependency, Docker/CI service và `RateLimiter` dùng Redis; chat tái sử dụng `RateLimiter` |
| Elasticsearch | Đã có dependency/config nhưng không thuộc Phase 0 chat |
| Actuator/Micrometer | Chưa có dependency; không thêm ở Phase 0 |
| Spring scheduling | Đã bật `@EnableScheduling`; job dọn `PENDING` để Phase 2 |
| Profile sức khoẻ / daily summary | Chưa có entity/service trong repo; Phase 2 cần cổng tích hợp hoặc module sở hữu cung cấp API |
| AI service | Chưa có endpoint trong backend; hợp đồng đề xuất nằm trong `CHAT_AI_CONTRACT.md` |

## Quyết định triển khai Phase 1

1. Dùng `UUID` cho `conversations.id`, `chat_messages.id`, `chat_messages.parent_message_id`.
2. Migration dùng timestamp UTC theo quy ước repo: `V20261116100000__chat_schema.sql`; ràng buộc bổ sung nằm trong `V20261116110000__chat_integrity.sql`.
3. Bảng chat tham chiếu `users(id)` với `ON DELETE CASCADE`.
4. REST đồng bộ trước. SSE, feedback, suggestions, ảnh, FCM để phase sau.
5. `ChatAiClient` là interface; mock client dùng cho dev/test; HTTP client chỉ bật khi hợp đồng được owner AI service duyệt.
6. Tách transaction DB trước và sau lần gọi AI. Không giữ transaction trong thời gian chờ AI.
7. Idempotency key là UUID do mobile sinh. Tin đầu tiên dùng unique `(user_id, initial_client_message_id)` trên `conversations` vì `conversationId` còn thiếu; tin tiếp theo dùng `(conversation_id, client_message_id)` trên `chat_messages`.
8. Rate limit chat dùng Redis `RateLimiter` hiện có: mặc định 10 tin/phút/user và 200 tin/ngày/user bằng hai key/window độc lập; không cần chờ Phase 4.
9. Guardrail cá nhân hoá chờ module PROFILE/NUTRI cung cấp port; Phase 1 chỉ dựng interface payload tối thiểu, không bịa dữ liệu sức khoẻ.
10. Không thêm dependency mới ở Phase 0.

## Việc chặn trước Phase 1

- Owner AI service xác nhận `docs/CHAT_AI_CONTRACT.md`.
- Chốt chủ sở hữu contract cho dữ liệu `UserHealthProfile` và `DailySummary`.
- Chốt giới hạn AI service: context window, max tokens, timeout thực tế.
- Chốt chính sách lưu/xoá chat và consent dùng dữ liệu sức khoẻ.
