import React, { useMemo } from 'react';
import { useLocation, useParams, useNavigate } from 'react-router-dom';
import { CheckCircle2, ArrowRight, Package, Truck, MapPin, Calendar, HelpCircle } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatCurrencyVND, formatDateTimeVN } from '@/lib/format';
import type { CheckoutResponse } from '../order.types';

export const CheckoutSuccessPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  const stateOrder = location.state?.order as CheckoutResponse | undefined;

  const order = useMemo<CheckoutResponse | undefined>(() => {
    if (stateOrder) {
      return stateOrder;
    }
    if (typeof window !== 'undefined' && orderId) {
      try {
        const saved = sessionStorage.getItem('last_successful_order');
        if (saved) {
          const parsed = JSON.parse(saved) as CheckoutResponse;
          if (String(parsed.orderId) === String(orderId)) {
            return parsed;
          }
        }
      } catch {
        // Ignore JSON parsing errors
      }
    }
    return undefined;
  }, [stateOrder, orderId]);

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <Header />

      <main className="flex-1 mx-auto w-full max-w-[800px] px-4 sm:px-6 py-12">
        {order ? (
          <>
            <div className="text-center space-y-3 mb-8">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-100 text-green-600 mx-auto">
                <CheckCircle2 className="h-10 w-10 stroke-[2.5]" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
                Đặt hàng thành công!
              </h1>
              <p className="text-sm text-muted-foreground max-w-md mx-auto">
                Cảm ơn bạn đã mua sắm tại ATELIER. Mã đơn hàng của bạn là{' '}
                <strong className="text-foreground">#{order.orderId}</strong>. Chúng tôi sẽ sớm liên hệ để xác nhận và giao hàng.
              </p>
            </div>

            <div className="space-y-6">
              {/* Delivery & Payment Information */}
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-3 border-b border-border">
                  <CardTitle className="text-sm font-semibold text-foreground">
                    Thông tin đơn hàng #{order.orderId}
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4 grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  <div className="space-y-1.5">
                    <span className="font-semibold text-foreground flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5 text-muted-foreground" />
                      Địa chỉ nhận hàng:
                    </span>
                    <p className="text-foreground font-medium">{order.receiverName} — {order.receiverPhone}</p>
                    <p className="text-muted-foreground leading-relaxed">
                      {order.addressLine}
                      {order.ward && `, ${order.ward}`}
                      {order.district && `, ${order.district}`}
                      {order.province && `, ${order.province}`}
                    </p>
                    {order.note && (
                      <p className="text-muted-foreground italic pt-1">
                        Ghi chú: {order.note}
                      </p>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <span className="font-semibold text-foreground flex items-center gap-1.5">
                      <Truck className="h-3.5 w-3.5 text-muted-foreground" />
                      Thanh toán & Trạng thái:
                    </span>
                    <p className="text-foreground">
                      Phương thức: <span className="font-medium">Thanh toán khi nhận hàng (COD)</span>
                    </p>
                    <p className="text-foreground">
                      Trạng thái đơn: <span className="font-medium text-amber-600">Chờ xác nhận (PENDING)</span>
                    </p>
                    <p className="text-muted-foreground flex items-center gap-1 pt-1">
                      <Calendar className="h-3.5 w-3.5" />
                      {formatDateTimeVN(order.createdAt)}
                    </p>
                  </div>
                </CardContent>
              </Card>

              {/* Ordered Items List */}
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-3 border-b border-border">
                  <CardTitle className="text-sm font-semibold text-foreground flex items-center gap-2">
                    <Package className="h-4 w-4" />
                    Sản phẩm đã đặt ({order.items.length})
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4 space-y-3">
                  <div className="divide-y divide-border">
                    {order.items.map((item) => (
                      <div
                        key={item.orderDetailId}
                        className="py-3 first:pt-0 last:pb-0 flex items-center justify-between text-xs"
                      >
                        <div className="space-y-0.5 pr-4">
                          <p className="font-medium text-foreground">{item.productName}</p>
                          <p className="text-muted-foreground">
                            Phân loại: {item.variantColor} / {item.variantSize} × {item.quantity}
                          </p>
                        </div>
                        <div className="text-right font-semibold text-foreground shrink-0">
                          {formatCurrencyVND(item.totalPrice)}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Totals */}
                  <div className="pt-4 border-t border-border space-y-2 text-xs">
                    <div className="flex justify-between text-muted-foreground">
                      <span>Tạm tính</span>
                      <span className="font-medium text-foreground">
                        {formatCurrencyVND(order.subtotal)}
                      </span>
                    </div>

                    {order.discountAmount > 0 && (
                      <div className="flex justify-between text-green-600">
                        <span>Giảm giá voucher</span>
                        <span className="font-medium">
                          -{formatCurrencyVND(order.discountAmount)}
                        </span>
                      </div>
                    )}

                    <div className="flex justify-between text-muted-foreground">
                      <span>Phí vận chuyển</span>
                      <span className="font-medium text-foreground">
                        {order.shippingFee === 0 ? 'Miễn phí' : formatCurrencyVND(order.shippingFee)}
                      </span>
                    </div>

                    <div className="pt-3 border-t border-border flex justify-between items-baseline text-sm">
                      <span className="font-semibold text-foreground">Tổng thanh toán</span>
                      <span className="text-base font-bold text-foreground">
                        {formatCurrencyVND(order.totalAmount)}
                      </span>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </>
        ) : (
          <div className="space-y-6">
            <div className="text-center space-y-3 mb-8">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-secondary text-muted-foreground mx-auto">
                <HelpCircle className="h-10 w-10 stroke-[2]" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
                Không tìm thấy thông tin đơn hàng trong phiên hiện tại
              </h1>
              <p className="text-sm text-muted-foreground max-w-lg mx-auto leading-relaxed">
                Chi tiết đơn hàng chỉ hiển thị trực tiếp ngay sau khi hoàn tất đặt hàng trong cùng phiên duyệt web để bảo vệ thông tin khách hàng.
              </p>
            </div>

            <Card className="rounded-md border-border bg-card shadow-none">
              <CardHeader className="pb-3 border-b border-border">
                <CardTitle className="text-sm font-semibold text-foreground flex items-center gap-2">
                  <Package className="h-4 w-4 text-muted-foreground" />
                  Mã đơn hàng tham chiếu: #{orderId || 'N/A'}
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-4 text-xs space-y-3 text-muted-foreground leading-relaxed">
                <p>
                  Nếu bạn vừa thực hiện đặt hàng thành công nhưng tải lại trang hoặc truy cập từ liên kết bên ngoài, phiên lưu tạm thời trên trình duyệt có thể đã kết thúc.
                </p>
                <p>
                  Đơn hàng của bạn vẫn được lưu trữ an toàn trên hệ thống. Để kiểm tra chi tiết trạng thái hoặc thay đổi thông tin nhận hàng, vui lòng liên hệ bộ phận hỗ trợ khách hàng ATELIER kèm theo mã đơn hàng nêu trên.
                </p>
              </CardContent>
            </Card>
          </div>
        )}

        <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-8">
          <Button
            variant="outline"
            onClick={() => {
              const targetId = order?.orderId || orderId;
              if (targetId) {
                navigate(`/account/orders/${targetId}`);
              } else {
                navigate('/account/orders');
              }
            }}
            className="w-full sm:w-auto h-10 px-6 rounded-md text-sm border-border shadow-none"
          >
            Xem đơn hàng của tôi
          </Button>
          <Button
            onClick={() => navigate('/products')}
            className="w-full sm:w-auto h-10 px-6 gap-2 rounded-md font-medium text-sm shadow-none"
          >
            Khám phá sản phẩm
            <ArrowRight className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            onClick={() => navigate('/')}
            className="w-full sm:w-auto h-10 px-6 rounded-md text-sm shadow-none"
          >
            Về trang chủ
          </Button>
        </div>
      </main>

      <Footer />
    </div>
  );
};
