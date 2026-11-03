# Cấu trúc thư mục dự án — Shop quần áo

Tài liệu này mô tả cách tổ chức thư mục của **Frontend** và **Backend**, để thành viên mới đọc là biết code của mình đặt ở đâu, và biết cách mở rộng khi thêm chức năng mới.

**Nguyên tắc chung:** chia thư mục theo **module nghiệp vụ** (feature), không chia theo lớp kỹ thuật (không gom hết controller vào một thư mục, hết service vào một thư mục khác). Nhờ vậy mỗi người chỉ làm việc trong thư mục module của mình, hạn chế xung đột Git, và khi cần sửa một chức năng thì mọi thứ liên quan (giao diện, gọi API, xử lý) nằm gọn trong một chỗ.

---

## 1. Backend (Java + Spring Boot)

```
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/shop/backend/
│   │   │   ├── BackendApplication.java
│   │   │   │
│   │   │   ├── config/              # Cấu hình toàn hệ thống
│   │   │   ├── common/              # Dùng chung: exception, response chuẩn, tiện ích
│   │   │   ├── security/            # JWT, filter xác thực
│   │   │   │
│   │   │   ├── auth/                # Đăng ký, đăng nhập, refresh token
│   │   │   ├── user/                # Tài khoản người dùng
│   │   │   ├── product/             # Sản phẩm, biến thể, danh mục, ảnh
│   │   │   ├── cart/                # Giỏ hàng
│   │   │   ├── order/               # Đặt hàng, chi tiết đơn
│   │   │   ├── voucher/             # Mã giảm giá
│   │   │   ├── promotion/           # Khuyến mãi theo sản phẩm
│   │   │   ├── payment/             # Thanh toán
│   │   │   └── address/             # Sổ địa chỉ giao hàng
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/        # Flyway — nơi DUY NHẤT được sửa schema
│   │           └── V*__*.sql
│   │
│   └── test/java/com/shop/backend/  # Test, cấu trúc gương với main/
│
└── application-local.yml            # Không commit — chứa mật khẩu DB máy cá nhân
```

### Quy tắc trong mỗi thư mục module (ví dụ `product/`)

Mỗi module nghiệp vụ tự chứa đủ các lớp của riêng nó:

```
product/
├── Product.java              # Entity (ánh xạ bảng products)
├── ProductVariant.java
├── ProductImage.java
├── Category.java
├── ProductRepository.java    # Truy vấn CSDL
├── ProductService.java       # Xử lý nghiệp vụ
├── ProductController.java    # API cho khách xem sản phẩm
├── AdminProductController.java  # API cho admin quản lý sản phẩm
└── dto/                       # Request/Response, không lộ Entity ra ngoài API
    ├── ProductCreateRequest.java
    └── ProductResponse.java
```

**Khi thêm module mới:** tạo thư mục cùng cấp với `product/`, `order/`..., đặt tên theo danh từ nghiệp vụ số ít (ví dụ `review/` cho đánh giá sản phẩm), và giữ đúng bộ 5 loại file như trên (Entity, Repository, Service, Controller, dto/).

### Ai đặt gì ở đâu

| Thư mục | Vai trò | Ghi chú |
|---|---|---|
| `config/` | Cấu hình Spring Security, JWT, CORS, Swagger | Sửa khi đổi cấu hình toàn cục |
| `common/` | Exception dùng chung, khuôn response API | Không đặt logic nghiệp vụ ở đây |
| `security/` | Sinh và kiểm tra JWT | Người phụ trách `auth/` chịu trách nhiệm chính |
| `db/migration/` | Toàn bộ lệnh tạo/sửa bảng, index | Quản lý schema tập trung bằng các file migration Flyway `V*` |

### Phân công theo module Backend

| Thành viên / Vai trò | Module phụ trách | Ghi chú phạm vi |
|---|---|---|
| **Tôi (Admin Owner)** | `auth/`, `user/`, `product/`, `address/`, `dashboard/`, `order/` | Phụ trách toàn bộ Backend Admin và các module cốt lõi. Module `order/` hiện **chưa có ai triển khai (Not Started)**; Tôi chủ động triển khai. |
| **Thành viên khác** | `voucher/` | Hiện chỉ phụ trách riêng module Admin Voucher. |

---

## 2. Frontend (React + TypeScript + Vite)

```
frontend/
├── vite.config.ts
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   │
│   ├── api/              # Cấu hình axios, type sinh từ OpenAPI của backend
│   ├── routes/            # Khai báo route — KHÔNG chứa logic hiển thị
│   │   └── guards/         # Chặn truy cập theo vai trò (đăng nhập, admin)
│   ├── layouts/           # Khung trang: header/footer khách, sidebar admin
│   ├── components/        # Component dùng chung, KHÔNG thuộc riêng module nào
│   │   ├── ui/              # shadcn/ui
│   │   ├── common/          # PriceTag, Pagination, LoadingSpinner...
│   │   └── layout/          # Navbar, Sidebar
│   ├── stores/             # Zustand — trạng thái toàn cục (giỏ hàng, phiên đăng nhập)
│   ├── lib/                # Hàm tiện ích (định dạng tiền, ngày tháng...)
│   │
│   └── features/          # === CODE CHÍNH, CHIA THEO MODULE ===
│       ├── auth/
│       ├── product/
│       ├── cart/
│       ├── order/
│       ├── address/
│       └── admin/          # Trang quản trị, chia nhỏ tiếp theo module
│           ├── product/
│           ├── order/
│           ├── voucher/
│           ├── promotion/
│           ├── customer/    # Quản lý tài khoản khách hàng, khóa/mở, lịch sử đơn
│           └── dashboard/
│
└── public/
```

### Quy tắc trong mỗi thư mục `features/<module>/`

```
product/
├── api/
│   └── product.api.ts        # Hàm gọi API, dùng axios instance chung
├── hooks/
│   ├── useProducts.ts         # TanStack Query — lấy danh sách
│   └── useProductDetail.ts
├── components/
│   ├── ProductCard.tsx        # Component chỉ dùng trong module này
│   └── VariantSelector.tsx
├── schemas/                   # Zod schema cho form (nếu có)
└── pages/
    ├── ProductListPage.tsx    # Trang hoàn chỉnh, gắn vào router
    └── ProductDetailPage.tsx
```

**Khi thêm module mới:** tạo thư mục cùng cấp trong `features/`, giữ đúng các thư mục con (`api/`, `hooks/`, `components/`, `pages/`). Thêm route mới vào `routes/storefront.routes.tsx` hoặc `routes/admin.routes.tsx` tương ứng.

### Quy tắc phân biệt `components/` (dùng chung) và `features/<module>/components/` (riêng module)

- Một component **chỉ dùng trong một trang/module** → để trong `features/<module>/components/`.
- Một component **dùng ở nhiều module khác nhau** (nút bấm, hộp thoại, hiển thị giá tiền...) → chuyển lên `components/common/` hoặc `components/ui/`.
- Nếu không chắc, cứ để trong module trước; khi thấy cần dùng lại ở module khác thì chuyển lên `components/common/`.

### Ai đặt gì ở đâu

| Thư mục | Vai trò | Ghi chú |
|---|---|---|
| `api/client.ts` | Cấu hình axios, interceptor tự làm mới token | Sửa khi đổi cách xác thực |
| `api/types.gen.ts` | Type TypeScript sinh tự động từ Swagger của backend | **Không sửa tay** — chạy lại `npm run gen:api` khi backend đổi |
| `routes/guards/` | `RequireAuth`, `RequireAdmin` | Thêm guard mới nếu cần thêm vai trò (ví dụ nhân viên kho) |
| `stores/` | Trạng thái dùng ở nhiều trang (giỏ hàng, người dùng đăng nhập) | Trạng thái chỉ dùng trong một trang thì để `useState` tại trang đó, không đưa vào đây |
| `features/admin/customer/` | Quản lý danh sách khách hàng, chi tiết tài khoản, khóa/mở | Gọi API `GET`, `PATCH` quản lý người dùng |

### Phân công theo module Frontend

| Thành viên / Vai trò | Module phụ trách | Ghi chú phạm vi |
|---|---|---|
| **Tôi (Admin Owner)** | `features/auth/`, `features/product/`, `features/address/`, `features/admin/` (toàn bộ: `dashboard/`, `product/`, `category/`, `customer/`, `order/`... trừ `voucher/`), `features/order/` | Phụ trách toàn bộ Frontend Admin (Dashboard, Sản phẩm, Danh mục, Khách hàng, Đơn hàng) ngoại trừ Admin Voucher. |
| **Thành viên khác** | `features/admin/voucher/` | Hiện chỉ phụ trách riêng Frontend Admin Voucher. Đang thực hiện Admin Voucher. |

---

## 3. Quy ước chung giữa Frontend và Backend

- Tên module ở hai bên đặt **giống nhau** (`product` ↔ `product`, `order` ↔ `order`) để dễ tra cứu API tương ứng.
- Mỗi khi backend đổi API, chạy `npm run gen:api` ở frontend để đồng bộ type, tránh gọi sai tên trường.
- Dự án là hệ thống thương mại điện tử độc lập, quản lý dữ liệu khép kín từ danh mục, sản phẩm, giỏ hàng, thanh toán đến vận hành đơn hàng.
- Khi không chắc một đoạn code nên đặt ở module nào, ưu tiên đặt theo **bảng dữ liệu chính** mà nó thao tác (ví dụ xử lý áp voucher khi đặt hàng → đặt trong `order/`, không đặt trong `voucher/`).
