# Closet Shop — Backend API

Dự án Backend phục vụ hệ sinh thái thương mại điện tử Closet Shop (ATELIER), cung cấp hệ thống RESTful APIs hoàn chỉnh cho cả hai phân hệ: Storefront (khách mua hàng) và Admin (quản trị vận hành).

---

## 🛠 Công Nghệ Sử Dụng

- **Ngôn ngữ & Runtime:** Java 17+ (LTS)
- **Framework:** Spring Boot 3.3.4
- **Security & Authentication:** Spring Security, JSON Web Token (JJWT 0.12.6, HMAC-SHA512)
- **Database Access:** Spring Data JPA, Hibernate ORM 6.5.3 (`ddl-auto=validate`)
- **Database Engine:** MySQL 8.0+
- **Schema Management:** Flyway Migration 10.x (`flyway-core`, `flyway-mysql`, migration scripts tại `src/main/resources/db/migration/`)
- **API Documentation:** SpringDoc OpenAPI 2.6.0 (Swagger UI & OpenAPI v3)
- **Build Tool:** Apache Maven 3.8+ (khuyên dùng Maven 3.9+)
- **Tiện ích:** Project Lombok, Jakarta Bean Validation, TransactionTemplate

---

## 📁 Cấu Trúc Module (`com.shop.backend`)

```text
src/main/java/com/shop/backend/
├── address/         # Quản lý địa chỉ giao hàng của khách hàng
├── auth/            # Xác thực, đăng nhập, đăng ký, JWT token
├── cart/            # Quản lý giỏ hàng & sản phẩm trong giỏ
├── common/          # Exceptions, Response envelope, DTO base, Enums
├── config/          # CORS, OpenAPI, JPA Audit, DevDataSeeder
├── einvoice/        # Tích hợp hóa đơn điện tử & Outbox pattern worker
├── inventory/       # Quản lý tồn kho, trừ kho & hoàn kho
├── order/           # Đặt hàng, checkout, trạng thái đơn hàng
├── payment/         # Cổng thanh toán COD, VNPay, MoMo, kiểm tra giao dịch
├── product/         # Sản phẩm, danh mục, biến thể (size/color), hình ảnh
├── promotion/       # Chương trình khuyến mãi, flash sale
├── review/          # Đánh giá và nhận xét sản phẩm
├── security/        # JWT Filter, UserPrincipal, SecurityConfig
├── user/            # Tài khoản người dùng, phân quyền (Role)
└── voucher/         # Mã giảm giá, điều kiện áp dụng & tính chiết khấu
```

---

## 🚀 Thiết Lập Môi Trường & Onboarding

> 📖 **HƯỚNG DẪN CHI TIẾT DÀNH CHO THÀNH VIÊN MỚI:**
> Để tự thiết lập môi trường local, chạy migration và bootstrap tài khoản Admin cùng dữ liệu mẫu ban đầu, vui lòng đọc tài liệu từng bước tại:
> 👉 **[README_LOCAL_SETUP.md — Hướng Dẫn Khởi Tạo Database Local & Bootstrap Admin](./README_LOCAL_SETUP.md)**

### Tóm Tắt Nhanh Các Bước Khởi Chạy Local:

1. **Tạo 2 CSDL MySQL Local:**
   ```sql
   CREATE DATABASE IF NOT EXISTS clothing_shop_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE DATABASE IF NOT EXISTS clothing_shop_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. **Cấu hình file `.env`:** Copy `.env.example` thành `.env` và điền `DB_PASSWORD`, `JWT_SECRET`, cấu hình Admin credentials nếu muốn seed dữ liệu lần đầu.
3. **Khởi chạy Backend bằng PowerShell:**
   ```powershell
   cd backend
   .\run-local.ps1
   ```
   *(Script `run-local.ps1` sẽ tự động nạp các biến từ `.env`, che giấu mật khẩu và khởi động Spring Boot qua Maven).*

---

## 📚 API Documentation

Khi Backend đang chạy ở môi trường phát triển (port 8080):

- **Swagger UI (Interactive API Explorer):**
  `http://localhost:8080/swagger-ui.html`
- **OpenAPI v3 JSON Specification:**
  `http://localhost:8080/v3/api-docs`

---

## 🛡 Khởi Tạo Dữ Liệu Phát Triển (Dev Data Seeder)

Backend tích hợp cơ chế nạp dữ liệu mẫu ban đầu (`DevDataSeeder`) an toàn cho môi trường phát triển:

- **Profile áp dụng:** `@Profile({"local", "dev"})`. Bean seeder **không bao giờ hoạt động** trên môi trường `test` hoặc `prod` (được bảo vệ kép bởi cả profile annotation và runtime check trong phương thức `run()`).
- **Kích hoạt qua cấu hình:**
  ```env
  SEED_DEV_ENABLED=true
  SEED_ADMIN_EMAIL=admin@closet.com
  SEED_ADMIN_PASSWORD=YourSecurePassword123
  ```
- **Bảo Vệ Đa Bảng Nghiệp Vụ (Multi-Table Business Guard):**
  Trước khi seed, Seeder kiểm tra tuần tự 8 bảng nghiệp vụ: `users`, `orders`, `payments`, `invoices`, `categories`, `products`, `vouchers`, `reviews`. Nếu phát hiện bất kỳ bảng nào đã chứa dữ liệu, seeder lập tức dừng lại để bảo vệ toàn vẹn dữ liệu, không bao giờ ghi đè hoặc seed nửa vời.
- **Nguyên Tử Hóa Giao Dịch (Transactional Atomicity):**
  Toàn bộ thao tác ghi dữ liệu nghiệp vụ (Admin, 13 categories, 23 products kèm 66 biến thể, 2 vouchers `WELCOME10` và `ATELIER50`) được bao bọc trong một `TransactionTemplate`. Nếu phát sinh bất kỳ ngoại lệ nào giữa chừng, toàn bộ thay đổi sẽ được rollback 100%. Thao tác copy ảnh demo trên filesystem được tách riêng ngoài giao dịch database.

### Local Admin Account

- Khi tạo Database local mới và bật `SEED_DEV_ENABLED=true`, DevDataSeeder có thể tự tạo tài khoản Admin.
- Email được lấy từ `SEED_ADMIN_EMAIL`.
- Mật khẩu là giá trị do thành viên tự đặt trong `SEED_ADMIN_PASSWORD`.
- Mật khẩu được mã hóa BCrypt trước khi lưu vào Database.
- Không có tài khoản Admin và mật khẩu chung được hardcode trong source code.
- Mỗi thành viên sử dụng Database và tài khoản Admin riêng.
- Liên kết đến [README_LOCAL_SETUP.md](./README_LOCAL_SETUP.md) để xem hướng dẫn chi tiết.

---

## 🧪 Chạy Kiểm Thử (Testing)

Mọi kiểm thử tự động của Backend đều chạy cô lập hoàn toàn trên CSDL kiểm thử `clothing_shop_test`:

```bash
# Chạy toàn bộ test suite từ thư mục backend/
mvn test

# Hoặc chạy kiểm thử một lớp cụ thể
mvn test -Dtest=DevDataSeederTest
```

> ⚠️ **Quy tắc An Toàn Bắt Buộc Cho Kiểm Thử:**
> - Toàn bộ lớp kiểm thử đều được cấu hình với `@ActiveProfiles("test")`.
> - Lớp phòng vệ [`TestDatabaseSafetyGuard.java`](./src/test/java/com/shop/backend/config/TestDatabaseSafetyGuard.java) sẽ kiểm tra URL kết nối và catalog CSDL trước khi chạy test. Nếu CSDL không chứa hậu tố `_test`, ứng dụng sẽ ném lỗi khởi động ngay lập tức để bảo vệ tuyệt đối CSDL môi trường dev. **Tuyệt đối không được chỉnh sửa hoặc gỡ bỏ `TestDatabaseSafetyGuard.java`.**

---

## 📖 Tài Liệu Quy Tắc Dự Án

Mọi thành viên tham gia phát triển bắt buộc phải đọc và tuân thủ các tài liệu kiến trúc tại thư mục gốc:

- **[PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md):** Tổng quan kiến trúc, phân hệ, ranh giới công nghệ và phân công nhiệm vụ.
- **[BACKEND_RULES.md](../BACKEND_RULES.md):** Quy chuẩn kiến trúc Controller -> Service -> Repository -> DTO, coding conventions, xử lý exception và transactional.
- **[schema-spec.md](../schema-spec.md):** Đặc tả chi tiết cơ sở dữ liệu (kiểu dữ liệu, khóa chính, khóa ngoại, ràng buộc CHECK, index).
- **[GEMINI.md](../GEMINI.md):** Nguyên tắc làm việc tổng thể của dự án và quy ước ngôn ngữ (giao tiếp tiếng Việt, code tiếng Anh).
