import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
  SlidersHorizontal,
  X,
  ChevronRight,
  RotateCcw,
  ChevronLeft,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { ProductCard } from '../components/ProductCard';
import {
  useCategories,
  useProducts,
  useFilterOptions,
} from '../hooks/useProducts';
import type {
  CategoryPublicResponse,
  ProductSortType,
} from '../api/product.api';

export const ProductCatalogPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);

  // Read state from URL
  const keywordParam = searchParams.get('keyword') || '';
  const categoryIdParam = searchParams.get('categoryId')
    ? Number(searchParams.get('categoryId'))
    : undefined;
  const minPriceParam = searchParams.get('minPrice')
    ? Number(searchParams.get('minPrice'))
    : undefined;
  const maxPriceParam = searchParams.get('maxPrice')
    ? Number(searchParams.get('maxPrice'))
    : undefined;
  const sortParam = (searchParams.get('sort') as ProductSortType) || 'newest';
  const pageParam = searchParams.get('page')
    ? Math.max(1, Number(searchParams.get('page')))
    : 1;

  const sizesParam = useMemo(() => {
    const raw = searchParams.get('sizes');
    return raw ? raw.split(',').filter(Boolean) : [];
  }, [searchParams]);

  const colorsParam = useMemo(() => {
    const raw = searchParams.get('colors');
    return raw ? raw.split(',').filter(Boolean) : [];
  }, [searchParams]);

  // Local inputs with debounce
  const [searchInput, setSearchInput] = useState(keywordParam);
  const [minPriceInput, setMinPriceInput] = useState(
    minPriceParam != null ? String(minPriceParam) : ''
  );
  const [maxPriceInput, setMaxPriceInput] = useState(
    maxPriceParam != null ? String(maxPriceParam) : ''
  );

  // Sync local inputs when URL changes from outside
  useEffect(() => {
    setSearchInput(keywordParam);
  }, [keywordParam]);

  useEffect(() => {
    setMinPriceInput(minPriceParam != null ? String(minPriceParam) : '');
  }, [minPriceParam]);

  useEffect(() => {
    setMaxPriceInput(maxPriceParam != null ? String(maxPriceParam) : '');
  }, [maxPriceParam]);

  // Document Title
  useEffect(() => {
    document.title = 'Tất cả sản phẩm | ATELIER';
  }, []);

  // Update URL helper (resets page to 1 when changing filters)
  const updateParams = useCallback(
    (newParams: Record<string, string | null | undefined>, resetPage = true) => {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        if (resetPage) {
          next.delete('page');
        }
        Object.entries(newParams).forEach(([key, val]) => {
          if (val === null || val === undefined || val === '') {
            next.delete(key);
          } else {
            next.set(key, val);
          }
        });
        return next;
      });
    },
    [setSearchParams]
  );

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      if (searchInput !== keywordParam) {
        updateParams({ keyword: searchInput.trim() || null });
      }
    }, 400);
    return () => clearTimeout(timer);
  }, [searchInput, keywordParam, updateParams]);

  // Debounce price input
  useEffect(() => {
    const timer = setTimeout(() => {
      const minNum = minPriceInput ? Number(minPriceInput) : null;
      const maxNum = maxPriceInput ? Number(maxPriceInput) : null;

      const hasMinChanged = minNum !== (minPriceParam ?? null);
      const hasMaxChanged = maxNum !== (maxPriceParam ?? null);

      if (hasMinChanged || hasMaxChanged) {
        updateParams({
          minPrice: minNum != null && !isNaN(minNum) ? String(minNum) : null,
          maxPrice: maxNum != null && !isNaN(maxNum) ? String(maxNum) : null,
        });
      }
    }, 500);
    return () => clearTimeout(timer);
  }, [minPriceInput, maxPriceInput, minPriceParam, maxPriceParam, updateParams]);

  // Fetch API Data
  const { data: categories = [] } = useCategories();
  const { data: filterOptions } = useFilterOptions(categoryIdParam);

  const {
    data: productsData,
    isLoading: isProductsLoading,
    isError: isProductsError,
    refetch,
  } = useProducts({
    keyword: keywordParam || undefined,
    categoryId: categoryIdParam,
    minPrice: minPriceParam,
    maxPrice: maxPriceParam,
    sizes: sizesParam.length ? sizesParam : undefined,
    colors: colorsParam.length ? colorsParam : undefined,
    sort: sortParam,
    page: pageParam - 1,
    size: 12,
  });

  const products = productsData?.content || [];
  const totalPages = productsData?.totalPages || 1;
  const totalElements = productsData?.totalElements || 0;

  // Filter actions
  const handleSelectCategory = (id?: number) => {
    updateParams({ categoryId: id ? String(id) : null });
  };

  const handleToggleSize = (size: string) => {
    const next = sizesParam.includes(size)
      ? sizesParam.filter((s) => s !== size)
      : [...sizesParam, size];
    updateParams({ sizes: next.length ? next.join(',') : null });
  };

  const handleToggleColor = (color: string) => {
    const next = colorsParam.includes(color)
      ? colorsParam.filter((c) => c !== color)
      : [...colorsParam, color];
    updateParams({ colors: next.length ? next.join(',') : null });
  };

  const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    updateParams({ sort: e.target.value as ProductSortType });
  };

  const handlePageChange = (newPage: number) => {
    if (newPage < 1 || newPage > totalPages) return;
    updateParams({ page: String(newPage) }, false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const clearAllFilters = () => {
    setSearchInput('');
    setMinPriceInput('');
    setMaxPriceInput('');
    setSearchParams(new URLSearchParams());
  };

  const hasActiveFilters = Boolean(
    keywordParam ||
      categoryIdParam ||
      minPriceParam != null ||
      maxPriceParam != null ||
      sizesParam.length > 0 ||
      colorsParam.length > 0 ||
      sortParam !== 'newest'
  );

  // Find active category label
  const activeCategoryName = useMemo(() => {
    if (!categoryIdParam) return null;
    const findCat = (list: CategoryPublicResponse[]): string | null => {
      for (const item of list) {
        if (item.id === categoryIdParam) return (item.slug === 'ao-ba-lo' ? 'Áo Ba Lỗ' : item.name) || null;
        if (item.children) {
          const sub = findCat(item.children);
          if (sub) return sub;
        }
      }
      return null;
    };
    return findCat(categories);
  }, [categoryIdParam, categories]);

  // Reusable Filter Sidebar Content
  const renderFilterPanel = () => (
    <div className="space-y-6 text-sm">
      {/* Categories */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h4 className="font-semibold text-foreground">Danh mục</h4>
          {categoryIdParam && (
            <button
              type="button"
              onClick={() => handleSelectCategory(undefined)}
              className="text-xs text-muted-foreground hover:text-foreground underline"
            >
              Tất cả
            </button>
          )}
        </div>
        <div className="space-y-1">
          <button
            type="button"
            onClick={() => handleSelectCategory(undefined)}
            className={`w-full text-left py-1.5 px-2 rounded-md transition-colors text-xs font-medium ${
              !categoryIdParam
                ? 'bg-secondary text-foreground font-semibold'
                : 'text-muted-foreground hover:bg-secondary/50 hover:text-foreground'
            }`}
          >
            Tất cả danh mục
          </button>
          {categories.map((cat) => {
            const isParentActive = categoryIdParam === cat.id;
            return (
              <div key={cat.id} className="space-y-0.5">
                <button
                  type="button"
                  onClick={() => handleSelectCategory(cat.id)}
                  className={`w-full text-left py-1.5 px-2 rounded-md transition-colors text-xs flex items-center justify-between ${
                    isParentActive
                      ? 'bg-secondary text-foreground font-semibold'
                      : 'text-foreground hover:bg-secondary/50'
                  }`}
                >
                  <span className="truncate">{cat.name}</span>
                  {cat.children && cat.children.length > 0 && (
                    <ChevronRight className="h-3 w-3 text-muted-foreground shrink-0" />
                  )}
                </button>
                {/* Children categories */}
                {cat.children && cat.children.length > 0 && (
                  <div className="pl-3 space-y-0.5 border-l border-border ml-2 my-1">
                    {cat.children.map((sub) => {
                      const isSubActive = categoryIdParam === sub.id;
                      return (
                        <button
                          key={sub.id}
                          type="button"
                          onClick={() => handleSelectCategory(sub.id)}
                          className={`w-full text-left py-1 px-2 rounded-md transition-colors text-xs ${
                            isSubActive
                              ? 'bg-secondary text-foreground font-semibold'
                              : 'text-muted-foreground hover:text-foreground hover:bg-secondary/40'
                          }`}
                        >
                          {sub.slug === 'ao-ba-lo' ? 'Áo Ba Lỗ' : sub.name}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      <div className="h-px bg-border" />

      {/* Price Range */}
      <div className="space-y-2">
        <h4 className="font-semibold text-foreground">Khoảng giá (₫)</h4>
        <div className="grid grid-cols-2 gap-2">
          <Input
            type="number"
            placeholder="Từ"
            min={0}
            value={minPriceInput}
            onChange={(e) => setMinPriceInput(e.target.value)}
            className="h-9 text-xs rounded-md border-border bg-card shadow-none"
          />
          <Input
            type="number"
            placeholder="Đến"
            min={0}
            value={maxPriceInput}
            onChange={(e) => setMaxPriceInput(e.target.value)}
            className="h-9 text-xs rounded-md border-border bg-card shadow-none"
          />
        </div>
      </div>

      <div className="h-px bg-border" />

      {/* Sizes */}
      {filterOptions?.sizes && filterOptions.sizes.length > 0 && (
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="font-semibold text-foreground">Kích cỡ</h4>
            {sizesParam.length > 0 && (
              <button
                type="button"
                onClick={() => updateParams({ sizes: null })}
                className="text-xs text-muted-foreground hover:text-foreground underline"
              >
                Bỏ chọn
              </button>
            )}
          </div>
          <div className="flex flex-wrap gap-1.5">
            {filterOptions.sizes.map((size) => {
              const isSelected = sizesParam.includes(size);
              return (
                <button
                  key={size}
                  type="button"
                  onClick={() => handleToggleSize(size)}
                  className={`min-w-8 h-8 px-2 text-xs rounded-md border transition-colors ${
                    isSelected
                      ? 'border-foreground bg-foreground text-background font-semibold'
                      : 'border-border bg-card text-foreground hover:border-foreground'
                  }`}
                >
                  {size}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Colors */}
      {filterOptions?.colors && filterOptions.colors.length > 0 && (
        <>
          <div className="h-px bg-border" />
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <h4 className="font-semibold text-foreground">Màu sắc</h4>
              {colorsParam.length > 0 && (
                <button
                  type="button"
                  onClick={() => updateParams({ colors: null })}
                  className="text-xs text-muted-foreground hover:text-foreground underline"
                >
                  Bỏ chọn
                </button>
              )}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {filterOptions.colors.map((color) => {
                const isSelected = colorsParam.includes(color);
                return (
                  <button
                    key={color}
                    type="button"
                    onClick={() => handleToggleColor(color)}
                    className={`h-8 px-2.5 text-xs rounded-md border transition-colors truncate max-w-[140px] ${
                      isSelected
                        ? 'border-foreground bg-foreground text-background font-semibold'
                        : 'border-border bg-card text-foreground hover:border-foreground'
                    }`}
                  >
                    {color}
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}

      {/* Reset button */}
      {hasActiveFilters && (
        <Button
          variant="outline"
          size="sm"
          onClick={clearAllFilters}
          className="w-full gap-2 rounded-md border-border text-xs text-muted-foreground hover:text-foreground shadow-none"
        >
          <RotateCcw className="h-3.5 w-3.5" />
          Xóa tất cả bộ lọc
        </Button>
      )}
    </div>
  );

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header
        activeNav="san-pham"
        searchQuery={searchInput}
        onSearchChange={setSearchInput}
      />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-xs text-muted-foreground mb-3 sm:mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">
            {activeCategoryName || 'Tất cả sản phẩm'}
          </span>
        </nav>

        {/* Page Header & Controls */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 sm:pb-4 border-b border-border">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground">
              {activeCategoryName || 'Danh mục sản phẩm'}
            </h1>
            <p className="text-xs text-muted-foreground mt-1">
              {isProductsLoading
                ? 'Đang tải dữ liệu...'
                : `Hiển thị ${products.length} trong tổng số ${totalElements} sản phẩm`}
            </p>
          </div>

          <div className="flex items-center gap-3">
            {/* Mobile Filter Trigger Button */}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setMobileFilterOpen(true)}
              className="lg:hidden gap-2 rounded-md border-border text-xs shadow-none"
            >
              <SlidersHorizontal className="h-3.5 w-3.5" />
              Bộ lọc
              {hasActiveFilters && (
                <span className="h-2 w-2 rounded-full bg-foreground" />
              )}
            </Button>

            {/* Sort Selector */}
            <div className="flex items-center gap-2">
              <label htmlFor="product-sort" className="text-xs text-muted-foreground hidden sm:inline">
                Sắp xếp:
              </label>
              <select
                id="product-sort"
                value={sortParam}
                onChange={handleSortChange}
                className="h-8 px-2.5 rounded-md border border-border bg-card text-xs font-medium text-foreground focus:outline-none focus:ring-1 focus:ring-foreground"
              >
                <option value="newest">Mới nhất</option>
                <option value="price_asc">Giá: Thấp đến cao</option>
                <option value="price_desc">Giá: Cao đến thấp</option>
              </select>
            </div>
          </div>
        </div>

        {/* Active Filters Pill Bar */}
        {hasActiveFilters && (
          <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 pt-2.5 sm:pt-3">
            <span className="text-xs text-muted-foreground">Đang lọc:</span>
            {keywordParam && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-secondary text-xs font-medium text-foreground border border-border">
                "{keywordParam}"
                <button
                  type="button"
                  onClick={() => updateParams({ keyword: null })}
                  className="hover:text-destructive"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            )}
            {activeCategoryName && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-secondary text-xs font-medium text-foreground border border-border">
                Danh mục: {activeCategoryName}
                <button
                  type="button"
                  onClick={() => updateParams({ categoryId: null })}
                  className="hover:text-destructive"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            )}
            {(minPriceParam != null || maxPriceParam != null) && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-secondary text-xs font-medium text-foreground border border-border">
                Giá: {minPriceParam ? `${minPriceParam.toLocaleString('vi-VN')}₫` : '0₫'} -{' '}
                {maxPriceParam ? `${maxPriceParam.toLocaleString('vi-VN')}₫` : '∞'}
                <button
                  type="button"
                  onClick={() => updateParams({ minPrice: null, maxPrice: null })}
                  className="hover:text-destructive"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            )}
            {sizesParam.map((s) => (
              <span
                key={s}
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-secondary text-xs font-medium text-foreground border border-border"
              >
                Size: {s}
                <button
                  type="button"
                  onClick={() => handleToggleSize(s)}
                  className="hover:text-destructive"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            {colorsParam.map((c) => (
              <span
                key={c}
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-secondary text-xs font-medium text-foreground border border-border"
              >
                Màu: {c}
                <button
                  type="button"
                  onClick={() => handleToggleColor(c)}
                  className="hover:text-destructive"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            <button
              type="button"
              onClick={clearAllFilters}
              className="text-xs text-muted-foreground hover:text-foreground underline ml-2"
            >
              Xóa tất cả
            </button>
          </div>
        )}

        {/* Content Layout: Sidebar + Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-5 pt-4 items-start">
          {/* Desktop Filter Sidebar */}
          <aside className="hidden lg:block lg:col-span-1 rounded-md border border-border bg-card p-3 sm:p-3.5 sticky top-16">
            <h3 className="font-semibold text-sm text-foreground mb-3">Bộ lọc</h3>
            {renderFilterPanel()}
          </aside>

          {/* Product Grid Area */}
          <div className="lg:col-span-3 space-y-5 sm:space-y-6">
            {isProductsError ? (
              <div className="rounded-md border border-destructive/20 bg-destructive/5 p-8 text-center space-y-3">
                <p className="text-sm font-medium text-destructive">
                  Đã xảy ra lỗi khi tải danh sách sản phẩm.
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
              <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-3 gap-3 sm:gap-3.5">
                {Array.from({ length: 6 }).map((_, idx) => (
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
                  Không tìm thấy sản phẩm phù hợp
                </p>
                <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                  Hãy thử thay đổi tiêu chí lọc hoặc nhập từ khóa tìm kiếm khác.
                </p>
                {hasActiveFilters && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={clearAllFilters}
                    className="mt-2 rounded-md border-border shadow-none"
                  >
                    Xóa tất cả bộ lọc
                  </Button>
                )}
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-3 gap-3 sm:gap-3.5">
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
                        // Only show adjacent pages if there are many pages
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
                                  ? 'border-foreground bg-foreground text-background font-semibold'
                                  : 'border-border bg-card text-foreground hover:border-foreground'
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
        </div>
      </main>

      {/* Mobile Filter Sheet */}
      <Sheet open={mobileFilterOpen} onOpenChange={setMobileFilterOpen}>
        <SheetContent side="right" className="w-full sm:max-w-xs flex flex-col p-4">
          <SheetHeader className="pb-3 border-b border-border text-left">
            <SheetTitle className="text-base font-semibold text-foreground">
              Bộ lọc sản phẩm
            </SheetTitle>
          </SheetHeader>

          <div className="flex-1 overflow-y-auto pr-1 my-2">
            {renderFilterPanel()}
          </div>

          <div className="pt-3 border-t border-border mt-auto">
            <Button
              variant="default"
              size="default"
              onClick={() => setMobileFilterOpen(false)}
              className="w-full h-10 rounded-md font-medium text-sm shadow-none"
            >
              Xem kết quả ({totalElements})
            </Button>
          </div>
        </SheetContent>
      </Sheet>

      <Footer />
    </div>
  );
};
