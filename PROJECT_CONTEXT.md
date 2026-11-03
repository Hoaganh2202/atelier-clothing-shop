# PROJECT_CONTEXT.md

Tài liệu ngữ cảnh chính của dự án. Đọc file này trước khi thực hiện bất kỳ tác vụ code nào. Với chi tiết bảng dữ liệu (khóa chính, kiểu dữ liệu, khóa ngoại, index), xem `schema-spec.md`. Với quy tắc code backend, xem `BACKEND_RULES.md`. Với quy tắc thiết kế giao diện, xem `FRONTEND_DESIGN_RULES.md`, `DESIGN.md` và `DESIGN_ADMIN.md`.

---

## 0. Phạm vi đồ án

Mục tiêu của dự án là xây dựng một **website thương mại điện tử thời trang hoàn chỉnh, độc lập**, bao gồm đầy đủ hai phân hệ:
1. **Storefront (Dành cho khách hàng):** Khám phá danh mục, tìm kiếm, lọc sản phẩm, xem chi tiết biến thể tồn kho, quản lý giỏ hàng (khách vãng lai & thành viên), sổ địa chỉ, thanh toán đơn hàng (COD và cổng thanh toán), quản lý và theo dõi trạng thái đơn hàng, đánh giá sản phẩm và danh sách yêu thích.
2. **Trang quản trị (Admin Workspace):** Bảng điều khiển (dashboard) thống kê doanh thu vận hành, quản trị sản phẩm & danh mục hàng hóa, quản lý tồn kho biến thể, xử lý đơn hàng và giao vận, quản lý mã giảm giá voucher & khuyến mãi, quản lý khách hàng và phân quyền tài khoản (ADMIN/STAFF).

Dự án là đồ án tốt nghiệp độc lập, sở hữu trọn vẹn cơ sở dữ liệu và toàn bộ schema, phát triển theo kiến trúc chuẩn mực sẵn sàng đưa vào vận hành.

---

## 1. Tổng quan dự án

Website thương mại điện tử bán quần áo, gồm 2 phần: **storefront** (khách mua hàng) và **trang quản trị** (admin). Dự án do **2 thành viên** phụ trách chung cả Frontend và Backend, chia việc theo module nghiệp vụ dọc (xem mục 3). Toàn bộ schema CSDL được quản lý độc lập và tự động thông qua Flyway migrations.

---

## 2. Công nghệ sử dụng

### Frontend
| Hạng mục | Lựa chọn |
|---|---|
| Ngôn ngữ | TypeScript |
| Framework | React, build bằng Vite |
| Styling | Tailwind CSS + **Ant Design** (khu vực Admin) + **shadcn/ui** (khu vực Storefront) — không trộn lẫn hai hệ trong cùng một khu vực |
| Routing | React Router (nhóm route khách và `/admin`, có route guard theo vai trò) |
| Dữ liệu | TanStack Query + Axios (interceptor tự làm mới access token) |
| Form | React Hook Form + Zod |
| Trạng thái | Zustand (giỏ hàng, phiên đăng nhập) |
| Biểu đồ | Recharts |
| Kiểu API | openapi-typescript (sinh từ springdoc-openapi của backend) |
| Triển khai | Nginx phục vụ bản build và chuyển tiếp `/api` tới Spring Boot |

### Backend
| Hạng mục | Lựa chọn |
|---|---|
| Ngôn ngữ | Java 17+ (theo yêu cầu phiên bản Spring Boot đã chọn) |
| Framework | Spring Boot (Web, Validation, Security + JWT) |
| ORM | Spring Data JPA + Hibernate (`ddl-auto=validate`) |
| CSDL | MySQL 8 |
| Migration | Flyway, sở hữu toàn bộ schema (`db/migration/V*__*.sql`) |
| Build | Maven |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Quyền sở hữu CSDL | Dự án độc lập, sở hữu toàn bộ bảng và dữ liệu; quản lý schema tập trung và duy nhất qua Flyway |

**Không tự ý đổi công nghệ:** không chuyển backend sang Node.js/Python/NestJS, không thay Flyway bằng công cụ khác, không thay đổi cặp Ant Design/shadcn đã chọn cho từng khu vực.

---

## 3. Cấu trúc thư mục và phân công

Nguyên tắc chung: chia theo **module nghiệp vụ** (feature), không chia theo lớp kỹ thuật, để mỗi thành viên chỉ làm việc trong thư mục module của mình, giảm xung đột Git. Chi tiết cây thư mục đầy đủ nằm trong `README.md`; đây là tóm tắt để tra nhanh.

### Backend (`backend/src/main/java/com/shop/backend/`)
```
config/ · common/ · security/
auth/ · user/ · product/ · cart/ · order/ · voucher/ · promotion/ · payment/ · address/
```
Mỗi module tự chứa: Entity, Repository, Service, Controller, `dto/`.

### Frontend (`frontend/src/`)
```
api/ · routes/ · layouts/ · components/ · stores/ · lib/
features/
  auth/ · product/ · cart/ · order/ · address/
  admin/
    product/ · order/ · voucher/ · promotion/
    customer/   ← Quản lý tài khoản khách hàng, lịch sử mua hàng, khóa/mở
    dashboard/
```

### Phân công trách nhiệm

| Vai trò / Thành viên | Backend | Frontend | Ghi chú phạm vi |
|---|---|---|---|
| **Tôi (Admin Owner)** | `auth/`, `user/`, `product/`, `address/`, `dashboard/`, `order/` | `features/auth/`, `features/product/`, `features/address/`, `features/admin/` (toàn bộ: `dashboard/`, `product/`, `category/`, `customer/`, `order/`... trừ `voucher/`), `features/order/` | **Phụ trách toàn bộ phân hệ Admin** (cả BE & FE) ngoại trừ Admin Voucher. Module `order/` hiện **chưa có ai triển khai (Not Started)**; Tôi chủ động triển khai theo thứ tự dependency. |
| **Thành viên khác** | `voucher/` | `features/admin/voucher/` | **Hiện chỉ phụ trách riêng module Admin Voucher**. Đang triển khai độc lập Admin Voucher. Cần phối hợp khi sinh `schema.d.ts` để tránh xung đột/ghi đè định nghĩa API. |

---

## 4. Dữ liệu

### 4.1 Nguồn dữ liệu — chiến lược lai (hybrid)

Dự án dùng kết hợp dữ liệu thật và dữ liệu giả lập, phân biệt bằng cột `data_source` trên `users`, `products`, `orders` (`hm_real` | `synthetic` | `website`):

- **`hm_real`**: nạp từ bộ dữ liệu mở H&M Personalized Fashion Recommendations (Kaggle) — sản phẩm, ảnh, danh mục, lịch sử giao dịch. Việc nạp thực hiện qua script ETL riêng (Python/pandas), không thuộc phạm vi code backend/frontend hằng ngày.
- **`synthetic`**: dữ liệu giả lập cho các bảng H&M không có (voucher, khuyến mãi, giỏ hàng, đổi trả...).
- **`website`**: dữ liệu phát sinh thật từ người dùng thao tác trên website.

### 4.2 Quy tắc nghiệp vụ quan trọng — không được bỏ qua khi viết Service

- **`orders.status`**: chỉ đơn hàng `completed` được tính vào các báo cáo/thống kê doanh thu, phân loại khách hàng (RFM). Đơn `pending`, `cancelled`, `refunded` không tính.
- **`order_details.unit_price`, `variant_size`, `variant_color` là snapshot** — giá và thông tin kích cỡ, màu sắc tại thời điểm mua, không tham chiếu ngược lại bảng `products` hay `product_variants` khi hiển thị lịch sử đơn hàng cũ.
- **`product_variants`** giữ tồn kho (`stock`) riêng theo từng size/màu — không dùng một cột tồn kho chung ở `products`. Ràng buộc `CHECK (stock >= 0)` ở DB; ở tầng Service bắt buộc dùng Atomic Update (`UPDATE product_variants SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity`) và kiểm tra affected rows để chống bán khống.
- **Địa chỉ giao hàng trong `orders`** (`receiver_name`, `receiver_phone`, `address_line`...) là **bản sao (snapshot)** tại thời điểm đặt hàng, độc lập với `user_addresses` — việc sửa sổ địa chỉ sau này không được làm thay đổi đơn hàng cũ. Lưu ý sự khác nhau về tên cột chi tiết: bảng `user_addresses` dùng `address`, bảng `orders` dùng `address_line` (OrderService cần map: `order.setAddressLine(userAddress.getAddress())`).
- **Giỏ hàng (`carts`)**: mỗi user chỉ có duy nhất **một** giỏ hàng ở trạng thái `active` tại một thời điểm, được kiểm soát chặt chẽ ở `CartService`. Khách chưa đăng nhập lưu giỏ hàng phía client bằng Zustand (persist localStorage); khi đăng nhập thành công, frontend gọi API để gộp (merge) giỏ tạm vào giỏ `active` của user trên server.
- **Mã giảm giá (`vouchers`)**: số lượt đã dùng được đếm động từ `orders.voucher_id` (chỉ tính các đơn có trạng thái khác `cancelled` và `refunded`), không tạo bảng phụ `voucher_usages`. Cột `target_segment_id` (nếu có từ V1) không sử dụng vì hệ thống phân nhóm CSDL cũ đã tách thành dự án riêng.
- **Tài khoản người dùng (`users`)**: đối với tài khoản đăng ký trên website (`data_source = 'website'`), bắt buộc phải có ít nhất một trong hai thông tin `email` hoặc `phone` (kiểm tra chặt chẽ ở tầng Service/Validation).
- **Tìm kiếm sản phẩm**: sử dụng truy vấn `LIKE %keyword%` (không phân biệt hoa thường theo collation) kết hợp phân trang cho giai đoạn hiện tại để tối ưu cho tiếng Việt có dấu/không dấu; khi dữ liệu mở rộng lớn hơn sẽ bổ sung `FULLTEXT INDEX`.
- Chi tiết đầy đủ về bảng, cột, khóa ngoại, index: xem `schema-spec.md`.

---

## 5. Quy ước API

- Tiền tố chung: `/api/...`, khu vực quản trị: `/api/admin/...`.
- Xác thực: JWT gửi qua header `Authorization: Bearer <token>`; refresh token lưu ở cookie HttpOnly, không lộ ra JavaScript phía client.
- Định dạng lỗi thống nhất, ví dụ:
```json
{ "status": 400, "message": "Số điện thoại không hợp lệ", "errors": { "phone": "..." } }
```
- Phân trang thống nhất: query `?page=0&size=20`, response kèm `totalElements`, `totalPages`.
- Tiền tệ: Trong database lưu `DECIMAL(12,2)`. Tuy nhiên trên API Response (DTO), tiền tệ bắt buộc **trả về dạng số nguyên VND (`Long`)**, không trả về số thập phân để tránh sai số khi qua JSON và tiện cho frontend hiển thị.

## 6. Quy ước code

**Backend:** khai báo rõ `@Table(name=...)`, `@Column(name=...)` (không để Hibernate tự suy đoán tên cột); tiền trong Entity dùng `BigDecimal`/`DECIMAL(12,2)`, DTO dùng `Long`; cột enum trong DB lưu `VARCHAR(30)` + ràng buộc `CHECK ... IN (...)` với giá trị chữ thường (`lowercase`), tầng Java dùng Enum hằng số chữ HOA và **bắt buộc dùng JPA `AttributeConverter` kế thừa `AbstractEnumConverter` kèm `@Converter(autoApply = true)`** để toàn bộ module nhất quán; quan hệ `@ManyToOne(fetch = FetchType.LAZY)`; truy vấn tổng hợp cho dashboard dùng `@Query` với `GROUP BY`, không nạp cả entity rồi đếm bằng vòng lặp Java.

**Frontend:** một hook TanStack Query riêng theo mỗi module (`useProducts`, `useOrders`...); schema Zod đặt trong `features/<module>/schemas/`; không gọi `fetch`/`axios` trực tiếp trong component, luôn qua lớp `api/`.

---

## 7. Tài liệu liên quan trong repo

| File | Nội dung |
|---|---|
| `GEMINI.md` | Điểm vào, tự động nạp cho agent, trỏ tới các file dưới đây |
| `PROJECT_CONTEXT.md` (file này) | Tổng quan, công nghệ, cấu trúc, quy tắc nghiệp vụ, quy ước API/code |
| `schema-spec.md` | Đặc tả chi tiết từng bảng: cột, kiểu dữ liệu, khóa ngoại, index |
| `BACKEND_RULES.md` | Quy tắc kiến trúc phân tầng, JPA/Hibernate, Flyway, bảo mật và kiểm thử backend |
| `FRONTEND_DESIGN_RULES.md` | Quy tắc thiết kế giao diện, token khóa cứng, thứ tự ưu tiên khi có skill bên thứ ba |
| `DESIGN.md` | Đặc tả hệ thống thiết kế và bố cục khu vực Storefront |
| `DESIGN_ADMIN.md` | Đặc tả bố cục, bảng biểu và thẻ KPI khu vực Admin (Ant Design) |
| `README.md` | Cây thư mục đầy đủ, hướng dẫn onboarding cho thành viên mới |
