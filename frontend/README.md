# Closet Shop — Frontend (Storefront & Admin)

Dự án Frontend cho website thương mại điện tử thời trang Closet Shop (Atelier), bao gồm hai phân hệ: Storefront (khách mua hàng) và Admin (quản trị vận hành).

---

## 🛠 Công nghệ sử dụng

- **Core:** React 18, TypeScript, Vite
- **Storefront UI:** Tailwind CSS, shadcn/ui components, Lucide Icons
- **Admin UI:** Ant Design 5 (`Layout`, `Menu`, `Table`, `Form`, `Card`, `Statistic`...), `@ant-design/icons`
- **Theme:** Ant Design `ConfigProvider` (`borderRadius = 6`, `colorPrimary = "#18181B"`)
- **State Management & Data Fetching:** Zustand, TanStack Query, Axios
- **Data Visualization:** Recharts

---

## 📁 Cấu trúc thư mục

```text
src/
├── api/             # Axios client, cấu hình interceptors, token refresh
├── components/      # Component dùng chung (ui, layout...)
├── features/        # Các feature theo module nghiệp vụ
│   ├── admin/       # Phân hệ quản trị (dashboard, product management...)
│   └── product/     # Phân hệ sản phẩm khách hàng (homepage, product card...)
├── layouts/         # Layout khung (AdminLayout, StorefrontLayout)
├── routes/          # Định tuyến & guards bảo vệ route
├── stores/          # Zustand global stores (auth, cart...)
└── App.tsx          # Router chính của ứng dụng
```

---

## 🚀 Khởi chạy dự án

### Cài đặt dependencies:
```bash
npm install
```

### Chạy môi trường phát triển (Dev server):
```bash
npm run dev
```
Truy cập:
- Storefront: `http://localhost:5173/`
- Admin Dashboard: `http://localhost:5173/admin`
- Quản lý sản phẩm Admin: `http://localhost:5173/admin/products`

### Build production:
```bash
npm run build
```