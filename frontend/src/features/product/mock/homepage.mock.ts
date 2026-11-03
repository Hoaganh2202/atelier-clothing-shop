export interface HeroBannerData {
  eyebrow: string;
  title: string;
  description: string;
  primaryCtaText: string;
  secondaryCtaText: string;
  imageUrl: string;
}

export interface ValuePropItem {
  icon: string;
  title: string;
  description: string;
}

export interface SeasonalPromoData {
  eyebrow: string;
  title: string;
  description: string;
  ctaText: string;
  imageUrl: string;
}

export interface PromoBanner {
  title: string;
  subtitle: string;
  code: string;
  validUntil: string;
}

export interface ProductItem {
  id: number;
  name: string;
  slug: string;
  categoryName: string;
  price: number;
  originalPrice?: number;
  badge?: string;
  imageUrl: string;
}

export const MOCK_PROMO_BANNER: PromoBanner = {
  title: 'Giảm giá giữa mùa',
  subtitle: 'Giảm 30% cho áo khoác và quần dài khi nhập mã MID30.',
  code: 'MID30',
  validUntil: '31/10/2026',
};

export const MOCK_HERO_BANNER: HeroBannerData = {
  eyebrow: 'Bộ sưu tập thường nhật 2026',
  title: 'Thời trang tối giản. Thiết kế cho đời sống thực tế.',
  description:
    'Chất liệu sợi tự nhiên thoáng nhẹ cùng phom dáng chuẩn mực, bền bỉ theo thời gian cho phong cách sống tối giản.',
  primaryCtaText: 'Mua ngay',
  secondaryCtaText: 'Xem bộ sưu tập mới',
  imageUrl:
    'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?q=80&w=1200&auto=format&fit=crop',
};

export const MOCK_VALUE_PROPS: ValuePropItem[] = [
  {
    icon: 'truck',
    title: 'Giao hàng miễn phí',
    description: 'Áp dụng cho mọi đơn hàng tiêu chuẩn từ 500.000đ trên toàn quốc.',
  },
  {
    icon: 'refresh-cw',
    title: 'Đổi trả trong 30 ngày',
    description: 'Hỗ trợ đổi kích cỡ hoặc hoàn trả nguyên giá trị khi sản phẩm còn nguyên tem.',
  },
  {
    icon: 'shield-check',
    title: 'Chất liệu tuyển chọn',
    description: 'Sợi dệt tự nhiên, mật độ sợi tiêu chuẩn và kiểm định độ bền đường may.',
  },
];

export const MOCK_POPULAR_MINI_PRODUCTS: ProductItem[] = [
  {
    id: 301,
    name: 'Áo thun Heavyweight',
    slug: 'ao-thun-heavyweight',
    categoryName: 'Áo thun',
    price: 350000,
    imageUrl:
      'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?q=80&w=600&auto=format&fit=crop',
  },
  {
    id: 302,
    name: 'Quần jean ống suông',
    slug: 'quan-jean-ong-suong',
    categoryName: 'Quần jean',
    price: 590000,
    imageUrl:
      'https://images.unsplash.com/photo-1542272604-787c3835535d?q=80&w=600&auto=format&fit=crop',
  },
  {
    id: 303,
    name: 'Áo sơ mi Oxford',
    slug: 'ao-so-mi-oxford',
    categoryName: 'Áo sơ mi',
    price: 450000,
    imageUrl:
      'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?q=80&w=600&auto=format&fit=crop',
  },
  {
    id: 304,
    name: 'Áo khoác Blazer',
    slug: 'ao-khoac-blazer',
    categoryName: 'Áo khoác',
    price: 890000,
    imageUrl:
      'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?q=80&w=600&auto=format&fit=crop',
  },
];

export const MOCK_SEASONAL_PROMO: SeasonalPromoData = {
  eyebrow: 'Đặc quyền giới hạn',
  title: 'Ưu đãi theo mùa: Giảm 20% nhóm áo khoác và sơ mi',
  description:
    'Áp dụng tự động tại bước thanh toán khi chọn từ 2 sản phẩm. Hết hạn 30/11/2026.',
  ctaText: 'Khám phá ngay',
  imageUrl:
    'https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=800&auto=format&fit=crop',
};
