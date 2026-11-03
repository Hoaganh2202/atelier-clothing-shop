import React, { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, SlidersHorizontal } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard } from '../components/ProductCard';
import { useCategories, useProducts } from '../hooks/useProducts';
import type { ProductSortType } from '../api/product.api';

export const AccessoriesPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const sortParam = (searchParams.get('sort') as ProductSortType) || 'newest';
  const pageParam = searchParams.get('page')
    ? Math.max(1, Number(searchParams.get('page')))
    : 1;
  const subCategoryParam = searchParams.get('subCategoryId')
    ? Number(searchParams.get('subCategoryId'))
    : undefined;

  useEffect(() => {
    document.title = 'Phụ kiện | ATELIER';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const { data: categories = [], isLoading: isCategoriesLoading } = useCategories();

  // Dynamically find 'phu-kien' category by slug
  const phuKienCat = categories.find((c) => c.slug === 'phu-kien');

  // If user selected a specific subcategory pill, use that ID; otherwise use the parent phuKienCat.id
  const effectiveCategoryId = subCategoryParam || phuKienCat?.id;

  const {
    data: productsData,
    isLoading: isProductsLoading,
    isError,
    refetch,
  } = useProducts(
    effectiveCategoryId
      ? {
          categoryId: effectiveCategoryId,
          sort: sortParam,
          page: pageParam - 1,
          size: 12,
        }
      : { size: 0 }
  );

  const products = productsData?.content || [];
  const totalPages = productsData?.totalPages || 1;

  const handleSelectSubCategory = (subId?: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('page');
      if (subId) {
        next.set('subCategoryId', String(subId));
      } else {
        next.delete('subCategoryId');
      }
      return next;
    });
  };

  const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const nextSort = e.target.value as ProductSortType;
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('sort', nextSort);
      return next;
    });
  };

  const handlePageChange = (newPage: number) => {
    if (newPage < 1 || newPage > totalPages) return;
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', String(newPage));
      return next;
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header activeNav="phu-kien" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Phụ kiện</span>
        </nav>

        {/* Compact Typography Hero Section */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-8 mb-8">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div className="max-w-2xl">
              <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground block mb-1.5">
                Atelier Accents
              </span>
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-sans">
                Phụ Kiện Thời Trang
              </h1>
              <p className="text-xs sm:text-sm text-muted-foreground mt-2 leading-relaxed">
                Điểm nhấn hoàn hảo nâng tầm phong cách thường nhật. Tuyển chọn nón canvas vành tròn, nón lưỡi trai cá tính cùng thắt lưng da bò thật gia công tỉ mỉ.
              </p>
            </div>

            {/* Sort Selector */}
            <div className="flex items-center gap-2 text-xs text-muted-foreground shrink-0">
              <span>Sắp xếp:</span>
              <select
                value={sortParam}
                onChange={handleSortChange}
                className="h-8 px-2.5 rounded-md border border-border bg-background text-xs font-medium text-foreground focus:outline-none focus:ring-1 focus:ring-foreground"
              >
                <option value="newest">Mới nhất</option>
                <option value="price_asc">Giá: Thấp đến cao</option>
                <option value="price_desc">Giá: Cao đến thấp</option>
              </select>
            </div>
          </div>

          {/* Subcategory Pills */}
          <div className="flex items-center gap-2 overflow-x-auto pt-5 pb-1 scrollbar-none border-t border-border mt-5">
            <button
              type="button"
              onClick={() => handleSelectSubCategory(undefined)}
              className={`h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border ${
                !subCategoryParam
                  ? 'border-foreground bg-foreground text-background font-semibold'
                  : 'border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground'
              }`}
            >
              Tất cả phụ kiện
            </button>
            {phuKienCat?.children?.map((child) => (
              <button
                key={child.id}
                type="button"
                onClick={() => handleSelectSubCategory(child.id)}
                className={`h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border ${
                  subCategoryParam === child.id
                    ? 'border-foreground bg-foreground text-background font-semibold'
                    : 'border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground'
                }`}
              >
                {child.name}
              </button>
            ))}
          </div>
        </div>

        {/* Product Grid Area */}
        <section className="mb-12">
          {isProductsLoading || isCategoriesLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="animate-pulse space-y-2">
                  <div className="aspect-square bg-secondary/60 rounded-md" />
                  <div className="h-4 bg-secondary/60 rounded w-3/4" />
                  <div className="h-3 bg-secondary/60 rounded w-1/2" />
                </div>
              ))}
            </div>
          ) : isError ? (
            <div className="py-12 text-center border border-dashed border-border rounded-lg">
              <p className="text-xs text-destructive mb-2">
                Không thể tải danh sách phụ kiện.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetch()}
                className="text-xs h-7 px-3 rounded-md shadow-none"
              >
                Thử lại
              </Button>
            </div>
          ) : products.length === 0 ? (
            <div className="py-12 text-center border border-dashed border-border rounded-lg space-y-2">
              <SlidersHorizontal className="h-6 w-6 text-muted-foreground mx-auto" />
              <p className="text-xs text-muted-foreground">
                Chưa có sản phẩm trong mục phụ kiện này.
              </p>
              {subCategoryParam && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleSelectSubCategory(undefined)}
                  className="text-xs h-7 px-3 rounded-md shadow-none"
                >
                  Xem tất cả phụ kiện
                </Button>
              )}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
                {products.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="flex items-center justify-center gap-1.5 pt-10 pb-4">
                  <Button
                    variant="outline"
                    size="icon"
                    disabled={pageParam <= 1}
                    onClick={() => handlePageChange(pageParam - 1)}
                    className="h-8 w-8 rounded-md border-border shadow-none"
                    aria-label="Trang trước"
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>

                  {Array.from({ length: totalPages }).map((_, idx) => {
                    const pNum = idx + 1;
                    const isCurrent = pNum === pageParam;
                    return (
                      <Button
                        key={pNum}
                        variant={isCurrent ? 'default' : 'outline'}
                        size="sm"
                        onClick={() => handlePageChange(pNum)}
                        className={`h-8 w-8 p-0 rounded-md text-xs shadow-none ${
                          isCurrent
                            ? 'bg-foreground text-background font-semibold'
                            : 'border-border text-foreground hover:bg-secondary'
                        }`}
                      >
                        {pNum}
                      </Button>
                    );
                  })}

                  <Button
                    variant="outline"
                    size="icon"
                    disabled={pageParam >= totalPages}
                    onClick={() => handlePageChange(pageParam + 1)}
                    className="h-8 w-8 rounded-md border-border shadow-none"
                    aria-label="Trang sau"
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </>
          )}
        </section>
      </main>

      <Footer />
    </div>
  );
};
