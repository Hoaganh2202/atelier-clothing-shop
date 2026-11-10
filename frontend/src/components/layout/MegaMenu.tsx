import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
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

function formatCategoryLabel(category: CategoryPublicResponse): string {
  if (category.slug === 'ao-ba-lo') {
    return 'Áo Ba Lỗ';
  }
  return category.name || '';
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

  const aoNamCat = categories.find((c) => c.slug === 'ao-nam');
  const quanNamCat = categories.find((c) => c.slug === 'quan-nam');
  const aoNuCat = categories.find((c) => c.slug === 'ao-nu');
  const quanNuCat = categories.find((c) => c.slug === 'quan-nu');

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
            <div className="col-span-4 space-y-3">
              <div className="h-4 w-24 bg-secondary/60 rounded-md animate-pulse" />
              <div className="h-px bg-border" />
              <div className="space-y-2 pt-1">
                <div className="h-3 w-32 bg-secondary/60 rounded-md animate-pulse" />
                <div className="h-3 w-28 bg-secondary/60 rounded-md animate-pulse" />
                <div className="h-3 w-36 bg-secondary/60 rounded-md animate-pulse" />
              </div>
            </div>
            <div className="col-span-4 space-y-3">
              <div className="h-4 w-24 bg-secondary/60 rounded-md animate-pulse" />
              <div className="h-px bg-border" />
              <div className="space-y-2 pt-1">
                <div className="h-3 w-32 bg-secondary/60 rounded-md animate-pulse" />
                <div className="h-3 w-28 bg-secondary/60 rounded-md animate-pulse" />
              </div>
            </div>
            <div className="col-span-4 space-y-3">
              <div className="h-4 w-28 bg-secondary/60 rounded-md animate-pulse" />
              <div className="h-px bg-border" />
              <div className="space-y-2 pt-1">
                <div className="h-3 w-40 bg-secondary/60 rounded-md animate-pulse" />
              </div>
            </div>
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
              <div className="col-span-4 space-y-3">
                <Link
                  to={aoNamCat?.id ? `/products?categoryId=${aoNamCat.id}` : '/collections/men'}
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
                          {formatCategoryLabel(child)}
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
                        <span>Tất cả áo nam</span>
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 2: Quần Nam */}
              <div className="col-span-4 space-y-3">
                <Link
                  to={quanNamCat?.id ? `/products?categoryId=${quanNamCat.id}` : '/collections/men'}
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
                          {formatCategoryLabel(child)}
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
                        <span>Tất cả quần nam</span>
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 3: Điều hướng tổng hợp Thời trang Nam */}
              <div className="col-span-4 space-y-3">
                <span className="text-xs font-bold uppercase tracking-wider text-foreground block">
                  Thời trang Nam
                </span>
                <div className="h-px bg-border" />
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Tuyển tập các thiết kế áo và quần nam phom dáng chuẩn mực, chất liệu tự nhiên bền bỉ cho tủ đồ thường nhật.
                </p>
                <div className="pt-1">
                  <Link
                    to="/collections/men"
                    onClick={onClose}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-foreground hover:underline"
                  >
                    <span>Xem tất cả thời trang nam</span>
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </div>
            </>
          ) : (
            <>
              {/* Column 1: Áo Nữ */}
              <div className="col-span-4 space-y-3">
                <Link
                  to={aoNuCat?.id ? `/products?categoryId=${aoNuCat.id}` : '/collections/women'}
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
                          {formatCategoryLabel(child)}
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
                        <span>Tất cả áo nữ</span>
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 2: Quần Nữ */}
              <div className="col-span-4 space-y-3">
                <Link
                  to={quanNuCat?.id ? `/products?categoryId=${quanNuCat.id}` : '/collections/women'}
                  onClick={onClose}
                  className="group flex items-center justify-between text-xs font-bold uppercase tracking-wider text-foreground hover:text-muted-foreground transition-colors"
                >
                  <span>{quanNuCat?.name || 'Quần Nữ'}</span>
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
                          {formatCategoryLabel(child)}
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
                        <span>Tất cả quần & váy</span>
                        <ArrowRight className="h-2.5 w-2.5" />
                      </Link>
                    </li>
                  )}
                </ul>
              </div>

              {/* Column 3: Điều hướng tổng hợp Thời trang Nữ */}
              <div className="col-span-4 space-y-3">
                <span className="text-xs font-bold uppercase tracking-wider text-foreground block">
                  Thời trang Nữ
                </span>
                <div className="h-px bg-border" />
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Tôn vinh nét thanh lịch tự nhiên với sơ mi lụa, áo kiểu tinh tế và các thiết kế đầm váy duyên dáng.
                </p>
                <div className="pt-1">
                  <Link
                    to="/collections/women"
                    onClick={onClose}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-foreground hover:underline"
                  >
                    <span>Xem tất cả thời trang nữ</span>
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
