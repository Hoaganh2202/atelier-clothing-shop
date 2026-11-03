import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ProductItem, SeasonalPromoData } from '../mock/homepage.mock';

interface SpecialShowcaseSectionProps {
  popularProducts: ProductItem[];
  seasonalPromo: SeasonalPromoData;
  onViewAllPopular?: () => void;
  onSeasonalCtaClick?: () => void;
}

export const SpecialShowcaseSection: React.FC<SpecialShowcaseSectionProps> = ({
  popularProducts,
  seasonalPromo,
  onViewAllPopular,
  onSeasonalCtaClick,
}) => {
  const formatPrice = (price: number): string => {
    return new Intl.NumberFormat('vi-VN').format(price) + ' đ';
  };

  return (
    <section className="w-full">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 sm:gap-5 items-stretch">
        <div className="lg:col-span-8 flex flex-col justify-between rounded-md border border-border bg-card p-4 sm:p-5">
          <div>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-lg sm:text-xl font-bold tracking-tight text-foreground">
                  Khách hàng yêu thích
                </h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Những thiết kế được mua lại nhiều nhất
                </p>
              </div>
              <button
                type="button"
                onClick={onViewAllPopular}
                className="inline-flex items-center gap-1 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
              >
                Xem tất cả
                <ArrowRight className="h-4 w-4" />
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 sm:gap-3">
              {popularProducts.map((item) => (
                <Link
                  key={item.id}
                  to="/products"
                  className="flex flex-col group cursor-pointer"
                >
                  <div className="w-full aspect-square overflow-hidden rounded-md bg-accent/40 mb-2 border border-border">
                    <img
                      src={item.imageUrl}
                      alt={item.name}
                      className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
                      loading="lazy"
                    />
                  </div>
                  <span className="text-[13px] font-semibold text-foreground line-clamp-1 group-hover:underline">
                    {item.name}
                  </span>
                  <span className="text-[13px] text-muted-foreground mt-0.5">
                    {formatPrice(item.price)}
                  </span>
                </Link>
              ))}
            </div>
          </div>
        </div>

        <div className="lg:col-span-4 flex flex-col justify-between rounded-md border border-border bg-accent/80 p-4 sm:p-5">
          <div>
            <span className="text-xs uppercase tracking-widest font-medium text-muted-foreground">
              {seasonalPromo.eyebrow}
            </span>
            <h3 className="text-lg sm:text-xl font-bold tracking-tight text-foreground mt-1 mb-2 leading-snug">
              {seasonalPromo.title}
            </h3>
            <p className="text-xs text-muted-foreground leading-relaxed mb-4">
              {seasonalPromo.description}
            </p>
            <Button
              variant="default"
              size="default"
              className="h-8.5 px-4 rounded-md font-medium text-xs mb-3"
              onClick={onSeasonalCtaClick}
            >
              {seasonalPromo.ctaText}
            </Button>
          </div>

          <div className="w-full h-36 sm:h-40 rounded-md overflow-hidden bg-card border border-border">
            <img
              src={seasonalPromo.imageUrl}
              alt={seasonalPromo.title}
              className="w-full h-full object-cover"
              loading="lazy"
            />
          </div>
        </div>
      </div>
    </section>
  );
};
