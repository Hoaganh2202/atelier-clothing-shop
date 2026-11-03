import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  FileText,
  KeyRound,
  LayoutDashboard,
  LogOut,
  MapPin,
  Package,
  User,
} from 'lucide-react';
import { useAuthStore } from '@/stores/auth.store';
import { cn } from '@/lib/utils';

export interface NavItem {
  label: string;
  href: string;
  icon: React.ElementType;
  exact?: boolean;
}

export const ACCOUNT_NAV_ITEMS: NavItem[] = [
  { label: 'Tổng quan tài khoản', href: '/account', icon: LayoutDashboard, exact: true },
  { label: 'Thông tin cá nhân', href: '/account/profile', icon: User },
  { label: 'Đơn hàng của tôi', href: '/account/orders', icon: Package },
  { label: 'Sổ địa chỉ', href: '/account/addresses', icon: MapPin },
  { label: 'Đổi mật khẩu', href: '/account/change-password', icon: KeyRound },
];

export const AccountSidebar: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);

  const handleLogout = () => {
    logout();
    queryClient.clear();
    navigate('/', { replace: true });
  };

  const isItemActive = (item: NavItem) => {
    if (item.exact) {
      return location.pathname === item.href;
    }
    return location.pathname.startsWith(item.href);
  };

  return (
    <>
      {/* Mobile Horizontal Navigation Bar */}
      <div className="md:hidden w-full overflow-x-auto pb-1 scrollbar-none">
        <div className="flex items-center gap-1.5 min-w-max py-0.5">
          {ACCOUNT_NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const active = isItemActive(item);

            return (
              <Link
                key={item.href}
                to={item.href}
                className={cn(
                  'flex items-center gap-2 px-3 py-2 rounded-md text-xs font-medium transition-colors shrink-0',
                  active
                    ? 'bg-foreground text-background font-semibold shadow-none'
                    : 'bg-card border border-border text-foreground hover:bg-secondary'
                )}
                aria-current={active ? 'page' : undefined}
              >
                <Icon className={cn('h-3.5 w-3.5 shrink-0', active ? 'text-background' : 'text-muted-foreground')} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </div>
      </div>

      {/* Desktop Vertical Sidebar */}
      <aside className="hidden md:block rounded-md border border-border bg-card p-2.5 shadow-none">
        <div className="px-3 py-2 mb-1 border-b border-border">
          <p className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
            Điều hướng tài khoản
          </p>
        </div>

        <nav aria-label="Account navigation" className="space-y-1">
          {ACCOUNT_NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const active = isItemActive(item);

            return (
              <Link
                key={item.href}
                to={item.href}
                className={cn(
                  'flex items-center gap-3 px-3 py-2.5 rounded-md text-xs sm:text-sm font-medium transition-colors',
                  active
                    ? 'bg-foreground text-background font-semibold shadow-none'
                    : 'text-foreground hover:bg-secondary'
                )}
                aria-current={active ? 'page' : undefined}
              >
                <Icon className={cn('h-4 w-4 shrink-0', active ? 'text-background' : 'text-muted-foreground')} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Invoice guidance notice */}
        <div className="mt-4 pt-3 border-t border-border px-3 text-[11px] text-muted-foreground leading-relaxed flex items-start gap-2">
          <FileText className="h-3.5 w-3.5 shrink-0 mt-0.5 text-muted-foreground" />
          <span>Hóa đơn điện tử có thể tra cứu và in tại chi tiết từng đơn hàng.</span>
        </div>

        {/* Logout button */}
        <div className="mt-3 pt-3 border-t border-border">
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center gap-3 px-3 py-2 rounded-md text-xs sm:text-sm font-medium text-destructive hover:bg-destructive/10 transition-colors text-left"
          >
            <LogOut className="h-4 w-4 shrink-0 text-destructive" />
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>
    </>
  );
};
