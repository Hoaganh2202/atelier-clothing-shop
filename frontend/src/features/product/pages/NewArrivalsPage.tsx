import React, { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, SlidersHorizontal, Sparkles } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard } from '../components/ProductCard';
import { useCategories, useProducts } from '../hooks/useProducts';
import type { ProductSortType } from '../api/product.api';

export const NewArrivalsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const sortParam = (searchParams.get('sort') as ProductSortType) || 'newest';
  const pageParam = searchParams.get('page')
    ? Math.max(1, Number(searchParams.get('page')))
    : 1;
  const categoryIdParam = searchParams.get('categoryId')
    ? Number(searchParams.get('categoryId'))
    : undefined;

  useEffect(() => {
    document.title = 'Hàng mới về | ATELIER';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, []);

  const { data: categories = [], isLoading: isCategoriesLoading } = useCategories();

  const {
    data: productsData,
    isLoading: isProductsLoading,
    isError,
    refetch,
  } = useProducts({
    categoryId: categoryIdParam,
    sort: sortParam,
    page: pageParam - 1,
    size: 12,
  });

  const products = productsData?.content || [];
  const totalPages = productsData?.totalPages || 1;
  const totalElements = productsData?.totalElements ?? 0;

  const handleSelectCategory = (catId?: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete('page');
      if (catId) {
        next.set('categoryId', String(catId));
      } else {
        next.delete('categoryId');
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
      <Header activeNav="hang-moi" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Hàng mới về</span>
        </nav>

        {/* Hero Section */}
        <div className="rounded-lg border border-border bg-card p-6 sm:p-8 mb-6">
          <div className="max-w-2xl">
            <span className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground mb-1.5">
              <Sparkles className="h-3 w-3 text-foreground" />
              Atelier New Arrivals
            </span>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground font-sans">
              Hàng Mới Về
            </h1>
            <p className="text-xs sm:text-sm text-muted-foreground mt-2 leading-relaxed">
              Tuyển tập những thiết kế vừa cập bến tại ATELIER. Tối giản trong phom dáng, chuẩn mực trong từng đường kim mũi chỉ, đồng hành cùng bạn trong mọi khoảnh khắc thường nhật.
            </p>
          </div>
        </div>

        {/* Filter Controls & Sorting Toolbar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pb-4 mb-6 border-b border-border">
          {/* Category Filter Tabs */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
            <button
              type="button"
              onClick={() => handleSelectCategory(undefined)}
              className={`h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border ${
                !categoryIdParam
                  ? 'border-foreground bg-foreground text-background font-medium'
                  : 'border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground'
              }`}
            >
              Tất cả
            </button>

            {!isCategoriesLoading &&
              categories.map((cat) => {
                const isSelected = categoryIdParam === cat.id;
                return (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => handleSelectCategory(cat.id)}
                    className={`h-7 px-3 text-xs rounded-full whitespace-nowrap transition-colors border ${
                      isSelected
                        ? 'border-foreground bg-foreground text-background font-medium'
                        : 'border-border bg-card text-muted-foreground hover:text-foreground hover:border-foreground'
                    }`}
                  >
                    {cat.name}
                  </button>
                );
              })}
          </div>

          {/* Sort Selector & Total Counter */}
          <div className="flex items-center justify-between md:justify-end gap-3 shrink-0">
            <span className="text-xs text-muted-foreground">
              {totalElements} sản phẩm
            </span>

            <div className="flex items-center gap-1.5">
              <SlidersHorizontal className="h-3.5 w-3.5 text-muted-foreground" />
              <label htmlFor="new-arrivals-sort" className="sr-only">
                Sắp xếp theo
              </label>
              <select
                id="new-arrivals-sort"
                value={sortParam}
                onChange={handleSortChange}
                className="h-8 rounded-md border border-border bg-background px-2.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-foreground"
              >
                <option value="newest">Mới nhất</option>
                <option value="price_asc">Giá: Thấp đến cao</option>
                <option value="price_desc">Giá: Cao đến thấp</option>
              </select>
            </div>
          </div>
        </div>

        {/* Product Grid Area */}
        <div className="space-y-6">
          {isError ? (
            <div className="rounded-md border border-destructive/20 bg-destructive/5 p-8 text-center space-y-3">
              <p className="text-sm font-medium text-destructive">
                Đã xảy ra lỗi khi tải danh sách sản phẩm mới.
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetch()}
                className="rounded-md border-border shadow-none"
              >
                Thử lại
              </Button>
            </div>
          ) : isProductsLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-3.5">
              {Array.from({ length: 8 }).map((_, idx) => (
                <div
                  key={idx}
                  className="flex flex-col justify-between rounded-md border border-border bg-card p-2 sm:p-2.5 animate-pulse"
                >
                  <div className="w-full aspect-square rounded-md bg-secondary mb-2" />
                  <div className="h-4 w-3/4 bg-secondary rounded-md mb-2" />
                  <div className="h-4 w-1/3 bg-secondary rounded-md mb-3" />
                  <div className="h-8 w-full bg-secondary rounded-md" />
                </div>
              ))}
            </div>
          ) : products.length === 0 ? (
            <div className="rounded-md border border-border bg-card p-12 text-center space-y-3">
              <p className="text-base font-semibold text-foreground">
                Chưa có sản phẩm mới nào trong mục này
              </p>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                Vui lòng quay lại sau hoặc khám phá các danh mục thời trang khác của ATELIER.
              </p>
              {categoryIdParam && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleSelectCategory(undefined)}
                  className="mt-2 rounded-md border-border shadow-none"
                >
                  Xem tất cả hàng mới
                </Button>
              )}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-3.5">
                {products.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>

              {/* Pagination Controls */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between pt-6 border-t border-border">
                  <span className="text-xs text-muted-foreground">
                    Trang {pageParam} / {totalPages}
                  </span>

                  <div className="flex items-center gap-1.5">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={pageParam <= 1}
                      onClick={() => handlePageChange(pageParam - 1)}
                      className="h-8 px-2.5 gap-1 rounded-md border-border text-xs shadow-none disabled:opacity-40"
                    >
                      <ChevronLeft className="h-3.5 w-3.5" />
                      Trước
                    </Button>

                    {Array.from({ length: totalPages }).map((_, idx) => {
                      const pNum = idx + 1;
                      if (
                        pNum === 1 ||
                        pNum === totalPages ||
                        Math.abs(pNum - pageParam) <= 1
                      ) {
                        const isActive = pNum === pageParam;
                        return (
                          <button
                            key={pNum}
                            type="button"
                            onClick={() => handlePageChange(pNum)}
                            className={`h-8 w-8 rounded-md text-xs font-medium border transition-colors ${
                              isActive
                                ? 'bg-primary text-primary-foreground border-primary'
                                : 'border-border bg-card text-foreground hover:bg-secondary'
                            }`}
                          >
                            {pNum}
                          </button>
                        );
                      }
                      if (
                        (pNum === 2 && pageParam > 3) ||
                        (pNum === totalPages - 1 && pageParam < totalPages - 2)
                      ) {
                        return (
                          <span
                            key={pNum}
                            className="px-1 text-xs text-muted-foreground"
                          >
                            ...
                          </span>
                        );
                      }
                      return null;
                    })}

                    <Button
                      variant="outline"
                      size="sm"
                      disabled={pageParam >= totalPages}
                      onClick={() => handlePageChange(pageParam + 1)}
                      className="h-8 px-2.5 gap-1 rounded-md border-border text-xs shadow-none disabled:opacity-40"
                    >
                      Sau
                      <ChevronRight className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
};
