import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronRight, SlidersHorizontal } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard } from '../components/ProductCard';
import { useCategories, useProducts } from '../hooks/useProducts';

export const WomenCollectionPage: React.FC = () => {
  useEffect(() => {
    document.title = 'Thời trang Nữ | ATELIER';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const { data: categories = [], isLoading: isCategoriesLoading } = useCategories();

  // Dynamically find categories by slug
  const aoNuCat = categories.find((c) => c.slug === 'ao-nu');
  const quanNuCat = categories.find((c) => c.slug === 'quan-nu');

  // Independent queries for each category section
  const {
    data: aoNuData,
    isLoading: isAoNuLoading,
    isError: isAoNuError,
    refetch: refetchAoNu,
  } = useProducts(
    aoNuCat?.id
      ? { categoryId: aoNuCat.id, size: 8, sort: 'newest' }
      : { size: 0 }
  );

  const {
    data: quanNuData,
    isLoading: isQuanNuLoading,
    isError: isQuanNuError,
    refetch: refetchQuanNu,
  } = useProducts(
    quanNuCat?.id
      ? { categoryId: quanNuCat.id, size: 8, sort: 'newest' }
      : { size: 0 }
  );

  const aoNuProducts = aoNuData?.content || [];
  const quanNuProducts = quanNuData?.content || [];

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header activeNav="nu" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Thời trang Nữ</span>
        </nav>

        {/* Compact Typography Hero Section */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-8 mb-8">
          <div className="max-w-2xl">
            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground block mb-1.5">
              Atelier Women Collection
            </span>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-sans">
              Thời Trang Nữ
            </h1>
            <p className="text-xs sm:text-sm text-muted-foreground mt-2 leading-relaxed">
              Tôn vinh nét thanh lịch tự nhiên và phong thái nhẹ nhàng. Đường cắt may tinh tế kết hợp cùng chất liệu lụa satin mượt mà, len dệt kim vintage và các phom đầm xòe duyên dáng.
            </p>
          </div>
        </div>

        {/* Section 1: Áo Nữ */}
        <section className="mb-12">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 pb-3 border-b border-border mb-5">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg sm:text-xl font-bold text-foreground">
                  {aoNuCat?.name || 'Áo Nữ'}
                </h2>
                {aoNuData?.totalElements !== undefined && (
                  <span className="text-xs text-muted-foreground">
                    ({aoNuData.totalElements} sản phẩm)
                  </span>
                )}
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">
                Áo sơ mi công sở lụa satin, cardigan len vintage và áo kiểu trễ vai nữ tính.
              </p>
            </div>

            {aoNuCat?.id && (
              <Link
                to={`/products?categoryId=${aoNuCat.id}`}
                className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1 shrink-0 self-start sm:self-auto"
              >
                <span>Xem tất cả áo nữ</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            )}
          </div>

          {/* Subcategory Pills for Áo Nữ */}
          {aoNuCat?.children && aoNuCat.children.length > 0 && (
            <div className="flex items-center gap-2 overflow-x-auto pb-4 scrollbar-none">
              {aoNuCat.children.map((child) => (
                <Link
                  key={child.id}
                  to={`/products?categoryId=${child.id}`}
                  className="h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground inline-flex items-center"
                >
                  {child.name}
                </Link>
              ))}
            </div>
          )}

          {/* Product Grid Area for Áo Nữ */}
          {isAoNuLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="animate-pulse space-y-2">
                  <div className="aspect-square bg-secondary/60 rounded-md" />
                  <div className="h-4 bg-secondary/60 rounded w-3/4" />
                  <div className="h-3 bg-secondary/60 rounded w-1/2" />
                </div>
              ))}
            </div>
          ) : isAoNuError ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg">
              <p className="text-xs text-destructive mb-2">
                Không thể tải danh sách áo nữ.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchAoNu()}
                className="text-xs h-7 px-3 rounded-md shadow-none"
              >
                Thử lại
              </Button>
            </div>
          ) : aoNuProducts.length === 0 ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg space-y-2">
              <SlidersHorizontal className="h-6 w-6 text-muted-foreground mx-auto" />
              <p className="text-xs text-muted-foreground">
                Chưa có sản phẩm trong mục áo nữ.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {aoNuProducts.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>

        {/* Section 2: Quần & Váy Nữ */}
        <section className="mb-12">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3 pb-3 border-b border-border mb-5">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg sm:text-xl font-bold text-foreground">
                  {quanNuCat?.name ? 'Quần & Váy Nữ' : 'Quần Nữ'}
                </h2>
                {quanNuData?.totalElements !== undefined && (
                  <span className="text-xs text-muted-foreground">
                    ({quanNuData.totalElements} sản phẩm)
                  </span>
                )}
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">
                Chân váy xếp ly, đầm hoa nhí midi và đầm dạ tiệc thanh lịch.
              </p>
            </div>

            {quanNuCat?.id && (
              <Link
                to={`/products?categoryId=${quanNuCat.id}`}
                className="text-xs font-semibold text-foreground hover:underline inline-flex items-center gap-1 shrink-0 self-start sm:self-auto"
              >
                <span>Xem tất cả quần & váy</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            )}
          </div>

          {/* Subcategory Pills for Quần & Váy Nữ */}
          {quanNuCat?.children && quanNuCat.children.length > 0 && (
            <div className="flex items-center gap-2 overflow-x-auto pb-4 scrollbar-none">
              {quanNuCat.children.map((child) => (
                <Link
                  key={child.id}
                  to={`/products?categoryId=${child.id}`}
                  className="h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground inline-flex items-center"
                >
                  {child.name}
                </Link>
              ))}
            </div>
          )}

          {/* Product Grid Area for Quần & Váy Nữ */}
          {isQuanNuLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="animate-pulse space-y-2">
                  <div className="aspect-square bg-secondary/60 rounded-md" />
                  <div className="h-4 bg-secondary/60 rounded w-3/4" />
                  <div className="h-3 bg-secondary/60 rounded w-1/2" />
                </div>
              ))}
            </div>
          ) : isQuanNuError ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg">
              <p className="text-xs text-destructive mb-2">
                Không thể tải danh sách quần và váy nữ.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchQuanNu()}
                className="text-xs h-7 px-3 rounded-md shadow-none"
              >
                Thử lại
              </Button>
            </div>
          ) : quanNuProducts.length === 0 ? (
            <div className="py-8 text-center border border-dashed border-border rounded-lg space-y-2">
              <SlidersHorizontal className="h-6 w-6 text-muted-foreground mx-auto" />
              <p className="text-xs text-muted-foreground">
                Chưa có sản phẩm trong mục quần và váy nữ.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {quanNuProducts.map((product) => (
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
