import React from 'react';
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { User } from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuthStore } from '@/stores/auth.store';
import { AccountSidebar } from '../components/AccountSidebar';
import { cn } from '@/lib/utils';

export const getInitials = (name?: string): string => {
  if (!name || !name.trim()) return '';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0].charAt(0).toUpperCase();
  }
  const lastPart = parts[parts.length - 1];
  return lastPart.charAt(0).toUpperCase();
};

const PAGE_TITLES: Record<string, string> = {
  '/account': 'Tổng quan',
  '/account/profile': 'Thông tin cá nhân',
  '/account/orders': 'Đơn hàng của tôi',
  '/account/addresses': 'Sổ địa chỉ',
  '/account/change-password': 'Đổi mật khẩu',
};

interface AccountLayoutProps {
  children?: React.ReactNode;
}

export const AccountLayout: React.FC<AccountLayoutProps> = ({ children }) => {
  const { user, accessToken, isInitializing } = useAuthStore();
  const location = useLocation();

  if (isInitializing) {
    return (
      <div className="min-h-screen flex flex-col bg-background text-foreground">
        <Header />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6">
          <div className="space-y-6">
            <Skeleton className="h-6 w-48 rounded-[6px]" />
            <Skeleton className="h-24 w-full rounded-[6px]" />
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
              <Skeleton className="h-64 md:col-span-1 rounded-[6px]" />
              <Skeleton className="h-96 md:col-span-3 rounded-[6px]" />
            </div>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  if (!accessToken || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const role = user.role?.toLowerCase();
  if (role === 'admin' || role === 'staff') {
    return <Navigate to="/admin" replace />;
  }

  const displayName = user.name || user.email || user.phone || 'Khách hàng';
  const initials = getInitials(user.name);
  const contactInfo = user.email || user.phone || 'Tài khoản thành viên';
  const isDashboard = location.pathname === '/account';
  const currentPageTitle = PAGE_TITLES[location.pathname] || 'Chi tiết';

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <Header />

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        {/* Breadcrumb Navigation */}
        <nav
          aria-label="Breadcrumb"
          className="mb-4 flex items-center gap-2 text-xs sm:text-[13px] text-muted-foreground"
        >
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <span>/</span>
          <Link
            to="/account"
            className={cn(
              isDashboard ? 'font-medium text-foreground' : 'hover:text-foreground transition-colors'
            )}
          >
            Tài khoản của tôi
          </Link>
          {!isDashboard && (
            <>
              <span>/</span>
              <span className="font-medium text-foreground">{currentPageTitle}</span>
            </>
          )}
        </nav>

        {/* Customer Identity Banner */}
        <div className="mb-6 rounded-md border border-border bg-card p-4 sm:p-6 shadow-none">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex items-center gap-4">
              <div className="flex h-12 w-12 sm:h-14 sm:w-14 shrink-0 items-center justify-center rounded-full bg-foreground text-background font-bold text-base sm:text-lg shadow-none">
                {initials || <User className="h-6 w-6" />}
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h1 className="text-base sm:text-lg font-bold text-foreground truncate">
                    {displayName}
                  </h1>
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-secondary-foreground border border-border">
                    Khách hàng
                  </span>
                </div>
                <p className="text-xs sm:text-sm text-muted-foreground truncate mt-0.5">
                  {contactInfo}
                </p>
              </div>
            </div>

            <div className="hidden sm:block text-right">
              <p className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                Trung tâm tài khoản
              </p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Quản lý hồ sơ, đơn hàng & bảo mật
              </p>
            </div>
          </div>
        </div>

        {/* Unified Layout Grid */}
        <div className="flex flex-col md:grid md:grid-cols-4 md:gap-8 items-start gap-6">
          {/* Sidebar (Desktop left / Mobile top scroll) */}
          <div className="w-full md:col-span-1">
            <AccountSidebar />
          </div>

          {/* Child Content Area */}
          <div className="w-full md:col-span-3 min-w-0">
            {children || <Outlet />}
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
};
