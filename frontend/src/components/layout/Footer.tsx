import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useCategories } from '@/features/product/hooks/useProducts';

export const Footer: React.FC = () => {
  const [email, setEmail] = useState('');
  const { data: categories } = useCategories();

  const handleSubscribe = (e: React.FormEvent) => {
    e.preventDefault();
    setEmail('');
  };

  return (
    <footer className="w-full border-t border-border bg-secondary/60">
      <div className="mx-auto w-full max-w-[1280px] px-4 py-8 sm:py-10 sm:px-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="flex flex-col gap-3">
            <span className="text-xl font-bold tracking-tight text-foreground uppercase select-none">
              ATELIER
            </span>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Cửa hàng thời trang may mặc tối giản cho nam và nữ tại Việt Nam.
            </p>

            <div className="mt-2 flex flex-col gap-2">
              <span className="text-xs font-semibold text-foreground uppercase tracking-wider">
                Đăng ký nhận tin
              </span>
              <p className="text-xs text-muted-foreground">
                Nhận thông báo bộ sưu tập mới và ưu đãi nội bộ.
              </p>
              <form onSubmit={handleSubscribe} className="flex items-center gap-1.5 mt-1">
                <Input
                  type="email"
                  placeholder="Nhập email của bạn..."
                  className="h-8.5 px-3 bg-card border-border rounded-md text-xs placeholder:text-muted-foreground flex-1"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
                <Button
                  type="submit"
                  variant="default"
                  size="default"
                  className="h-8.5 px-3.5 rounded-md text-xs font-medium"
                >
                  Đăng ký
                </Button>
              </form>
            </div>
          </div>

          <div className="flex flex-col gap-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-foreground">
              Danh mục
            </h4>
            <ul className="flex flex-col gap-2 text-sm text-muted-foreground">
              {categories && categories.length > 0 ? (
                categories.slice(0, 6).map((cat) => (
                  <li key={cat.id}>
                    <Link
                      to={`/products?categoryId=${cat.id}`}
                      className="hover:text-foreground transition-colors"
                    >
                      {cat.name}
                    </Link>
                  </li>
                ))
              ) : (
                <li>
                  <Link
                    to="/products"
                    className="hover:text-foreground transition-colors"
                  >
                    Tất cả sản phẩm
                  </Link>
                </li>
              )}
            </ul>
          </div>

          <div className="flex flex-col gap-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-foreground">
              Chăm sóc khách hàng
            </h4>
            <ul className="flex flex-col gap-2 text-sm text-muted-foreground">
              <li>
                <span>Chính sách đổi trả trong 30 ngày</span>
              </li>
              <li>
                <span>Hướng dẫn chọn kích cỡ</span>
              </li>
              <li>
                <span>Theo dõi đơn hàng</span>
              </li>
              <li>
                <span>Liên hệ hỗ trợ</span>
              </li>
            </ul>
          </div>

          <div className="flex flex-col gap-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-foreground">
              Thông tin cửa hàng
            </h4>
            <ul className="flex flex-col gap-2 text-sm text-muted-foreground">
              <li className="leading-relaxed">
                <span className="font-medium text-foreground">Địa chỉ:</span> 124 Nguyễn Trãi, Quận 1, TP. Hồ Chí Minh
              </li>
              <li className="leading-relaxed">
                <span className="font-medium text-foreground">Hotline:</span> 1900 6868
              </li>
              <li className="leading-relaxed">
                <span className="font-medium text-foreground">Giờ mở cửa:</span> 09:00 - 21:30 hàng ngày
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-12 pt-6 border-t border-border flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-muted-foreground">
          <p>© 2026 Atelier. Bảo lưu mọi quyền.</p>
          <div className="flex items-center gap-6">
            <span>Chính sách bảo mật</span>
            <span>Điều khoản dịch vụ</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
