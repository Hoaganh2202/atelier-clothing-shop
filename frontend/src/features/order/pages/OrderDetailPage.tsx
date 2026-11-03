import React, { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  Check,
  CheckCircle2,
  Clock,
  CreditCard,
  FileText,
  MapPin,
  Package,
  RotateCcw,
  ShoppingBag,
  Truck,
  X,
  XCircle,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useAuthStore } from '@/stores/auth.store';
import { formatCurrencyVND, formatDateTimeVN } from '@/lib/format';
import { getOrderErrorMessage } from '../api/order.api';
import { useCancelOrder, useMyOrder } from '../hooks/order.hooks';
import {
  ORDER_STATUS_CONFIG,
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
  type OrderStatus,
} from '../order.types';

interface ToastBanner {
  type: 'success' | 'error';
  message: string;
}

const ORDER_STEPS: { status: OrderStatus; label: string; icon: React.ElementType }[] = [
  { status: 'PENDING', label: 'Chờ xác nhận', icon: Clock },
  { status: 'CONFIRMED', label: 'Đã xác nhận', icon: CheckCircle2 },
  { status: 'SHIPPING', label: 'Đang giao hàng', icon: Truck },
  { status: 'COMPLETED', label: 'Hoàn thành', icon: Check },
];

export const OrderDetailPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  const user = useAuthStore((state) => state.user);
  const isInitializing = useAuthStore((state) => state.isInitializing);

  const [toast, setToast] = useState<ToastBanner | null>(null);
  const [isCancelConfirmOpen, setIsCancelConfirmOpen] = useState(false);

  const numericOrderId = orderId ? parseInt(orderId, 10) : NaN;
  const isIdValid = !Number.isNaN(numericOrderId) && numericOrderId > 0;

  const {
    data: order,
    isLoading,
    isError,
    error,
    refetch,
  } = useMyOrder(isIdValid ? numericOrderId : 0);

  const cancelMutation = useCancelOrder();

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  if (isInitializing) {
    return (
      <div className="min-h-screen flex flex-col bg-background text-foreground">
        <Header />
        <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
          <div className="space-y-4">
            <Skeleton className="h-6 w-52 rounded-[6px]" />
            <Skeleton className="h-10 w-full rounded-[6px]" />
            <Skeleton className="h-24 w-full rounded-[6px]" />
            <Skeleton className="h-64 w-full rounded-[6px]" />
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const role = user.role?.toLowerCase();
  if (role === 'admin' || role === 'staff') {
    return <Navigate to="/admin" replace />;
  }

  if (!isIdValid) {
    return (
      <div className="min-h-screen flex flex-col bg-background text-foreground">
        <Header />
        <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-12 sm:px-6">
          <Card className="rounded-[6px] border-border bg-card shadow-none py-14 text-center">
            <CardContent className="flex flex-col items-center gap-3">
              <AlertCircle className="h-10 w-10 text-muted-foreground" />
              <h2 className="text-lg font-semibold text-foreground">
                Mã đơn hàng không hợp lệ
              </h2>
              <p className="text-xs text-muted-foreground max-w-xs">
                Mã định danh đơn hàng phải là một số nguyên dương. Vui lòng kiểm tra lại liên kết.
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => navigate('/account/orders')}
                className="mt-3 rounded-[6px] gap-2 shadow-none"
              >
                <ArrowLeft className="h-4 w-4" />
                Quay lại đơn hàng của tôi
              </Button>
            </CardContent>
          </Card>
        </main>
        <Footer />
      </div>
    );
  }

  const handleConfirmCancel = () => {
    if (!numericOrderId) return;

    cancelMutation.mutate(numericOrderId, {
      onSuccess: () => {
        setIsCancelConfirmOpen(false);
        setToast({
          type: 'success',
          message: 'Đã hủy đơn hàng thành công',
        });
      },
      onError: (err) => {
        setIsCancelConfirmOpen(false);
        setToast({
          type: 'error',
          message: getOrderErrorMessage(err, 'Không thể hủy đơn hàng vào lúc này'),
        });
      },
    });
  };

  const getStepStatus = (stepIndex: number, currentStatus?: string) => {
    const statusOrder: Record<string, number> = {
      PENDING: 0,
      CONFIRMED: 1,
      SHIPPING: 2,
      COMPLETED: 3,
    };
    const currentStepIndex = currentStatus ? statusOrder[currentStatus] ?? -1 : -1;
    if (stepIndex < currentStepIndex) return 'completed';
    if (stepIndex === currentStepIndex) return 'current';
    return 'upcoming';
  };

  const fullAddress = order
    ? [order.addressLine, order.ward, order.district, order.province].filter(Boolean).join(', ')
    : '—';

  const statusMeta = order?.status
    ? ORDER_STATUS_CONFIG[order.status]
    : { label: order?.status || 'N/A', variant: 'outline' as const };

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <Header />

      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
        <div className="flex flex-col gap-6">
          <nav
            aria-label="Breadcrumb"
            className="flex items-center gap-2 text-[13px] text-muted-foreground flex-wrap"
          >
            <Link to="/" className="hover:text-foreground transition-colors">
              Trang chủ
            </Link>
            <span>/</span>
            <Link to="/account/orders" className="hover:text-foreground transition-colors">
              Đơn hàng của tôi
            </Link>
            <span>/</span>
            <span className="font-medium text-foreground">#{numericOrderId}</span>
          </nav>

          {toast && (
            <div
              role="status"
              className="flex items-center justify-between gap-3 rounded-[6px] border border-border bg-card px-4 py-3"
            >
              <div className="flex items-center gap-2.5">
                {toast.type === 'error' ? (
                  <AlertCircle className="h-4 w-4 shrink-0 text-destructive" />
                ) : (
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-foreground" />
                )}
                <span className="text-sm font-medium">{toast.message}</span>
              </div>
              <button
                type="button"
                onClick={() => setToast(null)}
                className="text-muted-foreground hover:text-foreground transition-colors"
                aria-label="Đóng thông báo"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          )}

          {isLoading ? (
            <div className="space-y-4">
              <Skeleton className="h-16 w-full rounded-[6px]" />
              <Skeleton className="h-28 w-full rounded-[6px]" />
              <Skeleton className="h-64 w-full rounded-[6px]" />
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Skeleton className="h-40 rounded-[6px]" />
                <Skeleton className="h-40 rounded-[6px]" />
              </div>
            </div>
          ) : isError || !order ? (
            <Card className="rounded-[6px] border-border bg-card shadow-none py-14 text-center">
              <CardContent className="flex flex-col items-center gap-3">
                <AlertCircle className="h-10 w-10 text-destructive" />
                <h2 className="text-base font-semibold text-foreground">
                  Không tìm thấy đơn hàng #{numericOrderId}
                </h2>
                <p className="text-xs text-muted-foreground max-w-sm">
                  {getOrderErrorMessage(
                    error,
                    'Đơn hàng không tồn tại hoặc bạn không có quyền xem thông tin đơn hàng này.'
                  )}
                </p>
                <div className="flex items-center gap-2 mt-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => refetch()}
                    className="rounded-[6px] gap-2 shadow-none"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    Thử lại
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => navigate('/account/orders')}
                    className="rounded-[6px] gap-2 shadow-none"
                  >
                    <ArrowLeft className="h-3.5 w-3.5" />
                    Quay lại danh sách
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-5">
                <div>
                  <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-bold tracking-tight text-foreground">
                      Đơn hàng #{order.orderId}
                    </h1>
                    <Badge variant={statusMeta.variant} className="rounded-[6px] text-xs font-normal px-2.5 py-0.5">
                      {statusMeta.label}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    Ngày đặt hàng: {formatDateTimeVN(order.createdAt)}
                    {order.completedAt && ` • Hoàn thành: ${formatDateTimeVN(order.completedAt)}`}
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  {order.canCancel && (
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      disabled={cancelMutation.isPending}
                      onClick={() => setIsCancelConfirmOpen(true)}
                      className="rounded-[6px] text-xs h-8 px-3 shadow-none"
                    >
                      Hủy đơn hàng
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => navigate(`/account/orders/${order.orderId}/invoice`)}
                    className="rounded-[6px] text-xs h-8 px-3 shadow-none border-border"
                  >
                    <FileText className="h-3.5 w-3.5 mr-1 text-muted-foreground" />
                    Xem hóa đơn điện tử
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => navigate('/account/orders')}
                    className="rounded-[6px] text-xs h-8 px-3 shadow-none border-border"
                  >
                    <ArrowLeft className="h-3.5 w-3.5 mr-1" />
                    Danh sách
                  </Button>
                </div>
              </div>

              {order.status === 'CANCELLED' ? (
                <div className="flex items-center gap-3 rounded-[6px] border border-destructive/30 bg-destructive/10 p-4 text-destructive">
                  <XCircle className="h-5 w-5 shrink-0" />
                  <div className="text-xs">
                    <p className="font-semibold text-sm">Đơn hàng đã bị hủy</p>
                    <p className="text-destructive/80 mt-0.5">
                      Đơn hàng này đã được hủy và không thể tiếp tục thực hiện giao dịch.
                    </p>
                  </div>
                </div>
              ) : order.status === 'REFUNDED' ? (
                <div className="flex items-center gap-3 rounded-[6px] border border-border bg-muted/40 p-4 text-foreground">
                  <AlertCircle className="h-5 w-5 shrink-0 text-muted-foreground" />
                  <div className="text-xs">
                    <p className="font-semibold text-sm">Đơn hàng đã được hoàn tiền</p>
                    <p className="text-muted-foreground mt-0.5">
                      Khoản thanh toán của đơn hàng này đã được xử lý hoàn trả theo chính sách cửa hàng.
                    </p>
                  </div>
                </div>
              ) : (
                <Card className="rounded-[6px] border-border bg-card shadow-none p-5">
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                    {ORDER_STEPS.map((step, idx) => {
                      const state = getStepStatus(idx, order.status);
                      const Icon = step.icon;
                      const isCompleted = state === 'completed';
                      const isCurrent = state === 'current';

                      return (
                        <div key={step.status} className="flex flex-col items-center text-center gap-2">
                          <div
                            className={`flex h-10 w-10 items-center justify-center rounded-full border transition-colors ${
                              isCompleted
                                ? 'bg-primary text-primary-foreground border-primary'
                                : isCurrent
                                ? 'bg-primary text-primary-foreground border-primary ring-2 ring-primary/20'
                                : 'bg-secondary text-muted-foreground border-border'
                            }`}
                          >
                            <Icon className="h-4 w-4" />
                          </div>
                          <div>
                            <p
                              className={`text-xs font-medium ${
                                isCurrent || isCompleted ? 'text-foreground' : 'text-muted-foreground'
                              }`}
                            >
                              {step.label}
                            </p>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </Card>
              )}

              <Card className="rounded-[6px] border-border bg-card shadow-none overflow-hidden">
                <CardHeader className="py-3.5 px-4 sm:px-5 border-b border-border bg-muted/20">
                  <CardTitle className="text-sm font-semibold text-foreground flex items-center gap-2">
                    <Package className="h-4 w-4 text-muted-foreground" />
                    Danh sách sản phẩm ({order.items?.length || 0})
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                  <div className="divide-y divide-border">
                    {order.items?.map((item) => (
                      <div
                        key={item.orderDetailId}
                        className="p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
                      >
                        <div className="flex items-center gap-3.5 min-w-0">
                          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[6px] bg-secondary text-muted-foreground border border-border">
                            <Package className="h-6 w-6 stroke-[1.5]" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-sm font-semibold text-foreground truncate">
                              {item.productName || 'Sản phẩm'}
                            </p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                              Phân loại: <span className="text-foreground">{item.variantSize || '—'}</span> /{' '}
                              <span className="text-foreground">{item.variantColor || '—'}</span>
                            </p>
                            <p className="text-xs text-muted-foreground mt-0.5 sm:hidden">
                              Đơn giá: {formatCurrencyVND(item.unitPrice)} x {item.quantity}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center justify-between sm:justify-end gap-6 w-full sm:w-auto text-xs">
                          <div className="hidden sm:block text-right">
                            <span className="text-muted-foreground">Đơn giá:</span>{' '}
                            <span className="font-medium text-foreground">
                              {formatCurrencyVND(item.unitPrice)}
                            </span>
                          </div>

                          <div className="hidden sm:block text-right">
                            <span className="text-muted-foreground">Số lượng:</span>{' '}
                            <span className="font-medium text-foreground">x{item.quantity}</span>
                          </div>

                          {(item.discountAmount ?? 0) > 0 && (
                            <div className="text-right">
                              <span className="text-muted-foreground">Giảm:</span>{' '}
                              <span className="font-medium text-destructive">
                                -{formatCurrencyVND(item.discountAmount)}
                              </span>
                            </div>
                          )}

                          <div className="text-right min-w-[90px]">
                            <span className="sm:hidden text-muted-foreground">Thành tiền: </span>
                            <span className="font-bold text-foreground text-sm">
                              {formatCurrencyVND(item.lineTotal)}
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <Card className="rounded-[6px] border-border bg-card shadow-none">
                  <CardHeader className="py-3.5 px-4 sm:px-5 border-b border-border bg-muted/20">
                    <CardTitle className="text-sm font-semibold text-foreground flex items-center gap-2">
                      <MapPin className="h-4 w-4 text-muted-foreground" />
                      Thông tin giao nhận & Thanh toán
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="p-4 sm:p-5 text-xs space-y-4">
                    <div>
                      <p className="text-muted-foreground font-medium mb-1">Người nhận hàng</p>
                      <p className="text-foreground font-semibold text-sm">
                        {order.receiverName || '—'}
                      </p>
                      <p className="text-muted-foreground">{order.receiverPhone || '—'}</p>
                    </div>

                    <div>
                      <p className="text-muted-foreground font-medium mb-1">Địa chỉ giao hàng</p>
                      <p className="text-foreground leading-relaxed">{fullAddress}</p>
                    </div>

                    {order.note && (
                      <div>
                        <p className="text-muted-foreground font-medium mb-1">Ghi chú đơn hàng</p>
                        <p className="text-foreground italic">{order.note}</p>
                      </div>
                    )}

                    <div className="border-t border-border pt-3 space-y-1.5">
                      <div className="flex justify-between items-center">
                        <span className="text-muted-foreground flex items-center gap-1.5">
                          <CreditCard className="h-3.5 w-3.5" />
                          Phương thức:
                        </span>
                        <span className="font-medium text-foreground">
                          {order.paymentMethod ? PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod : '—'}
                        </span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-muted-foreground">Trạng thái thanh toán:</span>
                        <span className="font-medium text-foreground">
                          {order.paymentStatus ? PAYMENT_STATUS_LABELS[order.paymentStatus] || order.paymentStatus : '—'}
                        </span>
                      </div>
                      {order.paidAt && (
                        <div className="flex justify-between items-center">
                          <span className="text-muted-foreground">Thời gian thanh toán:</span>
                          <span className="text-foreground">{formatDateTimeVN(order.paidAt)}</span>
                        </div>
                      )}
                      <div className="pt-2 border-t border-border flex justify-between items-center">
                        <span className="text-muted-foreground">Hóa đơn điện tử:</span>
                        <Button
                          type="button"
                          variant="link"
                          size="sm"
                          onClick={() => navigate(`/account/orders/${order.orderId}/invoice`)}
                          className="h-auto p-0 text-xs text-foreground font-medium underline flex items-center gap-1"
                        >
                          <FileText className="h-3.5 w-3.5" />
                          Xem & In hóa đơn
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <Card className="rounded-[6px] border-border bg-card shadow-none">
                  <CardHeader className="py-3.5 px-4 sm:px-5 border-b border-border bg-muted/20">
                    <CardTitle className="text-sm font-semibold text-foreground flex items-center gap-2">
                      <ShoppingBag className="h-4 w-4 text-muted-foreground" />
                      Tóm tắt chi phí
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="p-4 sm:p-5 text-xs space-y-3">
                    <div className="flex justify-between items-center">
                      <span className="text-muted-foreground">Tạm tính:</span>
                      <span className="text-foreground font-medium">
                        {formatCurrencyVND(order.subtotal)}
                      </span>
                    </div>

                    {(order.discountAmount ?? 0) > 0 && (
                      <div className="flex justify-between items-center text-destructive">
                        <span>Giảm giá (Voucher / Khuyến mãi):</span>
                        <span className="font-medium">
                          -{formatCurrencyVND(order.discountAmount)}
                        </span>
                      </div>
                    )}

                    <div className="flex justify-between items-center">
                      <span className="text-muted-foreground">Phí vận chuyển:</span>
                      <span className="text-foreground font-medium">
                        {formatCurrencyVND(order.shippingFee)}
                      </span>
                    </div>

                    <div className="border-t border-border pt-3 flex justify-between items-center">
                      <span className="text-sm font-bold text-foreground">Tổng thanh toán:</span>
                      <span className="text-base font-bold text-foreground">
                        {formatCurrencyVND(order.totalAmount)}
                      </span>
                    </div>
                  </CardContent>
                </Card>
              </div>

              <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-4 border-t border-border">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => navigate('/account/orders')}
                  className="w-full sm:w-auto rounded-[6px] gap-2 text-xs h-9 shadow-none border-border"
                >
                  <ArrowLeft className="h-4 w-4" />
                  Quay lại danh sách đơn hàng
                </Button>
                <Button
                  type="button"
                  onClick={() => navigate('/products')}
                  className="w-full sm:w-auto rounded-[6px] gap-2 text-xs h-9 shadow-none font-medium"
                >
                  <ShoppingBag className="h-4 w-4" />
                  Tiếp tục mua sắm
                </Button>
              </div>
            </div>
          )}
        </div>
      </main>

      <AlertDialog open={isCancelConfirmOpen} onOpenChange={setIsCancelConfirmOpen}>
        <AlertDialogContent className="rounded-[6px]">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-lg">
              Xác nhận hủy đơn hàng
            </AlertDialogTitle>
            <AlertDialogDescription className="text-sm">
              Bạn có chắc chắn muốn hủy đơn hàng #{numericOrderId}? Thao tác này không thể hoàn tác và các mặt hàng sẽ được hoàn trả lại tồn kho.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel
              disabled={cancelMutation.isPending}
              className="rounded-[6px] text-xs h-9 shadow-none"
            >
              Không, giữ lại
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={cancelMutation.isPending}
              onClick={handleConfirmCancel}
              className="rounded-[6px] text-xs h-9 shadow-none bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {cancelMutation.isPending ? 'Đang xử lý...' : 'Xác nhận hủy đơn'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Footer />
    </div>
  );
};
