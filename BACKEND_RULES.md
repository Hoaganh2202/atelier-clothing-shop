# BACKEND_RULES.md

Agent PHẢI đọc file này trước khi viết bất kỳ Entity, Repository, Service, Controller hoặc migration nào. Vi phạm bất kỳ mục nào dưới đây coi như phải sửa lại, không tính là hoàn thành.

Môi trường đã kiểm chứng: Java 17, Spring Boot 3.3.4, Hibernate 6.5.3, MySQL 8.0.46. Không tự ý nâng hoặc đổi phiên bản.

---

## 0. Thứ tự ưu tiên khi có mâu thuẫn

1. `schema-spec.md` quyết định cấu trúc bảng, cột, kiểu dữ liệu, ràng buộc.
2. `PROJECT_CONTEXT.md` quyết định công nghệ, phân công, quy tắc nghiệp vụ.
3. `BACKEND_RULES.md` (file này) quyết định cách viết code backend.
4. Nếu hai file mâu thuẫn nhau, hoặc yêu cầu của người dùng mâu thuẫn với file nào đó: **DỪNG và hỏi**, không tự chọn một bên.
5. Khi không chắc phương án nào đúng, chọn **phương án đơn giản hơn**.

---

## 1. Cấm tuyệt đối

- **KHÔNG** đổi ngôn ngữ/framework backend (không Node.js, Python, NestJS...).
- **KHÔNG** sửa file migration đã chạy (Flyway sẽ báo lỗi checksum). Mọi thay đổi schema mới đi qua file `V{n+1}__mo_ta.sql`. Ngoại lệ duy nhất: người dùng nói rõ là DB local, chưa chia sẻ, chấp nhận drop DB.
- **KHÔNG** đặt `ddl-auto` khác `validate`. Hibernate không được tạo hay sửa schema.
- **KHÔNG** thêm dependency mới (Maven/Gradle) mà không hỏi người dùng trước.
- **KHÔNG** hard-code mật khẩu, secret JWT, thông tin DB trong code hoặc file được commit. Dùng biến môi trường hoặc file cấu hình local nằm trong `.gitignore`.
- **KHÔNG** sửa code ngoài module được giao. Việc cần sửa ở `common/`, `config/`, `security/` phải nói rõ với người dùng, vì ảnh hưởng cả hai thành viên.
- **KHÔNG** sửa cảnh báo "Null type safety" của IDE bằng requireNonNull hay @NonNull. Chỉ xử lý cảnh báo unused import và các lỗi biên dịch thật.

---

## 2. Cấu trúc và phân tầng

Mỗi module (`auth/`, `user/`, `product/`, `cart/`, `order/`, `voucher/`, `promotion/`, `payment/`, `address/`) tự chứa: Entity, Repository, Service, Controller, `dto/`.

Luồng gọi duy nhất: **Controller → Service → Repository**.

- Controller chỉ nhận request, gọi Service, trả response. **Không chứa logic nghiệp vụ, không gọi Repository trực tiếp.**
- Service chứa toàn bộ nghiệp vụ và quản lý transaction.
- **Không trả Entity ra khỏi Controller.** Luôn dùng DTO riêng cho request và response. Không dùng chung một DTO cho cả tạo mới, cập nhật và hiển thị nếu các trường khác nhau.
- Module này **không gọi Repository của module khác**. Cần dữ liệu của module khác thì gọi qua Service của module đó.
- Đặt tên: `XxxController`, `XxxService`, `XxxRepository`, `XxxRequest`, `XxxResponse`. Package `dto/` nằm trong từng module.
- Ném lỗi nghiệp vụ bằng exception riêng (ví dụ `OutOfStockException`, `ResourceNotFoundException`), bắt tập trung ở một `@RestControllerAdvice` trong `common/`. Không `try/catch` rồi nuốt lỗi.

---

## 3. Database và Flyway

- Flyway sở hữu toàn bộ schema. Entity phải khớp schema, không ngược lại.
- Tên file: `V{số}__{mo_ta_ngan_khong_dau}.sql`. Số tăng dần, không nhảy, không trùng.
- **Không dùng kiểu `ENUM` của MySQL.** Cột trạng thái/loại dùng `VARCHAR(30)` kèm `CHECK (cot IN ('...', '...'))`, giá trị **chữ thường**.
- Tiền: `DECIMAL(12,2)`. Thời gian: `DATETIME`. Engine `InnoDB`, charset `utf8mb4`.
- Mọi khóa ngoại phải được khai báo thật theo đúng quan hệ thực tế giữa các bảng.
- Thứ tự tạo bảng theo phụ thuộc khóa ngoại.
- Thêm index khi có truy vấn lọc/sắp xếp thường xuyên, ghi rõ lý do ở comment đầu migration.
- Ràng buộc đã chốt phải giữ: `stock >= 0`, `quantity > 0` (cart_items, order_details), `rating BETWEEN 1 AND 5`.
- Lưu ý: `CHECK ... IN (...)` không phân biệt hoa/thường vì collation `utf8mb4_unicode_ci`. Quy ước chữ thường được đảm bảo bởi converter ở mục 4, không phải bởi CHECK.

---

## 4. Entity và JPA

- Khai báo rõ `@Table(name = "...")` và `@Column(name = "...")`. Không để Hibernate tự suy tên.
- Tiền dùng `BigDecimal`. Không dùng `double`/`float`.
- **Enum:** mỗi enum Java (chữ HOA: `ACTIVE`) có một converter kế thừa `AbstractEnumConverter` với `@Converter(autoApply = true)` để ghi xuống DB chữ thường (`active`). **Không dùng `@Enumerated` cùng converter.** Converter phải ném `IllegalArgumentException` khi gặp giá trị lạ, không trả `null`.
- Quan hệ `@ManyToOne(fetch = FetchType.LAZY)`. Không dùng `EAGER`.
- Không dùng `cascade = REMOVE` / `orphanRemoval` cho dữ liệu giao dịch (orders, payments). Dữ liệu lịch sử không được xóa dây chuyền.
- Không dùng `@Data` của Lombok trên Entity. Dùng `@Getter`/`@Setter`, `equals`/`hashCode` dựa trên `id` hoặc không override.
- Cấu hình `spring.jpa.open-in-view=false`. Dữ liệu cần cho response phải được nạp trong Service.
- Tránh N+1: danh sách kèm quan hệ dùng `@EntityGraph` hoặc `JOIN FETCH`, hoặc truy vấn thẳng ra DTO projection.
- Truy vấn tổng hợp cho dashboard dùng `@Query` với `GROUP BY`. Không nạp cả entity rồi đếm bằng vòng lặp Java.
- Mọi danh sách trả cho client phải phân trang, không `findAll()` không giới hạn.

---

## 5. Quy tắc nghiệp vụ bắt buộc

**Báo cáo và phân loại**

- Chỉ đơn `completed` được tính vào doanh thu, thống kê, RFM. Đơn `pending`, `confirmed`, `shipping`, `cancelled`, `refunded` không tính.

**Snapshot, không tham chiếu ngược**

- `order_details` lưu snapshot: `product_name`, `unit_price`, `variant_size`, `variant_color`. Khi hiển thị đơn cũ, đọc từ snapshot, **không** lấy từ `products` hay `product_variants`.
- `orders` lưu bản sao địa chỉ giao hàng (`receiver_name`, `receiver_phone`, `address_line`, `ward`, `district`, `province`). Chỉnh sổ địa chỉ sau này không được đổi đơn cũ. Lưu ý `user_addresses.address` ↔ `orders.address_line` khác tên cột, phải map đúng.

**Tồn kho**

- Tồn kho nằm ở `product_variants.stock`, không có cột tồn kho chung ở `products`.
- Trừ kho **bắt buộc** bằng câu lệnh nguyên tử, rồi kiểm tra số dòng bị ảnh hưởng:
  ```sql
  UPDATE product_variants SET stock = stock - :qty WHERE id = :id AND stock >= :qty
  ```
  Nếu `rowsAffected == 0` thì ném `OutOfStockException` với thông báo rõ ràng. **Không** đọc `stock` ra Java, kiểm tra rồi ghi lại.
- Hủy đơn hoặc hoàn tiền thì hoàn lại kho trong cùng transaction.

**Đặt hàng**

- Toàn bộ quy trình tạo đơn (kiểm tra giá, trừ kho, áp voucher, ghi `orders` + `order_details` + `payments`, chuyển giỏ sang `converted`) nằm trong **một** `@Transactional`. Một bước lỗi thì rollback toàn bộ.
- Giá tính ở server từ dữ liệu trong DB. **Không tin giá, tổng tiền hay phí giảm do client gửi lên.**
- Chuyển trạng thái đơn theo luồng đã định, mặc định: `pending → confirmed → shipping → completed`; hủy được từ `pending`/`confirmed`; `completed → refunded`. Chuyển sai luồng thì trả lỗi. Nếu cần luồng khác, hỏi người dùng.

**Voucher**

- Lượt dùng đếm từ `orders.voucher_id`, loại các đơn `cancelled`/`refunded`. **Không** tạo bảng ghi lượt dùng riêng.
- Kiểm tra đủ: còn hiệu lực, trong thời gian, `min_order`, `usage_limit`, `per_user_limit`, `max_discount`.

**Giỏ hàng**

- Mỗi user chỉ có một giỏ `active`, kiểm tra ở `CartService`. Khách chưa đăng nhập giữ giỏ ở Zustand (frontend), sau khi đăng nhập gọi API gộp vào giỏ trên server.

**Người dùng**

- Tài khoản `data_source = website` phải có ít nhất `email` hoặc `phone`, kiểm tra ở Service.
- Không trả `password_hash`, `token_hash` ra bất kỳ response nào.

---

## 6. Quy ước API

- Tiền tố `/api/...`; quản trị `/api/admin/...`.
- Lỗi thống nhất:
  ```json
  {
    "status": 400,
    "message": "Số điện thoại không hợp lệ",
    "errors": { "phone": "..." }
  }
  ```
  Thông báo lỗi nói đúng chuyện đang sai, không chung chung. Không trả stack trace hay thông điệp nội bộ của DB ra client.
- Mã HTTP đúng nghĩa: 200/201 thành công, 400 dữ liệu sai, 401 chưa đăng nhập, 403 không đủ quyền, 404 không tồn tại, 409 xung đột (hết hàng, trùng dữ liệu).
- Phân trang: `?page=0&size=20`, response dùng `PageResponse<T>` từ `common/dto` với cấu trúc **phẳng** (không lồng `pageable`, không dùng `Page<T>` của Spring trực tiếp):
  ```json
  { "content": [...], "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 }
  ```
  Giới hạn `size` tối đa (ví dụ 100). Khi danh sách rỗng trả `content: []`, không trả `null`.
- **Tiền trên API là số nguyên VND (`Long`)**, không trả số thập phân. DB giữ `DECIMAL(12,2)`, DTO làm việc chuyển đổi.
- **Boolean field:** dùng `Boolean` (wrapper, không phải `boolean`) để tránh Jackson sinh getter `isXxx` bị đổi tên. Tên JSON giữ nguyên tên field (`isActive` → `"isActive"`, không phải `"active"`).
- Kiểm tra đầu vào bằng Bean Validation (`@NotBlank`, `@Size`, `@Min`...) trên DTO request, kèm `@Valid` ở Controller.
- Mọi endpoint có chú thích springdoc (`@Operation`, mô tả response) để frontend sinh kiểu bằng openapi-typescript. **Đổi tên/kiểu trường của DTO đã có là thay đổi phá vỡ frontend**, phải báo người dùng.

---

## 7. Bảo mật

- Xác thực JWT qua `Authorization: Bearer <token>`. Refresh token lưu **dạng băm** trong DB, gửi qua cookie HttpOnly.
- Mật khẩu băm bằng BCrypt. Không log mật khẩu, token, hay dữ liệu cá nhân (số điện thoại, địa chỉ) ở mức INFO.
- `/api/admin/**` chỉ cho vai trò `admin`/`staff`. Quyền kiểm tra ở lớp bảo mật, không chỉ ẩn nút ở frontend.
- **Chống truy cập chéo (IDOR):** mọi endpoint của khách (đơn hàng, địa chỉ, giỏ hàng, hồ sơ) phải lọc theo user đang đăng nhập (lấy từ token, không lấy `userId` từ body hay query). Khách không được xem hoặc sửa dữ liệu của khách khác.
- Cấu hình CORS rõ ràng theo origin của frontend, không dùng `*` cho endpoint có xác thực.
- Không dùng nối chuỗi để dựng SQL. Dùng tham số (`:name` hoặc `?`).

---

## 8. Kiểm thử

- Mỗi quy tắc nghiệp vụ ở mục 5 có ít nhất một test (trừ kho khi hết hàng, hủy đơn hoàn kho, voucher quá hạn/vượt lượt, giỏ trùng...).
- Mọi lớp integration test phải có `@ActiveProfiles("test")`; không dùng profile dev/local trong test; nếu chạy test từ IDE thì kiểm tra DB đang dùng là `clothing_shop_test`.
- Integration test ghi vào DB phải `@Transactional` (tự rollback). Không để lại dữ liệu mẫu trong DB dev. Sau khi chạy test, `SELECT COUNT(*)` các bảng liên quan phải về trạng thái cũ.
- Test đi qua filter bảo mật hoặc truy cập quan hệ LAZY phải có ít nhất một test không bị `@Transactional` bao, vì `@Transactional` ở cấp lớp che các lỗi nạp lười.
- Với enum mới, test ghi rồi `entityManager.clear()` rồi đọc lại, và test giá trị lạ phải ném lỗi.
- Test đang đỏ thì không báo hoàn thành. Không xóa hoặc vô hiệu hóa test để cho qua.

---

## 9. Quy trình làm việc

1. **Đọc** `schema-spec.md`, `PROJECT_CONTEXT.md`, file này.
2. **Nêu kế hoạch ngắn**: module nào, file nào sẽ tạo/sửa. Việc có thể sai lệch (đổi schema, đổi DTO đã dùng, đụng `common/`) thì hỏi trước.
3. **Làm từng module nhỏ**, chạy test sau mỗi module, không dựng hàng loạt rồi mới thử.
4. **Audit** theo checklist dưới đây trước khi báo xong.
5. **Báo cáo** ngắn: đã làm gì, file nào thay đổi, test nào đã chạy và kết quả, việc nào chưa làm hoặc còn nghi ngờ. Không khẳng định "100% thành công" nếu chưa chạy thật.

Khi gặp điều không rõ (nghiệp vụ, luồng trạng thái, tên trường), **hỏi**, không tự bịa.

---

## Checklist audit (bắt buộc tự kiểm tra)

- [ ] Không sửa migration đã chạy; migration mới đúng số thứ tự
- [ ] Không có `ENUM` trong SQL; cột trạng thái là `VARCHAR(30)` + `CHECK`, chữ thường
- [ ] Entity không còn `@Enumerated`; mỗi enum có converter `autoApply`
- [ ] Tiền dùng `BigDecimal` trong code, `Long` VND trên API
- [ ] Controller không chứa logic, không trả Entity, không gọi Repository
- [ ] Không gọi Repository của module khác
- [ ] Trừ kho bằng `UPDATE ... WHERE stock >= :qty` và kiểm tra `rowsAffected`
- [ ] Tạo đơn nằm trong một transaction; giá tính ở server
- [ ] Đơn cũ đọc từ snapshot, không join ngược `products`/`product_variants`
- [ ] Endpoint của khách lọc theo user trong token; `/api/admin/**` có kiểm tra vai trò
- [ ] Không trả `password_hash`/`token_hash`; không log dữ liệu nhạy cảm
- [ ] Danh sách có phân trang; không N+1
- [ ] Test của quy tắc nghiệp vụ đã viết và chạy xanh; test ghi DB đã `@Transactional`
- [ ] Không thêm dependency hay đổi phiên bản khi chưa hỏi
- [ ] Không có secret trong code hoặc file được commit
