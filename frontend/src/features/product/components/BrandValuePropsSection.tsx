import React from 'react';
import { Truck, RotateCcw, ShieldCheck } from 'lucide-react';
import { ValuePropItem } from '../mock/homepage.mock';

interface BrandValuePropsSectionProps {
  items: ValuePropItem[];
}

export const BrandValuePropsSection: React.FC<BrandValuePropsSectionProps> = ({
  items,
}) => {
  const renderIcon = (iconName: string) => {
    switch (iconName) {
      case 'truck':
        return <Truck className="h-5 w-5 text-foreground shrink-0" />;
      case 'refresh-cw':
        return <RotateCcw className="h-5 w-5 text-foreground shrink-0" />;
      case 'shield-check':
      default:
        return <ShieldCheck className="h-5 w-5 text-foreground shrink-0" />;
    }
  };

  return (
    <section className="w-full border-t border-border pt-5 sm:pt-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-5">
        {items.map((item, index) => (
          <div key={index} className="flex items-start gap-3">
            {renderIcon(item.icon)}
            <div>
              <h4 className="text-sm font-semibold text-foreground">
                {item.title}
              </h4>
              <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                {item.description}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
};
