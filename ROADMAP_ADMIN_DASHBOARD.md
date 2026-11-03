# ROADMAP_ADMIN_DASHBOARD.md

> **Phạm vi:** Hoàn thiện trang Admin Dashboard (`/admin`), Module Quản lý Đơn hàng (`/admin/orders`) và toàn bộ phân hệ Quản trị (Admin Workspace) — từ UI mock-only sang dữ liệu thật từ API.
> **Phân công chính thức:** **Tôi (Admin Owner)** phụ trách toàn bộ phân hệ Admin (cả Backend & Frontend), bao gồm Dashboard, Product, Category, Customer, Order. **Thành viên khác** phụ trách riêng module Admin Voucher.
> **Lưu ý:** File này dùng để theo dõi tiến độ nội bộ. Không stage/commit file này vào các PR tính năng.

---

## A. Tiêu đề & Tóm tắt tiến độ

| Mục | Thông tin |
|---|---|
| **Ngày tạo** | 2026-10-24 |
| **Người phụ trách phân hệ Admin** | **Tôi (Admin Owner)** |
| **Phạm vi** | Toàn bộ Admin Backend (`dashboard/`, `order/`, `product/`, `user/`) & Frontend (`features/admin/*` ngoại trừ `admin/voucher` & `admin/promotion`) |
| **Cập nhật lần cuối** | 2026-10-30 |

### Bảng đếm trạng thái

| Trạng thái | Số task | Ghi chú |
|---|---|---|
| ✅ Xong (Completed / Merged) | 12 | DASH-0.1 → DASH-0.4 (Layout), DASH-1.1 → DASH-1.4 (Phase 1), ORD-1.1 → ORD-1.4 (Admin Order — BE PR #13, #14; FE PR #11, #12) |
| 🔄 Đang làm (In Progress) | 0 | Không có task nào đang chạy dở dang |
| ⬜ Chưa làm (Not Started) | 8 | DASH-2.1 → DASH-2.4 (Phase 2), DASH-4.1 → DASH-4.4 (Polish) |
| ⏸ Tạm hoãn (Deferred) | 3 | DASH-3.1 → DASH-3.3 (Phân loại khách hàng, chờ quyết định G-1) |
| ⛔ Bị chặn (Blocked) | 0 | Đã giải tỏa hoàn toàn: Module Order đã hoàn tất 100%, sẵn sàng triển khai Dashboard Phase 2 |
| **Tổng** | **23** | |

---

## B. Chú giải trạng thái

| Ký hiệu | Trạng thái | Ý nghĩa |
|---|---|---|
| ✅ | **Completed / Merged** | Đã hoàn thành, code đã merge vào nhánh chính (`main`), có bằng chứng commit/PR và kiểm thử xác nhận |
| 🔄 | **In Progress** | Đang triển khai trong branch riêng, chưa merge |
| ⬜ | **Not Started** | Chưa bắt đầu — chưa có ai triển khai, chưa tạo branch |
| ⏸ | **Deferred** | Tạm hoãn — chờ quyết định nghiệp vụ của chủ dự án |
| ⛔ | **Blocked** | Bị chặn bởi phụ thuộc kỹ thuật chưa hoàn tất |

---

## C. Bảng phụ thuộc — Khối Dashboard × Endpoint/Module

| Khối Dashboard | Bảng DB cần | Endpoint BE cần | Phụ thuộc | Người phụ trách | Trạng thái | Ghi chú |
|---|---|---|---|---|---|---|
| **KPI: Doanh thu hôm nay** | `orders` (`status='completed'`) | `GET /api/v1/admin/dashboard/summary` hoặc `/kpi` | Module `order/` (Tôi) | **Tôi (Admin Owner)** | ⬜ Chưa làm | Chỉ tính đơn có `status = completed` |
| **KPI: Đơn hàng mới** | `orders` (tất cả trạng thái) | `GET /api/v1/admin/dashboard/summary` hoặc `/kpi` | Module `order/` (Tôi) | **Tôi (Admin Owner)** | ⬜ Chưa làm | Đếm tất cả đơn phát sinh trong kỳ |
| **KPI: Khách hàng mới** | `users` (`created_at`) | `GET /api/v1/admin/dashboard/summary?period=` | `UserRepository` | **Tôi (Admin Owner)** | ✅ Xong | Đã kết nối dữ liệu thật ở DASH-1.4 |
| **KPI: Cảnh báo tồn kho** | `product_variants` (`stock < 10`) | `GET /api/v1/admin/dashboard/summary` | `ProductVariantRepository` | **Tôi (Admin Owner)** | ✅ Xong | Đã kết nối dữ liệu thật ở DASH-1.4 |
| **Biểu đồ doanh thu (7d/30d/year)** | `orders`, `order_details` | `GET /api/v1/admin/dashboard/revenue-trend?period=` | Module `order/` (Tôi) | **Tôi (Admin Owner)** | ⬜ Chưa làm | `GROUP BY` ngày theo múi giờ `Asia/Ho_Chi_Minh` |
| **Phân loại khách hàng** | `orders` (`completed`), `users` | `GET /api/v1/admin/dashboard/customer-segments` | Module `order/` (Tôi) + `UserRepo` | **Tôi (Admin Owner)** | ⏸ Tạm hoãn | Chờ quyết định G-1 của chủ dự án |
| **Đơn hàng gần đây (bảng)** | `orders`, `order_details`, `users` | `GET /api/v1/admin/orders?size=5&sort=createdAt,desc` | Module `order/` (Tôi) | **Tôi (Admin Owner)** | ⬜ Chưa làm | Dùng lại endpoint quản lý đơn hàng |
| **Cảnh báo tồn kho (danh sách)** | `product_variants`, `products` | `GET /api/v1/admin/products/variants/low-stock` | `ProductVariantRepository` | **Tôi (Admin Owner)** | ✅ Xong | Đã kết nối dữ liệu thật ở DASH-1.4 |
| **AdminLayout: Session + Đăng xuất** | `refresh_tokens`, `users` | `POST /api/v1/auth/logout` | `AuthService` | **Tôi (Admin Owner)** | ✅ Xong | Đã merge ở PR #4 (`9db1eae`) |
| **AdminLayout: Tên user động** | `users` | Zustand `useAuthStore` | `auth.store` | **Tôi (Admin Owner)** | ✅ Xong | Đã merge ở PR #4 (`9db1eae`) |
| **Bộ lọc kỳ (Select)** | — | Query param `period` (`today`, `7days`, `30days`, `quarter`) | Summary API | **Tôi (Admin Owner)** | ✅ Xong | Chuẩn hóa nhãn "30 ngày qua" ở DASH-1.4 |

---

## D. Các giai đoạn triển khai chi tiết

---

### Giai đoạn 0 — Nền & AdminLayout hoàn chỉnh (✅ ĐÃ HOÀN THÀNH)

**Mục tiêu:** AdminLayout đọc session thật, nút Đăng xuất gọi API, tên user hiển thị động từ Zustand store, Breadcrumb động theo route.

| ID | Mô tả | Repo | Nhánh đã merge | Tiêu chí hoàn thành (DoD) | Trạng thái | Bằng chứng |
|---|---|---|---|---|---|---|
| **DASH-0.1** | AdminLayout: hiển thị tên user & role từ `useAuthStore` | FE | `feature/admin-layout-session` | Topbar hiển thị tên user đang đăng nhập; chưa đăng nhập redirect về `/admin/login` | ✅ Xong | PR #4 (`9db1eae`) |
| **DASH-0.2** | AdminLayout: nút Đăng xuất gọi `POST /api/v1/auth/logout` | FE | `feature/admin-layout-session` | Click Đăng xuất $\rightarrow$ gọi API, xóa refresh token, clear Zustand store, điều hướng về login | ✅ Xong | PR #4 (`9db1eae`) |
| **DASH-0.3** | AdminLayout: Breadcrumb động theo route hiện tại | FE | `feature/admin-layout-session` | Breadcrumb hiển thị đúng nhãn phân cấp cho các route `/admin/*` | ✅ Xong | PR #4 (`9db1eae`) |
| **DASH-0.4** | AdminLayout: kiểm tra responsive trên mobile/tablet | FE | `feature/admin-layout-session` | Viewport nhỏ: Sidebar tự collapse, Topbar không tràn, trigger hoạt động mượt mà | ✅ Xong | PR #4 (`9db1eae`) |

---

### Giai đoạn 1 — Dashboard Phase 1: Sản phẩm & Khách hàng (✅ ĐÃ HOÀN THÀNH)

**Mục tiêu:** Kết nối dữ liệu thật cho các KPI khách hàng mới, cảnh báo tồn kho và danh sách tồn kho thấp — hoàn toàn độc lập với module Order.

| ID | Mô tả | Repo | Nhánh đã merge | Tiêu chí hoàn thành (DoD) | Trạng thái | Bằng chứng |
|---|---|---|---|---|---|---|
| **DASH-1.1** | BE: Thêm query tồn kho thấp vào `ProductVariantRepository` | BE | `feature/admin-dashboard-low-stock-query` | `findLowStockVariants(threshold, pageable)` và `countLowStockVariants(threshold)` lọc đúng `stock < threshold` và `p.isActive = true`. Test pass 100%. | ✅ Xong | BE PR #9 (commit `2fa6b82`) |
| **DASH-1.2** | BE: Tạo API `GET /api/v1/admin/dashboard/summary` & `GET /api/v1/admin/products/variants/low-stock` | BE | `feature/admin-dashboard-phase1` | Endpoint summary tính đúng khách mới, tỷ lệ tăng trưởng theo kỳ (`today`, `7days`, `30days`, `quarter` theo múi giờ `Asia/Ho_Chi_Minh`), tổng khách và số lượng low-stock. Test integration pass. | ✅ Xong | BE PR #10 (commit `ff725d1`) |
| **DASH-1.3** | FE: Xây dựng API client và TanStack Query hooks cho Dashboard | FE | `feature/admin-dashboard-api-hooks` | `useDashboardSummary(period)` và `useLowStockVariants(params)` có typing chuẩn từ `schema.d.ts`, độc lập lỗi, tự refetch khi đổi kỳ. Typecheck pass. | ✅ Xong | FE PR #7 (commit `581b265`) |
| **DASH-1.4** | FE: Kết nối dữ liệu thật vào `DashboardPage.tsx` | FE | `feature/admin-dashboard-phase1-ui` | KPI Khách hàng mới và Tồn kho thấp dùng dữ liệu thật; card danh sách tồn thấp render Ant Design `<Progress />` phân cấp màu; nhãn `30days` chuẩn hóa thành "30 ngày qua"; có Skeleton, Empty, Error retry độc lập; `tsc` và `build` pass. | ✅ Xong | FE PR #8 (commit `37f1e63`) |

---

### Giai đoạn 1.5 — Module Quản lý Đơn hàng (Order Module Foundation & Admin Order — PREREQUISITE)

**Mục tiêu:** Xây dựng hoàn chỉnh tầng backend foundation và giao diện Admin Order cho module Đơn hàng. Đây là tiền đề bắt buộc cung cấp bảng dữ liệu và API cho Dashboard Giai đoạn 2.  
**Người phụ trách:** **Tôi (Admin Owner)**.  
**Hiện trạng:** **Chưa bắt đầu (Not Started)** — chưa có ai thực hiện. Bắt đầu sau khi hoàn tất xác nhận merge DASH-1.4 trên `main`.  
**Khảo sát hiện trạng code & CSDL:**
- Schema CSDL: Các bảng `orders`, `order_details`, `payments`, `order_returns` và index `idx_orders_status_created`, `idx_orders_user_created` đã được tạo đầy đủ trong migration `V1__init_schema.sql`. Không cần tạo thêm migration mới trừ khi phát sinh nhu cầu schema đặc thù.
- Backend code: Mới chỉ có `OrderStatus.java`, `OrderStatusConverter.java`, `OrderReturnStatus.java`, `OrderReturnStatusConverter.java`. Chưa có Entity, Repository, Service, Controller nào.
- Frontend code: Mới chỉ có thư mục rỗng với `.gitkeep` tại `features/admin/order` và `features/order`. Route `/admin/orders` đã có sẵn vị trí menu trong `AdminLayout.tsx`.

#### Quy tắc kỹ thuật bắt buộc khi triển khai Order:
1. **Kiến trúc phân tầng chuẩn:** `Controller → Service (Interface + Impl) → Repository → DTO`. Không inject chéo Repository giữa các module khác nhau.
2. **Tiền tệ:** Trong Entity dùng `BigDecimal`/`DECIMAL(12,2)`. Trong DTO/API Response **bắt buộc dùng `Long` VND** (không dùng floating-point hoặc `BigDecimal` trên API Contract).
3. **Trạng thái đơn hàng:** Dùng enum hằng số chữ HOA trong Java (`PENDING`, `CONFIRMED`, `SHIPPING`, `COMPLETED`, `CANCELLED`, `REFUNDED`), lưu DB chữ thường (`pending`, `confirmed`, `shipping`, `completed`, `cancelled`, `refunded`) thông qua `OrderStatusConverter` tự động.
4. **Bảo vệ kho hàng nguyên tử (Atomic Stock Control):** Khi đơn hàng chuyển sang `CANCELLED` hoặc `REFUNDED`, bắt buộc thực hiện hoàn lại số lượng tồn kho nguyên tử (`UPDATE product_variants SET stock = stock + :qty WHERE id = :variantId`).
5. **Snapshot dữ liệu đơn hàng:** `order_details` (`product_name`, `unit_price`, `variant_size`, `variant_color`) và địa chỉ giao hàng trong `orders` là snapshot tại thời điểm mua, không join ngược lại bảng gốc khi hiển thị lịch sử đơn.
6. **Bảo toàn schema Voucher:** Khi chạy `npm run gen:api` ở Frontend, phải phối hợp bảo đảm các định nghĩa schema của module Voucher không bị ghi đè hay thất thoát.

| ID | Tên task & Mục tiêu | Repo / Module | Nhánh dự kiến | Phụ thuộc | Tiêu chí hoàn thành (Definition of Done) | Trạng thái |
|---|---|---|---|---|---|:---:|
| **ORD-1.1** | **BE: Order Foundation — Entity & Repository**<br>Tạo Entity `Order`, `OrderDetail`, `Payment` và các JPA Repository tương ứng. | BE (`order/`) | `feature/admin-order-foundation` | DASH-1.4 merged | • Entity map chính xác bảng `orders`, `order_details`, `payments` per `schema-spec.md`<br>• `OrderRepository` có custom query chuẩn nửa mở `[start, end)`: lọc đơn theo status, tìm kiếm mã đơn/tên/SĐT, phân trang, đếm theo status & khoảng ngày, tính tổng doanh thu đơn `completed` theo `completedAt`<br>• 6/6 Integration test pass 100% | ✅ Xong (BE PR #13 — `20e57bc`) |
| **ORD-1.2** | **BE: Admin Order Service & APIs**<br>Triển khai Service nghiệp vụ và Controller quản lý đơn hàng cho Admin. | BE (`order/`) | `feature/admin-order-apis` | ORD-1.1 | • `GET /api/v1/admin/orders`: phân trang, lọc status, tìm kiếm, sắp xếp `createdAt,desc`<br>• `GET /api/v1/admin/orders/{id}`: xem chi tiết đầy đủ đơn hàng kèm items snapshot và payment info<br>• `PATCH /api/v1/admin/orders/{id}/status`: chuyển trạng thái đơn hàng theo luồng hợp lệ; hoàn tồn kho nguyên tử khi hủy đơn<br>• Integration test với `TestDatabaseSafetyGuard` pass | ✅ Xong (BE PR #14 — `3998200`) |
| **ORD-1.3** | **FE: Admin Order API Client & TanStack Query Hooks**<br>Tạo lớp API và hooks phục vụ trang quản trị đơn hàng. | FE (`features/admin/order/api`) | `feature/admin-order-ui` | ORD-1.2 | • Cập nhật API client / OpenAPI type cho Order (bảo toàn schema Voucher & Promotion)<br>• Hooks: `useAdminOrders(params)`, `useAdminOrderDetail(id)`, `useUpdateOrderStatus()`<br>• Type-check `tsc --noEmit` pass 100% | ✅ Xong (FE PR #11 — `6358e69`) |
| **ORD-1.4** | **FE: Trang Quản trị Đơn hàng (`/admin/orders`)**<br>Xây dựng trang danh sách, bộ lọc, xem chi tiết và cập nhật trạng thái đơn hàng. | FE (`features/admin/order/pages`) | `feature/admin-order-ui` | ORD-1.3 | • Giao diện Ant Design Table per `DESIGN_ADMIN.md`<br>• Lọc trạng thái (All, Chờ xác nhận, Đang giao, Hoàn thành, Đã hủy...)<br>• Drawer/Modal xem chi tiết đơn hàng snapshot<br>• Modal cập nhật trạng thái đơn hàng có xác nhận an toàn<br>• Đầy đủ Skeleton loading, Empty state, Error retry<br>• Khai báo route trong `App.tsx`, breadcrumb và active menu sidebar chuẩn | ✅ Xong (FE PR #11 `6358e69` & PR #12 `394c8f0`) |

---

### Giai đoạn 2 — Dashboard Phase 2: Doanh thu & Đơn hàng vận hành

**Mục tiêu:** Kết nối dữ liệu thật cho 2 thẻ KPI còn lại (Doanh thu hôm nay, Đơn hàng mới), Biểu đồ xu hướng doanh thu (Recharts `AreaChart`) và Bảng đơn hàng gần đây trên `/admin`.  
**Người phụ trách:** **Tôi (Admin Owner)**.  
**Trạng thái:** **Chưa bắt đầu (Not Started)** — bắt đầu ngay sau khi hoàn thành ORD-1.1 & ORD-1.2.  
**Quy tắc nghiệp vụ cốt lõi:**
- Doanh thu: **CHỈ TÍNH đơn hàng có `status = 'completed'`**. Đơn `pending`, `shipping`, `cancelled`, `refunded` tuyệt đối không cộng vào doanh thu.
- Biểu đồ xu hướng: Gom nhóm (`GROUP BY`) theo ngày dựa trên múi giờ Việt Nam (`Asia/Ho_Chi_Minh` / UTC+7).
- Đơn hàng gần đây: Tái sử dụng trực tiếp API `GET /api/v1/admin/orders?size=5&sort=createdAt,desc` từ ORD-1.2, không tạo endpoint trùng lặp.
- Hiệu năng: Toàn bộ phép tính tổng và gom nhóm thực hiện trực tiếp tại database qua `@Query`; cấm nạp toàn bộ Entity về bộ nhớ để đếm bằng vòng lặp Java.

| ID | Tên task & Mục tiêu | Repo / Module | Nhánh dự kiến | Phụ thuộc | Tiêu chí hoàn thành (Definition of Done) | Trạng thái |
|---|---|---|---|---|---|:---:|
| **DASH-2.1** | **BE: API KPI Doanh thu & Đơn hàng**<br>Bổ sung thống kê doanh thu và đơn hàng vào Dashboard API. | BE (`dashboard/`) | `feature/admin-dashboard-phase2-be` | ORD-1.1, ORD-1.2 | • Mở rộng `DashboardSummaryResponse` hoặc API KPI: trả `revenueToday`, `revenueChangeRate`, `ordersToday`, `ordersChangeRate`<br>• Doanh thu chỉ tính đơn `completed`; đơn hàng đếm tất cả trạng thái<br>• Tiền tệ dùng `Long` VND<br>• Integration test pass | ⬜ Chưa làm |
| **DASH-2.2** | **BE: API Biểu đồ Xu hướng Doanh thu**<br>`GET /api/v1/admin/dashboard/revenue-trend?period=7days\|30days\|year` | BE (`dashboard/`) | `feature/admin-dashboard-phase2-be` | ORD-1.1 | • Native query hoặc HQL gom nhóm theo ngày theo múi giờ `Asia/Ho_Chi_Minh`<br>• Trả danh sách `[{date: "dd/MM", revenue: Long, orders: Long}]`<br>• Test dữ liệu với data seed chuẩn | ⬜ Chưa làm |
| **DASH-2.3** | **FE: TanStack Query Hooks Dashboard Phase 2**<br>Xây dựng hooks gọi API doanh thu và biểu đồ xu hướng. | FE (`features/admin/dashboard/api`) | `feature/admin-dashboard-phase2-fe` | DASH-2.1, DASH-2.2 | • Hook `useRevenueTrend(period)` tự động gọi lại khi đổi tab (7 ngày, 30 ngày, Cả năm)<br>• Cập nhật hook KPI tích hợp doanh thu và đơn hàng mới<br>• Tái sử dụng hook `useAdminOrders` cho bảng gần đây | ⬜ Chưa làm |
| **DASH-2.4** | **FE: Kết nối dữ liệu thật Dashboard Phase 2**<br>Thay thế mock data cho KPI Doanh thu, Đơn hàng, Biểu đồ và Bảng đơn hàng gần đây. | FE (`features/admin/dashboard/pages`) | `feature/admin-dashboard-phase2-fe` | DASH-2.3, ORD-1.4 | • 2 thẻ KPI Doanh thu và Đơn hàng hiển thị dữ liệu thật và tag tăng/giảm<br>• Recharts `AreaChart` render đúng mảng doanh thu thật theo ngày, tooltip format tiền tệ VND<br>• Bảng Đơn hàng gần đây hiển thị 5 đơn thật, click mã đơn/chi tiết mở drawer<br>• Loading Skeleton, Empty state, Error retry hoạt động trơn tru<br>• `npm run build` pass | ⬜ Chưa làm |

---

### Giai đoạn 3 — Phân loại khách hàng (RFM light) — ⏸ TẠM HOÃN

**Mục tiêu:** Phân loại nhóm khách hàng dựa trên chi tiêu và tần suất mua sắm (VIP, Loyal, New, Potential).  
**Người phụ trách:** **Tôi (Admin Owner)**.  
**Trạng thái:** **Tạm hoãn (Deferred)** — Chờ chủ dự án xác nhận ngưỡng phân loại và định nghĩa nghiệp vụ (vấn đề G-1).

| ID | Mô tả | Repo | Nhánh dự kiến | Phụ thuộc | Tiêu chí hoàn thành | Trạng thái |
|---|---|---|---|---|---|:---:|
| **DASH-3.1** | BE: `GET /api/v1/admin/dashboard/customer-segments` — phân nhóm khách hàng | BE | `feature/admin-dashboard-phase3` | ORD-1.1, Quyết định G-1 | Trả về 4 nhóm tỷ lệ phần trăm và số lượng khách thật bằng query database | ⏸ Tạm hoãn |
| **DASH-3.2** | FE: Hook `useCustomerSegments()` | FE | `feature/admin-dashboard-phase3` | DASH-3.1 | Hook trả data + loading/error states | ⏸ Tạm hoãn |
| **DASH-3.3** | FE: Card "Phân loại khách hàng" dùng data thật | FE | `feature/admin-dashboard-phase3` | DASH-3.2 | Render thanh Progress và phần trăm theo 4 nhóm thật; liên kết sang `/admin/customers` | ⏸ Tạm hoãn |

---

### Giai đoạn 4 — Hoàn thiện & Chất lượng vận hành (Not Started)

**Mục tiêu:** Đồng bộ trải nghiệm UX, audit thiết kế theo `DESIGN_ADMIN.md`, tính năng xuất báo cáo và tối ưu hóa hiệu năng.  
**Người phụ trách:** **Tôi (Admin Owner)**.

| ID | Mô tả | Repo | Nhánh dự kiến | Phụ thuộc | Tiêu chí hoàn thành | Trạng thái |
|---|---|---|---|---|---|:---:|
| **DASH-4.1** | FE: Kiểm tra toàn diện Skeleton loading & Empty states trên toàn bộ các Card/Table Admin | FE | `feature/admin-dashboard-polish` | Giai đoạn 2 done | Mọi khối dữ liệu đều có Skeleton Ant Design khi loading và Empty thân thiện khi không có dữ liệu | ⬜ Chưa làm |
| **DASH-4.2** | FE: Chuẩn hóa Error State và cơ chế phục hồi (Retry button) độc lập cho từng khối | FE | `feature/admin-dashboard-polish` | Giai đoạn 2 done | Một API lỗi không ảnh hưởng các khối khác; người dùng có thể bấm Thử lại để fetch lại khối đó | ⬜ Chưa làm |
| **DASH-4.3** | FE: Audit toàn diện giao diện Dashboard và Order theo `DESIGN_ADMIN.md` | FE | `feature/admin-dashboard-polish` | Giai đoạn 2 done | Bố cục lưới span 16/8, màu sắc `#18181B`, bo góc `6px`, font chữ, spacing đồng nhất 100% | ⬜ Chưa làm |
| **DASH-4.4** | FE: Nút "Xuất báo cáo CSV/Excel" trên Dashboard header | FE | `feature/admin-dashboard-polish` | Giai đoạn 2, Quyết định G-2 | Xuất file CSV dữ liệu KPI và doanh thu theo kỳ đã chọn (hoặc hiển thị Tooltip nếu chưa kích hoạt) | ⬜ Chưa làm |

---

## E. Ma trận ranh giới và phối hợp giữa hai thành viên

| Phân hệ / Module | Người phụ trách chính | Phạm vi công việc | Điểm tiếp giáp & Quy tắc phối hợp |
|---|---|---|---|
| **Toàn bộ Admin Workspace** (Dashboard, Product, Category, Customer, Order) | **Tôi (Admin Owner)** | Toàn quyền thiết kế kiến trúc, triển khai Backend API và xây dựng Frontend UI | Không để thành viên khác can thiệp vào logic và giao diện Admin (trừ trang Voucher). |
| **Module Order** (`order/`, `admin/order`) | **Tôi (Admin Owner)** | Xây dựng Entity, Repository, Service, Controller, DTO, Frontend Order UI | Hiện tại **chưa bắt đầu (Not Started)**; Tôi chủ động triển khai theo thứ tự ưu tiên sau DASH-1.4. |
| **Module Admin Voucher** (`voucher/`, `admin/voucher`) | **Thành viên khác (Voucher Owner)** | Xây dựng Backend Voucher API và trang quản lý Voucher tại `features/admin/voucher` | **✅ Đã hoàn thành & merge vào `main`** (BE PR #12 `b2f4d62`, FE PR #9 `45dd0f1`). Khi chạy `npm run gen:api` ở Frontend, cần chạy từ `main` để giữ nguyên định nghĩa TypeScript của Voucher trong `schema.d.ts`. |

---

## F. Nhật ký thay đổi (Changelog)

| Ngày giờ | Task ID / Hạng mục | Nội dung thay đổi |
|---|---|---|
| 2026-10-24 11:10 | Khởi tạo roadmap | Thiết lập khung roadmap ban đầu từ hiện trạng code. |
| 2026-10-27 23:50 | DASH-1.1 → DASH-1.4 | Hoàn thành và merge 4 task của Dashboard Phase 1 (Sản phẩm & Khách hàng): BE PR #9, BE PR #10, FE PR #7, FE PR #8. |
| 2026-10-28 00:30 | **Tái cơ cấu toàn diện Roadmap & Phân công** | • **Chuyển giao toàn bộ quyền sở hữu Admin Workspace cho Tôi (Admin Owner)** (bao gồm Dashboard, Order, Product, Category, Customer).<br>• Ghi nhận thành viên khác **chỉ phụ trách riêng Admin Voucher**.<br>• Làm rõ trạng thái **Module Order: Chưa bắt đầu (Not Started) — do Tôi trực tiếp phụ trách**.<br>• Tái cấu trúc lộ trình: Chèn Giai đoạn 1.5 (Module Order: ORD-1.1 → ORD-1.4) làm điều kiện tiên quyết cho Giai đoạn 2 (Dashboard Phase 2: DASH-2.1 → DASH-2.4).<br>• Cập nhật bảng đếm trạng thái: 8 Done, 12 Not Started, 3 Deferred. |

---

## G. Quyết định chờ chủ dự án

| # | Vấn đề | Lựa chọn đề xuất | Ảnh hưởng task | Trạng thái |
|---|---|---|---|:---:|
| **G-1** | **Phân loại khách hàng (Giai đoạn 3):** Có triển khai tính năng này trên Dashboard không? | (a) Triển khai theo 4 nhóm chi tiêu (VIP, Thân thiết, Mới, Tiềm năng)<br>(b) Tạm thời giữ mock hoặc ẩn card | DASH-3.1 → DASH-3.3 | ⏸ Đang chờ |
| **G-2** | **Xuất báo cáo CSV (DASH-4.4):** Có cần xuất file thật không? | (a) Xuất CSV client-side từ dữ liệu các khối<br>(b) Giữ nút kèm Tooltip "Tính năng đang hoàn thiện" | DASH-4.4 | ⏸ Đang chờ |
| **G-3** | **Bộ lọc kỳ Dashboard:** Định nghĩa khoảng thời gian | Đã giải quyết: Chuẩn hóa theo rolling days (`Asia/Ho_Chi_Minh`): hôm nay, 7 ngày qua, 30 ngày qua, quý này. | DASH-1.2, DASH-1.4 | ✅ Đã chốt |

---

## H. Rủi ro & Biện pháp kiểm soát

| # | Rủi ro | Mức độ | Biện pháp kiểm soát |
|---|---|---|---|
| **R-1** | Module Order backend chưa có code — chưa có Entity, Repository, Service | Trung bình | Đã đưa vào lộ trình ưu tiên số 1 ngay sau DASH-1.4: Tôi sẽ xây dựng tuần tự từ ORD-1.1 (Entity/Repo) $\rightarrow$ ORD-1.2 (Service/API) trước khi làm Dashboard Phase 2. |
| **R-2** | Xung đột `schema.d.ts` khi cả hai thành viên cùng phát triển API (Order vs Voucher) | Cao | Thực hiện git worktree cô lập cho từng module; kiểm tra kỹ diff của `schema.d.ts` trước khi commit; merge API vào `main` trước khi chạy `npm run gen:api`. |
| **R-3** | Trạng thái đơn hàng giữa Frontend và Backend không khớp | Thấp | Toàn bộ trạng thái chuẩn hóa chữ thường theo DB/Backend: `pending`, `confirmed`, `shipping`, `completed`, `cancelled`, `refunded`. Frontend dùng map hiển thị nhãn tiếng Việt rõ nghĩa. |
| **R-4** | Hoàn tồn kho khi hủy đơn phát sinh tranh chấp dữ liệu (Race condition) | Trung bình | Bắt buộc dùng truy vấn Atomic Update (`UPDATE product_variants SET stock = stock + :qty`) ở tầng Repository thay vì đọc về rồi cộng thủ công trong Java. |
