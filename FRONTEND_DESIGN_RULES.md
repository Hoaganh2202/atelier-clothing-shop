# FRONTEND_DESIGN_RULES.md

Agent PHẢI đọc file này trước khi viết bất kỳ component hoặc trang giao diện nào. Vi phạm bất kỳ mục nào dưới đây coi như phải sửa lại, không tính là hoàn thành.

---

## 0. Thứ tự ưu tiên khi có mâu thuẫn về thiết kế

File này áp dụng cho MỌI skill khác đang được cài (bao gồm nhưng không giới hạn: taste-skill, ui-ux-pro-max-skill, hoặc bất kỳ skill thiết kế/animation nào thêm sau này).

1. **`FRONTEND_DESIGN_RULES.md` (file này) LUÔN thắng, không có ngoại lệ.**
2. Bất kỳ skill nào khác chỉ được áp dụng **CHO PHẦN KHÔNG** bị file này cấm hoặc khóa cứng (xem mục 2 bên dưới).
3. Nếu một skill gợi ý điều gì vi phạm mục 2 (gradient, glassmorphism, card lồng card, shadow ngoài Modal/Dropdown, bo góc ngoài chuẩn, spacing ngoài thang đã khóa) — **BỎ QUA gợi ý đó, không thực hiện**, dù skill đó có nói gì.
4. Khi không chắc phương án nào đúng, ưu tiên **PHƯƠNG ÁN ĐƠN GIẢN HƠN**, không phải phương án "ấn tượng hơn" hay "premium hơn".

---

## 1. Chọn design system theo khu vực — không tự trộn lẫn

| Khu vực | Dùng | Lý do |
|---|---|---|
| **Admin / CMS** (`/admin/**`: quản lý sản phẩm, đơn hàng, voucher, khách hàng, dashboard) | **Ant Design** | Cần Table, Form, Modal, DatePicker dựng nhanh, đúng chuẩn, không tốn công tùy biến |
| **Phần khách hàng cần tùy biến sâu** (trang chủ, trang sản phẩm, giỏ hàng, thanh toán — nơi cần đúng thương hiệu shop) | **shadcn/ui** (Tailwind) | Cần kiểm soát giao diện theo bản sắc riêng của shop |

**Luật cứng:** một khu vực chỉ dùng một hệ. Không chèn component Ant Design vào trang dùng shadcn hoặc ngược lại trong cùng một màn hình.

**Cấm tự chế lại các component nguyên thủy:** `Button`, `Select`, `Modal`, `Dialog`, `Table`, `Input`, `Dropdown`, `DatePicker`. Luôn dùng đúng component gốc của Ant Design hoặc shadcn/ui, chỉ được:
- Đổi token (màu, bo góc, spacing) qua file cấu hình theme (xem mục 2).
- Truyền props có sẵn của thư viện (`size`, `variant`, `disabled`...).

**Không được:** viết lại cấu trúc HTML/CSS từ đầu để "trông giống" Button nhưng thực chất là một `<div>` tự chế; không override sâu vào bên trong bằng CSS tùy ý đè lên class nội bộ của thư viện.

---

## 2. Token bị khóa cứng — không được tự sáng tạo thêm

### Cấm tuyệt đối, không có ngoại lệ
- **Gradient** (nền, chữ, viền, nút) — mọi màu nền và màu chữ phải là màu đặc (solid).
- **Glassmorphism** (`backdrop-filter: blur(...)`, nền trong mờ chồng lớp).
- **Card lồng trong Card** — một khối nội dung chỉ có một lớp viền/nền, không đặt Card bên trong Card.
- **Shadow rải khắp nơi** — không tự thêm `box-shadow` cho mọi component. Shadow chỉ dùng ở đúng 2 chỗ: **Modal/Dialog** (để tách lớp khỏi nền) và **Dropdown/Popover**. Card, Button, Input, Table **không có shadow**, chỉ dùng viền (`border`) 1px để phân tách.
- Hiệu ứng hoạt ảnh trang trí không phục vụ mục đích rõ ràng (không thêm animation "cho đẹp" nếu không có lý do UX).

---

### Token chốt sẵn — chỉ dùng đúng các giá trị này

#### 1. Bo góc (Border Radius)
Khóa cứng một giá trị `6px` duy nhất cho toàn bộ hệ thống:
- Ant Design theme token: `borderRadius: 6`, `borderRadiusSM: 6`, `borderRadiusLG: 6` (tất cả các cấp đều là 6px).
- shadcn/ui CSS variable: `--radius: 6px` (trong `tailwind.config.js` các biến `DEFAULT`, `sm`, `md`, `lg` đều ánh xạ về `6px`).
- Cấm `rounded-xl`, `rounded-2xl`, `rounded-3xl`. Chỉ cho phép `rounded-full` cho Avatar tròn hoặc Pill Badge.

#### 2. Thang Spacing
**Thang Spacing chuẩn dự án:** ưu tiên chọn trong tập sau (đơn vị px): `4, 8, 12, 16, 24, 32, 48, 64`. Cấm số tự do kiểu `13px`, `22px`, `p-[18px]`.

> [!WARNING]
> **Lưu ý rủi ro khi ghi đè hoàn toàn thang spacing:** Ghi đè trực tiếp dưới `theme.spacing` sẽ triệt tiêu các class con của thư viện và component shadcn/ui (ví dụ: `h-9`, `h-10`, `size-4`, `gap-1.5`...). Việc ghi đè hoàn toàn cần một PR riêng biệt kèm kiểm thử giao diện toàn diện. Hiện tại thang spacing của dự án được đặt trong `theme.extend.spacing`.

```ts
// tailwind.config.ts / tailwind.config.js
export default {
  theme: {
    extend: {
      borderRadius: {
        DEFAULT: '6px',
        sm: '6px',
        md: '6px',
        lg: '6px',
        full: '9999px',
      },
      spacing: {
        1: '4px',
        2: '8px',
        3: '12px',
        4: '16px',
        6: '24px',
        8: '32px',
        12: '48px',
        16: '64px',
      },
    },
  },
};
```

#### 3. Bảng mã màu chốt cứng (Warm Minimalist Palette)
Không tự ý bịa mã màu mới:
- **Primary / Foreground / Focus Border:** `#18181B` (Deep Charcoal)
- **Background (Canvas):** `#FBF9F5` (Be ngà ấm)
- **Surface (Card / Khối nền trắng):** `#FFFFFF`
- **Secondary / Muted Surface:** `#F5F0EA` (Nền ô tìm kiếm, block phụ trợ)
- **Border:** `#E4DCD3` (Đường viền mảnh 1px định hình khối)
- **Muted Text (Chữ phụ / Caption):** `#6E675F`
- **Accent (Điểm nhấn / Badge sale):** `#C2410C` (Terracotta)
- **Success:** `#16A34A`
- **Warning:** `#D97706`
- **Danger:** `#DC2626`

#### 4. Cấu hình Ant Design Token (`ConfigProvider`)
Đối với khu vực Admin (`/admin/**`), bắt buộc bao bọc bằng `ConfigProvider` với token khóa cứng:
```tsx
import { ConfigProvider } from 'antd';

export const AdminAntdProvider = ({ children }: { children: React.ReactNode }) => (
  <ConfigProvider
    theme={{
      token: {
        colorPrimary: '#18181B',
        colorSuccess: '#16A34A',
        colorWarning: '#D97706',
        colorError: '#DC2626',
        colorInfo: '#18181B',
        borderRadius: 6,
        borderRadiusSM: 6,
        borderRadiusLG: 6,
        // Triệt tiêu shadow rải khắp nơi:
        boxShadow: 'none',
        boxShadowCard: 'none',
        boxShadowTertiary: 'none',
        // Chỉ giữ shadow nhẹ để tách lớp cho Modal / Dropdown:
        boxShadowSecondary: '0 4px 16px rgba(0, 0, 0, 0.08)',
        boxShadowPopoverArrow: 'none',
        fontFamily: "'Be Vietnam Pro', sans-serif",
      },
    }}
  >
    {children}
  </ConfigProvider>
);
```

#### 5. Typography & Phân cấp thị giác bằng `font-weight`
Tối đa 4 cỡ chữ trong một màn hình:
- `Heading 1`: `32px` (Hero / Tiêu đề lớn)
- `Heading 2`: `22px` (Section title)
- `Body`: `15px` (Nội dung văn bản, bảng biểu, form input)
- `Caption`: `13px` (Ghi chú, nhãn phụ, ngày tháng)

**Phân cấp bằng độ đậm chữ (`font-weight`):** Không lạm dụng việc thay đổi kích cỡ chữ liên tục; ưu tiên dùng `font-weight` để phân biệt thứ cấp thông tin:
- `font-normal` (`400`): Văn bản đọc dài, mô tả, nội dung ô trong bảng.
- `font-medium` (`500`): Nhãn form (`label`), liên kết menu, tên cột bảng, nhãn nút phụ.
- `font-semibold` (`600`): Tiêu đề danh mục, tiêu đề card, nút chính (`Primary Button`), giá bán chính.
- `font-bold` (`700`): Tiêu đề Hero chính (`h1`), số liệu tổng KPI quan trọng trên Dashboard.

---

## 3. Humanize nội dung chữ trên giao diện

- Viết **đúng thứ đang thật sự có**, không thêm mô tả hoa mỹ, không bịa tính năng ("trải nghiệm đẳng cấp", "giải pháp toàn diện", "khám phá ngay hôm nay" — cấm các cụm sáo rỗng kiểu này).
- Nhãn nút, tiêu đề, thông báo lỗi: **ngắn, chính xác, đúng hành động**. Ví dụ: nút xác nhận đặt hàng ghi "Đặt hàng", không ghi "Hoàn tất hành trình mua sắm của bạn".
- Thông báo lỗi nói đúng chuyện gì đang sai ("Số điện thoại không hợp lệ"), không nói chung chung ("Đã có lỗi xảy ra, vui lòng thử lại sau" chỉ dùng khi thật sự không xác định được lỗi cụ thể).
- Không thêm câu dẫn dắt dài dòng trước khi vào nội dung chính (không có đoạn mở đầu kiểu marketing trên trang admin).

---

## 4. Checklist audit (bắt buộc tự kiểm tra trước khi coi là xong)

- [ ] Không có gradient nào trong CSS/class vừa viết
- [ ] Không có `backdrop-filter`, không có nền trong mờ chồng lớp
- [ ] Không có Card nằm trong Card
- [ ] Shadow chỉ xuất hiện ở Modal/Dialog và Dropdown/Popover, không ở đâu khác (Card/Button/Input `shadow-none`)
- [ ] Mọi bo góc dùng đúng `6px` (`rounded-md`), không dùng `rounded-xl`/`rounded-2xl` (chỉ dùng `rounded-full` cho Avatar tròn hoặc Pill Badge)
- [ ] Mọi khoảng cách chỉ dùng giá trị trong thang spacing đã khóa (`4, 8, 12, 16, 24, 32, 48, 64px`)
- [ ] Dùng đúng bảng mã màu chốt cứng (Primary `#18181B`, Canvas `#FBF9F5`, Border `#E4DCD3`...)
- [ ] Khu vực admin bọc `ConfigProvider` với đúng token bo góc 3 cấp và shadow triệt tiêu
- [ ] Phân cấp thị giác ưu tiên dùng `font-weight` thay vì lạm dụng cỡ chữ
- [ ] Không có component Button/Select/Modal/Table tự chế — toàn bộ lấy từ Ant Design hoặc shadcn/ui
- [ ] Khu vực admin không lẫn component shadcn, khu vực khách không lẫn component Ant Design
- [ ] Chữ trên giao diện đúng thực tế, không sáo rỗng, không dài dòng
