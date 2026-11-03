# DESIGN.md — Atelier Minimalist Fashion Design System

Tài liệu này được trích xuất từ Stitch Project: **Atelier Fashion E-Commerce** (`projects/13872002092305760553`) và đã được đối chiếu với **`FRONTEND_DESIGN_RULES.md`**.

---

## 1. Nguyên tắc cốt lõi & Thứ tự ưu tiên (Conflict Resolution)

Theo quy định tại `FRONTEND_DESIGN_RULES.md`:
1. **`FRONTEND_DESIGN_RULES.md` LUÔN THẮNG khi có bất kỳ xung đột nào.**
2. **Bo góc:** Stitch ban đầu định nghĩa `4px` (`0.25rem`), nhưng theo `FRONTEND_DESIGN_RULES.md`, bo góc **khóa cứng ở 6px (`rounded-md`)**.
3. **Độ bóng (Shadow):** Khóa cứng **Zero Shadow** trên mọi Card, Button, Input, Table, Header. Chỉ Modal/Dialog và Dropdown mới được dùng shadow nhẹ để tách lớp.
4. **Hiệu ứng cấm:** 100% Solid colors, tuyệt đối cấm Gradient, cấm Glassmorphism (`backdrop-filter: blur`).
5. **Thang khoảng cách (Spacing):** Khóa cứng trong tập số nguyên: `4, 8, 12, 16, 24, 32, 48, 64px`.
6. **Màu sắc:** Primary là `#18181B` (Deep Charcoal), Neutral là thang be/nâu ấm (`50` đến `900`), điểm nhấn phụ Accent/Secondary là `#C2410C` (Terracotta).

---

## 2. Bảng đối chiếu Token: Stitch vs. FRONTEND_DESIGN_RULES.md

| Thuộc tính | Giá trị từ Stitch (`designMd`) | Quy chuẩn tại `FRONTEND_DESIGN_RULES.md` | Áp dụng trong Code |
|---|---|---|---|
| **Bo góc (Border Radius)** | `4px` (`0.25rem`) | **Khóa cứng 6px (`rounded-md`)** | `6px` (`rounded-md`) |
| **Bóng đổ (Box Shadow)** | Không dùng shadow trên card/button | **Chỉ dùng ở Modal & Dropdown** | `shadow-none` (phẳng 100%) |
| **Gradient / Blur** | Không sử dụng | **Cấm tuyệt đối** | 100% Solid Color, không blur |
| **Thang Spacing** | `8, 16, 24, 32, 48px` | `4, 8, 12, 16, 24, 32, 48, 64px` | Đồng nhất theo thang 4-64px |
| **Màu Neutral** | Thang be/nâu ấm (`#FBF9F5`, `#F5F0EA`, `#E4DCD3`, `#6E675F`) | **Thang be/nâu ấm** | `#FBF9F5`, `#F5F0EA`, `#E4DCD3`, `#6E675F`, `#18181B` |
| **Font chữ** | Be Vietnam Pro | Sans-serif hiện đại, tối đa 4 cỡ | Be Vietnam Pro (32px, 22px, 15px, 13px) |

---

## 3. Hệ thống màu sắc (Color Tokens — Warm Neutral Palette)

| Token Name | Hex Code | HSL Value | Mục đích sử dụng |
|---|---|---|---|
| `--background` / Canvas | `#FBF9F5` | `38° 40% 97%` | Nền trang tổng thể, sắc be ngà ấm tự nhiên |
| `--surface-card` / Card | `#FFFFFF` | `0° 0% 100%` | Nền thẻ sản phẩm, thẻ danh mục |
| `--secondary` / Muted Surface | `#F5F0EA` | `36° 27% 94%` | Nền Hero Banner, ô tìm kiếm, khối phụ trợ |
| `--border` / Stroke | `#E4DCD3` | `33° 22% 86%` | Đường viền mảnh 1px định hình mọi khối giao diện |
| `--border-strong` / Focus | `#18181B` | `240° 6% 10%` | Đường viền khi hover hoặc active |
| `--foreground` / Text Primary | `#18181B` | `240° 6% 10%` | Tiêu đề, tên sản phẩm, giá bán, nút chính |
| `--muted-foreground` / Text Secondary | `#6E675F` | `32° 7% 40%` | Nhãn danh mục, phụ đề, thông tin cam kết |
| `--accent` / Terracotta | `#C2410C` | `17° 88% 40%` | Badge giảm giá, điểm nhấn ưu đãi giới hạn |

---

## 4. Typography Scale (Tối đa 4 cấp độ)

1. **Heading 1 (`32px` / line-height `40px` / Bold 700):** Dùng cho tiêu đề Hero Banner chính.
2. **Heading 2 (`22px` / line-height `30px` / Semibold 600):** Dùng cho tiêu đề các Section ("Danh mục sản phẩm", "Sản phẩm nổi bật", "Khách hàng yêu thích").
3. **Body (`15px` / line-height `24px` / Regular 400 & Medium 500):** Dùng cho tên sản phẩm, nội dung mô tả, nút bấm, liên kết điều hướng.
4. **Caption (`13px` / line-height `18px` / Regular 400 & Medium 500):** Dùng cho nhãn phân loại, giá cũ gạch ngang, thông tin phụ kiện, copyright.

---

## 5. Cấu trúc bố cục trang chủ (Trích xuất từ Stitch)

1. **Header (h-16 / 64px):**
   - Logo thương hiệu `ATELIER` uppercase.
   - Menu điều hướng desktop: `Trang chủ`, `Sản phẩm`, `Danh mục`, `Giới thiệu`, `Ưu đãi`.
   - Ô tìm kiếm nền `#F5F0EA`, viền `#E4DCD3`.
   - Nút giỏ hàng có badge số lượng `2`.
   - Nút tài khoản người dùng cá nhân.

2. **Hero Banner (Split 50/50):**
   - Khối lưới 2 cột: Cột trái nội dung thông điệp + 2 nút hành động (`Mua ngay` và `Xem bộ sưu tập mới`); Cột phải là ảnh lookbook thời trang tối giản chất lượng cao.

3. **Danh mục sản phẩm (Category Grid 6 cột):**
   - Tiêu đề mục kèm link `Tất cả danh mục ->`.
   - 6 khối danh mục với ảnh tỷ lệ 3:4: Áo thun, Quần jean, Áo khoác, Áo sơ mi, Đầm, Phụ kiện.

4. **Sản phẩm nổi bật (Featured Products Grid 4 cột):**
   - Tiêu đề mục kèm phụ đề: "Đường cắt may tối giản, cấu trúc vải bền vững cho tủ đồ hằng ngày".
   - 8 thẻ sản phẩm với ảnh tỷ lệ 4:5, tag định lượng/giảm giá, tên sản phẩm, giá bán chuẩn VNĐ và nút `Thêm vào giỏ`.

5. **Cam kết chất lượng (Brand Value Props - 3 cột):**
   - Giao hàng miễn phí toàn quốc (đơn từ 500.000đ).
   - Đổi trả trong 30 ngày còn nguyên tem.
   - Chất liệu sợi tự nhiên tuyển chọn.

6. **Bố cục nâng cao (Showcase 8/4 Split Grid):**
   - **8 cột (Khách hàng yêu thích):** 4 sản phẩm bán chạy dáng mini (Áo thun Heavyweight, Quần jean suông, Sơ mi Oxford, Áo khoác Blazer).
   - **4 cột (Ưu đãi theo mùa):** Khối nền ấm `#ECE5DC` giới thiệu giảm 20% nhóm áo khoác & sơ mi, nút `Khám phá ngay` và ảnh thời trang theo mùa.

7. **Footer (4 cột thông tin):**
   - Cột 1: Thương hiệu, giới thiệu ngắn gọn, form đăng ký nhận bản tin email.
   - Cột 2: Danh mục thời trang.
   - Cột 3: Chăm sóc khách hàng & chính sách.
   - Cột 4: Thông tin cửa hàng (Địa chỉ, hotline, giờ mở cửa).
   - Dòng chân trang: Bản quyền, Chính sách bảo mật, Điều khoản dịch vụ.
