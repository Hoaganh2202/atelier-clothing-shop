import React from 'react';
import { ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { HeroBannerData } from '../mock/homepage.mock';

interface HeroBannerSectionProps {
  data: HeroBannerData;
  onPrimaryClick?: () => void;
  onSecondaryClick?: () => void;
}

export const HeroBannerSection: React.FC<HeroBannerSectionProps> = ({
  data,
  onPrimaryClick,
  onSecondaryClick,
}) => {
  return (
    <section className="w-full">
      <div className="grid grid-cols-1 lg:grid-cols-2 overflow-hidden rounded-md border border-border bg-secondary min-h-[340px] sm:min-h-[360px] lg:min-h-[380px]">
        <div className="flex flex-col justify-center items-start p-5 sm:p-6 lg:p-8 text-left">
          <span className="text-[11px] uppercase tracking-widest font-semibold text-muted-foreground mb-1.5">
            {data.eyebrow}
          </span>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground leading-snug mb-2">
            {data.title}
          </h1>
          <p className="text-xs sm:text-sm text-muted-foreground max-w-[440px] leading-relaxed mb-4">
            {data.description}
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="default"
              size="default"
              className="h-9 px-4 rounded-md font-medium text-xs"
              onClick={onPrimaryClick}
            >
              {data.primaryCtaText}
            </Button>
            <Button
              variant="outline"
              size="default"
              className="h-9 px-4 rounded-md font-medium text-xs gap-1.5 border-foreground bg-card text-foreground hover:bg-accent"
              onClick={onSecondaryClick}
            >
              {data.secondaryCtaText}
              <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>

        <div className="relative w-full h-[240px] sm:h-[280px] lg:h-full min-h-[240px] bg-accent overflow-hidden">
          <img
            src={data.imageUrl}
            alt={data.title}
            className="w-full h-full object-cover"
            loading="eager"
          />
        </div>
      </div>
    </section>
  );
};
