# Spike mobile SSE cho Chat

## Mục tiêu

Xác nhận mobile nhận được token đầu tiên qua SSE trên Android và iOS thật, qua đúng reverse proxy môi trường deploy. REST đồng bộ `/api/chat/messages` giữ làm fallback bắt buộc.

## Thời lượng

1–2 ngày trước Phase 3.

## Ma trận thử nghiệm

| Client | Nền tảng | Cách đọc stream | Thiết bị | Proxy | Kết quả |
| --- | --- | --- | --- | --- | --- |
| Expo | Android thật | `fetch` streaming | ghi model/OS | URL deploy thật | |
| Expo | iOS thật | `fetch` streaming | ghi model/OS | URL deploy thật | |
| Expo | Android thật | XHR incremental | ghi model/OS | URL deploy thật | |
| Expo | iOS thật | XHR incremental | ghi model/OS | URL deploy thật | |

## Cách đo

1. Ghi `t_request` ngay trước request.
2. Ghi `t_headers` khi nhận response headers.
3. Ghi `t_first_byte` khi nhận byte đầu tiên.
4. Ghi `t_first_delta` khi parse event `delta` đầu tiên.
5. Ghi `t_done` khi nhận event `done`.
6. Chạy tối thiểu 30 request cho mỗi ô trong ma trận; ghi p50/p95 của `t_first_delta - t_request`.
7. Kiểm tra event `:ping` khoảng 15 giây không bị proxy buffer hoặc timeout.
8. Ngắt app giữa stream, mở lại và gọi `GET /api/chat/conversations/{id}/messages`; xác nhận trạng thái không còn `PENDING`.

## Tiêu chí chốt

- Chọn cách đọc ổn định trên cả Android và iOS thật.
- p95 token đầu tiên không vượt 2 giây khi AI service đáp ứng trong ngưỡng.
- Proxy chuyển tiếp `text/event-stream`, tắt buffering và không đóng kết nối vì idle heartbeat.
- Khi SSE không hỗ trợ hoặc lỗi parse, app tự gọi REST đồng bộ; nội dung vẫn gửi/nhận được, chỉ mất hiệu ứng hiện chữ dần.

## Header và event cần kiểm tra

- Request: `Authorization: Bearer <JWT>`, `Content-Type: application/json`.
- Response: `Content-Type: text/event-stream`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`.
- Event: `meta`, `delta`, `done`, `error`; comment heartbeat `:ping`.

## Kết quả cần ghi lại

- Thiết bị, OS, Expo/RN version.
- `fetch` hoặc XHR và thư viện parse SSE.
- Reverse proxy/version và cấu hình buffering/idle timeout.
- p50/p95 `first delta`, tỉ lệ disconnect, tỉ lệ fallback REST.
- Quyết định: dùng `fetch`, XHR, hoặc tắt SSE trên client đó.
