import React, { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  ChevronRight,
  FileText,
  KeyRound,
  MapPin,
  Package,
  Plus,
  ShoppingBag,
  Sparkles,
  User,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuthStore } from '@/stores/auth.store';
import { useMyOrders } from '@/features/order/hooks/order.hooks';
import { useAddresses } from '@/features/address/address.hooks';
import { formatFullAddress } from '@/features/address/address.api';
import { ORDER_STATUS_CONFIG, type OrderStatus } from '@/features/order/order.types';
import { formatCurrencyVND, formatDateTimeVN } from '@/lib/format';

export const AccountDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  useEffect(() => {
    document.title = 'Tổng quan tài khoản | ATELIER';
  }, []);

  const displayName = user?.name || user?.email || user?.phone || 'Quý khách';

  // Fetch recent orders (page 0, size 5)
  const {
    data: ordersData,
    isLoading: isOrdersLoading,
    isError: isOrdersError,
  } = useMyOrders({ page: 0, size: 5 });

  // Fetch customer addresses to find default address
  const {
    data: addressesData,
    isLoading: isAddressesLoading,
  } = useAddresses();

  const recentOrders = ordersData?.content || [];
  const totalOrders = ordersData?.totalElements || 0;

  const defaultAddress = React.useMemo(() => {
    if (!addressesData || addressesData.length === 0) return null;
    return addressesData.find((addr) => addr.isDefault) || addressesData[0];
  }, [addressesData]);

  const QUICK_ACTIONS = [
    {
      title: 'Đơn hàng của tôi',
      description: 'Theo dõi tiến trình giao hàng & hóa đơn',
      icon: Package,
      href: '/account/orders',
    },
    {
      title: 'Sổ địa chỉ',
      description: 'Quản lý địa chỉ giao nhận hàng hóa',
      icon: MapPin,
      href: '/account/addresses',
    },
    {
      title: 'Thông tin cá nhân',
      description: 'Cập nhật họ tên, số điện thoại, ngày sinh',
      icon: User,
      href: '/account/profile',
    },
    {
      title: 'Đổi mật khẩu',
      description: 'Định kỳ đổi mật khẩu để bảo vệ tài khoản',
      icon: KeyRound,
      href: '/account/change-password',
    },
  ];

  return (
    <div className="space-y-6">
      {/* Welcome Message Card */}
      <Card className="rounded-md border border-border bg-card shadow-none">
        <CardContent className="p-5 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-accent" />
                <h2 className="text-base sm:text-lg font-bold text-foreground">
                  Xin chào, {displayName}!
                </h2>
              </div>
              <p className="text-xs sm:text-sm text-muted-foreground leading-relaxed">
                Chào mừng bạn đến với khu vực quản lý tài khoản của Closet-Shop. Tại đây bạn có thể kiểm tra các đơn hàng mới nhất, cập nhật hồ sơ và lưu sổ địa chỉ giao hàng.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Quick Actions Grid */}
      <div>
        <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">
          Lối tắt chức năng
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
          {QUICK_ACTIONS.map((action) => {
            const Icon = action.icon;
            return (
              <Link
                key={action.href}
                to={action.href}
                className="group flex items-start gap-3.5 p-4 rounded-md border border-border bg-card hover:bg-secondary/60 hover:border-foreground/30 transition-all shadow-none"
              >
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-secondary text-foreground group-hover:bg-foreground group-hover:text-background transition-colors">
                  <Icon className="h-5 w-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-semibold text-foreground group-hover:text-foreground">
                      {action.title}
                    </p>
                    <ChevronRight className="h-4 w-4 text-muted-foreground group-hover:translate-x-0.5 transition-transform" />
                  </div>
                  <p className="text-xs text-muted-foreground line-clamp-1 mt-0.5">
                    {action.description}
                  </p>
                </div>
              </Link>
            );
          })}
        </div>
      </div>

      {/* Recent Orders Section */}
      <Card className="rounded-md border border-border bg-card shadow-none">
        <CardHeader className="p-4 sm:p-6 pb-3 border-b border-border flex flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle className="text-sm sm:text-base font-bold text-foreground">
              Đơn hàng gần đây
            </CardTitle>
            <CardDescription className="text-xs text-muted-foreground mt-0.5">
              Theo dõi các đơn hàng mới nhất của bạn
            </CardDescription>
          </div>
          <Link
            to="/account/orders"
            className="inline-flex items-center gap-1 text-xs font-medium text-foreground hover:underline"
          >
            <span>Xem tất cả ({totalOrders})</span>
            <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </CardHeader>
        <CardContent className="p-4 sm:p-6">
          {isOrdersLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-16 w-full rounded-md" />
              <Skeleton className="h-16 w-full rounded-md" />
              <Skeleton className="h-16 w-full rounded-md" />
            </div>
          ) : isOrdersError ? (
            <div className="py-8 text-center text-xs text-muted-foreground">
              Không thể tải danh sách đơn hàng lúc này. Vui lòng thử lại sau.
            </div>
          ) : recentOrders.length === 0 ? (
            <div className="py-8 text-center space-y-3">
              <div className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-secondary text-muted-foreground">
                <ShoppingBag className="h-6 w-6" />
              </div>
              <div className="space-y-1">
                <p className="text-sm font-semibold text-foreground">Chưa có đơn hàng nào</p>
                <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                  Bạn chưa thực hiện đơn đặt hàng nào tại Closet-Shop. Hãy khám phá bộ sưu tập thời trang mới nhất!
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/products')}
                className="mt-2 text-xs font-semibold gap-1.5"
              >
                <span>Khám phá sản phẩm</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Button>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {recentOrders.map((order) => {
                const statusKey: OrderStatus = order.status || 'PENDING';
                const statusMeta = ORDER_STATUS_CONFIG[statusKey] || {
                  label: statusKey,
                  variant: 'outline' as const,
                };

                return (
                  <div
                    key={order.orderId}
                    className="py-3.5 first:pt-0 last:pb-0 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"
                  >
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <Link
                          to={`/account/orders/${order.orderId}`}
                          className="text-xs sm:text-sm font-bold text-foreground hover:underline"
                        >
                          #{order.orderId}
                        </Link>
                        <Badge
                          variant={statusMeta.variant}
                          className="text-[11px] px-2 py-0.5 rounded-full font-medium"
                        >
                          {statusMeta.label}
                        </Badge>
                      </div>
                      <p className="text-[11px] text-muted-foreground">
                        Ngày đặt: {order.createdAt ? formatDateTimeVN(order.createdAt) : '—'} • {order.previewItems?.length || 1} sản phẩm
                      </p>
                    </div>

                    <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0">
                      <span className="text-xs sm:text-sm font-bold text-foreground">
                        {order.totalAmount !== undefined ? formatCurrencyVND(order.totalAmount) : '—'}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => navigate(`/account/orders/${order.orderId}`)}
                        className="h-8 px-3 text-xs font-medium rounded-md shadow-none"
                      >
                        Chi tiết
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Default Address & E-Invoice Info Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Default Address Preview */}
        <Card className="rounded-md border border-border bg-card shadow-none flex flex-col justify-between">
          <CardHeader className="p-4 sm:p-5 pb-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MapPin className="h-4 w-4 text-muted-foreground" />
                <CardTitle className="text-sm font-bold text-foreground">
                  Địa chỉ mặc định
                </CardTitle>
              </div>
              <Link
                to="/account/addresses"
                className="text-xs text-foreground hover:underline font-medium"
              >
                Quản lý
              </Link>
            </div>
          </CardHeader>
          <CardContent className="p-4 sm:p-5 pt-2 flex-1 flex flex-col justify-between">
            {isAddressesLoading ? (
              <div className="space-y-2 py-2">
                <Skeleton className="h-4 w-32" />
                <Skeleton className="h-4 w-full" />
              </div>
            ) : defaultAddress ? (
              <div className="space-y-1.5 text-xs">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-foreground">
                    {defaultAddress.receiverName}
                  </span>
                  <span className="text-muted-foreground">
                    ({defaultAddress.phone})
                  </span>
                  {defaultAddress.isDefault && (
                    <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[10px] bg-secondary text-secondary-foreground border border-border">
                      Mặc định
                    </span>
                  )}
                </div>
                <p className="text-muted-foreground leading-relaxed">
                  {formatFullAddress(defaultAddress)}
                </p>
              </div>
            ) : (
              <div className="py-3 text-center space-y-2">
                <p className="text-xs text-muted-foreground">
                  Chưa có địa chỉ nhận hàng nào được lưu.
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => navigate('/account/addresses')}
                  className="h-7 text-xs gap-1 font-medium shadow-none"
                >
                  <Plus className="h-3 w-3" />
                  <span>Thêm địa chỉ</span>
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        {/* E-Invoice Guidance Card */}
        <Card className="rounded-md border border-border bg-card shadow-none flex flex-col justify-between">
          <CardHeader className="p-4 sm:p-5 pb-2">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-muted-foreground" />
              <CardTitle className="text-sm font-bold text-foreground">
                Hóa đơn điện tử (E-Invoice)
              </CardTitle>
            </div>
          </CardHeader>
          <CardContent className="p-4 sm:p-5 pt-2 flex-1 flex flex-col justify-between text-xs space-y-2">
            <p className="text-muted-foreground leading-relaxed">
              Mọi đơn hàng sau khi hoàn tất xác nhận đều được tự động phát hành hóa đơn điện tử hợp lệ. Bạn có thể tra cứu và xem bản in chuẩn A4 trực tiếp tại trang chi tiết đơn hàng.
            </p>
            <div className="pt-2">
              <Link
                to="/account/orders"
                className="inline-flex items-center gap-1 font-semibold text-foreground hover:underline"
              >
                <span>Xem đơn hàng để tra cứu hóa đơn</span>
                <ChevronRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
