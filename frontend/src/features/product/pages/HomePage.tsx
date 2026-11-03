import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { HeroBannerSection } from '../components/HeroBannerSection';
import { CategoryListSection } from '../components/CategoryListSection';
import { ProductCard } from '../components/ProductCard';
import { BrandValuePropsSection } from '../components/BrandValuePropsSection';
import { SpecialShowcaseSection } from '../components/SpecialShowcaseSection';
import { useCategories, useProducts } from '../hooks/useProducts';
import {
  MOCK_HERO_BANNER,
  MOCK_VALUE_PROPS,
  MOCK_POPULAR_MINI_PRODUCTS,
  MOCK_SEASONAL_PROMO,
} from '../mock/homepage.mock';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [cartCount, setCartCount] = useState<number>(0);
  const [searchQuery, setSearchQuery] = useState<string>('');

  useEffect(() => {
    document.title = 'ATELIER | Thời trang tối giản';
  }, []);

  // Real API: Active category tree
  const { data: categories, isLoading: isCategoriesLoading } = useCategories();

  // Real API: New arrivals (8 newest active products)
  const { data: productsData, isLoading: isProductsLoading } = useProducts({
    sort: 'newest',
    size: 8,
  });

  const newestProducts = productsData?.content || [];

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header
        cartCount={cartCount}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        activeNav="trang-chu"
        onNavClick={(navId) => {
          if (navId === 'san-pham' || navId === 'danh-muc') {
            navigate('/products');
          }
        }}
        onCartClick={() => {
          // TODO: Open cart drawer once cart module is implemented
        }}
      />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6 space-y-7 sm:space-y-8">
        <HeroBannerSection
          data={MOCK_HERO_BANNER}
          onPrimaryClick={() => {
            const section = document.getElementById('san-pham-moi');
            section?.scrollIntoView({ behavior: 'smooth' });
          }}
          onSecondaryClick={() => navigate('/products')}
        />

        <CategoryListSection
          categories={categories}
          isLoading={isCategoriesLoading}
        />

        <section id="san-pham-moi" className="space-y-3 sm:space-y-3.5">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg sm:text-xl font-bold tracking-tight text-foreground">
                Sản phẩm mới
              </h2>
              <p className="text-xs text-muted-foreground mt-1">
                Thiết kế mới nhất cho tủ đồ tối giản hằng ngày
              </p>
            </div>
            <button
              type="button"
              onClick={() => navigate('/products?sort=newest')}
              className="text-xs font-medium text-foreground underline underline-offset-4 hover:text-muted-foreground"
            >
              Xem tất cả
            </button>
          </div>

          {isProductsLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 sm:gap-3.5">
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
          ) : newestProducts.length === 0 ? (
            <div className="rounded-md border border-border p-8 text-center bg-card">
              <p className="text-sm text-muted-foreground">
                Hiện chưa có sản phẩm mới nào.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 sm:gap-3.5">
              {newestProducts.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onAddToCart={() => {
                    setCartCount((prev) => prev + 1);
                  }}
                />
              ))}
            </div>
          )}

          <BrandValuePropsSection items={MOCK_VALUE_PROPS} />
        </section>

        <SpecialShowcaseSection
          popularProducts={MOCK_POPULAR_MINI_PRODUCTS}
          seasonalPromo={MOCK_SEASONAL_PROMO}
          onViewAllPopular={() => navigate('/products')}
          onSeasonalCtaClick={() => navigate('/products')}
        />
      </main>

      <Footer />
    </div>
  );
};
