import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ChevronRight,
  Gift,
  HeartHandshake,
  Percent,
  RefreshCw,
  ShieldCheck,
  Truck,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard } from '../components/ProductCard';
import { useProducts } from '../hooks/useProducts';

export const SalePage: React.FC = () => {
  useEffect(() => {
    document.title = 'Ưu đãi & Quyền lợi | ATELIER';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const {
    data: productsData,
    isLoading: isProductsLoading,
    isError,
    refetch,
  } = useProducts({
    size: 8,
    sort: 'newest',
  });

  const products = productsData?.content || [];

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header activeNav="uu-dai" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Ưu đãi & Quyền lợi</span>
        </nav>

        {/* Hero Section */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-8 mb-8">
          <div className="max-w-2xl">
            <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1.5">
              <Percent className="h-3 w-3 text-foreground" />
              Atelier Privileges & Promotions
            </span>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-sans">
              Đặc Quyền & Ưu Đãi
            </h1>
            <p className="text-xs sm:text-sm text-muted-foreground mt-2 leading-relaxed">
              Trải nghiệm mua sắm an tâm với chính sách minh bạch, chăm sóc tận tâm và các đặc quyền khách hàng chuẩn mực tại ATELIER.
            </p>
          </div>
        </div>

        {/* Customer Privileges Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-10">
          {/* Privilege 1: Free shipping */}
          <div className="rounded-lg border border-border bg-card p-5 space-y-2.5">
            <div className="h-9 w-9 rounded-md bg-secondary flex items-center justify-center text-foreground">
              <Truck className="h-4 w-4" />
            </div>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm text-foreground">
                Miễn phí vận chuyển
              </h3>
              <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground border border-border">
                Từ 500k
              </span>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Miễn phí vận chuyển cho đơn hàng từ 500.000₫.
            </p>
          </div>

          {/* Privilege 2: 30 days return policy */}
          <div className="rounded-lg border border-border bg-card p-5 space-y-2.5">
            <div className="h-9 w-9 rounded-md bg-secondary flex items-center justify-center text-foreground">
              <RefreshCw className="h-4 w-4" />
            </div>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm text-foreground">
                Đổi hàng 30 ngày
              </h3>
              <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground border border-border">
                30 ngày
              </span>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Chính sách đổi trả trong 30 ngày theo quy định thương hiệu được công bố của ATELIER.
            </p>
          </div>

          {/* Privilege 3: Quality Craftsmanship */}
          <div className="rounded-lg border border-border bg-card p-5 space-y-2.5">
            <div className="h-9 w-9 rounded-md bg-secondary flex items-center justify-center text-foreground">
              <ShieldCheck className="h-4 w-4" />
            </div>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm text-foreground">
                Thiết kế chỉn chu
              </h3>
              <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground border border-border">
                Chỉn chu
              </span>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Thiết kế chú trọng sự chỉn chu trong từng chi tiết và độ bền của phom dáng.
            </p>
          </div>

          {/* Privilege 4: Customer Support */}
          <div className="rounded-lg border border-border bg-card p-5 space-y-2.5">
            <div className="h-9 w-9 rounded-md bg-secondary flex items-center justify-center text-foreground">
              <HeartHandshake className="h-4 w-4" />
            </div>
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm text-foreground">
                Tư vấn khách hàng
              </h3>
              <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground border border-border">
                09:00 - 21:30
              </span>
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Đội ngũ tư vấn viên hỗ trợ từ 09:00–21:30 hàng ngày qua hotline 1900 6868.
            </p>
          </div>
        </div>

        {/* Voucher Redemption Guide Banner */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-7 mb-12">
          <div className="flex items-center gap-2 mb-3">
            <Gift className="h-4 w-4 text-foreground" />
            <h2 className="text-base sm:text-lg font-bold text-foreground">
              Hướng Dẫn Áp Dụng Mã Ưu Đãi (Voucher)
            </h2>
          </div>
          <p className="text-xs text-muted-foreground mb-5 max-w-2xl leading-relaxed">
            ATELIER áp dụng chính sách giá minh bạch. Khi sở hữu mã ưu đãi hợp lệ, bạn có thể áp dụng theo các bước sau:
          </p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="rounded-md border border-border bg-background p-4 space-y-1.5">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">
                Bước 01
              </span>
              <h4 className="text-xs font-semibold text-foreground">
                Chọn sản phẩm & Thanh toán
              </h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed">
                Lựa chọn sản phẩm phù hợp vào giỏ hàng và chuyển sang trang Thanh toán.
              </p>
            </div>

            <div className="rounded-md border border-border bg-background p-4 space-y-1.5">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">
                Bước 02
              </span>
              <h4 className="text-xs font-semibold text-foreground">
                Nhập mã ưu đãi tại Checkout
              </h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed">
                Tại trang Thanh toán, nhập mã giảm giá vào ô &quot;Mã giảm giá (Voucher)&quot;.
              </p>
            </div>

            <div className="rounded-md border border-border bg-background p-4 space-y-1.5">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">
                Bước 03
              </span>
              <h4 className="text-xs font-semibold text-foreground">
                Kiểm tra & Hoàn tất
              </h4>
              <p className="text-[11px] text-muted-foreground leading-relaxed">
                Hệ thống ghi nhận mã hợp lệ và trừ trực tiếp vào đơn hàng trước khi bạn bấm Đặt hàng.
              </p>
            </div>
          </div>
        </div>

        {/* Recommendations Section */}
        <section className="mb-12">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 pb-3 border-b border-border mb-6">
            <div>
              <h2 className="text-lg sm:text-xl font-bold text-foreground">
                Gợi Ý Từ ATELIER
              </h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Những thiết kế phù hợp cho phong cách tối giản thường nhật của bạn.
              </p>
            </div>

            <Link
              to="/new-arrivals"
              className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1 shrink-0 self-start sm:self-auto"
            >
              <span>Xem hàng mới về</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          {isError ? (
            <div className="rounded-md border border-destructive/20 bg-destructive/5 p-6 text-center space-y-2">
              <p className="text-xs font-medium text-destructive">
                Không thể tải danh sách sản phẩm.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetch()}
                className="h-7 text-xs rounded-md border-border"
              >
                Thử lại
              </Button>
            </div>
          ) : isProductsLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-3.5">
              {Array.from({ length: 8 }).map((_, idx) => (
                <div
                  key={idx}
                  className="rounded-md border border-border bg-card p-2 sm:p-2.5 animate-pulse"
                >
                  <div className="w-full aspect-square rounded-md bg-secondary mb-2" />
                  <div className="h-4 w-3/4 bg-secondary rounded-md mb-2" />
                  <div className="h-4 w-1/3 bg-secondary rounded-md" />
                </div>
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-3.5">
              {products.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>

        {/* Category Navigation Shortcut */}
        <div className="rounded-lg border border-border bg-card/60 p-6 mb-8 text-center">
          <h3 className="text-sm font-semibold text-foreground mb-1">
            Khám Phá Các Khu Vực Mua Sắm ATELIER
          </h3>
          <p className="text-xs text-muted-foreground mb-4">
            Lựa chọn danh mục trang phục bạn muốn tìm kiếm ngay hôm nay.
          </p>
          <div className="flex flex-wrap items-center justify-center gap-2.5">
            <Link
              to="/collections/men"
              className="h-8 px-4 text-xs rounded-md border border-border bg-background hover:bg-secondary text-foreground inline-flex items-center font-medium transition-colors"
            >
              Thời trang Nam
            </Link>
            <Link
              to="/collections/women"
              className="h-8 px-4 text-xs rounded-md border border-border bg-background hover:bg-secondary text-foreground inline-flex items-center font-medium transition-colors"
            >
              Thời trang Nữ
            </Link>
            <Link
              to="/collections/accessories"
              className="h-8 px-4 text-xs rounded-md border border-border bg-background hover:bg-secondary text-foreground inline-flex items-center font-medium transition-colors"
            >
              Phụ kiện & Nón mũ
            </Link>
            <Link
              to="/new-arrivals"
              className="h-8 px-4 text-xs rounded-md border border-border bg-background hover:bg-secondary text-foreground inline-flex items-center font-medium transition-colors"
            >
              Hàng mới về
            </Link>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
};
