# DESIGN_ADMIN.md — Đặc tả Bố cục & Nội dung Giao diện Quản trị (Admin)

Tài liệu này trích xuất từ thiết kế màn hình **Atelier Admin - Tổng quan vận hành** trên Stitch MCP (`projects/13872002092305760553/screens/13423d4dce754006900733dc4a511853`).

> **Quy ước triển khai:**
> - Tài liệu này **CHỈ** quy định cấu trúc trang, thứ tự nội dung, các cột trong bảng và danh sách thẻ KPI.
> - **KHÔNG** sao chép màu sắc, gradient, hay CSS tùy biến từ bản vẽ Stitch.
> - Toàn bộ khu vực Admin được hiện thực hóa bằng **Ant Design gốc** (`Layout`, `Menu`, `Card`, `Statistic`, `Table`, `Badge`, `Row`, `Col`, `Breadcrumb`, `Dropdown`, `Button`, `Progress`...).
> - Toàn bộ styling được định hình qua `ConfigProvider`:
>   - `token.borderRadius = 6`
>   - `token.colorPrimary = "#18181B"`

---

## 1. Cấu trúc Khung tổng thể (Admin Shell Architecture)

Admin sử dụng bố cục chuẩn của Ant Design (`Layout`, `Layout.Sider`, `Layout.Header`, `Layout.Content`):

### 1.1. Sidebar Điều hướng (`Layout.Sider` + `Menu`)
- **Header Sidebar:**
  - Tên thương hiệu: **ATELIER**
  - Nhãn phân hệ: **Hệ thống Quản trị** (Admin Workspace)
- **Menu điều hướng phân nhóm (Hierarchical Navigation):**
  1. **Nhóm Tổng quan:**
     - `dashboard`: Tổng quan vận hành
  2. **Nhóm Bán lẻ & Sản phẩm:**
     - `products`: Sản phẩm
     - `categories`: Danh mục hàng hóa
     - `inventory`: Kho hàng & Nhập kho
     - `orders`: Quản lý đơn hàng
     - `shipping`: Vận chuyển & Giao nhận
  3. **Nhóm Khách hàng & Phân tích:**
     - `customers`: Khách hàng
     - `vouchers`: Mã khuyến mãi
     - `reports`: Báo cáo & Thống kê
  4. **Nhóm Hệ thống:**
     - `settings`: Cài đặt hệ thống
- **Footer Sidebar:**
  - Trạng thái kết nối máy chủ: Ổn định
  - Phiên bản hệ thống: `v2.4.0`

### 1.2. Topbar Quản trị (`Layout.Header`)
- **Bên trái:**
  - Nút thu/gọn Sidebar (`trigger`)
  - Đường dẫn điều hướng (`Breadcrumb`): `Trang chủ / Tổng quan`
- **Bên phải:**
  - Ô tìm kiếm nhanh toàn hệ thống (`Input.Search` có phím tắt `⌘K`)
  - Nút làm mới dữ liệu (`Button` reload)
  - Chuông thông báo (`Badge` hiển thị số thông báo chưa đọc, kèm dropdown thông báo gần đây)
  - Thông tin người dùng (`Avatar`, Tên hiển thị: "Trần Nhật Minh", Vai trò: "Quản lý vận hành", `Dropdown` thao tác: Hồ sơ, Cài đặt tài khoản, Đăng xuất)

---

## 2. Cấu trúc Trang Tổng quan Vận hành (`DashboardPage`)

Nội dung trang nằm trong `Layout.Content`, sắp xếp theo chiều dọc gồm 4 khu vực:

### 2.1. Tiêu đề Trang (Page Header)
- **Tiêu đề chính:** Tổng quan vận hành
- **Mô tả phụ:** Cập nhật lần cuối: 14:32 hôm nay • Chu kỳ đồng bộ 5 phút
- **Bộ công cụ (Actions):**
  - Bộ chọn chu kỳ báo cáo (`Select`): Hôm nay, 7 ngày qua, 30 ngày qua, Quý này
  - Nút xuất báo cáo (`Button` icon tải xuống): "Xuất báo cáo CSV/Excel"

### 2.2. Hàng Thẻ Chỉ số Hiệu suất (KPI Metric Cards)
Hiển thị dạng lưới 4 cột ngang (`Row` / `Col` span 6 trên màn hình lớn, span 12 trên tablet):

1. **Thẻ 1: Doanh thu hôm nay**
   - Tiêu đề: Doanh thu hôm nay
   - Giá trị chính: `48.650.000 đ`
   - Chỉ số biến động: `+12.4%` (so với hôm qua)
   - Thông tin bổ trợ: Đạt 92% kế hoạch ngày
2. **Thẻ 2: Đơn hàng mới**
   - Tiêu đề: Đơn hàng mới
   - Giá trị chính: `142 đơn`
   - Chỉ số biến động: `+8.1%` (so với cùng giờ hôm qua)
   - Thông tin bổ trợ: 28 đơn cần đóng gói gấp
3. **Thẻ 3: Khách hàng mới**
   - Tiêu đề: Khách hàng mới
   - Giá trị chính: `39 khách`
   - Chỉ số biến động: `+5.3%` (so với tuần trước)
   - Thông tin bổ trợ: 64% phát sinh giao dịch đầu tiên
4. **Thẻ 4: Cảnh báo tồn kho**
   - Tiêu đề: Sắp hết hàng
   - Giá trị chính: `7 mã hàng` (tồn dưới 10 sản phẩm)
   - Mức độ ưu tiên: Cảnh báo cao
   - Thông tin bổ trợ: 3 áo thun, 2 sơ mi cần nhập thêm

---

### 2.3. Khối Phân tích Doanh thu & Nhóm Khách hàng (Analytics Grid)
Bố cục 2 cột không đều (`Col span 16` và `Col span 8`):

#### Cột lớn (Span 16): Doanh thu & Tăng trưởng bán hàng
- **Card Header:**
  - Tiêu đề: Biểu đồ doanh thu 30 ngày gần nhất
  - Tổng lũy kế: `1.248.500.000 đ` (`+15.2%` so với tháng trước)
  - Phân loại kỳ (`Radio.Group` hoặc `Segmented`): 7 ngày / 30 ngày / Cả năm
- **Nội dung:** Biểu đồ thể hiện xu hướng doanh thu và số lượng đơn hàng qua từng ngày.

#### Cột nhỏ (Span 8): Phân loại Khách hàng theo chi tiêu & đơn hàng (Thống kê nội bộ)
- **Card Header:**
  - Tiêu đề: Phân loại khách hàng
  - Thống kê tổng số: `2.140 khách hàng`
- **Nội dung:**
  - Danh sách tỷ lệ các nhóm khách hàng (tính toán từ lịch sử đơn hàng và tổng chi tiêu):
    - **VIP / Chi tiêu cao:** 15% (320 khách - chi tiêu > 5.000.000 ₫)
    - **Khách hàng thân thiết:** 35% (750 khách - từ 3 đơn trở lên)
    - **Khách hàng mới:** 28% (600 khách - 1-2 đơn hoàn tất)
    - **Khách hàng tiềm năng:** 22% (470 khách - chưa phát sinh đơn)
  - Liên kết: "Xem danh sách khách hàng →"

---

### 2.4. Khối Vận hành Đơn hàng & Cảnh báo Tồn kho (Operations Grid)
Bố cục 2 cột (`Col span 16` và `Col span 8`):

#### Cột lớn (Span 16): Bảng Đơn hàng gần đây (`Table`)
- **Card Header:**
  - Tiêu đề: Đơn hàng gần đây
  - Mô tả phụ: 5 đơn mới nhất trên tổng số 142 đơn phát sinh trong ngày
  - Liên kết phụ: "Xem toàn bộ đơn hàng →"
- **Cấu trúc các cột của bảng (`Table.Column`):**
  1. **Mã đơn (`orderCode`):** Hiển thị mã đơn dạng link/bold (VD: `#ATL-9082`).
  2. **Khách hàng (`customer`):** Tên khách hàng + số điện thoại ẩn bớt số giữa.
  3. **Thời gian đặt (`createdAt`):** Giờ phút hoặc ngày tháng (VD: `14:15 hôm nay`).
  4. **Chi tiết sản phẩm (`itemsSummary`):** Tên các sản phẩm tóm tắt + số lượng món.
  5. **Tổng tiền (`totalAmount`):** Số tiền thanh toán (VND, căn phải, định dạng tiền tệ).
  6. **Trạng thái (`status`):** Dùng `Badge` hoặc `Tag` hiển thị trạng thái chuẩn (đồng bộ với `schema-spec.md` & `BACKEND_RULES.md`):
     - `pending` (Chờ xác nhận): Vàng / Warning
     - `confirmed` (Đã xác nhận): Xanh lam nhạt / Default
     - `shipping` (Đang giao hàng): Xanh dương / Processing
     - `completed` (Hoàn thành): Xanh lá / Success
     - `cancelled` (Đã hủy): Đỏ / Error
     - `refunded` (Đã hoàn tiền): Xám / Default
  7. **Thao tác (`action`):** Nút "Chi tiết" hoặc icon menu xem chi tiết đơn.

#### Cột nhỏ (Span 8): Cảnh báo Tồn kho sắp hết (`Stock Alert Card`)
- **Card Header:**
  - Tiêu đề: Cảnh báo tồn kho
  - Mô tả: Ngưỡng an toàn tối thiểu: 10 sản phẩm
  - Huy hiệu số lượng vi phạm: 4 mã hàng
- **Nội dung danh sách sản phẩm tồn thấp:**
  - Tên sản phẩm, thuộc tính (Màu sắc, Size, SKU)
  - Số lượng còn lại trong kho
  - Thanh tiến độ hiển thị mức độ cạn kiệt (`Progress`)
- **Card Footer:**
  - Nút hành động: "Tạo đơn nhập hàng bổ sung"
