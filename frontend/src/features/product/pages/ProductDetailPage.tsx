import React, { useEffect, useState, useMemo } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  ChevronRight,
  Minus,
  Plus,
  ShoppingCart,
  Check,
  AlertCircle,
  ArrowLeft,
  Truck,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { ProductCard, FALLBACK_PLACEHOLDER } from '../components/ProductCard';
import { useProduct } from '../hooks/useProducts';
import { formatCurrencyVND } from '@/lib/format';
import { useCartStore } from '@/features/cart/cart.store';
import { ProductReviews } from '@/features/review/components/ProductReviews';
import type {
  ProductVariantPublicResponse,
  ProductImageResponse,
} from '../api/product.api';

export const ProductDetailPage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();

  const { data: product, isLoading, isError, error } = useProduct(slug);

  // Gallery state
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [mainImageFailed, setMainImageFailed] = useState(false);
  const [failedThumbnailIndices, setFailedThumbnailIndices] = useState<Set<number>>(new Set());

  // Variant selection state
  const [selectedColor, setSelectedColor] = useState<string | null>(null);
  const [selectedSize, setSelectedSize] = useState<string | null>(null);
  const [quantity, setQuantity] = useState<number>(1);
  const [quantityInput, setQuantityInput] = useState<string>('1');
  const [cartNotification, setCartNotification] = useState<{
    message: string;
    type: 'success' | 'error';
    actionLink?: string;
    actionText?: string;
  } | null>(null);
  const addItem = useCartStore((state) => state.addItem);

  // Update document title and reset selections on slug change
  useEffect(() => {
    if (product?.name) {
      document.title = `${product.name} | ATELIER`;
    } else if (isError) {
      document.title = 'Không tìm thấy sản phẩm | ATELIER';
    }
    setActiveImageIndex(0);
    setMainImageFailed(false);
    setFailedThumbnailIndices(new Set());
    setQuantity(1);
    setQuantityInput('1');
    setSelectedColor(null);
    setSelectedSize(null);
  }, [product, isError, slug]);

  const handleSelectImage = (index: number) => {
    setActiveImageIndex(index);
    setMainImageFailed(false);
  };

  const images: ProductImageResponse[] = useMemo(() => {
    return product?.images || [];
  }, [product]);

  const variants: ProductVariantPublicResponse[] = useMemo(() => {
    return product?.variants || [];
  }, [product]);

  // Extract distinct colors and sizes
  const availableColors = useMemo(() => {
    const set = new Set<string>();
    variants.forEach((v) => {
      if (v.color) set.add(v.color);
    });
    return Array.from(set);
  }, [variants]);

  // Only auto-preselect variant if there is exactly 1 variant
  useEffect(() => {
    if (variants.length === 1) {
      if (variants[0].color) setSelectedColor(variants[0].color);
      if (variants[0].size) setSelectedSize(variants[0].size);
    }
  }, [variants]);

  // Available sizes for currently selected color
  const availableSizesForColor = useMemo(() => {
    if (!selectedColor) {
      const allSizes = new Set<string>();
      variants.forEach((v) => {
        if (v.size) allSizes.add(v.size);
      });
      return Array.from(allSizes);
    }
    return variants
      .filter((v) => v.color === selectedColor)
      .map((v) => v.size!)
      .filter(Boolean);
  }, [variants, selectedColor]);

  // Handle color change: reset size if not applicable
  const handleSelectColor = (color: string) => {
    setSelectedColor(color);
    const validSizes = variants
      .filter((v) => v.color === color)
      .map((v) => v.size!)
      .filter(Boolean);
    if (selectedSize && !validSizes.includes(selectedSize)) {
      setSelectedSize(null);
    }
  };

  // Matched variant
  const selectedVariant = useMemo(() => {
    if (!selectedColor || !selectedSize) return null;
    return (
      variants.find(
        (v) => v.color === selectedColor && v.size === selectedSize
      ) || null
    );
  }, [variants, selectedColor, selectedSize]);

  // Stock status logic
  const variantStock = selectedVariant?.stock ?? 0;
  const isOutOfStock = selectedVariant ? variantStock === 0 : product?.inStock === false;
  const maxAllowedQuantity = selectedVariant ? Math.max(1, variantStock) : 99;

  // Adjust quantity when switching to a variant with lower stock
  useEffect(() => {
    if (selectedVariant && variantStock > 0 && quantity > variantStock) {
      setQuantity(variantStock);
      setQuantityInput(String(variantStock));
    }
  }, [selectedVariant, variantStock, quantity]);

  // Quantity input handlers
  const handleQuantityInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    if (raw === '') {
      setQuantityInput('');
      return;
    }
    const digitsOnly = raw.replace(/\D/g, '');
    if (digitsOnly === '') {
      setQuantityInput('');
      return;
    }
    const parsed = parseInt(digitsOnly, 10);
    if (parsed < 1) {
      setQuantity(1);
      setQuantityInput('1');
    } else if (variantStock > 0 && parsed > maxAllowedQuantity) {
      setQuantity(maxAllowedQuantity);
      setQuantityInput(String(maxAllowedQuantity));
    } else {
      setQuantity(parsed);
      setQuantityInput(String(parsed));
    }
  };

  const handleQuantityInputBlur = () => {
    if (quantityInput === '' || parseInt(quantityInput, 10) < 1) {
      setQuantity(1);
      setQuantityInput('1');
    } else {
      const parsed = parseInt(quantityInput, 10);
      const clamped = variantStock > 0 ? Math.min(parsed, maxAllowedQuantity) : Math.max(1, parsed);
      setQuantity(clamped);
      setQuantityInput(String(clamped));
    }
  };

  const handleDecreaseQuantity = () => {
    setQuantity((prev) => {
      const next = Math.max(1, prev - 1);
      setQuantityInput(String(next));
      return next;
    });
  };

  const handleIncreaseQuantity = () => {
    setQuantity((prev) => {
      const limit = selectedVariant ? variantStock : 99;
      const next = limit > 0 ? Math.min(limit, prev + 1) : prev + 1;
      setQuantityInput(String(next));
      return next;
    });
  };

  const handleAddToCart = () => {
    setCartNotification(null);
    if (!product) return;
    if (typeof window !== 'undefined' && Boolean(sessionStorage.getItem('pending_checkout_snapshot'))) {
      setCartNotification({
        type: 'error',
        message: 'Bạn đang có một đơn hàng chờ xác nhận kết quả thanh toán. Vui lòng đối soát đơn hàng trước khi thêm sản phẩm mới.',
        actionLink: '/checkout',
        actionText: 'Kiểm tra đơn hàng',
      });
      return;
    }
    if (!selectedVariant) {
      setCartNotification({
        type: 'error',
        message: 'Vui lòng chọn màu sắc và kích cỡ sản phẩm.',
      });
      return;
    }
    if (isOutOfStock) {
      setCartNotification({
        type: 'error',
        message: 'Sản phẩm này hiện đã hết hàng.',
      });
      return;
    }
    addItem(
      {
        variantId: selectedVariant.id!,
        productId: product.id!,
        productName: product.name || '',
        productSlug: product.slug || '',
        color: selectedVariant.color || '',
        size: selectedVariant.size || '',
        price: selectedVariant.effectivePrice ?? product.minPrice ?? 0,
        imageUrl: images[0]?.url,
        stock: variantStock,
      },
      quantity
    );
    setCartNotification({
      type: 'success',
      message: `Đã thêm ${quantity} sản phẩm vào giỏ hàng thành công!`,
    });
  };

  const handleBuyNow = () => {
    setCartNotification(null);
    if (!product) return;
    if (typeof window !== 'undefined' && Boolean(sessionStorage.getItem('pending_checkout_snapshot'))) {
      setCartNotification({
        type: 'error',
        message: 'Bạn đang có một đơn hàng chờ xác nhận kết quả thanh toán. Vui lòng đối soát đơn hàng trước khi mua thêm sản phẩm.',
        actionLink: '/checkout',
        actionText: 'Kiểm tra đơn hàng',
      });
      return;
    }
    if (!selectedVariant) {
      setCartNotification({
        type: 'error',
        message: 'Vui lòng chọn màu sắc và kích cỡ sản phẩm trước khi mua.',
      });
      return;
    }
    if (isOutOfStock) {
      setCartNotification({
        type: 'error',
        message: 'Sản phẩm này hiện đã hết hàng.',
      });
      return;
    }
    addItem(
      {
        variantId: selectedVariant.id!,
        productId: product.id!,
        productName: product.name || '',
        productSlug: product.slug || '',
        color: selectedVariant.color || '',
        size: selectedVariant.size || '',
        price: selectedVariant.effectivePrice ?? product.minPrice ?? 0,
        imageUrl: images[0]?.url,
        stock: variantStock,
      },
      quantity
    );
    navigate('/checkout');
  };

  // Breadcrumb items
  const breadcrumbCategory = product?.category;
  const parentCategory = breadcrumbCategory?.parent;

  // Active Image URL
  const activeImageUrl =
    images.length > 0 && images[activeImageIndex]?.url
      ? images[activeImageIndex].url
      : FALLBACK_PLACEHOLDER;

  // 404 / Error State
  if (isError) {
    const is404 = (error as any)?.response?.status === 404;
    return (
      <div className="flex min-h-screen flex-col bg-background text-foreground">
        <Header activeNav="san-pham" />
        <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-16 flex flex-col items-center justify-center text-center">
          <div className="rounded-md border border-border bg-card p-8 sm:p-12 max-w-md w-full space-y-4">
            <AlertCircle className="h-10 w-10 text-muted-foreground mx-auto" />
            <h1 className="text-xl font-bold text-foreground">
              {is404
                ? 'Sản phẩm không tồn tại hoặc đã ngừng kinh doanh'
                : 'Đã xảy ra lỗi khi tải sản phẩm'}
            </h1>
            <p className="text-xs text-muted-foreground leading-relaxed">
              {is404
                ? 'Sản phẩm bạn đang tìm kiếm có thể đã bị xóa hoặc đổi địa chỉ liên kết.'
                : 'Vui lòng kiểm tra lại kết nối mạng hoặc thử lại sau.'}
            </p>
            <div className="pt-2 flex flex-col sm:flex-row gap-2 justify-center">
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/products')}
                className="rounded-md border-border text-xs shadow-none"
              >
                <ArrowLeft className="h-3.5 w-3.5 mr-1" />
                Về danh mục sản phẩm
              </Button>
              <Button
                variant="default"
                size="sm"
                onClick={() => navigate('/')}
                className="rounded-md text-xs shadow-none"
              >
                Về trang chủ
              </Button>
            </div>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  // Loading Skeleton State
  if (isLoading || !product) {
    return (
      <div className="flex min-h-screen flex-col bg-background text-foreground">
        <Header activeNav="san-pham" />
        <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
          <div className="h-4 w-56 bg-secondary rounded-md animate-pulse mb-4" />
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-start">
            <div className="lg:col-span-6 flex flex-col gap-3">
              <div className="w-full aspect-square max-h-[440px] bg-secondary rounded-md animate-pulse" />
              <div className="flex gap-2">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-14 w-12 bg-secondary rounded-md animate-pulse" />
                ))}
              </div>
            </div>
            <div className="lg:col-span-6 space-y-4">
              <div className="h-8 w-3/4 bg-secondary rounded-md animate-pulse" />
              <div className="h-6 w-1/3 bg-secondary rounded-md animate-pulse" />
              <div className="h-16 w-full bg-secondary rounded-md animate-pulse" />
              <div className="h-10 w-full bg-secondary rounded-md animate-pulse" />
            </div>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <Header activeNav="san-pham" />

      <main className="mx-auto flex-1 w-full max-w-[1280px] px-4 py-4 sm:px-6">
        {/* Breadcrumb Hierarchy */}
        <nav className="flex flex-wrap items-center gap-1.5 sm:gap-2 text-xs text-muted-foreground mb-3 sm:mb-4">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="h-3 w-3" />
          <Link to="/products" className="hover:text-foreground transition-colors">
            Sản phẩm
          </Link>
          {parentCategory && (
            <>
              <ChevronRight className="h-3 w-3" />
              <Link
                to={`/products?categoryId=${parentCategory.id}`}
                className="hover:text-foreground transition-colors"
              >
                {parentCategory.name}
              </Link>
            </>
          )}
          {breadcrumbCategory && (
            <>
              <ChevronRight className="h-3 w-3" />
              <Link
                to={`/products?categoryId=${breadcrumbCategory.id}`}
                className="hover:text-foreground transition-colors"
              >
                {breadcrumbCategory.name}
              </Link>
            </>
          )}
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium truncate max-w-[200px] sm:max-w-none">
            {product.name}
          </span>
        </nav>

        {/* Product Main Section */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-start mb-8 sm:mb-10">
          {/* Gallery View (7 Columns on LG) */}
          <div className="lg:col-span-6 flex flex-col gap-3">
            {/* Primary Large Image */}
            <div className="relative w-full aspect-square sm:aspect-[4/5] max-h-[440px] sm:max-h-[460px] overflow-hidden rounded-md border border-border bg-card flex items-center justify-center">
              <img
                src={mainImageFailed ? FALLBACK_PLACEHOLDER : activeImageUrl}
                alt={product.name || 'Ảnh sản phẩm'}
                className="h-full w-full object-contain sm:object-cover"
                onError={() => setMainImageFailed(true)}
              />
              {isOutOfStock && (
                <div className="absolute top-3 left-3 rounded-md bg-card border border-border px-3 py-1 text-xs font-semibold text-destructive">
                  Hết hàng
                </div>
              )}
            </div>

            {/* Thumbnail Navigation */}
            {images.length > 1 && (
              <div className="flex items-center gap-2 overflow-x-auto pb-1.5">
                {images.map((img, index) => {
                  const isActive = index === activeImageIndex;
                  return (
                    <button
                      key={img.id || index}
                      type="button"
                      onClick={() => handleSelectImage(index)}
                      className={`relative h-14 w-12 sm:h-16 sm:w-14 shrink-0 overflow-hidden rounded-md border transition-all ${
                        isActive
                          ? 'border-foreground ring-1 ring-foreground'
                          : 'border-border opacity-70 hover:opacity-100 hover:border-foreground'
                      }`}
                    >
                      <img
                        src={failedThumbnailIndices.has(index) ? FALLBACK_PLACEHOLDER : (img.url || FALLBACK_PLACEHOLDER)}
                        alt={img.alt || `Ảnh ${index + 1}`}
                        className="h-full w-full object-contain sm:object-cover"
                        onError={() => setFailedThumbnailIndices((prev) => new Set(prev).add(index))}
                      />
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Product Info & Actions (5 Columns on LG) */}
          <div className="lg:col-span-6 space-y-3.5 sm:space-y-4">
            <div>
              {breadcrumbCategory && (
                <span className="text-xs uppercase tracking-wider font-medium text-muted-foreground block mb-1">
                  {breadcrumbCategory.name}
                </span>
              )}
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground leading-snug">
                {product.name}
              </h1>

              {/* Price display */}
              <div className="mt-2 flex items-baseline gap-2.5">
                <span className="text-xl sm:text-2xl font-bold text-foreground">
                  {selectedVariant?.effectivePrice != null
                    ? formatCurrencyVND(selectedVariant.effectivePrice)
                    : product.minPrice != null &&
                      product.maxPrice != null &&
                      product.minPrice !== product.maxPrice
                    ? `${formatCurrencyVND(product.minPrice)} – ${formatCurrencyVND(product.maxPrice)}`
                    : product.minPrice != null
                    ? formatCurrencyVND(product.minPrice)
                    : 'Liên hệ'}
                </span>
                {selectedVariant &&
                  product.minPrice != null &&
                  product.maxPrice != null &&
                  product.minPrice !== product.maxPrice && (
                    <span className="text-xs text-muted-foreground">
                      (Khoảng giá: {formatCurrencyVND(product.minPrice)} – {formatCurrencyVND(product.maxPrice)})
                    </span>
                  )}
              </div>
            </div>

            <div className="h-px bg-border" />

            {/* Color Selection */}
            {availableColors.length > 0 && (
              <div className="space-y-2">
                <label className="text-xs font-semibold text-foreground flex items-center justify-between">
                  <span>Màu sắc</span>
                  <span className="text-muted-foreground font-normal">
                    {selectedColor || 'Chưa chọn'}
                  </span>
                </label>
                <div className="flex flex-wrap gap-2">
                  {availableColors.map((color) => {
                    const isSelected = selectedColor === color;
                    return (
                      <button
                        key={color}
                        type="button"
                        onClick={() => handleSelectColor(color)}
                        className={`h-8 px-2.5 rounded-md border text-xs font-medium transition-colors flex items-center gap-1.5 ${
                          isSelected
                            ? 'border-foreground bg-foreground text-background font-semibold'
                            : 'border-border bg-card text-foreground hover:border-foreground'
                        }`}
                      >
                        {isSelected && <Check className="h-3 w-3" />}
                        {color}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Size Selection */}
            {availableSizesForColor.length > 0 && (
              <div className="space-y-2">
                <label className="text-xs font-semibold text-foreground flex items-center justify-between">
                  <span>Kích cỡ</span>
                  <span className="text-muted-foreground font-normal">
                    {selectedSize || 'Chưa chọn'}
                  </span>
                </label>
                <div className="flex flex-wrap gap-2">
                  {availableSizesForColor.map((size) => {
                    const isSelected = selectedSize === size;
                    // Check if this variant has 0 stock
                    const v = variants.find(
                      (item) => item.color === selectedColor && item.size === size
                    );
                    const isSizeOutOfStock = v ? v.stock === 0 : false;

                    return (
                      <button
                        key={size}
                        type="button"
                        disabled={isSizeOutOfStock}
                        onClick={() => setSelectedSize(size)}
                        className={`min-w-8 h-8 px-2.5 rounded-md border text-xs font-medium transition-colors ${
                          isSelected
                            ? 'border-foreground bg-foreground text-background font-semibold'
                            : isSizeOutOfStock
                            ? 'border-border/50 bg-secondary/50 text-muted-foreground line-through cursor-not-allowed opacity-50'
                            : 'border-border bg-card text-foreground hover:border-foreground'
                        }`}
                      >
                        {size}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Stock status indicator */}
            <div className="text-xs font-medium">
              {selectedVariant ? (
                variantStock === 0 ? (
                  <span className="text-destructive font-semibold">
                    Hết hàng
                  </span>
                ) : variantStock < 5 ? (
                  <span className="text-amber-600 font-semibold">
                    Chỉ còn {variantStock} sản phẩm trong kho
                  </span>
                ) : (
                  <span className="text-emerald-600 font-semibold flex items-center gap-1">
                    <Check className="h-3.5 w-3.5" />
                    Còn hàng ({variantStock} sản phẩm)
                  </span>
                )
              ) : (
                <span className="text-muted-foreground">
                  Vui lòng chọn màu sắc và kích cỡ
                </span>
              )}
            </div>

            {/* Quantity Selector */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-foreground">
                Số lượng
              </label>
              <div className="flex items-center gap-3">
                <div className="flex items-center border border-border rounded-md bg-card">
                  <button
                    type="button"
                    disabled={quantity <= 1 || isOutOfStock}
                    onClick={handleDecreaseQuantity}
                    className="h-8 w-8 flex items-center justify-center text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    aria-label="Giảm số lượng"
                  >
                    <Minus className="h-3.5 w-3.5" />
                  </button>
                  <input
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    value={quantityInput}
                    disabled={isOutOfStock}
                    onChange={handleQuantityInputChange}
                    onBlur={handleQuantityInputBlur}
                    className="w-10 text-center text-xs font-semibold text-foreground bg-transparent border-0 focus:outline-none focus:ring-0 select-all"
                    aria-label="Số lượng"
                  />
                  <button
                    type="button"
                    disabled={isOutOfStock || (selectedVariant != null && quantity >= variantStock)}
                    onClick={handleIncreaseQuantity}
                    className="h-8 w-8 flex items-center justify-center text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    aria-label="Tăng số lượng"
                  >
                    <Plus className="h-3.5 w-3.5" />
                  </button>
                </div>
                {selectedVariant && variantStock > 0 && (
                  <span className="text-xs text-muted-foreground">
                    Tối đa {variantStock} sản phẩm
                  </span>
                )}
              </div>
            </div>

            {cartNotification && (
              <div
                className={`p-3 rounded-md text-xs border flex items-center justify-between gap-2 ${
                  cartNotification.type === 'success'
                    ? 'bg-green-50 text-green-700 border-green-200'
                    : 'bg-destructive/10 text-destructive border-destructive/20'
                }`}
              >
                <span>{cartNotification.message}</span>
                {cartNotification.actionLink ? (
                  <Link
                    to={cartNotification.actionLink}
                    className="font-semibold underline shrink-0 hover:opacity-80"
                  >
                    {cartNotification.actionText || 'Xem chi tiết'}
                  </Link>
                ) : cartNotification.type === 'success' ? (
                  <Link
                    to="/cart"
                    className="font-semibold underline shrink-0 hover:text-green-800"
                  >
                    Xem giỏ hàng
                  </Link>
                ) : null}
              </div>
            )}

            {/* Action Buttons */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-1 sm:pt-1.5">
              <Button
                type="button"
                variant="outline"
                size="default"
                disabled={isOutOfStock}
                onClick={handleAddToCart}
                className="w-full h-10 gap-1.5 rounded-md font-semibold text-xs sm:text-sm border-border shadow-none"
              >
                <ShoppingCart className="h-4 w-4" />
                {isOutOfStock ? 'Sản phẩm hết hàng' : 'Thêm vào giỏ hàng'}
              </Button>
              <Button
                type="button"
                variant="default"
                size="default"
                disabled={isOutOfStock}
                onClick={handleBuyNow}
                className="w-full h-10 gap-1.5 rounded-md font-semibold text-xs sm:text-sm shadow-none"
              >
                Mua ngay
              </Button>
            </div>

            {/* Value Guarantees */}
            <div className="rounded-md border border-border bg-card p-3 space-y-2 text-xs text-muted-foreground">
              <div className="flex items-center gap-2.5">
                <Truck className="h-3.5 w-3.5 text-foreground shrink-0" />
                <span>Giao hàng tiêu chuẩn toàn quốc từ 2 - 4 ngày làm việc</span>
              </div>
              <div className="flex items-center gap-2.5">
                <RefreshCw className="h-3.5 w-3.5 text-foreground shrink-0" />
                <span>Đổi trả sản phẩm miễn phí trong vòng 30 ngày</span>
              </div>
              <div className="flex items-center gap-2.5">
                <ShieldCheck className="h-3.5 w-3.5 text-foreground shrink-0" />
                <span>Cam kết 100% chất lượng từ sợi tự nhiên tuyển chọn</span>
              </div>
            </div>

            {/* Description */}
            {product.description && (
              <div className="space-y-2 pt-2">
                <h4 className="text-sm font-semibold text-foreground">
                  Mô tả sản phẩm
                </h4>
                <p className="text-xs text-muted-foreground leading-relaxed whitespace-pre-line">
                  {product.description}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Product Reviews Section */}
        {product?.id && (
          <ProductReviews productId={product.id} />
        )}

        {/* Related Products Section */}
        {product.relatedProducts && product.relatedProducts.length > 0 && (
          <section className="space-y-4 pt-8 sm:pt-10 border-t border-border">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-xl sm:text-2xl font-semibold tracking-tight text-foreground">
                  Sản phẩm tương tự
                </h2>
                <p className="text-xs text-muted-foreground mt-1">
                  Khám phá thêm các thiết kế cùng phân loại
                </p>
              </div>
              {breadcrumbCategory && (
                <Link
                  to={`/products?categoryId=${breadcrumbCategory.id}`}
                  className="text-xs font-medium text-foreground underline underline-offset-4 hover:text-muted-foreground"
                >
                  Xem tất cả
                </Link>
              )}
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 sm:gap-3.5">
              {product.relatedProducts.map((rel) => (
                <ProductCard key={rel.id} product={rel} />
              ))}
            </div>
          </section>
        )}
      </main>

      <Footer />
    </div>
  );
};
