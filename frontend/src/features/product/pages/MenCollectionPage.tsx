import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronRight, SlidersHorizontal } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard } from '../components/ProductCard';
import { useCategories, useProducts } from '../hooks/useProducts';

export const MenCollectionPage: React.FC = () => {
  useEffect(() => {
    document.title = 'Thời trang Nam | ATELIER';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const { data: categories = [], isLoading: isCategoriesLoading } = useCategories();

  // Dynamically find categories by slug
  const aoNamCat = categories.find((c) => c.slug === 'ao-nam');
  const quanNamCat = categories.find((c) => c.slug === 'quan-nam');

  // Independent queries for each category section
  const {
    data: aoNamData,
    isLoading: isAoNamLoading,
    isError: isAoNamError,
    refetch: refetchAoNam,
  } = useProducts(
    aoNamCat?.id
      ? { categoryId: aoNamCat.id, size: 8, sort: 'newest' }
      : { size: 0 }
  );

  const {
    data: quanNamData,
    isLoading: isQuanNamLoading,
    isError: isQuanNamError,
    refetch: refetchQuanNam,
  } = useProducts(
    quanNamCat?.id
      ? { categoryId: quanNamCat.id, size: 8, sort: 'newest' }
      : { size: 0 }
  );

  const aoNamProducts = aoNamData?.content || [];
  const quanNamProducts = quanNamData?.content || [];

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header activeNav="nam" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Thời trang Nam</span>
        </nav>

        {/* Compact Typography Hero Section */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-8 mb-8">
          <div className="max-w-2xl">
            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground block mb-1.5">
              Atelier Men Collection
            </span>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-sans">
              Thời Trang Nam
            </h1>
            <p className="text-xs sm:text-sm text-muted-foreground mt-2 leading-relaxed">
              Tuyển tập các thiết kế nam tính, tối giản và bền bỉ. Được dệt may tỉ mỉ từ chất liệu cotton tự nhiên, nỉ bông và sợi dệt tinh xảo, mang lại phong thái chỉn chu trong mọi khoảnh khắc.
            </p>
          </div>
        </div>

        {/* Section 1: Áo Nam */}
        <section className="mb-12">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 pb-3 border-b border-border mb-5">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg sm:text-xl font-bold text-foreground">
                  {aoNamCat?.name || 'Áo Nam'}
                </h2>
                {aoNamData?.totalElements !== undefined && (
                  <span className="text-xs text-muted-foreground">
                    ({aoNamData.totalElements} sản phẩm)
                  </span>
                )}
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">
                Áo thun cotton, sơ mi oxford và áo dệt kim form chuẩn mực.
              </p>
            </div>

            {aoNamCat?.id && (
              <Link
                to={`/products?categoryId=${aoNamCat.id}`}
                className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1 shrink-0 self-start sm:self-auto"
              >
                <span>Xem tất cả áo nam</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            )}
          </div>

          {/* Subcategory Pills for Áo Nam */}
          {aoNamCat?.children && aoNamCat.children.length > 0 && (
            <div className="flex items-center gap-2 overflow-x-auto pb-4 scrollbar-none">
              {aoNamCat.children.map((child) => (
                <Link
                  key={child.id}
                  to={`/products?categoryId=${child.id}`}
                  className="h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground inline-flex items-center"
                >
                  {child.slug === 'ao-ba-lo' ? 'Áo Ba Lỗ' : child.name}
                </Link>
              ))}
            </div>
          )}

          {/* Product Grid Area for Áo Nam */}
          {isAoNamLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="animate-pulse space-y-2">
                  <div className="aspect-square bg-secondary/60 rounded-md" />
                  <div className="h-4 bg-secondary/60 rounded w-3/4" />
                  <div className="h-3 bg-secondary/60 rounded w-1/2" />
                </div>
              ))}
            </div>
          ) : isAoNamError ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg">
              <p className="text-xs text-destructive mb-2">
                Không thể tải danh sách áo nam.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchAoNam()}
                className="text-xs h-7 px-3 rounded-md shadow-none"
              >
                Thử lại
              </Button>
            </div>
          ) : aoNamProducts.length === 0 ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg space-y-2">
              <SlidersHorizontal className="h-6 w-6 text-muted-foreground mx-auto" />
              <p className="text-xs text-muted-foreground">
                Chưa có sản phẩm trong mục áo nam.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {aoNamProducts.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>

        {/* Section 2: Quần Nam */}
        <section className="mb-12">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 pb-3 border-b border-border mb-5">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg sm:text-xl font-bold text-foreground">
                  {quanNamCat?.name || 'Quần Nam'}
                </h2>
                {quanNamData?.totalElements !== undefined && (
                  <span className="text-xs text-muted-foreground">
                    ({quanNamData.totalElements} sản phẩm)
                  </span>
                )}
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">
                Quần jean slimfit, kaki ống đứng và short thoải mái năng động.
              </p>
            </div>

            {quanNamCat?.id && (
              <Link
                to={`/products?categoryId=${quanNamCat.id}`}
                className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1 shrink-0 self-start sm:self-auto"
              >
                <span>Xem tất cả quần nam</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            )}
          </div>

          {/* Subcategory Pills for Quần Nam */}
          {quanNamCat?.children && quanNamCat.children.length > 0 && (
            <div className="flex items-center gap-2 overflow-x-auto pb-4 scrollbar-none">
              {quanNamCat.children.map((child) => (
                <Link
                  key={child.id}
                  to={`/products?categoryId=${child.id}`}
                  className="h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground inline-flex items-center"
                >
                  {child.slug === 'ao-ba-lo' ? 'Áo Ba Lỗ' : child.name}
                </Link>
              ))}
            </div>
          )}

          {/* Product Grid Area for Quần Nam */}
          {isQuanNamLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="animate-pulse space-y-2">
                  <div className="aspect-square bg-secondary/60 rounded-md" />
                  <div className="h-4 bg-secondary/60 rounded w-3/4" />
                  <div className="h-3 bg-secondary/60 rounded w-1/2" />
                </div>
              ))}
            </div>
          ) : isQuanNamError ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg">
              <p className="text-xs text-destructive mb-2">
                Không thể tải danh sách quần nam.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchQuanNam()}
                className="text-xs h-7 px-3 rounded-md shadow-none"
              >
                Thử lại
              </Button>
            </div>
          ) : quanNamProducts.length === 0 ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg space-y-2">
              <SlidersHorizontal className="h-6 w-6 text-muted-foreground mx-auto" />
              <p className="text-xs text-muted-foreground">
                Chưa có sản phẩm trong mục quần nam.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {quanNamProducts.map((product) => (
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
