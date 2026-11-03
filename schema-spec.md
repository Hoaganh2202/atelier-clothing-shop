# Đặc tả CSDL — V1__init_schema.sql

Phạm vi: CSDL của website thương mại điện tử độc lập.
*Ghi chú về phân hệ ngoài phạm vi:* Ba bảng `segments`, `segmentation_runs`, `customer_segments` thuộc phân hệ khai thác CSDL cũ đã được tách thành dự án riêng và **đã loại khỏi phạm vi** của repo này.

Quy ước chung: mọi bảng có `id BIGINT AUTO_INCREMENT PRIMARY KEY` trừ bảng liên kết nhiều-nhiều. Tiền tệ dùng `DECIMAL(12,2)`. Thời gian dùng `DATETIME`. Engine `InnoDB`, charset `utf8mb4`. Các cột phân loại/trạng thái dùng `VARCHAR(30)` kèm ràng buộc `CHECK ... IN (...)` với giá trị chữ thường (`lowercase`) để tương thích tối đa với Hibernate 6 (`ddl-auto=validate`).

---

## Nhóm 1: Người dùng & Bảo mật

### roles
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| name | VARCHAR(30) | UNIQUE, NOT NULL — admin / staff / customer |

### users
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| role_id | BIGINT | NOT NULL, FK → roles.id |
| name | VARCHAR(150) | NOT NULL |
| email | VARCHAR(150) | UNIQUE, NULL — cần cho đăng nhập/khôi phục mật khẩu |
| phone | VARCHAR(20) | UNIQUE, NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'active', CHECK (status IN ('active', 'blocked')) |
| last_login_at | DATETIME | NULL |
| gender | VARCHAR(30) | NULL, CHECK (gender IS NULL OR gender IN ('male', 'female', 'other')) |
| birth_date | DATE | NULL |
| data_source | VARCHAR(30) | NOT NULL, DEFAULT 'website', CHECK (data_source IN ('hm_real', 'synthetic', 'website')) |
| source_customer_id | VARCHAR(64) | NULL — lưu mã khách gốc khi nạp từ H&M |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

Index: `idx_users_role (role_id)`

### refresh_tokens
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users.id |
| token_hash | VARCHAR(255) | NOT NULL |
| expires_at | DATETIME | NOT NULL |
| revoked_at | DATETIME | NULL |
| replaced_by_id | BIGINT | NULL, FK → refresh_tokens.id |
| user_agent | VARCHAR(255) | NULL |
| ip | VARCHAR(45) | NULL |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

Index: `idx_refresh_user (user_id)`, `idx_refresh_expires (expires_at)`

### password_reset_tokens
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users.id |
| token_hash | VARCHAR(255) | NOT NULL |
| expires_at | DATETIME | NOT NULL |
| used_at | DATETIME | NULL |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

Index: `idx_prt_user (user_id)`

---

## Nhóm 2: Sản phẩm & Danh mục

### categories
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| parent_id | BIGINT | NULL, FK → categories.id (tự tham chiếu) |
| name | VARCHAR(150) | NOT NULL |
| slug | VARCHAR(180) | UNIQUE, NOT NULL |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |

Index: `idx_cat_parent (parent_id)`

### products
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| category_id | BIGINT | NOT NULL, FK → categories.id |
| name | VARCHAR(255) | NOT NULL |
| slug | VARCHAR(280) | UNIQUE, NOT NULL |
| description | TEXT | NULL |
| base_price | DECIMAL(12,2) | NOT NULL |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| data_source | VARCHAR(30) | NOT NULL, DEFAULT 'synthetic', CHECK (data_source IN ('hm_real', 'synthetic', 'website')) |
| source_product_code | VARCHAR(64) | NULL — mã `product_code` gốc từ H&M |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

Index: `idx_products_category (category_id)`

### product_variants
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| product_id | BIGINT | NOT NULL, FK → products.id |
| sku | VARCHAR(64) | UNIQUE, NOT NULL |
| size | VARCHAR(20) | NOT NULL, DEFAULT 'N/A' |
| color | VARCHAR(50) | NOT NULL |
| stock | INT | NOT NULL, DEFAULT 0, CHECK (stock >= 0) |
| price | DECIMAL(12,2) | NULL — null nghĩa là dùng `base_price` |
| source_article_id | VARCHAR(64) | NULL — mã `article_id` gốc từ H&M |

Ràng buộc: `UNIQUE (product_id, size, color)`
Index: `idx_variant_product (product_id)`

### product_images
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| product_id | BIGINT | NOT NULL, FK → products.id |
| url | VARCHAR(500) | NOT NULL |
| alt | VARCHAR(255) | NULL |
| is_primary | BOOLEAN | NOT NULL, DEFAULT FALSE |
| sort_order | INT | NOT NULL, DEFAULT 0 |
| color | VARCHAR(50) | NULL |

Index: `idx_images_product (product_id)`

### promotions
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(150) | NOT NULL |
| discount_type | VARCHAR(30) | NOT NULL, CHECK (discount_type IN ('percent', 'amount')) |
| discount_value | DECIMAL(12,2) | NOT NULL |
| start_at | DATETIME | NOT NULL |
| end_at | DATETIME | NOT NULL |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |

### product_promotions
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| product_id | BIGINT | NOT NULL, FK → products.id |
| promotion_id | BIGINT | NOT NULL, FK → promotions.id |

Khóa chính kép: `PRIMARY KEY (product_id, promotion_id)`

---

## Nhóm 3: Mua sắm & Giao dịch

### user_addresses
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users.id |
| receiver_name | VARCHAR(150) | NOT NULL |
| phone | VARCHAR(20) | NOT NULL |
| province | VARCHAR(100) | NOT NULL |
| district | VARCHAR(100) | NULL |
| ward | VARCHAR(100) | NULL — bổ sung đồng bộ với orders |
| address | VARCHAR(255) | NOT NULL |
| is_default | BOOLEAN | NOT NULL, DEFAULT FALSE |

Index: `idx_address_user (user_id)`

### carts
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users.id |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'active', CHECK (status IN ('active', 'converted', 'abandoned')) |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |

Index: `idx_cart_user_status (user_id, status)`

### cart_items
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| cart_id | BIGINT | NOT NULL, FK → carts.id |
| variant_id | BIGINT | NOT NULL, FK → product_variants.id |
| quantity | INT | NOT NULL, DEFAULT 1, CHECK (quantity > 0) |

Ràng buộc: `UNIQUE (cart_id, variant_id)`

### vouchers
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| code | VARCHAR(50) | UNIQUE, NOT NULL |
| discount_type | VARCHAR(30) | NOT NULL, CHECK (discount_type IN ('percent', 'amount')) |
| discount_value | DECIMAL(12,2) | NOT NULL |
| min_order | DECIMAL(12,2) | NULL |
| max_discount | DECIMAL(12,2) | NULL |
| usage_limit | INT | NULL |
| per_user_limit | INT | NULL, DEFAULT 1 |
| start_at | DATETIME | NOT NULL |
| end_at | DATETIME | NOT NULL |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| target_segment_id | BIGINT | NULL — Cột kế thừa từ V1 cũ, không sử dụng do bảng phân khúc đã loại khỏi phạm vi |

### orders
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users.id |
| voucher_id | BIGINT | NULL, FK → vouchers.id |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'pending', CHECK (status IN ('pending', 'confirmed', 'shipping', 'completed', 'cancelled', 'refunded')) |
| subtotal | DECIMAL(12,2) | NOT NULL |
| discount_amount | DECIMAL(12,2) | NOT NULL, DEFAULT 0 |
| shipping_fee | DECIMAL(12,2) | NOT NULL, DEFAULT 0 |
| total_amount | DECIMAL(12,2) | NOT NULL |
| payment_method | VARCHAR(30) | NULL |
| receiver_name | VARCHAR(150) | NOT NULL |
| receiver_phone | VARCHAR(20) | NOT NULL |
| address_line | VARCHAR(255) | NULL |
| ward | VARCHAR(100) | NULL |
| district | VARCHAR(100) | NULL |
| province | VARCHAR(100) | NOT NULL |
| note | VARCHAR(500) | NULL |
| sales_channel | VARCHAR(20) | NULL — dùng khi nạp dữ liệu H&M (store/online) |
| data_source | VARCHAR(30) | NOT NULL, DEFAULT 'website', CHECK (data_source IN ('hm_real', 'synthetic', 'website')) |
| created_at | DATETIME | NOT NULL |
| completed_at | DATETIME | NULL |

Index: `idx_orders_user_created (user_id, created_at)`, `idx_orders_status_created (status, created_at)`, `idx_orders_province (province)`

### order_details
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | NOT NULL, FK → orders.id |
| variant_id | BIGINT | NOT NULL, FK → product_variants.id |
| product_name | VARCHAR(255) | NOT NULL — snapshot tên sản phẩm |
| variant_size | VARCHAR(20) | NOT NULL, DEFAULT 'N/A' — snapshot size biến thể tại thời điểm mua |
| variant_color | VARCHAR(50) | NOT NULL — snapshot màu biến thể tại thời điểm mua |
| quantity | INT | NOT NULL, CHECK (quantity > 0) |
| unit_price | DECIMAL(12,2) | NOT NULL — snapshot giá tại thời điểm mua |
| discount_amount | DECIMAL(12,2) | NOT NULL, DEFAULT 0 |

Index: `idx_details_order (order_id)`, `idx_details_variant (variant_id)`

### payments
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | NOT NULL, FK → orders.id |
| method | VARCHAR(30) | NOT NULL, CHECK (method IN ('cod', 'vnpay', 'momo', 'bank')) |
| amount | DECIMAL(12,2) | NOT NULL |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'pending', CHECK (status IN ('pending', 'success', 'failed', 'refunded')) |
| transaction_code | VARCHAR(100) | NULL |
| paid_at | DATETIME | NULL |

Index: `idx_payments_order (order_id)`

---

## Nhóm 4: Mở rộng (làm nếu còn thời gian, ưu tiên thấp hơn 3 nhóm trên)

### order_returns
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | NOT NULL, FK → orders.id |
| order_detail_id | BIGINT | NOT NULL, FK → order_details.id |
| reason | VARCHAR(255) | NULL |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'requested', CHECK (status IN ('requested', 'approved', 'rejected', 'completed')) |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

### reviews
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| product_id | BIGINT | NOT NULL, FK → products.id |
| user_id | BIGINT | NOT NULL, FK → users.id |
| order_id | BIGINT | NULL, FK → orders.id — ràng buộc chỉ khách đã mua mới đánh giá (tùy chọn) |
| rating | TINYINT | NOT NULL, CHECK (rating BETWEEN 1 AND 5) |
| comment | TEXT | NULL |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

Ràng buộc gợi ý: `UNIQUE (product_id, user_id)` — mỗi khách chỉ đánh giá một sản phẩm một lần

### wishlists
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK |
| user_id | BIGINT | NOT NULL, FK → users.id |
| product_id | BIGINT | NOT NULL, FK → products.id |
| created_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

Ràng buộc: `UNIQUE (user_id, product_id)`

---

## Nhóm 5: Cấu hình hệ thống — V8__store_settings.sql

### store_settings
Bảng cấu hình chung của cửa hàng và vận hành hệ thống. Bảng áp dụng thiết kế một dòng duy nhất (`id = 1`) có dữ liệu khởi tạo mặc định.

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | BIGINT | PK, DEFAULT 1, CHECK (id = 1) |
| store_name | VARCHAR(150) | NOT NULL — Tên hiển thị cửa hàng |
| legal_name | VARCHAR(255) | NULL — Tên pháp nhân công ty |
| tax_code | VARCHAR(30) | NULL — Mã số thuế doanh nghiệp |
| address | VARCHAR(300) | NULL — Địa chỉ trụ sở/cửa hàng |
| phone | VARCHAR(30) | NULL — Số điện thoại liên hệ |
| email | VARCHAR(150) | NULL — Email liên hệ |
| logo_url | VARCHAR(500) | NULL — Đường dẫn ảnh logo cửa hàng |
| low_stock_threshold | INT | NOT NULL, DEFAULT 10, CHECK (low_stock_threshold >= 0) |
| default_shipping_fee | DECIMAL(12,2) | NOT NULL, DEFAULT 30000.00, CHECK (default_shipping_fee >= 0) |
| free_shipping_threshold | DECIMAL(12,2) | NOT NULL, DEFAULT 500000.00, CHECK (free_shipping_threshold >= 0) |
| updated_at | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |
| updated_by | VARCHAR(150) | NULL — Tài khoản thực hiện cập nhật |

---

## Lưu ý bắt buộc khi giao cho agent

1. **Bảng phân loại khách hàng cũ (`segments`, `segmentation_runs`, `customer_segments`):** Đã loại khỏi phạm vi dự án, không tạo và không sử dụng trong repo này.
2. **`vouchers.target_segment_id`:** Cột này kế thừa từ migration V1 ban đầu (NULL), hiện không sử dụng. Có thể loại bỏ trong migration dọn dẹp sau nếu cần.
3. **Thứ tự tạo bảng phải theo đúng phụ thuộc khóa ngoại**, ví dụ: `roles` → `users` → `categories` → `products` → `product_variants`/`product_images` → `promotions` → `product_promotions` → `vouchers` → `user_addresses` → `carts` → `cart_items` → `orders` → `order_details` → `payments` → (tùy chọn) `order_returns`, `reviews`, `wishlists`.
4. **Nhóm 4 là tùy chọn** — nếu thời gian gấp, agent chỉ cần tạo Nhóm 1–3 trước, Nhóm 4 thêm bằng migration `V2` sau.
5. **Quy ước cột Enum & ràng buộc CHECK:** Toàn bộ các cột phân loại trạng thái/loại dữ liệu được lưu dưới dạng `VARCHAR(30)` kết hợp ràng buộc `CHECK ... IN (...)` ở mức database. Do database dùng collation `utf8mb4_unicode_ci` (case-insensitive), ràng buộc `CHECK ... IN ('...')` không phân biệt hoa thường; chuỗi thực tế lưu vào DB được đảm bảo 100% là chữ thường (`lowercase`) nhờ các JPA `AttributeConverter` ở tầng Java gọi `toLowerCase()` trước khi ghi để tương thích với Hibernate `ddl-auto=validate`.
6. **Ràng buộc tài khoản `users`:** Đối với tài khoản đăng ký từ website (`data_source = 'website'`), bắt buộc có ít nhất một trong hai thông tin `email` hoặc `phone` (kiểm tra ở tầng Service/Validation).
7. **Lượt dùng `vouchers`:** Số lượt đã sử dụng được tính toán động bằng cách đếm từ `orders.voucher_id` (loại trừ các đơn `cancelled` và `refunded`), không tạo bảng phụ `voucher_usages`.
8. **Quy ước tiền tệ & địa chỉ:** Tiền tệ trong DB lưu `DECIMAL(12,2)`, khi trả về API DTO chuyển thành số nguyên VND (`Long`). Cột địa chỉ chi tiết trong `user_addresses` là `address`, còn trong `orders` là `address_line` (OrderService map: `order.setAddressLine(userAddress.getAddress())`).
