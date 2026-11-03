import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import type { CategoryPublicResponse } from '../api/product.api';

interface CategoryListSectionProps {
  categories?: CategoryPublicResponse[];
  isLoading?: boolean;
}

const CATEGORY_IMAGES: Record<string, string> = {
  'ao-nam': 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?q=80&w=600&auto=format&fit=crop',
  'quan-nam': 'https://images.unsplash.com/photo-1542272604-787c3835535d?q=80&w=600&auto=format&fit=crop',
  'ao-nu': 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?q=80&w=600&auto=format&fit=crop',
  'quan-nu': 'https://images.unsplash.com/photo-1509551388413-e18d0ac5d495?q=80&w=600&auto=format&fit=crop',
  'phu-kien': 'https://images.unsplash.com/photo-1523293182086-7651a899d37f?q=80&w=600&auto=format&fit=crop',
  'ao-thun-nam': 'https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?q=80&w=600&auto=format&fit=crop',
  'ao-so-mi-nam': 'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?q=80&w=600&auto=format&fit=crop',
  'quan-jean-nam': 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?q=80&w=600&auto=format&fit=crop',
};

const DEFAULT_CATEGORY_IMG =
  'https://images.unsplash.com/photo-1441986300917-64674bd600d8?q=80&w=600&auto=format&fit=crop';

export const CategoryListSection: React.FC<CategoryListSectionProps> = ({
  categories = [],
  isLoading = false,
}) => {
  if (isLoading) {
    return (
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="h-7 w-48 bg-secondary rounded-md animate-pulse" />
          <div className="h-5 w-32 bg-secondary rounded-md animate-pulse" />
        </div>
        <div className="grid grid-cols-3 sm:grid-cols-3 lg:grid-cols-6 gap-2.5 sm:gap-3">
          {Array.from({ length: 6 }).map((_, idx) => (
            <div
              key={idx}
              className="flex flex-col p-2 sm:p-2.5 rounded-md border border-border bg-card animate-pulse"
            >
              <div className="w-full aspect-square rounded-md bg-secondary mb-2" />
              <div className="h-4 w-3/4 mx-auto bg-secondary rounded-md" />
            </div>
          ))}
        </div>
      </section>
    );
  }

  // Display top parent categories or primary active categories
  const displayCategories = categories.slice(0, 6);

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg sm:text-xl font-bold tracking-tight text-foreground">
          Danh mục sản phẩm
        </h2>
        <Link
          to="/products"
          className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
        >
          Tất cả danh mục
          <ArrowRight className="h-4 w-4" />
        </Link>
      </div>

      <div className="grid grid-cols-3 sm:grid-cols-3 lg:grid-cols-6 gap-2.5 sm:gap-3">
        {displayCategories.map((cat) => {
          const imgUrl = (cat.slug && CATEGORY_IMAGES[cat.slug]) || DEFAULT_CATEGORY_IMG;

          return (
            <Link
              key={cat.id}
              to={`/products?categoryId=${cat.id}`}
              className="group flex flex-col p-2 sm:p-2.5 rounded-md border border-border bg-card text-center transition-colors hover:border-foreground shadow-none"
            >
              <div className="w-full aspect-square overflow-hidden rounded-md bg-secondary mb-1.5 sm:mb-2">
                <img
                  src={imgUrl}
                  alt={cat.name || 'Danh mục'}
                  className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
                  loading="lazy"
                />
              </div>
              <span className="block text-xs sm:text-[13px] font-semibold text-foreground truncate">
                {cat.name}
              </span>
              {cat.children && cat.children.length > 0 && (
                <span className="text-[10px] text-muted-foreground mt-0.5">
                  {cat.children.length} nhóm con
                </span>
              )}
            </Link>
          );
        })}
      </div>
    </section>
  );
};
