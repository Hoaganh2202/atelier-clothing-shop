import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Sparkles } from 'lucide-react';
import type { CategoryPublicResponse } from '@/features/product/api/product.api';

interface MegaMenuProps {
  type: 'men' | 'women';
  isOpen: boolean;
  onClose: () => void;
  categories?: CategoryPublicResponse[];
  isLoading?: boolean;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
}

export const MegaMenu: React.FC<MegaMenuProps> = ({
  type,
  isOpen,
  onClose,
  categories = [],
  isLoading = false,
  onMouseEnter,
  onMouseLeave,
}) => {
  if (!isOpen) return null;

  const isMen = type === 'men';

  // Find parent categories dynamically by slug from API
  const aoNamCat = categories.find((c) => c.slug === 'ao-nam');
  const quanNamCat = categories.find((c) => c.slug === 'quan-nam');
  const aoNuCat = categories.find((c) => c.slug === 'ao-nu');
  const quanNuCat = categories.find((c) => c.slug === 'quan-nu');

  // Loading skeleton state
  if (isLoading) {
    return (
      <div
        className="absolute top-full left-0 w-full border-b border-border bg-background shadow-md z-40 animate-in fade-in-50 duration-150"
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        role="region"
        aria-label="Đang tải menu danh mục"
      >
        <div className="mx-auto max-w-[1280px] px-6 py-6">
          <div className="grid grid-cols-12 gap-8">
            <div className="col-span-3 space-y-3">
              <div className="h-4 w-24 bg-secondary/60 rounded animate-pulse" />
              <div className="h-px bg-border" />
              <div className="space-y-2 pt-1">
                <div className="h-3 w-32 bg-secondary/60 rounded animate-pulse" />
                <div className="h-3 w-28 bg-secondary/60 rounded animate-pulse" />
                <div className="h-3 w-36 bg-secondary/60 rounded animate-pulse" />
              </div>
            </div>
            <div className="col-span-3 space-y-3">
              <div className="h-4 w-24 bg-secondary/60 rounded animate-pulse" />
              <div className="h-px bg-border" />
              <div className="space-y-2 pt-1">
                <div className="h-3 w-32 bg-secondary/60 rounded animate-pulse" />
                <div className="h-3 w-28 bg-secondary/60 rounded animate-pulse" />
              </div>
            </div>
            <div className="col-span-3 space-y-3">
              <div className="h-4 w-28 bg-secondary/60 rounded animate-pulse" />
              <div className="h-px bg-border" />
              <div className="space-y-2 pt-1">
                <div className="h-3 w-36 bg-secondary/60 rounded animate-pulse" />
                <div className="h-3 w-30 bg-secondary/60 rounded animate-pulse" />
              </div>
            </div>
            <div className="col-span-3 h-32 bg-secondary/40 rounded-md animate-pulse" />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className="absolute top-full left-0 w-full border-b border-border bg-background shadow-md z-40 animate-in fade-in-50 duration-150"
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      role="region"
      aria-label={isMen ? 'Mega menu Thời trang Nam' : 'Mega menu Thời trang Nữ'}
    >
      <div className="mx-auto max-w-[1280px] px-6 py-6">
        <div className="grid grid-cols-12 gap-8 items-start">
          {isMen ? (
            <>
              {/* Column 1: Áo Nam */}
              <div className="col-span-3 space-y-3">
                <Link
                  to={aoNamCat?.id ? `/products?categoryId=${aoNamCat.id}` : '/products'}
                  onClick={onClose}
                  className="group flex items-center justify-between text-xs font-bold uppercase tracking-wider text-foreground hover:text-muted-foreground transition-colors"
                >
                  <span>{aoNamCat?.name || 'Áo Nam'}</span>
                  <ArrowRight className="h-3 w-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                </Link>
                <div className="h-px bg-border" />
                <ul className="space-y-2 text-xs">
                  {aoNamCat?.children && aoNamCat.children.length > 0 ? (
                    aoNamCat.children.map((child) => (
                      <li key={child.id}>
                        <Link
                          to={`/products?categoryId=${child.id}`}
                          onClick={onClose}
                          className="text-muted-foreground hover:text-foreground transition-colors block py-0.5"
                        >
                          {child.name}
                        </Link>
                      </li>
                    ))
                  ) : (
                    <li className="text-muted-foreground py-0.5">Không có danh mục con</li>
                  )}
                  {aoNamCat?.id && (
                    <li className="pt-1">
                      <Link
                        to={`/products?categoryId=${aoNamCat.id}`}
                        onClick={onClose}
                        className="text-[11px] font-semibold text-foreground hover:underline inline-flex items-center gap-1"
                      >
                        Tất cả áo nam
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 2: Quần Nam */}
              <div className="col-span-3 space-y-3">
                <Link
                  to={quanNamCat?.id ? `/products?categoryId=${quanNamCat.id}` : '/products'}
                  onClick={onClose}
                  className="group flex items-center justify-between text-xs font-bold uppercase tracking-wider text-foreground hover:text-muted-foreground transition-colors"
                >
                  <span>{quanNamCat?.name || 'Quần Nam'}</span>
                  <ArrowRight className="h-3 w-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                </Link>
                <div className="h-px bg-border" />
                <ul className="space-y-2 text-xs">
                  {quanNamCat?.children && quanNamCat.children.length > 0 ? (
                    quanNamCat.children.map((child) => (
                      <li key={child.id}>
                        <Link
                          to={`/products?categoryId=${child.id}`}
                          onClick={onClose}
                          className="text-muted-foreground hover:text-foreground transition-colors block py-0.5"
                        >
                          {child.name}
                        </Link>
                      </li>
                    ))
                  ) : (
                    <li className="text-muted-foreground py-0.5">Không có danh mục con</li>
                  )}
                  {quanNamCat?.id && (
                    <li className="pt-1">
                      <Link
                        to={`/products?categoryId=${quanNamCat.id}`}
                        onClick={onClose}
                        className="text-[11px] font-semibold text-foreground hover:underline inline-flex items-center gap-1"
                      >
                        Tất cả quần nam
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 3: Tiêu điểm & Điều hướng */}
              <div className="col-span-3 space-y-3">
                <span className="text-xs font-bold uppercase tracking-wider text-foreground block">
                  Tiêu điểm nam
                </span>
                <div className="h-px bg-border" />
                <ul className="space-y-2.5 text-xs">
                  <li>
                    <Link
                      to="/collections/men"
                      onClick={onClose}
                      className="font-medium text-foreground hover:underline flex items-center justify-between"
                    >
                      <span>Xem tất cả thời trang nam</span>
                      <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/new-arrivals"
                      onClick={onClose}
                      className="text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1.5"
                    >
                      <Sparkles className="h-3 w-3 text-foreground" />
                      <span>Hàng mới về</span>
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/collections"
                      onClick={onClose}
                      className="text-muted-foreground hover:text-foreground transition-colors block"
                    >
                      Bộ sưu tập thiết kế
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/collections/accessories"
                      onClick={onClose}
                      className="text-muted-foreground hover:text-foreground transition-colors block"
                    >
                      Phụ kiện nam đồng bộ
                    </Link>
                  </li>
                </ul>
              </div>

              {/* Column 4: Editorial Feature Box */}
              <div className="col-span-3 rounded-md border border-border bg-secondary/50 p-4 flex flex-col justify-between">
                <div>
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-muted-foreground block mb-1">
                    Atelier Men
                  </span>
                  <h4 className="text-sm font-bold text-foreground leading-snug mb-1.5">
                    Thời trang tối giản thường nhật
                  </h4>
                  <p className="text-xs text-muted-foreground leading-relaxed">
                    Chất liệu cotton tự nhiên, phom dáng thoải mái bền vững cùng thời gian.
                  </p>
                </div>
                <Link
                  to="/collections/men"
                  onClick={onClose}
                  className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-foreground underline underline-offset-4 hover:text-muted-foreground"
                >
                  Khám phá ngay
                  <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </>
          ) : (
            <>
              {/* Column 1: Áo Nữ */}
              <div className="col-span-3 space-y-3">
                <Link
                  to={aoNuCat?.id ? `/products?categoryId=${aoNuCat.id}` : '/products'}
                  onClick={onClose}
                  className="group flex items-center justify-between text-xs font-bold uppercase tracking-wider text-foreground hover:text-muted-foreground transition-colors"
                >
                  <span>{aoNuCat?.name || 'Áo Nữ'}</span>
                  <ArrowRight className="h-3 w-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                </Link>
                <div className="h-px bg-border" />
                <ul className="space-y-2 text-xs">
                  {aoNuCat?.children && aoNuCat.children.length > 0 ? (
                    aoNuCat.children.map((child) => (
                      <li key={child.id}>
                        <Link
                          to={`/products?categoryId=${child.id}`}
                          onClick={onClose}
                          className="text-muted-foreground hover:text-foreground transition-colors block py-0.5"
                        >
                          {child.name}
                        </Link>
                      </li>
                    ))
                  ) : (
                    <li className="text-muted-foreground py-0.5">Không có danh mục con</li>
                  )}
                  {aoNuCat?.id && (
                    <li className="pt-1">
                      <Link
                        to={`/products?categoryId=${aoNuCat.id}`}
                        onClick={onClose}
                        className="text-[11px] font-semibold text-foreground hover:underline inline-flex items-center gap-1"
                      >
                        Tất cả áo nữ
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 2: Quần & Váy Nữ */}
              <div className="col-span-3 space-y-3">
                <Link
                  to={quanNuCat?.id ? `/products?categoryId=${quanNuCat.id}` : '/products'}
                  onClick={onClose}
                  className="group flex items-center justify-between text-xs font-bold uppercase tracking-wider text-foreground hover:text-muted-foreground transition-colors"
                >
                  <span>{quanNuCat?.name || 'Quần & Váy Nữ'}</span>
                  <ArrowRight className="h-3 w-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                </Link>
                <div className="h-px bg-border" />
                <ul className="space-y-2 text-xs">
                  {quanNuCat?.children && quanNuCat.children.length > 0 ? (
                    quanNuCat.children.map((child) => (
                      <li key={child.id}>
                        <Link
                          to={`/products?categoryId=${child.id}`}
                          onClick={onClose}
                          className="text-muted-foreground hover:text-foreground transition-colors block py-0.5"
                        >
                          {child.name}
                        </Link>
                      </li>
                    ))
                  ) : (
                    <li className="text-muted-foreground py-0.5">Không có danh mục con</li>
                  )}
                  {quanNuCat?.id && (
                    <li className="pt-1">
                      <Link
                        to={`/products?categoryId=${quanNuCat.id}`}
                        onClick={onClose}
                        className="text-[11px] font-semibold text-foreground hover:underline inline-flex items-center gap-1"
                      >
                        Tất cả quần & váy
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 3: Tiêu điểm Nữ */}
              <div className="col-span-3 space-y-3">
                <span className="text-xs font-bold uppercase tracking-wider text-foreground block">
                  Tiêu điểm nữ
                </span>
                <div className="h-px bg-border" />
                <ul className="space-y-2.5 text-xs">
                  <li>
                    <Link
                      to="/collections/women"
                      onClick={onClose}
                      className="font-medium text-foreground hover:underline flex items-center justify-between"
                    >
                      <span>Xem tất cả thời trang nữ</span>
                      <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/new-arrivals"
                      onClick={onClose}
                      className="text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1.5"
                    >
                      <Sparkles className="h-3 w-3 text-foreground" />
                      <span>Hàng mới về</span>
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/collections"
                      onClick={onClose}
                      className="text-muted-foreground hover:text-foreground transition-colors block"
                    >
                      Bộ sưu tập thiết kế
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/collections/accessories"
                      onClick={onClose}
                      className="text-muted-foreground hover:text-foreground transition-colors block"
                    >
                      Phụ kiện nữ đồng bộ
                    </Link>
                  </li>
                </ul>
              </div>

              {/* Column 4: Editorial Feature Box */}
              <div className="col-span-3 rounded-md border border-border bg-secondary/50 p-4 flex flex-col justify-between">
                <div>
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-muted-foreground block mb-1">
                    Atelier Women
                  </span>
                  <h4 className="text-sm font-bold text-foreground leading-snug mb-1.5">
                    Thanh lịch & Phóng khoáng
                  </h4>
                  <p className="text-xs text-muted-foreground leading-relaxed">
                    Phom dáng tự nhiên, đường cắt may tỉ mỉ tôn vinh phong thái nhẹ nhàng.
                  </p>
                </div>
                <Link
                  to="/collections/women"
                  onClick={onClose}
                  className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-foreground underline underline-offset-4 hover:text-muted-foreground"
                >
                  Khám phá ngay
                  <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
