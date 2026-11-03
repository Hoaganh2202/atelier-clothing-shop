# Hướng Dẫn Khởi Tạo Database Local & Bootstrap Admin (ATELIER)

Tài liệu này hướng dẫn chi tiết từng bước dành cho thành viên mới trong nhóm dự án Closet Shop (ATELIER) để tự thiết lập môi trường CSDL local độc lập, chạy migration schema, nạp dữ liệu mẫu ban đầu (seeder) và bootstrap tài khoản Admin để đăng nhập Dashboard quản trị một cách an toàn, độc lập và không làm ảnh hưởng đến dữ liệu của các thành viên khác.

---

## 📋 Mục Lục

1. [Bước 1: Cài Đặt Yêu Cầu Hệ Thống](#bước-1-cài-đặt-yêu-cầu-hệ-thống)
2. [Bước 2: Clone Mã Nguồn Dự Án](#bước-2-clone-mã-nguồn-dự-án)
3. [Bước 3: Tạo Cơ Sở Dữ Liệu MySQL Local](#bước-3-tạo-cơ-sở-dữ-liệu-mysql-local)
4. [Bước 4: Cấu Hình Biến Môi Trường (.env)](#bước-4-cấu-hình-biến-môi-trường-env)
5. [Bước 5: Khởi Chạy Backend Bằng PowerShell](#bước-5-khởi-chạy-backend-bằng-powershell)
6. [Bước 6: Khởi Chạy Frontend Storefront](#bước-6-khởi-chạy-frontend-storefront)
7. [Bước 7: Đăng Nhập Trang Quản Trị (Admin Dashboard)](#bước-7-đăng-nhập-trang-quản-trị-admin-dashboard)
8. [Bước 8: Chạy Toàn Bộ Kiểm Thử Tự Động (Testing)](#bước-8-chạy-toàn-bộ-kiểm-thử-tự-động-testing)
9. [Xử Lý Sự Cố Thường Gặp (Troubleshooting - 10 Lỗi Phổ Biến)](#xử-lý-sự-cố-thường-gặp-troubleshooting)
10. [Nguyên Tắc An Toàn CSDL Bắt Buộc (Database Safety Notes)](#nguyên-tắc-an-toàn-csdl-bắt-buộc)

---

## Bước 1: Cài Đặt Yêu Cầu Hệ Thống

Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã cài đặt các công cụ sau:

- **Java Development Kit (JDK):** Java 17 LTS (khuyến nghị OpenJDK 17 hoặc Eclipse Temurin 17). Kiểm tra bằng lệnh: `java -version`.
- **Build Tool:** Apache Maven 3.8+ (khuyên dùng Maven 3.9+). Kiểm tra bằng lệnh: `mvn -version`.
- **Database Server:** MySQL Server 8.0+. Kiểm tra bằng lệnh: `mysql --version`.
- **Node.js & npm:** Node.js 18+ LTS và npm 9+ (dành cho Frontend). Kiểm tra bằng lệnh: `node -v` và `npm -v`.
- **Version Control:** Git 2.30+.

---

## Bước 2: Clone Mã Nguồn Dự Án

Dự án ATELIER được phân chia thành hai repository độc lập:

```bash
# 1. Clone repository Backend
git clone https://github.com/Closet-Shop/BE.git backend

# 2. Clone repository Frontend
git clone https://github.com/Closet-Shop/Fe.git frontend
```

Sau khi clone, bạn sẽ có hai thư mục ngang hàng `backend/` và `frontend/`. Mọi thao tác cấu hình Backend dưới đây được thực hiện từ thư mục `backend/`.

---

## Bước 3: Tạo Cơ Sở Dữ Liệu MySQL Local

Mỗi thành viên sử dụng cơ sở dữ liệu MySQL local riêng trên máy tính cá nhân. **Tuyệt đối không kết nối trực tiếp vào cơ sở dữ liệu trên máy của trưởng nhóm.**

Mở MySQL client (MySQL Workbench, DBeaver, hoặc MySQL CLI) và thực thi script SQL sau để khởi tạo 2 database độc lập:

```sql
-- 1. Database chính cho phát triển và vận hành local
CREATE DATABASE IF NOT EXISTS clothing_shop_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Database riêng biệt cho bộ kiểm thử tự động (Test suite)
CREATE DATABASE IF NOT EXISTS clothing_shop_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 3. Cấp quyền truy cập cho user 'shop' local (nếu sử dụng user riêng):
CREATE USER IF NOT EXISTS 'shop'@'localhost' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON clothing_shop_db.* TO 'shop'@'localhost';
GRANT ALL PRIVILEGES ON clothing_shop_test.* TO 'shop'@'localhost';
FLUSH PRIVILEGES;
```

---

## Bước 4: Cấu Hình Biến Môi Trường (.env)

Tại thư mục `backend/`, sao chép file mẫu `.env.example` thành `.env`:

```bash
cd backend
cp .env.example .env
```

Mở file `.env` bằng trình soạn thảo văn bản và điền thông tin cấu hình cho máy của bạn:

```env
# Mật khẩu kết nối MySQL local của bạn (user: shop hoặc root tùy cấu hình)
DB_PASSWORD=your_actual_mysql_password

# Chuỗi bí mật JWT dùng để ký và xác thực token (bắt buộc >= 32 ký tự / 256 bits)
JWT_SECRET=super_secret_jwt_key_at_least_32_characters_random_string_123456

# Kích hoạt Dev Seeder (CHỈ đặt 'true' khi Database local mới tạo còn rỗng)
SEED_DEV_ENABLED=true

# Thông tin tài khoản Admin khởi tạo (Mỗi thành viên tự đặt email và mật khẩu riêng)
SEED_ADMIN_EMAIL=admin@closet.com
SEED_ADMIN_PASSWORD=YourSecurePassword123

# Nguồn Frontend được phép gọi API (mặc định Vite chạy ở port 5173)
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

> **Lưu ý bảo mật quan trọng:**
> - File `.env` đã được cấu hình trong `.gitignore`. **Tuyệt đối không bao giờ commit file `.env` lên Git.**
> - Không chia sẻ file `.env` hoặc dùng chung mật khẩu với thành viên khác.
> - Độ dài của `SEED_ADMIN_PASSWORD` phải từ **6 đến 72 bytes** (giới hạn an toàn của thuật toán mã hóa BCrypt).
> - Sau khi đã khởi tạo xong CSDL lần đầu, nên đổi lại `SEED_DEV_ENABLED=false` để đảm bảo an toàn.

---

## Bước 5: Khởi Chạy Backend Bằng PowerShell

Do Spring Boot tiêu chuẩn không tự động nạp file `.env` từ ổ đĩa khi chạy `mvn spring-boot:run`, dự án cung cấp sẵn script PowerShell chuyên dụng [`run-local.ps1`](./run-local.ps1) giúp tự động nạp môi trường một cách an toàn.

### Cách 1: Sử dụng script `run-local.ps1` (Khuyến nghị)

Mở PowerShell tại thư mục `backend/` và thực thi:

```powershell
cd backend
.\run-local.ps1
```

**Cơ chế hoạt động của `run-local.ps1`:**
- Tự động định vị file `.env` cùng thư mục.
- Bỏ qua các dòng trống và dòng ghi chú bắt đầu bằng ký tự `#`.
- Xử lý các cặp `KEY=VALUE`, tự động bóc tách dấu nháy đơn (`'`) hoặc nháy kép (`"`) nếu có.
- Đặt các biến môi trường vào phạm vi `Process` của tiến trình con (không ảnh hưởng đến biến môi trường hệ thống).
- Tự động ánh xạ relaxed binding aliases của Spring Boot (`APP_SEED_DEV_ENABLED`, `SPRING_DATASOURCE_PASSWORD`, `APP_JWT_SECRET`).
- Kiểm tra tính đầy đủ của các biến bắt buộc (`DB_PASSWORD`, `JWT_SECRET`) trước khi chạy; che giấu mật khẩu và không bao giờ in thông tin nhạy cảm ra màn hình console.
- Khởi động ứng dụng bằng `mvn spring-boot:run` và trả về đúng exit code nếu quá trình chạy thất bại.

### Cách 2: Cấu hình biến môi trường trong IDE (IntelliJ IDEA)

Nếu bạn muốn debug trực tiếp qua IDE:
1. Mở dự án trong IntelliJ IDEA.
2. Mở file `BackendApplication.java`.
3. Nhấp chuột phải chọn **Edit 'BackendApplication' Run Configuration...**
4. Trong ô **Environment variables**, thêm chuỗi sau:
   ```text
   DB_PASSWORD=your_password;JWT_SECRET=your_jwt_secret_min_32_chars;SEED_DEV_ENABLED=true;SEED_ADMIN_EMAIL=admin@closet.com;SEED_ADMIN_PASSWORD=YourPassword123
   ```
5. Nhấn **Apply** và **Run**.

---

## Bước 6: Khởi Chạy Frontend Storefront

Mở một cửa sổ Terminal mới và chuyển đến thư mục `frontend/`:

```bash
cd frontend

# Cài đặt các thư viện phụ thuộc
npm install

# Khởi chạy Vite dev server
npm run dev
```

Giao diện Storefront sẽ khởi chạy tại: **`http://localhost:5173`**.

---

## Bước 7: Đăng Nhập Trang Quản Trị (Admin Dashboard)

### Thông tin tài khoản Admin sau khi khởi tạo

Ví dụ cấu hình trong `.env`:

```env
SEED_DEV_ENABLED=true
SEED_ADMIN_EMAIL=admin_a@closet.com
SEED_ADMIN_PASSWORD=MatKhauRiengCuaThanhVien
```

**Giải thích:**
- `admin_a@closet.com` là email dùng để đăng nhập.
- `MatKhauRiengCuaThanhVien` là mật khẩu do chính thành viên tự đặt.
- **Không sử dụng mật khẩu ví dụ làm mật khẩu chung cho cả nhóm.**
- **Không commit file `.env` lên GitHub.**
- Backend không in mật khẩu ra log.

### Đăng nhập Admin

Sau khi Backend khởi động và Seeder hoàn tất:

- **Frontend local:** `http://localhost:5173`
- **Trang đăng nhập:** `http://localhost:5173/login`
- Sau khi đăng nhập với tài khoản có role ADMIN, sử dụng route Admin hiện tại: **`/admin`** (truy cập `http://localhost:5173/admin` để vào Dashboard Quản trị).
  *(Lưu ý: Nếu bạn truy cập thẳng vào `http://localhost:5173/admin` khi chưa đăng nhập, `AdminRoute` sẽ tự động chuyển hướng về trang `/login`)*.

### Sau khi Seeder hoàn tất

Hướng dẫn thành viên:
1. **Xác nhận log:** Xác nhận log console của Backend cho biết Seeder hoàn thành (ví dụ: `Dev seeder: Sample data seeding completed successfully.`).
2. **Thử đăng nhập:** Thử đăng nhập bằng email (`SEED_ADMIN_EMAIL`) và mật khẩu (`SEED_ADMIN_PASSWORD`) đã cấu hình tại trang `http://localhost:5173/login`.
3. **Tắt Seeder:** Sau khi khởi tạo thành công, chuyển `SEED_DEV_ENABLED=false` trong `.env` để tránh kích hoạt seed ngoài ý muốn trong các lần khởi động tiếp theo.
4. **Giữ nguyên trạng thái:** Giữ nguyên Database local và tài khoản Admin đã tạo để tiếp tục phát triển.

**Lưu ý:**
- **Tắt Seeder không xóa tài khoản Admin đã được tạo.**
- **Nếu Database đã có dữ liệu nghiệp vụ, Seeder sẽ bỏ qua toàn bộ quá trình seed** (kiểm tra ngắn mạch qua `hasExistingBusinessData()`).
- **Seeder không ghi đè mật khẩu hoặc role của tài khoản Admin đã tồn tại.**
- **Nếu quên mật khẩu, sử dụng quy trình reset/change password hiện có.** Không thể lấy lại mật khẩu gốc từ BCrypt hash trong Database.

---

## Bước 8: Chạy Toàn Bộ Kiểm Thử Tự Động (Testing)

Để đảm bảo toàn bộ hệ thống hoạt động ổn định và không phát sinh lỗi hồi quy (regression), thực thi lệnh sau:

```bash
cd backend
mvn test
```

> ⚠️ **Quy Định An Toàn Cho Kiểm Thử:**
> - Toàn bộ test suite được cấu hình chạy cô lập trên database `clothing_shop_test`.
> - Lớp phòng vệ `TestDatabaseSafetyGuard.java` sẽ tự động xác minh URL và tên catalog CSDL trước khi thực thi. Nếu phát hiện CSDL không kết thúc bằng `_test`, hệ thống sẽ ném ngoại lệ dừng ngay lập tức để bảo vệ dữ liệu dev.
> - **Tuyệt đối không được chỉnh sửa hoặc vô hiệu hóa file `TestDatabaseSafetyGuard.java`.**

---

## Xử Lý Sự Cố Thường Gặp (Troubleshooting)

Dưới đây là 10 tình huống lỗi phổ biến nhất khi khởi tạo môi trường local và cách khắc phục:

### 1. `MySQL connection refused` (Không thể kết nối MySQL)
- **Triệu chứng:** Backend báo lỗi `Communications link failure` hoặc `Connection refused: connect`.
- **Nguyên nhân:** Dịch vụ MySQL Server chưa được bật, hoặc MySQL đang lắng nghe ở cổng khác cổng 3306.
- **Cách xử lý:** Mở Services (trên Windows) kiểm tra dịch vụ `MySQL80` (hoặc tên dịch vụ MySQL của bạn) và bật dịch vụ. Nếu dùng cổng khác (ví dụ 3307), bổ sung biến `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/clothing_shop_db...` vào file `.env`.

### 2. `Access denied for user 'shop'@'localhost'` (Sai mật khẩu hoặc quyền)
- **Triệu chứng:** Backend báo `Access denied for user 'shop'@'localhost' (using password: YES)`.
- **Nguyên nhân:** Mật khẩu trong `DB_PASSWORD` không khớp với mật khẩu thực tế trong MySQL, hoặc user `shop` chưa được tạo/cấp quyền.
- **Cách xử lý:** Mở MySQL CLI với tài khoản `root`, chạy lại các lệnh `CREATE USER` và `GRANT ALL PRIVILEGES` ở Bước 3, đồng thời kiểm tra lại giá trị `DB_PASSWORD` trong file `.env`.

### 3. `DB_PASSWORD chưa được load`
- **Triệu chứng:** Backend báo lỗi `IllegalArgumentException: Could not resolve placeholder 'DB_PASSWORD'`.
- **Nguyên nhân:** Chưa tạo file `.env`, hoặc chạy lệnh `mvn spring-boot:run` trên cửa sổ dòng lệnh mà không qua script `run-local.ps1` hay cấu hình IDE.
- **Cách xử lý:** Khởi động Backend bằng `.\run-local.ps1` hoặc thiết lập biến môi trường trước khi chạy Maven.

### 4. `JWT_SECRET thiếu hoặc quá ngắn`
- **Triệu chứng:** Ứng dụng ném lỗi `WeakKeyException` hoặc `The specified key byte array is 160 bits which is not secure enough for any JWT HMAC-SHA algorithm`.
- **Nguyên nhân:** Chuỗi `JWT_SECRET` trong file `.env` ngắn hơn 32 ký tự (yêu cầu tối thiểu 256 bits).
- **Cách xử lý:** Tạo một chuỗi ngẫu nhiên dài ít nhất 32 ký tự (khuyến nghị 64 ký tự hex) và cập nhật vào `JWT_SECRET` trong file `.env`.

### 5. `.env không được đọc khi chạy terminal`
- **Triệu chứng:** Chạy `mvn spring-boot:run` báo thiếu biến môi trường dù đã có file `.env`.
- **Nguyên nhân:** Maven và Spring Boot không có cơ chế tự động đọc file `.env` trên filesystem theo mặc định.
- **Cách xử lý:** Luôn sử dụng lệnh `.\run-local.ps1` từ thư mục `backend/`.

### 6. `Flyway migration failure` (Lỗi áp dụng migration)
- **Triệu chứng:** Khởi động gặp lỗi `Migration checksum mismatch` hoặc `Migration V... failed`.
- **Nguyên nhân:** CSDL local đã bị sửa tay cấu hình bảng hoặc có các migration cũ không khớp với git branch hiện tại.
- **Cách xử lý:** Do đây là CSDL local của bạn, hãy DROP database local và tạo lại từ đầu để Flyway migrate sạch từ V1 đến V8:
  ```sql
  DROP DATABASE IF EXISTS clothing_shop_db;
  CREATE DATABASE clothing_shop_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

### 7. `Role 'admin' not found in database`
- **Triệu chứng:** Seeder ném lỗi `IllegalStateException: Role 'admin' not found in database. Run Flyway migrations first.`
- **Nguyên nhân:** Migration V2 (`V2__seed_roles.sql`) chưa được áp dụng vào database.
- **Cách xử lý:** Kiểm tra bảng `flyway_schema_history` trong `clothing_shop_db`. Đảm bảo Flyway đã chạy xong migration V2.

### 8. `Seeder bị skip do Database đã có dữ liệu`
- **Triệu chứng:** Log hiển thị: `WARN: Dev seeder: Existing user data detected (users: ...). Skipping seed to protect database state.`
- **Nguyên nhân:** Cơ chế an toàn `Multi-Table Business Guard` phát hiện trong CSDL đã có dữ liệu (users, orders, categories,...) nên chủ động bỏ qua để tránh ghi đè dữ liệu đang có.
- **Cách xử lý:** Đây là tính năng an toàn bảo vệ CSDL, không phải lỗi. Nếu bạn muốn nạp lại dữ liệu mẫu từ đầu trên máy cá nhân, hãy DROP và tạo lại database `clothing_shop_db` rồi khởi chạy lại Backend.

### 9. `Seed enabled nhưng thiếu Admin credentials`
- **Triệu chứng:** Backend ném lỗi `IllegalStateException: Dev seeder is enabled (SEED_DEV_ENABLED=true) but SEED_ADMIN_EMAIL or SEED_ADMIN_PASSWORD is missing...` hoặc `SEED_ADMIN_PASSWORD must be between 6 and 72 bytes`.
- **Nguyên nhân:** Đặt `SEED_DEV_ENABLED=true` nhưng để trống email/password, hoặc mật khẩu ngắn hơn 6 ký tự.
- **Cách xử lý:** Điền đầy đủ email và mật khẩu hợp lệ (từ 6 đến 72 ký tự) vào file `.env`.

### 10. `Frontend không kết nối được Backend / Lỗi CORS`
- **Triệu chứng:** Trình duyệt báo `Network Error` hoặc `Access to XMLHttpRequest at 'http://localhost:8080/api/...' from origin 'http://localhost:5173' has been blocked by CORS policy`.
- **Nguyên nhân:** Backend chưa chạy, đang chạy ở port khác 8080, hoặc biến `CORS_ALLOWED_ORIGINS` trong Backend không chứa `http://localhost:5173`.
- **Cách xử lý:** Đảm bảo Backend đang chạy ở port 8080 và biến `CORS_ALLOWED_ORIGINS=http://localhost:5173` được thiết lập chính xác trong file `.env`.

---

## Nguyên Tắc An Toàn CSDL Bắt Buộc

Mọi thành viên trong nhóm phải tuân thủ nghiêm ngặt các nguyên tắc an toàn dữ liệu sau:

1. **Chỉ dùng Seeder cho CSDL local mới:** Cơ chế Dev Data Seeder được thiết kế duy nhất cho mục đích khởi tạo nhanh dữ liệu mẫu khi mới dựng môi trường.
2. **`SEED_DEV_ENABLED=false` là mặc định:** Sau khi đã có tài khoản Admin và dữ liệu mẫu, luôn đặt lại `SEED_DEV_ENABLED=false`.
3. **Tuyệt đối không kết nối CSDL trưởng nhóm:** Mỗi thành viên sở hữu và chịu trách nhiệm độc lập với CSDL trên máy cá nhân.
4. **Không chạy seeder trong môi trường kiểm thử hoặc production:** Lớp bảo vệ kép `@Profile({"local", "dev"})` kết hợp kiểm tra runtime sẽ chặn đứng seeder nếu phát hiện profile `prod` hoặc `test`.
5. **Bảo mật tuyệt đối file `.env`:** Tuyệt đối không đưa mật khẩu, JWT secret hay thông tin bí mật cá nhân lên GitHub.
