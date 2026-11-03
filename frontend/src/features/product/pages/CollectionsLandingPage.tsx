import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronRight, Compass } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard } from '../components/ProductCard';
import { useCategories, useProducts } from '../hooks/useProducts';

export const CollectionsLandingPage: React.FC = () => {
  useEffect(() => {
    document.title = 'Bộ sưu tập | ATELIER';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const { data: categories = [], isLoading: isCategoriesLoading } = useCategories();

  const aoNamCat = categories.find((c) => c.slug === 'ao-nam');
  const quanNamCat = categories.find((c) => c.slug === 'quan-nam');
  const phuKienCat = categories.find((c) => c.slug === 'phu-kien');

  // Query 1: Warm Minimalist Daily (Áo nam thường nhật)
  const {
    data: dailyData,
    isLoading: isDailyLoading,
    isError: isDailyError,
    refetch: refetchDaily,
  } = useProducts(
    aoNamCat?.id
      ? { categoryId: aoNamCat.id, size: 4, sort: 'newest' }
      : { size: 0 }
  );

  // Query 2: Tuyển tập Quần Nam Đa Dụng
  const {
    data: pantsData,
    isLoading: isPantsLoading,
    isError: isPantsError,
    refetch: refetchPants,
  } = useProducts(
    quanNamCat?.id
      ? { categoryId: quanNamCat.id, size: 4, sort: 'newest' }
      : { size: 0 }
  );

  // Query 3: Urban Essentials (Phụ kiện & Nón mũ)
  const {
    data: urbanData,
    isLoading: isUrbanLoading,
    isError: isUrbanError,
    refetch: refetchUrban,
  } = useProducts(
    phuKienCat?.id
      ? { categoryId: phuKienCat.id, size: 4, sort: 'newest' }
      : { size: 0 }
  );

  const dailyProducts = dailyData?.content || [];
  const pantsProducts = pantsData?.content || [];
  const urbanProducts = urbanData?.content || [];

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header activeNav="bo-suu-tap" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Bộ sưu tập</span>
        </nav>

        {/* Hero Section */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-8 mb-8">
          <div className="max-w-2xl">
            <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1.5">
              <Compass className="h-3 w-3 text-foreground" />
              Atelier Collections
            </span>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-sans">
              Bộ Sưu Tập Tuyển Chọn
            </h1>
            <p className="text-xs sm:text-sm text-muted-foreground mt-2 leading-relaxed">
              Những cảm hứng thời trang đương đại giao thoa cùng phong cách Warm Minimalist. Thiết kế tối giản, bảng màu thanh lịch, đồng hành cùng bạn trong mọi hoạt động hàng ngày.
            </p>
          </div>
        </div>

        {/* Collection 1: Warm Minimalist Daily */}
        <section className="mb-12">
          <div className="rounded-lg border border-border bg-card/50 p-5 sm:p-6 mb-5">
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
              <div className="max-w-2xl">
                <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground block mb-1">
                  Editorial 01 / Thường Nhật Tối Giản
                </span>
                <h2 className="text-xl sm:text-2xl font-bold text-foreground">
                  Warm Minimalist Daily
                </h2>
                <p className="text-xs sm:text-sm text-muted-foreground mt-1.5 leading-relaxed">
                  Tuyển tập trang phục thường nhật từ áo hoodie nỉ bông, áo len dệt kim, áo khoác thể thao đến áo thun phong cách.
                </p>
                <div className="flex flex-wrap gap-2 mt-3">
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Trang phục thường nhật
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Thiết kế hiện đại
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Tone màu trung tính
                  </span>
                </div>
              </div>

              <Link
                to="/collections/men"
                className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1.5 shrink-0 self-start md:self-auto"
              >
                <span>Xem toàn bộ dòng nam</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </div>

          {/* Daily Products Grid */}
          {isDailyError ? (
            <div className="rounded-md border border-destructive/20 bg-destructive/5 p-6 text-center space-y-2">
              <p className="text-xs font-medium text-destructive">
                Không thể tải sản phẩm bộ sưu tập Daily.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchDaily()}
                className="h-7 text-xs rounded-md border-border"
              >
                Thử lại
              </Button>
            </div>
          ) : isDailyLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-4 gap-3 sm:gap-3.5">
              {Array.from({ length: 4 }).map((_, idx) => (
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
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-4 gap-3 sm:gap-3.5">
              {dailyProducts.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>

        {/* Collection 2: Tuyển Tập Quần Nam Đa Dụng */}
        <section className="mb-12">
          <div className="rounded-lg border border-border bg-card/50 p-5 sm:p-6 mb-5">
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
              <div className="max-w-2xl">
                <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground block mb-1">
                  Editorial 02 / Phong Cách Đa Dạng
                </span>
                <h2 className="text-xl sm:text-2xl font-bold text-foreground">
                  Tuyển Tập Quần Nam Đa Dụng
                </h2>
                <p className="text-xs sm:text-sm text-muted-foreground mt-1.5 leading-relaxed">
                  Các thiết kế quần nam đa dạng phong cách: từ jean rách bụi bặm, short túi hộp năng động đến quần tây xếp ly chỉn chu.
                </p>
                <div className="flex flex-wrap gap-2 mt-3">
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Đa dạng phom dáng
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Dễ phối đồ
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Phong cách linh hoạt
                  </span>
                </div>
              </div>

              {quanNamCat?.id && (
                <Link
                  to={`/products?categoryId=${quanNamCat.id}`}
                  className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1.5 shrink-0 self-start md:self-auto"
                >
                  <span>Xem toàn bộ dòng quần nam</span>
                  <ArrowRight className="h-3.5 w-3.5" />
                </Link>
              )}
            </div>
          </div>

          {/* Pants Products Grid */}
          {isPantsError ? (
            <div className="rounded-md border border-destructive/20 bg-destructive/5 p-6 text-center space-y-2">
              <p className="text-xs font-medium text-destructive">
                Không thể tải sản phẩm bộ sưu tập Quần Nam.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchPants()}
                className="h-7 text-xs rounded-md border-border"
              >
                Thử lại
              </Button>
            </div>
          ) : isPantsLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-4 gap-3 sm:gap-3.5">
              {Array.from({ length: 4 }).map((_, idx) => (
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
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-4 gap-3 sm:gap-3.5">
              {pantsProducts.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>

        {/* Collection 3: Urban Essentials */}
        <section className="mb-12">
          <div className="rounded-lg border border-border bg-card/50 p-5 sm:p-6 mb-5">
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
              <div className="max-w-2xl">
                <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground block mb-1">
                  Editorial 03 / Tiện Ích Đô Thị
                </span>
                <h2 className="text-xl sm:text-2xl font-bold text-foreground">
                  Urban Essentials
                </h2>
                <p className="text-xs sm:text-sm text-muted-foreground mt-1.5 leading-relaxed">
                  Phụ kiện hoàn thiện phong cách đô thị: nón bucket canvas, nón lưỡi trai thêu chữ và thắt lưng da bò.
                </p>
                <div className="flex flex-wrap gap-2 mt-3">
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Phụ kiện thiết yếu
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Da & Canvas
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Điểm nhấn tinh tế
                  </span>
                </div>
              </div>

              <Link
                to="/collections/accessories"
                className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1.5 shrink-0 self-start md:self-auto"
              >
                <span>Xem toàn bộ Phụ kiện</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </div>

          {/* Urban Products Grid */}
          {isUrbanError ? (
            <div className="rounded-md border border-destructive/20 bg-destructive/5 p-6 text-center space-y-2">
              <p className="text-xs font-medium text-destructive">
                Không thể tải sản phẩm bộ sưu tập Urban.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchUrban()}
                className="h-7 text-xs rounded-md border-border"
              >
                Thử lại
              </Button>
            </div>
          ) : isUrbanLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-4 gap-3 sm:gap-3.5">
              {Array.from({ length: 4 }).map((_, idx) => (
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
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-4 gap-3 sm:gap-3.5">
              {urbanProducts.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>
      </main>

      <Footer />
    </div>
  );
};
