import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { ShoppingCart } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { formatCurrencyVND } from '@/lib/format';
import type { ProductCardResponse } from '../api/product.api';

interface ProductCardProps {
  product: ProductCardResponse;
  onAddToCart?: (product: ProductCardResponse) => void;
}

export const FALLBACK_PLACEHOLDER =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="500" viewBox="0 0 400 500" fill="%23F5F0EA"><rect width="400" height="500" fill="%23F5F0EA"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="sans-serif" font-size="16" fill="%236E675F">ATELIER</text></svg>';

export const ProductCard: React.FC<ProductCardProps> = ({ product, onAddToCart }) => {
  const [imgSrc, setImgSrc] = useState<string>(product.thumbnailUrl || FALLBACK_PLACEHOLDER);

  const isOutOfStock = product.inStock === false;

  const displayPrice = () => {
    if (product.minPrice == null) {
      return 'Liên hệ';
    }
    if (product.maxPrice != null && product.maxPrice !== product.minPrice) {
      return `Từ ${formatCurrencyVND(product.minPrice)}`;
    }
    return formatCurrencyVND(product.minPrice);
  };

  return (
    <div className="group flex flex-col justify-between rounded-md border border-border bg-card p-2 sm:p-2.5 transition-colors hover:border-foreground shadow-none">
      <div className="flex flex-col">
        <Link
          to={`/products/${product.slug}`}
          className="relative w-full aspect-square overflow-hidden rounded-md bg-secondary mb-2 block"
        >
          <img
            src={imgSrc}
            alt={product.name || 'Sản phẩm'}
            onError={() => setImgSrc(FALLBACK_PLACEHOLDER)}
            className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
            loading="lazy"
          />
          {isOutOfStock ? (
            <span className="absolute top-2 left-2 rounded-md border border-border bg-card px-2 py-0.5 text-xs font-semibold text-destructive shadow-none">
              Hết hàng
            </span>
          ) : product.categoryName ? (
            <span className="absolute top-2 left-2 rounded-md border border-border bg-card/90 px-2 py-0.5 text-[11px] font-medium text-muted-foreground shadow-none">
              {product.categoryName}
            </span>
          ) : null}
        </Link>

        <Link to={`/products/${product.slug}`} className="focus:outline-none">
          <h3
            className="text-xs sm:text-[13px] font-medium text-foreground mb-1 line-clamp-2 min-h-[34px] sm:min-h-[36px] leading-snug hover:underline"
            title={product.name}
          >
            {product.name}
          </h3>
        </Link>

        <div className="flex items-baseline gap-2 mb-2">
          <span className="text-sm sm:text-[15px] font-bold text-foreground">
            {displayPrice()}
          </span>
        </div>
      </div>

      <Button
        variant="outline"
        size="default"
        disabled={isOutOfStock}
        className="w-full h-8 gap-1.5 rounded-md border-border bg-card text-foreground font-medium text-xs hover:bg-foreground hover:text-background transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-none"
        onClick={(e) => {
          e.preventDefault();
          if (!isOutOfStock) {
            onAddToCart?.(product);
          }
        }}
      >
        <ShoppingCart className="h-3.5 w-3.5" />
        {isOutOfStock ? 'Hết hàng' : 'Thêm vào giỏ'}
      </Button>
    </div>
  );
};
