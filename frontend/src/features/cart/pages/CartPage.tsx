import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ShoppingBag,
  Trash2,
  ArrowLeft,
  ArrowRight,
  Minus,
  Plus,
  ShieldCheck,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatCurrencyVND } from '@/lib/format';
import { useCartStore } from '../cart.store';
import { useShippingPolicy } from '../hooks/useShippingPolicy';

export const CartPage: React.FC = () => {
  const navigate = useNavigate();
  const {
    items,
    updateQuantity,
    removeItem,
    clearCart,
    getTotalItems,
    getEstimatedSubtotal,
    getEstimatedShippingFee,
    getEstimatedTotal,
  } = useCartStore();

  const totalItems = getTotalItems();
  const estimatedSubtotal = getEstimatedSubtotal();
  const estimatedShippingFee = getEstimatedShippingFee();
  const estimatedTotal = getEstimatedTotal();

  const [hasPendingCheckout, setHasPendingCheckout] = useState(false);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const rawSnapshot = sessionStorage.getItem('pending_checkout_snapshot');
      setHasPendingCheckout(Boolean(rawSnapshot));
    }
  }, []);

  const { data: shippingPolicy, isLoading: isPolicyLoading, isError: isPolicyError } = useShippingPolicy();
  const hasPolicy = Boolean(shippingPolicy && !isPolicyLoading && !isPolicyError);
  const effectiveFreeShippingThreshold = shippingPolicy?.freeShippingThreshold ?? 0;
  const amountNeededForFreeShip = hasPolicy
    ? Math.max(0, effectiveFreeShippingThreshold - estimatedSubtotal)
    : 0;

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <Header cartCount={totalItems} />

      <main className="flex-1 mx-auto w-full max-w-[1280px] px-4 sm:px-6 py-8">
        {/* Breadcrumb */}
        <nav className="mb-6 flex items-center gap-2 text-xs text-muted-foreground">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <span>/</span>
          <span className="text-foreground font-medium">Giỏ hàng ({totalItems})</span>
        </nav>

        <h1 className="text-2xl font-bold tracking-tight text-foreground mb-8">
          Giỏ hàng của bạn
        </h1>

        {/* Warning banner when an indeterminate checkout attempt is pending */}
        {hasPendingCheckout && (
          <div className="mb-6 p-4 rounded-md border border-amber-300 bg-amber-50 text-amber-900 flex items-start gap-3">
            <AlertCircle className="h-5 w-5 shrink-0 text-amber-600 mt-0.5" />
            <div className="flex-1 space-y-1">
              <p className="font-semibold text-xs">
                Giỏ hàng đang tạm khóa để bảo toàn dữ liệu đối soát đơn hàng
              </p>
              <p className="text-xs text-amber-800 leading-relaxed">
                Giao dịch đặt hàng trước đó chưa nhận được phản hồi xác nhận từ máy chủ. Các thao tác thêm, sửa, xóa giỏ hàng tạm thời bị khóa nhằm ngăn ngừa sai lệch số lượng và trùng lặp đơn hàng. Vui lòng quay lại trang Thanh toán để đối soát kết quả đơn hàng đang chờ.
              </p>
            </div>
          </div>
        )}

        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 px-4 text-center border border-border rounded-md bg-card">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-secondary mb-4">
              <ShoppingBag className="h-8 w-8 text-muted-foreground" />
            </div>
            <h2 className="text-lg font-semibold text-foreground mb-2">
              Giỏ hàng của bạn đang trống
            </h2>
            <p className="text-sm text-muted-foreground max-w-sm mb-6">
              Bạn chưa có sản phẩm nào trong giỏ hàng. Hãy khám phá bộ sưu tập thời trang mới nhất của chúng tôi.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3">
              {hasPendingCheckout && (
                <Button
                  onClick={() => navigate('/checkout')}
                  className="h-10 px-6 gap-2 rounded-md font-medium text-sm shadow-none bg-amber-700 hover:bg-amber-800 text-white"
                >
                  <RefreshCw className="h-4 w-4" />
                  Xem đơn hàng đang chờ xác nhận
                </Button>
              )}
              <Button
                variant="outline"
                onClick={() => navigate('/products')}
                className="h-10 px-6 gap-2 rounded-md font-medium text-sm border-border shadow-none"
              >
                <ArrowLeft className="h-4 w-4" />
                Khám phá sản phẩm
              </Button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Cart Items List */}
            <div className="lg:col-span-8 space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-border">
                <span className="text-sm font-medium text-foreground">
                  Danh sách sản phẩm ({totalItems})
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={hasPendingCheckout}
                  onClick={clearCart}
                  className="text-xs text-muted-foreground hover:text-destructive gap-1.5 h-8 px-2 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  Xóa toàn bộ
                </Button>
              </div>

              <div className="divide-y divide-border border border-border rounded-md bg-card overflow-hidden">
                {items.map((item) => (
                  <div
                    key={item.variantId}
                    className="p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
                  >
                    <div className="flex items-center gap-4 flex-1 min-w-0">
                      <div className="h-20 w-20 sm:h-24 sm:w-24 shrink-0 overflow-hidden rounded-md border border-border bg-secondary">
                        {item.imageUrl ? (
                          <img
                            src={item.imageUrl}
                            alt={item.productName}
                            className="h-full w-full object-cover object-center"
                          />
                        ) : (
                          <div className="h-full w-full flex items-center justify-center text-xs text-muted-foreground">
                            Không có ảnh
                          </div>
                        )}
                      </div>

                      <div className="space-y-1 min-w-0 flex-1">
                        <Link
                          to={`/products/${item.productSlug}`}
                          className="font-medium text-sm text-foreground hover:underline line-clamp-1"
                        >
                          {item.productName}
                        </Link>
                        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                          {item.color && (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-secondary text-secondary-foreground">
                              Màu: {item.color}
                            </span>
                          )}
                          {item.size && (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-secondary text-secondary-foreground">
                              Size: {item.size}
                            </span>
                          )}
                        </div>
                        <div className="text-sm font-semibold text-foreground pt-1">
                          {formatCurrencyVND(item.price)}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between sm:justify-end gap-4 w-full sm:w-auto pt-2 sm:pt-0 border-t sm:border-t-0 border-border">
                      {/* Quantity Controls */}
                      <div className={`flex items-center border border-border rounded-md bg-background ${hasPendingCheckout ? 'opacity-50 pointer-events-none' : ''}`}>
                        <button
                          type="button"
                          disabled={hasPendingCheckout || item.quantity <= 1}
                          onClick={() => updateQuantity(item.variantId, item.quantity - 1)}
                          className="flex h-8 w-8 items-center justify-center text-muted-foreground hover:text-foreground transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                          aria-label="Giảm số lượng"
                        >
                          <Minus className="h-3.5 w-3.5" />
                        </button>
                        <span className="w-10 text-center text-xs font-semibold text-foreground select-none">
                          {item.quantity}
                        </span>
                        <button
                          type="button"
                          disabled={hasPendingCheckout}
                          onClick={() => updateQuantity(item.variantId, item.quantity + 1)}
                          className="flex h-8 w-8 items-center justify-center text-muted-foreground hover:text-foreground transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                          aria-label="Tăng số lượng"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>

                      <div className="text-right min-w-[90px]">
                        <div className="text-sm font-semibold text-foreground">
                          {formatCurrencyVND(item.price * item.quantity)}
                        </div>
                      </div>

                      <button
                        type="button"
                        disabled={hasPendingCheckout}
                        onClick={() => removeItem(item.variantId)}
                        className="text-muted-foreground hover:text-destructive transition-colors p-1 disabled:opacity-40 disabled:cursor-not-allowed"
                        title={hasPendingCheckout ? 'Giỏ hàng đang tạm khóa' : 'Xóa khỏi giỏ hàng'}
                        aria-label="Xóa sản phẩm"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              <div className="pt-2">
                <Button
                  variant="outline"
                  onClick={() => navigate('/products')}
                  className="gap-2 h-10 rounded-md text-xs font-medium border-border shadow-none"
                >
                  <ArrowLeft className="h-3.5 w-3.5" />
                  Tiếp tục chọn thêm sản phẩm
                </Button>
              </div>
            </div>

            {/* Order Summary Column */}
            <div className="lg:col-span-4 sticky top-24">
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-4 border-b border-border">
                  <CardTitle className="text-base font-semibold text-foreground">
                    Tóm tắt đơn hàng
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-5 space-y-4">
                  {/* Shipping Promo message */}
                  <div className="p-3 rounded-md bg-secondary/80 border border-border text-xs text-foreground space-y-1">
                    {amountNeededForFreeShip > 0 ? (
                      <p>
                        Mua thêm{' '}
                        <strong className="text-primary font-semibold">
                          {formatCurrencyVND(amountNeededForFreeShip)}
                        </strong>{' '}
                        để được <strong className="text-green-600 font-semibold">Miễn phí vận chuyển</strong>.
                      </p>
                    ) : (
                      <p className="text-green-600 font-semibold flex items-center gap-1.5">
                        <ShieldCheck className="h-4 w-4 shrink-0" />
                        Đơn hàng của bạn đủ điều kiện Miễn phí vận chuyển!
                      </p>
                    )}
                  </div>

                  <div className="space-y-2.5 text-sm">
                    <div className="flex justify-between text-muted-foreground">
                      <span>Tạm tính ước tính</span>
                      <span className="font-medium text-foreground">
                        {formatCurrencyVND(estimatedSubtotal)}
                      </span>
                    </div>

                    <div className="flex justify-between text-muted-foreground">
                      <span>Phí vận chuyển ước tính</span>
                      <span className={estimatedShippingFee === 0 ? 'font-medium text-green-600' : 'font-medium text-foreground'}>
                        {estimatedShippingFee === 0 ? 'Miễn phí' : formatCurrencyVND(estimatedShippingFee)}
                      </span>
                    </div>

                    <div className="pt-3 border-t border-border flex justify-between items-baseline">
                      <span className="font-semibold text-foreground text-base">Tổng tiền ước tính</span>
                      <div className="text-right">
                        <div className="text-lg font-bold text-foreground">
                          {formatCurrencyVND(estimatedTotal)}
                        </div>
                        <span className="text-[11px] text-muted-foreground">
                          (Đã bao gồm VAT nếu có)
                        </span>
                      </div>
                    </div>
                  </div>

                  <p className="text-[11px] text-muted-foreground italic leading-relaxed pt-1">
                    * Giá và phí vận chuyển chính thức sẽ được hệ thống tính toán và xác nhận khi đặt hàng.
                  </p>

                  {hasPendingCheckout ? (
                    <Button
                      onClick={() => navigate('/checkout')}
                      className="w-full h-11 gap-2 rounded-md font-medium text-sm shadow-none mt-2 bg-amber-700 hover:bg-amber-800 text-white"
                    >
                      <RefreshCw className="h-4 w-4" />
                      Kiểm tra đơn hàng đang chờ xác nhận
                    </Button>
                  ) : (
                    <Button
                      onClick={() => navigate('/checkout')}
                      className="w-full h-11 gap-2 rounded-md font-medium text-sm shadow-none mt-2"
                    >
                      Tiến hành đặt hàng
                      <ArrowRight className="h-4 w-4" />
                    </Button>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
        )}
      </main>

      <Footer />
    </div>
  );
};
