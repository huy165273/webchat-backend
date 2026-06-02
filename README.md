# Web Chat AI - Backend

Đây là dịch vụ backend cho ứng dụng Web Chat AI, được thiết kế để cung cấp trải nghiệm tương tự ChatGPT. Hệ thống tuân theo kiến trúc đơn giản, không yêu cầu cấu hình hạ tầng phức tạp (Zero-DevOps cho Web/App), sử dụng các tính năng mới nhất của Java và Spring Boot 3.

## 🏗 Kiến trúc hệ thống

Backend được xây dựng với mục tiêu đơn giản hóa, tối ưu hiệu năng và tiết kiệm chi phí:

- **Ngôn ngữ:** Java 21 (Sử dụng Virtual Threads để xử lý streaming đồng thời)
- **Framework:** Spring Boot 3 MVC
- **Cơ sở dữ liệu:** PostgreSQL (Môi trường Production) / H2 (Môi trường Test Local)
- **ORM:** Spring Data JPA / Hibernate
- **Bảo mật:** Spring Security với JWT (JSON Web Tokens)
- **Tích hợp AI:** Gọi REST trực tiếp đến Triton Inference Server tự host thông qua `RestClient` của Spring.
- **Streaming:** Sử dụng Server-Sent Events (`SseEmitter`) để hiển thị phản hồi từ AI theo thời gian thực (từng token một).

### Các thành phần chính
1. **Authentication:** Xác thực không lưu trạng thái (stateless) dựa trên JWT (`/api/auth/login`, `/api/auth/register`).
2. **Chat & Streaming:** `ChatController` tiếp nhận tin nhắn từ người dùng, lưu vào database, và gọi `TritonService`.
3. **Virtual Threads:** `TritonService` tận dụng `Executors.newVirtualThreadPerTaskExecutor()` của Java 21 để xử lý các kết nối HTTP streaming kéo dài mà không làm block (chặn) các thread hệ thống chính.

---

## 🚀 Hướng dẫn bắt đầu

### Yêu cầu cài đặt
- JDK 21
- Maven (hoặc sử dụng wrapper `./mvnw` đính kèm)
- PostgreSQL (không bắt buộc nếu chạy test local, nhưng bắt buộc khi lên Production)

### 1. Thiết lập Database

**Cách A: Test Local với H2 (In-Memory)**
Bạn không cần phải cài đặt bất kỳ database nào. Ứng dụng sẽ sử dụng database trong RAM và tự động tạo lại các bảng mỗi lần khởi động.
Để chạy với H2, hãy sử dụng profile `test`:
```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=test"
```

**Cách chạy Debug (Test Local):**
Nếu bạn muốn debug code, có 2 cách:
1. **Dùng IDE (VS Code / IntelliJ):**
   - Mở file `BackendApplication.java`.
   - Chạy ứng dụng bằng nút **Debug** của IDE.
   - Để ứng dụng nhận profile `test`, bạn cần thêm vào cấu hình Run/Debug (hoặc VM options) dòng sau: `-Dspring.profiles.active=test`.
2. **Dùng Command Line (CLI):**
   - Chạy lệnh sau để mở port debug ở `5005`:
   ```bash
   ./mvnw spring-boot:run "-Dspring-boot.run.profiles=test" "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
   ```
   - Sau đó, trong IDE của bạn, tạo cấu hình **Remote JVM Debug** kết nối tới `localhost:5005` và nhấn Debug.

**Cách B: PostgreSQL (Môi trường thực tế / Production)**
1. Cài đặt PostgreSQL và tạo một database tên là `webchat`.
2. Cập nhật file `src/main/resources/application.yml` với thông tin kết nối của bạn:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/webchat
    username: your_postgres_user
    password: your_postgres_password
```
3. Chạy ứng dụng với profile mặc định:
```bash
./mvnw spring-boot:run
```

### 2. Cấu hình biến môi trường

Các thông số sau có thể được điều chỉnh trong file `application.yml`:

- `jwt.secret`: Khóa bí mật JWT của bạn (cần là một chuỗi base64 dài và bảo mật).
- `jwt.expiration`: Thời gian hết hạn của token tính bằng mili-giây (mặc định: 86400000 / 1 ngày).
- `ai.triton.url`: URL trỏ tới API của Triton Inference Server (Ví dụ: `http://your-gpu-server:8000/v2/models/gpt-oss/generate_stream`).

### 3. Danh sách API Endpoints

- `POST /api/auth/register` - Đăng ký tài khoản người dùng mới.
- `POST /api/auth/login` - Đăng nhập và lấy token JWT.
- `GET /api/conversations` - Lấy danh sách đoạn chat của người dùng đang đăng nhập.
- `POST /api/conversations` - Tạo một đoạn chat mới.
- `GET /api/chat/{conversationId}/messages` - Lấy lịch sử chat của một đoạn chat cụ thể.
- `POST /api/chat/{conversationId}/stream` - Gửi tin nhắn và nhận luồng dữ liệu SSE (từng token) từ AI.

---

## 🛠 Triển khai (Deployment)

Ứng dụng được đóng gói dưới dạng một file JAR có thể chạy trực tiếp.

1. Build ứng dụng:
```bash
./mvnw clean package -DskipTests
```

2. File JAR sau khi build sẽ nằm trong thư mục `target/`. Khởi chạy bằng lệnh:
```bash
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Đối với phương pháp triển khai Zero-DevOps, bạn có thể đẩy mã nguồn này lên các nền tảng như **Render**, **Railway**, hoặc **Fly.io**. Chúng sẽ tự động nhận diện file `pom.xml`, tự build và tự deploy ứng dụng. Hãy đảm bảo bạn cung cấp biến môi trường `DATABASE_URL` nếu nền tảng yêu cầu, hoặc ghi đè các cấu hình `spring.datasource.*` thông qua biến môi trường của nền tảng đó.
