# GEMINI.md — Website shop quần áo

File này được Antigravity tự động nạp cho mọi thao tác trong project (gốc và toàn bộ thư mục con). Đây là điểm bắt buộc phải tuân thủ, không phải gợi ý.

## Tổng quan dự án

Website thương mại điện tử bán quần áo hoàn chỉnh, gồm 2 phần: storefront (khách mua hàng) và trang quản trị (admin). Đây là dự án độc lập làm đồ án tốt nghiệp, do 2 thành viên phụ trách chung cả Frontend + Backend. Dự án sở hữu toàn bộ cơ sở dữ liệu của mình, quản lý schema tập trung và nhất quán thông qua Flyway migrations.

## Công nghệ

- **Backend:** Java 17+, Spring Boot (Web, Validation, Security + JWT), Spring Data JPA + Hibernate (`ddl-auto=validate`), MySQL, Flyway sở hữu toàn bộ schema, springdoc-openapi (Swagger UI).
- **Frontend:** React + TypeScript, Vite, Tailwind CSS, Ant Design (khu admin), shadcn/ui (khu khách hàng), React Router, TanStack Query + Axios, React Hook Form + Zod, Zustand, Recharts.

## Quy tắc bắt buộc phải đọc theo ngữ cảnh công việc

- **Toàn bộ thông tin dự án** (công nghệ, cấu trúc thư mục theo module, phân công, danh sách bảng dữ liệu, khóa ngoại, quy ước migration) bắt buộc tuân thủ theo: @PROJECT_CONTEXT.md
- **Đặc tả CSDL chi tiết** (bảng, cột, kiểu dữ liệu, ràng buộc `CHECK`, index) bắt buộc tuân thủ theo: @schema-spec.md
- **Khi làm việc với Backend** (Entity, Repository, Service, Controller, DTO, Flyway, Test) bắt buộc tuân thủ theo: @BACKEND_RULES.md
- **Khi làm việc với Frontend** bắt buộc tuân thủ theo: @FRONTEND_DESIGN_RULES.md, kết hợp đặc tả giao diện @DESIGN.md (khu vực Storefront) và @DESIGN_ADMIN.md (khu vực Admin).
- **Quy ước ngôn ngữ trong code:** Tên biến, hàm, class, interface, comment kỹ thuật viết 100% bằng tiếng Anh; riêng **chuỗi hiển thị cho người dùng cuối** (nhãn UI, placeholder, thông báo lỗi trả về client từ API/Validation) viết bằng **tiếng Việt tự nhiên, rõ nghĩa**.

## Quản lý CSDL và Schema — quyền sở hữu độc lập

- Dự án sở hữu độc lập toàn bộ CSDL (`clothing_shop_db` và `clothing_shop_test`).
- Mọi thay đổi schema mới bắt buộc đi qua file migration Flyway (`V{n+1}__mo_ta.sql`), tuyệt đối KHÔNG sửa file migration đã chạy.
- Các bảng thuộc phân hệ khai thác CSDL cũ (`segments`, `segmentation_runs`, `customer_segments`) đã được tách thành dự án riêng và loại khỏi phạm vi của repo này.

## Ranh giới về công nghệ — không tự ý thêm

- Backend chỉ dùng Java thuần + Spring Boot. Không đề xuất hay chuyển sang Node.js, Python, NestJS, hay bất kỳ ngôn ngữ/framework nào khác cho phần API chính.
- **`taste-skill` (chỉ skill con `design-taste-frontend`) ĐÃ ĐƯỢC KÍCH HOẠT và PHẢI được chủ động dùng** khi dựng mới hoặc chỉnh sửa giao diện storefront/admin, nhằm tránh bố cục, ảnh, câu chữ khuôn mẫu ("AI slop"): tăng phân cấp thị giác, chọn ảnh nhất quán phong cách, viết chữ tự nhiên không sáo rỗng. Không cài, không dùng các skill con khác của `taste-skill` (`imagegen`, `brandkit`, `stitch-design-taste`, `gpt-taste`).
- Không cài đặt hay đề xuất **bất kỳ skill/thư viện thiết kế bên thứ ba nào khác** (ví dụ `ui-ux-pro-max-skill`, các skill tạo hiệu ứng animation) trừ khi được yêu cầu rõ ràng.
- Dù `taste-skill` được bật, mọi gợi ý của nó vẫn phải tuân theo thứ tự ưu tiên tại mục 0 của @FRONTEND_DESIGN_RULES.md — token khóa cứng (bo góc, spacing, cấm gradient/glassmorphism/shadow thừa) LUÔN thắng, không có ngoại lệ.
- **`ponytail-shop` (`.agents/skills/ponytail-shop/SKILL.md`) ĐÃ ĐƯỢC KÍCH HOẠT cho dự án này:** Áp dụng triết lý thực dụng (YAGNI, ưu tiên tái sử dụng code/hook/DTO có sẵn trong repo, cấm tự ý cài thêm thư viện cho vài dòng code, giữ diff ngắn gọn đúng nguyên nhân gốc). Tuy nhiên, `ponytail-shop` **TUYỆT ĐỐI KHÔNG** được cắt giảm kiến trúc phân lớp của Spring Boot (`Controller -> Service -> Repository -> DTO`), validation/bảo mật, hay các token thiết kế & trạng thái UX của Frontend.
