import React from 'react';
import { ArrowRight, Tag } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PromoBanner } from '../mock/homepage.mock';

interface PromoBannerSectionProps {
  banner: PromoBanner;
  onCtaClick?: () => void;
}

export const PromoBannerSection: React.FC<PromoBannerSectionProps> = ({
  banner,
  onCtaClick,
}) => {
  return (
    <section className="rounded-md border border-neutral-800 bg-neutral-900 p-6 text-white sm:p-8">
      <div className="flex flex-col items-start justify-between gap-6 sm:flex-row sm:items-center">
        <div className="max-w-xl space-y-2">
          <div className="inline-flex items-center gap-2 rounded-full bg-neutral-800 px-3 py-1 text-xs font-medium text-neutral-300">
            <Tag className="h-3.5 w-3.5" />
            Mã ưu đãi: {banner.code}
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-white">
            {banner.title}
          </h2>
          <p className="text-sm text-neutral-400">
            {banner.subtitle} Áp dụng đến hết ngày {banner.validUntil}.
          </p>
        </div>

        <Button
          variant="secondary"
          size="default"
          className="gap-2 self-start font-medium text-xs sm:self-auto"
          onClick={onCtaClick}
        >
          Xem ưu đãi
          <ArrowRight className="h-4 w-4" />
        </Button>
      </div>
    </section>
  );
};
