import React, { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Package,
  RotateCcw,
  ShoppingBag,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card';
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
import { useCancelOrder, useMyOrders } from '../hooks/order.hooks';
import {
  ORDER_STATUS_CONFIG,
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
  type CustomerOrderListItemResponse,
  type OrderStatus,
} from '../order.types';

interface ToastBanner {
  type: 'success' | 'error';
  message: string;
}

const STATUS_FILTERS: { label: string; value?: OrderStatus }[] = [
  { label: 'Tất cả', value: undefined },
  { label: 'Chờ xác nhận', value: 'PENDING' },
  { label: 'Đã xác nhận', value: 'CONFIRMED' },
  { label: 'Đang giao hàng', value: 'SHIPPING' },
  { label: 'Hoàn thành', value: 'COMPLETED' },
  { label: 'Đã hủy', value: 'CANCELLED' },
];

export const OrderHistoryPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const user = useAuthStore((state) => state.user);
  const isInitializing = useAuthStore((state) => state.isInitializing);

  const [toast, setToast] = useState<ToastBanner | null>(null);
  const [cancellingOrder, setCancellingOrder] = useState<CustomerOrderListItemResponse | null>(null);

  const currentStatus = searchParams.get('status') as OrderStatus | null;
  const validStatus = useMemo(() => {
    if (!currentStatus) return undefined;
    return ['PENDING', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED'].includes(currentStatus)
      ? currentStatus
      : undefined;
  }, [currentStatus]);

  const rawPage = parseInt(searchParams.get('page') || '1', 10);
  const currentPage = Number.isNaN(rawPage) || rawPage < 1 ? 1 : rawPage;
  const pageSize = 10;

  const {
    data: ordersData,
    isLoading,
    isError,
    error,
    refetch,
  } = useMyOrders({
    status: validStatus,
    page: currentPage - 1,
    size: pageSize,
  });

  const cancelMutation = useCancelOrder();

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  if (isInitializing) {
    return (
      <div className="flex flex-col gap-6">
        <Skeleton className="h-48 w-full rounded-[6px]" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const handleFilterChange = (status?: OrderStatus) => {
    const nextParams = new URLSearchParams();
    if (status) {
      nextParams.set('status', status);
    }
    nextParams.set('page', '1');
    setSearchParams(nextParams);
  };

  const handlePageChange = (newPage: number) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('page', String(newPage));
    setSearchParams(nextParams);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleConfirmCancel = () => {
    if (!cancellingOrder?.orderId) return;
    const orderIdToCancel = cancellingOrder.orderId;

    cancelMutation.mutate(orderIdToCancel, {
      onSuccess: () => {
        setCancellingOrder(null);
        setToast({
          type: 'success',
          message: `Đã hủy đơn hàng #${orderIdToCancel} thành công`,
        });
      },
      onError: (err) => {
        setCancellingOrder(null);
        setToast({
          type: 'error',
          message: getOrderErrorMessage(err, 'Không thể hủy đơn hàng vào lúc này'),
        });
      },
    });
  };

  const totalPages = ordersData?.totalPages ?? 0;
  const orders = ordersData?.content ?? [];

  return (
    <div className="flex flex-col gap-6">

          <div className="border-b border-border pb-5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Đơn hàng của tôi
            </h1>
            <p className="text-sm text-muted-foreground mt-1">
              Theo dõi tình trạng đơn hàng và quản lý các giao dịch mua sắm của bạn
            </p>
          </div>

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

          <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
            {STATUS_FILTERS.map((filter) => {
              const isActive = filter.value === validStatus;
              return (
                <Button
                  key={filter.label}
                  type="button"
                  size="sm"
                  variant={isActive ? 'default' : 'outline'}
                  onClick={() => handleFilterChange(filter.value)}
                  className="rounded-[6px] text-xs font-medium shrink-0 shadow-none h-8 px-3"
                >
                  {filter.label}
                </Button>
              );
            })}
          </div>

          {isLoading ? (
            <div className="space-y-4">
              {[1, 2, 3].map((item) => (
                <Card key={item} className="rounded-[6px] border-border bg-card shadow-none p-5">
                  <div className="space-y-3">
                    <div className="flex justify-between items-center">
                      <Skeleton className="h-5 w-32 rounded-[6px]" />
                      <Skeleton className="h-5 w-24 rounded-[6px]" />
                    </div>
                    <Skeleton className="h-16 w-full rounded-[6px]" />
                    <div className="flex justify-between items-center pt-2">
                      <Skeleton className="h-5 w-40 rounded-[6px]" />
                      <Skeleton className="h-8 w-24 rounded-[6px]" />
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          ) : isError ? (
            <Card className="rounded-[6px] border-border bg-card shadow-none py-12 text-center">
              <CardContent className="flex flex-col items-center gap-3">
                <AlertCircle className="h-10 w-10 text-destructive" />
                <h2 className="text-base font-semibold text-foreground">
                  Không thể tải danh sách đơn hàng
                </h2>
                <p className="text-xs text-muted-foreground max-w-sm">
                  {getOrderErrorMessage(error, 'Đã xảy ra lỗi khi kết nối tới máy chủ. Vui lòng thử lại.')}
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => refetch()}
                  className="mt-2 rounded-[6px] gap-2 shadow-none"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  Thử lại
                </Button>
              </CardContent>
            </Card>
          ) : orders.length === 0 ? (
            <Card className="rounded-[6px] border-border bg-card shadow-none py-16 text-center">
              <CardContent className="flex flex-col items-center gap-3">
                <div className="flex h-14 w-14 items-center justify-center rounded-full bg-secondary text-muted-foreground">
                  <Package className="h-7 w-7 stroke-[1.75]" />
                </div>
                <h2 className="text-base font-semibold text-foreground">
                  {validStatus ? 'Không có đơn hàng nào ở trạng thái này' : 'Bạn chưa có đơn hàng nào'}
                </h2>
                <p className="text-xs text-muted-foreground max-w-xs">
                  {validStatus
                    ? 'Hãy chọn bộ lọc trạng thái khác để kiểm tra đơn hàng của bạn.'
                    : 'Hãy khám phá các bộ sưu tập thời trang mới nhất của chúng tôi.'}
                </p>
                <Button
                  asChild
                  size="sm"
                  className="mt-3 rounded-[6px] gap-2 shadow-none font-medium"
                >
                  <Link to="/products">
                    <ShoppingBag className="h-4 w-4" />
                    Khám phá sản phẩm
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-4">
              {orders.map((order) => {
                const statusMeta = order.status
                  ? ORDER_STATUS_CONFIG[order.status]
                  : { label: order.status || 'N/A', variant: 'outline' as const };
                const paymentMethodText = order.paymentMethod
                  ? PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod
                  : 'N/A';
                const paymentStatusText = order.paymentStatus
                  ? PAYMENT_STATUS_LABELS[order.paymentStatus] || order.paymentStatus
                  : 'N/A';
                const previewItems = order.previewItems || [];
                const remainingItems = (order.lineCount || previewItems.length) - previewItems.length;

                return (
                  <Card
                    key={order.orderId}
                    className="rounded-[6px] border-border bg-card shadow-none overflow-hidden transition-colors hover:border-foreground/30"
                  >
                    <CardHeader className="py-3.5 px-4 sm:px-5 border-b border-border bg-muted/20 flex flex-row items-center justify-between gap-3">
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                        <span className="font-bold text-foreground text-sm">
                          Đơn hàng #{order.orderId}
                        </span>
                        <span className="text-muted-foreground">
                          {formatDateTimeVN(order.createdAt)}
                        </span>
                      </div>
                      <Badge variant={statusMeta.variant} className="rounded-[6px] font-normal text-xs px-2.5 py-0.5">
                        {statusMeta.label}
                      </Badge>
                    </CardHeader>

                    <CardContent className="p-4 sm:p-5 space-y-3">
                      <div className="divide-y divide-border">
                        {previewItems.map((item, idx) => (
                          <div
                            key={idx}
                            className={`flex items-center gap-3.5 ${idx > 0 ? 'pt-3' : ''} ${idx < previewItems.length - 1 ? 'pb-3' : ''}`}
                          >
                            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[6px] bg-secondary text-muted-foreground border border-border">
                              <Package className="h-5 w-5 stroke-[1.5]" />
                            </div>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-foreground truncate">
                                {item.productName || 'Sản phẩm'}
                              </p>
                              <p className="text-xs text-muted-foreground mt-0.5">
                                Phân loại: {item.variantSize || '—'} / {item.variantColor || '—'}
                              </p>
                            </div>
                            <div className="text-xs font-medium text-muted-foreground shrink-0">
                              x{item.quantity ?? 1}
                            </div>
                          </div>
                        ))}
                      </div>

                      {remainingItems > 0 && (
                        <p className="text-xs text-muted-foreground italic pt-1">
                          + còn {remainingItems} sản phẩm khác trong đơn hàng
                        </p>
                      )}
                    </CardContent>

                    <CardFooter className="py-3 px-4 sm:px-5 border-t border-border bg-muted/10 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                      <div className="text-xs text-muted-foreground flex flex-col gap-0.5">
                        <span>
                          Thanh toán: <strong className="text-foreground font-medium">{paymentMethodText}</strong> ({paymentStatusText})
                        </span>
                        <span>
                          Tổng thanh toán:{' '}
                          <strong className="text-foreground font-semibold text-sm">
                            {formatCurrencyVND(order.totalAmount)}
                          </strong>
                        </span>
                      </div>

                      <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
                        {order.canCancel && (
                          <Button
                            type="button"
                            variant="destructive"
                            size="sm"
                            disabled={cancelMutation.isPending}
                            onClick={() => setCancellingOrder(order)}
                            className="rounded-[6px] text-xs h-8 px-3 shadow-none"
                          >
                            Hủy đơn
                          </Button>
                        )}
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => navigate(`/account/orders/${order.orderId}`)}
                          className="rounded-[6px] text-xs h-8 px-3 shadow-none border-border"
                        >
                          Xem chi tiết
                        </Button>
                      </div>
                    </CardFooter>
                  </Card>
                );
              })}

              {totalPages > 1 && (
                <div className="flex items-center justify-between pt-4 border-t border-border">
                  <div className="text-xs text-muted-foreground">
                    Trang {currentPage} / {totalPages}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={currentPage <= 1 || isLoading}
                      onClick={() => handlePageChange(currentPage - 1)}
                      className="rounded-[6px] text-xs h-8 px-2.5 gap-1 shadow-none"
                    >
                      <ChevronLeft className="h-3.5 w-3.5" />
                      Trước
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={currentPage >= totalPages || isLoading}
                      onClick={() => handlePageChange(currentPage + 1)}
                      className="rounded-[6px] text-xs h-8 px-2.5 gap-1 shadow-none"
                    >
                      Sau
                      <ChevronRight className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        <AlertDialog
        open={Boolean(cancellingOrder)}
        onOpenChange={(open) => {
          if (!open) setCancellingOrder(null);
        }}
      >
        <AlertDialogContent className="rounded-[6px]">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-lg">
              Xác nhận hủy đơn hàng
            </AlertDialogTitle>
            <AlertDialogDescription className="text-sm">
              Bạn có chắc chắn muốn hủy đơn hàng #{cancellingOrder?.orderId}? Hành động này không thể hoàn tác và các mặt hàng sẽ được hoàn trả lại tồn kho.
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
    </div>
  );
};
