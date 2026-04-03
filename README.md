# Tài liệu Hướng dẫn Tích hợp Frontend - Ứng dụng Chat Realtime (Spring Boot + Redis + WebSocket)

Tài liệu này cung cấp toàn bộ thông tin cần thiết về API, cấu trúc dữ liệu và luồng hoạt động (Flow) của hệ thống Backend, giúp team Frontend (React, Vue, Angular, v.v.) dễ dàng tích hợp và xây dựng giao diện ứng dụng chat.

## I. Tổng quan Kiến trúc

Ứng dụng sử dụng 2 giao thức giao tiếp chính:

1.  **REST API (HTTP/HTTPS):** Xử lý các tác vụ một lần (One-off) như Đăng ký, Đăng nhập, Lấy danh sách Online ban đầu.
2.  **WebSocket (STOMP qua SockJS):** Đảm nhiệm luồng dữ liệu thời gian thực, duy trì kết nối liên tục để truyền tải tin nhắn, trạng thái Online/Offline và trạng thái "Đang gõ...".

**Cơ chế Bảo mật:** Toàn bộ hệ thống được bảo vệ bởi **JWT (JSON Web Token)**. Token bắt buộc phải được đính kèm trong HTTP Header (`Authorization: Bearer <token>`) đối với REST API và trong **Headers của lệnh Connect** đối với STOMP WebSocket.

---

## II. Danh sách REST API

Frontend sử dụng thư viện HTTP client (ví dụ: `axios` hoặc `fetch`) để gọi các API này.

### 1. Đăng ký tài khoản (Register)

*   **Endpoint:** `POST /api/auth/register`
*   **Body (JSON):**
    ```json
    {
      "username": "nguyenvan_a",
      "password": "password123",
      "email": "a@gmail.com"
    }
    ```
*   **Response:**
    *   Thành công (`200 OK`): Trả về plain text `"User registered successfully"`.
    *   Thất bại (`400 Bad Request`): Trả về plain text `"Username is already taken"` (nếu username đã tồn tại).

### 2. Đăng nhập (Login)

*   **Endpoint:** `POST /api/auth/login`
*   **Body (JSON):**
    ```json
    {
      "username": "nguyenvan_a",
      "password": "password123"
    }
    ```
*   **Response:**
    *   Thành công (`200 OK`): Trả về trực tiếp chuỗi **JWT Token** (ví dụ: `eyJhbGciOiJIUzI1NiJ9...`). Frontend cần lưu token này vào `localStorage` hoặc `sessionStorage` để sử dụng cho các request sau.
    *   Thất bại (`401 Unauthorized`): Trả về plain text `"Invalid username or password"`.

### 3. Lấy danh sách User đang Online (Khởi tạo State)

API này nên được gọi **ngay sau khi đăng nhập thành công** và bắt đầu load giao diện chat để hiển thị danh sách bạn bè đang trực tuyến.

*   **Endpoint:** `GET /api/users/online`
*   **Headers:**
    ```http
    Authorization: Bearer <YOUR_JWT_TOKEN>
    ```
*   **Response (`200 OK`):** Trả về một mảng (Array) các username đang online.
    ```json
    [
      "user_b",
      "user_c",
      "nguyenvan_a"
    ]
    ```

---

## III. Giao tiếp Real-time qua WebSocket (STOMP)

Frontend cần cài đặt thư viện `sockjs-client` và `@stomp/stompjs` (hoặc `stompjs` phiên bản cũ).

### 1. Mở kết nối (Connect)

Khởi tạo kết nối STOMP đến endpoint WebSocket của Server.

*   **Endpoint:** `http://localhost:8080/ws` (hoặc URL server của bạn, sử dụng SockJS)
*   **Xác thực (Quan trọng):** Khi gọi hàm `connect()`, bắt buộc phải truyền JWT token vào biến `headers`. Nếu không có token hợp lệ, kết nối sẽ bị từ chối ngay lập tức.

**Ví dụ code JS (sử dụng `@stomp/stompjs`):**

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const token = localStorage.getItem('token');

const stompClient = new Client({
    webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
    connectHeaders: {
        Authorization: `Bearer ${token}`
    },
    debug: function (str) {
        console.log(str);
    },
    onConnect: (frame) => {
        console.log('Connected: ' + frame);
        // Đăng ký nhận tin nhắn (Subscribe) tại đây
        subscribeToChannels();
    },
    onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
    },
});

stompClient.activate(); // Bắt đầu kết nối
```

### 2. Theo dõi Kênh nhận dữ liệu (Subscribe Channels)

Sau khi `onConnect` thành công, Frontend cần sử dụng `stompClient.subscribe(topic, callback)` để lắng nghe các kênh sau:

#### A. Kênh Thông báo Chung (Public / Trạng thái Online-Offline)
*   **Topic:** `/topic/public`
*   **Mục đích:** Nhận thông báo khi có người dùng khác vừa đăng nhập (JOIN) hoặc vừa thoát (LEAVE).
*   **Hành động UI:** Cập nhật lại danh sách "Online Users" (thêm chấm xanh hoặc gỡ bỏ).

#### B. Kênh Nhận tin nhắn Cá nhân (1-1 Chat)
*   **Topic:** `/user/queue/messages`
*   **Mục đích:** Kênh dành riêng (Private Queue) cho user đang đăng nhập. Chỉ nhận những tin nhắn mà `recipient` là username của bạn.

#### C. Kênh Nhận tin nhắn Nhóm (Group Chat)
*   **Topic:** `/topic/group/{groupId}` (Thay `{groupId}` bằng ID thực tế của nhóm).
*   **Mục đích:** Kênh phát sóng (Broadcast) cho tất cả các thành viên trong một nhóm chat cụ thể.

---

### 3. Gửi Dữ liệu lên Server (Send/Publish)

Frontend sử dụng `stompClient.publish({ destination, body })` để gửi dữ liệu.
**Lưu ý:** Thuộc tính `body` phải là một chuỗi JSON (sử dụng `JSON.stringify()`).

#### Cấu trúc Object `ChatMessage` chuẩn:

```javascript
const chatMessage = {
    sender: "Tên_Người_Gửi",     // Bắt buộc (Thường là username đang đăng nhập)
    recipient: "Tên_Người_Nhận", // Chỉ dùng cho Chat 1-1 (Gửi cho ai?)
    groupId: "ID_Nhóm",          // Chỉ dùng cho Chat Group (Gửi vào nhóm nào?)
    content: "Nội dung...",      // Nội dung văn bản (Có thể rỗng nếu là Typing)
    type: "CHAT"                 // "CHAT" hoặc "TYPING"
};
```

#### A. Gửi Tin nhắn văn bản (Chat)
*   **Destination:** `/app/chat.sendMessage`
*   **Payload (`body`):** Object `ChatMessage` với `type: "CHAT"`.
*   **Logic:**
    *   Nếu là chat 1-1: Bắt buộc điền trường `recipient`. Backend sẽ tự động định tuyến đến kênh `/user/{recipient}/queue/messages`.
    *   Nếu là chat nhóm: Bắt buộc điền trường `groupId`. Backend sẽ tự động định tuyến đến kênh `/topic/group/{groupId}`.

#### B. Gửi Tín hiệu "Đang gõ..." (Typing Indicator)
*   **Destination:** `/app/chat.typing`
*   **Payload (`body`):** Object `ChatMessage` với `type: "TYPING"`. (Bạn có thể bỏ trống trường `content`).
*   **Logic:**
    *   Tương tự như gửi tin nhắn, điền `recipient` hoặc `groupId` để chỉ định đích đến của tín hiệu typing. Đầu bên kia sẽ nhận được thông điệp này và hiển thị hiệu ứng.

---

## IV. Hướng dẫn và Mẹo xử lý UX/UI (Best Practices)

1.  **Quản lý Danh sách Online (Real-time):**
    *   Khi ứng dụng khởi chạy: Gọi API `GET /api/users/online` để lấy danh sách ban đầu (State `onlineUsers`).
    *   Trong quá trình chạy: Lắng nghe kênh `/topic/public`.
        *   Nếu nhận được `message.type === "JOIN"`: Kiểm tra xem `message.sender` đã có trong State chưa, nếu chưa thì `push` vào mảng `onlineUsers` (Bật chấm xanh).
        *   Nếu nhận được `message.type === "LEAVE"`: Lọc (filter) và xóa `message.sender` ra khỏi mảng `onlineUsers` (Tắt chấm xanh).
2.  **Xử lý Hiệu ứng "Đang gõ..." (Typing Indicator):**
    *   **Bên gửi:** Bắt sự kiện `onKeyDown` hoặc `onChange` của ô input. **Rất quan trọng:** Phải sử dụng kỹ thuật **Debounce** (ví dụ: 1000ms - 2000ms) hoặc **Throttle** để giới hạn số lượng tin nhắn `/app/chat.typing` gửi lên server. Tránh việc gõ 10 phím gửi 10 request.
    *   **Bên nhận:** Khi nhận được message có `type === "TYPING"`, Frontend bật component/hiệu ứng "User X đang gõ...". Đồng thời, thiết lập một `setTimeout` khoảng 3000ms để tự động ẩn hiệu ứng đó đi. Nếu trong vòng 3000ms lại nhận được tín hiệu Typing mới, hãy `clearTimeout` cũ và reset lại bộ đếm.
3.  **Tự động Kết nối lại (Auto Reconnect):**
    *   Kết nối WebSocket có thể bị ngắt do mạng chập chờn. Thư viện `@stomp/stompjs` có hỗ trợ tính năng tự động kết nối lại (`reconnectDelay`). Hãy thiết lập thuộc tính này (ví dụ: `reconnectDelay: 5000`) khi cấu hình `Client` để Frontend tự động phục hồi kết nối mà không cần user tải lại trang.
    *   Khi mất kết nối, nên hiển thị một banner nhỏ UI (ví dụ: "Đang mất kết nối, đang thử kết nối lại...") để cải thiện trải nghiệm người dùng.
