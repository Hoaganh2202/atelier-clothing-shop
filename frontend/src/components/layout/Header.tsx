import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  ChevronDown,
  ChevronRight,
  ClipboardList,
  KeyRound,
  LayoutDashboard,
  LogIn,
  LogOut,
  MapPin,
  Menu,
  Search,
  ShieldCheck,
  ShoppingBag,
  User,
  UserPlus,
} from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { useAuthStore } from '@/stores/auth.store';
import { useCartStore } from '@/features/cart/cart.store';
import { useCategories } from '@/features/product/hooks/useProducts';
import { useQueryClient } from '@tanstack/react-query';
import { MegaMenu } from './MegaMenu';

interface HeaderProps {
  cartCount?: number;
  onCartClick?: () => void;
  searchQuery?: string;
  onSearchChange?: (query: string) => void;
  activeNav?: string;
  onNavClick?: (id: string) => void;
}

interface NavItemConfig {
  label: string;
  id: string;
  href: string;
  megaMenu?: 'men' | 'women';
}

const NAV_ITEMS: NavItemConfig[] = [
  { label: 'Hàng Mới', id: 'hang-moi', href: '/new-arrivals' },
  { label: 'Nam', id: 'nam', href: '/collections/men', megaMenu: 'men' },
  { label: 'Nữ', id: 'nu', href: '/collections/women', megaMenu: 'women' },
  { label: 'Phụ kiện', id: 'phu-kien', href: '/collections/accessories' },
  { label: 'Bộ Sưu Tập', id: 'bo-suu-tap', href: '/collections' },
  { label: 'Ưu đãi', id: 'uu-dai', href: '/sale' },
];

const getInitials = (name?: string | null): string => {
  if (!name || !name.trim()) return '';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0].charAt(0).toUpperCase();
  }
  const lastPart = parts[parts.length - 1];
  return lastPart.charAt(0).toUpperCase();
};

export const Header: React.FC<HeaderProps> = ({
  cartCount,
  onCartClick,
  searchQuery,
  onSearchChange,
  activeNav,
  onNavClick,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { user, accessToken, isInitializing, logout } = useAuthStore();
  const isAuthenticated = Boolean(accessToken && user);
  const storeCartCount = useCartStore((state) => state.getTotalItems());
  const effectiveCartCount = cartCount !== undefined ? cartCount : storeCartCount;

  const [internalSearchQuery, setInternalSearchQuery] = useState<string>(() => {
    if (searchQuery !== undefined) return searchQuery;
    const params = new URLSearchParams(location.search);
    return params.get('keyword') || '';
  });

  useEffect(() => {
    if (searchQuery !== undefined) {
      setInternalSearchQuery(searchQuery);
    }
  }, [searchQuery]);

  useEffect(() => {
    if (searchQuery === undefined) {
      const params = new URLSearchParams(location.search);
      setInternalSearchQuery(params.get('keyword') || '');
    }
  }, [location.pathname, location.search, searchQuery]);

  const effectiveSearchQuery = searchQuery !== undefined ? searchQuery : internalSearchQuery;

  const handleSearchInputChange = (value: string) => {
    setInternalSearchQuery(value);
    if (onSearchChange) {
      onSearchChange(value);
    }
  };

  const { data: categories = [], isLoading: isCategoriesLoading } = useCategories();

  // Find categories dynamically by slug
  const aoNamCat = categories.find((c) => c.slug === 'ao-nam');
  const quanNamCat = categories.find((c) => c.slug === 'quan-nam');
  const aoNuCat = categories.find((c) => c.slug === 'ao-nu');
  const quanNuCat = categories.find((c) => c.slug === 'quan-nu');
  const phuKienCat = categories.find((c) => c.slug === 'phu-kien');

  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const [mobileExpanded, setMobileExpanded] = useState<'men' | 'women' | 'accessories' | null>(null);

  const [activeMegaMenu, setActiveMegaMenu] = useState<'men' | 'women' | null>(null);
  const closeTimerRef = useRef<NodeJS.Timeout | null>(null);
  const megaMenuOpenedAtRef = useRef<number>(0);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const megaMenuContainerRef = useRef<HTMLDivElement>(null);
  const megaMenuPanelRef = useRef<HTMLDivElement>(null);

  const formatCategoryName = (slug?: string, name?: string) => {
    if (slug === 'ao-ba-lo') {
      return 'Áo Ba Lỗ';
    }
    return name || '';
  };

  const currentNavId = useMemo(() => {
    if (activeNav) return activeNav;
    const path = location.pathname;
    if (path === '/new-arrivals') return 'hang-moi';
    if (path.startsWith('/collections/men')) return 'nam';
    if (path.startsWith('/collections/women')) return 'nu';
    if (path.startsWith('/collections/accessories')) return 'phu-kien';
    if (path === '/collections') return 'bo-suu-tap';
    if (path === '/sale') return 'uu-dai';
    return '';
  }, [activeNav, location.pathname]);

  const handleMouseEnter = (type: 'men' | 'women') => {
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    setActiveMegaMenu((prev) => {
      if (prev !== type) {
        megaMenuOpenedAtRef.current = Date.now();
      }
      return type;
    });
  };

  const handleMouseLeave = () => {
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
    }
    closeTimerRef.current = setTimeout(() => {
      setActiveMegaMenu(null);
    }, 150);
  };

  const handleCloseMegaMenu = () => {
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    setActiveMegaMenu(null);
  };

  const handleToggleMegaMenu = (type: 'men' | 'women') => {
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    setActiveMegaMenu((prev) => {
      if (prev !== type) {
        megaMenuOpenedAtRef.current = Date.now();
        return type;
      }
      if (Date.now() - megaMenuOpenedAtRef.current < 400) {
        return type;
      }
      return null;
    });
  };

  // Preview testing params support
  useEffect(() => {
    const search = new URLSearchParams(location.search);
    const previewMenu = search.get('previewMenu');
    if (previewMenu === 'men' || previewMenu === 'women') {
      setActiveMegaMenu(previewMenu);
    }
    if (search.get('previewMobileNav') === 'true') {
      setIsMobileNavOpen(true);
      const expand = search.get('expand');
      if (expand === 'women') {
        setMobileExpanded('women');
      } else if (expand === 'accessories') {
        setMobileExpanded('accessories');
      } else {
        setMobileExpanded('men');
      }
    }
  }, [location.search]);

  // Escape key and click outside listener for Mega Menu
  useEffect(() => {
    if (!activeMegaMenu) return;

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        handleCloseMegaMenu();
      }
    };

    const handleClickOutside = (event: MouseEvent | TouchEvent) => {
      const target = event.target as Node;
      const isInsideNav = megaMenuContainerRef.current?.contains(target) ?? false;
      const isInsidePanel = megaMenuPanelRef.current?.contains(target) ?? false;
      if (!isInsideNav && !isInsidePanel) {
        handleCloseMegaMenu();
      }
    };

    document.addEventListener('keydown', handleEscape);
    document.addEventListener('mousedown', handleClickOutside);

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [activeMegaMenu]);

  // User menu outside click
  useEffect(() => {
    if (!isMenuOpen) return;

    const handleClickOutside = (event: MouseEvent | TouchEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false);
      }
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsMenuOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('touchstart', handleClickOutside);
    document.addEventListener('keydown', handleEscape);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('touchstart', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [isMenuOpen]);

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) {
        clearTimeout(closeTimerRef.current);
      }
    };
  }, []);

  const handleSubmitSearch = (closeMobileDrawer = false) => {
    const trimmed = effectiveSearchQuery.trim();
    if (!trimmed) return;
    if (closeMobileDrawer) {
      setIsMobileNavOpen(false);
    }
    handleCloseMegaMenu();

    if (location.pathname === '/products') {
      const nextParams = new URLSearchParams(location.search);
      if (nextParams.get('keyword') === trimmed && !nextParams.get('page')) {
        return;
      }
      nextParams.set('keyword', trimmed);
      nextParams.delete('page');
      navigate(`/products?${nextParams.toString()}`);
      return;
    }
    navigate(`/products?keyword=${encodeURIComponent(trimmed)}`);
  };

  const handleSearchFormSubmit = (e: React.FormEvent<HTMLFormElement>, closeMobileDrawer = false) => {
    e.preventDefault();
    handleSubmitSearch(closeMobileDrawer);
  };

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>, closeMobileDrawer = false) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSubmitSearch(closeMobileDrawer);
    }
  };

  const handleLogout = () => {
    setIsMenuOpen(false);
    logout();
    queryClient.clear();
    navigate('/', { replace: true });
  };

  const handleNavigate = (path: string) => {
    setIsMenuOpen(false);
    navigate(path);
  };

  const userRole = user?.role?.toLowerCase();
  const isAdminOrStaff = userRole === 'admin' || userRole === 'staff';
  const displayName = user?.name?.trim() || 'Khách hàng';
  const initials = getInitials(user?.name);

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border bg-background relative">
      <div className="mx-auto flex h-14 max-w-[1280px] items-center justify-between gap-4 px-4 sm:px-6">
        {/* Mobile Hamburger & Brand */}
        <div className="flex items-center gap-2 lg:hidden">
          <Button
            variant="outline"
            size="icon"
            className="h-9 w-9 shrink-0 rounded-md border-border bg-transparent hover:bg-secondary text-foreground shadow-none"
            onClick={() => setIsMobileNavOpen(true)}
            aria-label="Mở menu điều hướng"
          >
            <Menu className="h-4 w-4" />
          </Button>
          <Link
            to="/"
            className="text-lg font-bold tracking-tight text-foreground transition-opacity hover:opacity-80 shrink-0 font-sans"
          >
            ATELIER
          </Link>
        </div>

        {/* Desktop Brand Logo */}
        <Link
          to="/"
          className="hidden lg:block text-xl font-bold tracking-tight text-foreground transition-opacity hover:opacity-80 shrink-0 font-sans"
        >
          ATELIER
        </Link>

        {/* Navigation Links (Desktop) */}
        <nav className="hidden lg:flex items-center gap-6 h-full" ref={megaMenuContainerRef}>
          {NAV_ITEMS.map((item) => {
            const isActive = currentNavId === item.id;
            const isMegaActive = activeMegaMenu === item.megaMenu;
            return (
              <div
                key={item.id}
                className="relative flex items-center h-full"
                onMouseEnter={() => {
                  if (item.megaMenu) {
                    handleMouseEnter(item.megaMenu);
                  } else {
                    handleCloseMegaMenu();
                  }
                }}
                onMouseLeave={() => {
                  if (item.megaMenu) {
                    handleMouseLeave();
                  }
                }}
              >
                {item.megaMenu ? (
                  <button
                    type="button"
                    onClick={() => handleToggleMegaMenu(item.megaMenu!)}
                    onKeyDown={(e) => {
                      if (e.key === 'ArrowDown') {
                        e.preventDefault();
                        handleMouseEnter(item.megaMenu!);
                      }
                    }}
                    className={`text-sm font-medium transition-colors hover:text-foreground inline-flex items-center gap-1 py-1 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-foreground rounded-sm ${
                      isActive || isMegaActive ? 'text-foreground font-semibold' : 'text-muted-foreground'
                    }`}
                    aria-expanded={isMegaActive}
                    aria-haspopup="true"
                    aria-label={`Danh mục ${item.label}`}
                  >
                    <span>{item.label}</span>
                    <ChevronDown
                      className={`h-3 w-3 text-muted-foreground transition-transform duration-150 ${
                        isMegaActive ? 'rotate-180 text-foreground' : ''
                      }`}
                    />
                  </button>
                ) : (
                  <Link
                    to={item.href}
                    onClick={() => {
                      handleCloseMegaMenu();
                      onNavClick && onNavClick(item.id);
                    }}
                    onFocus={() => handleCloseMegaMenu()}
                    className={`text-sm font-medium transition-colors hover:text-foreground inline-flex items-center gap-1 py-1 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-foreground rounded-sm ${
                      isActive ? 'text-foreground font-semibold' : 'text-muted-foreground'
                    }`}
                  >
                    <span>{item.label}</span>
                  </Link>
                )}
                {isActive && (
                  <span className="absolute bottom-0 left-0 h-0.5 w-full bg-foreground rounded-full" />
                )}
              </div>
            );
          })}
        </nav>

        {/* Search Bar (Desktop) */}
        <div className="flex-1 max-w-sm hidden sm:block">
          <form
            role="search"
            onSubmit={(e) => handleSearchFormSubmit(e, false)}
            className="relative"
          >
            <button
              type="submit"
              aria-label="Tìm kiếm"
              className="absolute left-0 top-0 bottom-0 px-2.5 flex items-center justify-center text-muted-foreground hover:text-foreground transition-colors"
            >
              <Search className="h-3.5 w-3.5" />
            </button>
            <Input
              type="search"
              placeholder="Tìm kiếm sản phẩm..."
              value={effectiveSearchQuery}
              onChange={(e) => handleSearchInputChange(e.target.value)}
              onKeyDown={(e) => handleSearchKeyDown(e, false)}
              className="w-full pl-8 h-8 text-xs bg-secondary/50 border-input focus:bg-background transition-colors"
            />
          </form>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2">
          {/* Cart Button */}
          <Button
            variant="outline"
            size="icon"
            className="relative h-9 w-9 shrink-0 rounded-md border-border bg-transparent hover:bg-secondary transition-colors shadow-none"
            aria-label="Giỏ hàng"
            onClick={onCartClick ? onCartClick : () => navigate('/cart')}
          >
            <ShoppingBag className="h-4 w-4 text-foreground" />
            {effectiveCartCount > 0 && (
              <span className="absolute -top-1 -right-1 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-foreground px-1 text-[10px] font-bold text-background leading-none">
                {effectiveCartCount > 99 ? '99+' : effectiveCartCount}
              </span>
            )}
          </Button>

          {/* Quick Orders Link */}
          <Button
            variant="outline"
            size="icon"
            className="h-9 w-9 shrink-0 rounded-md border-border bg-transparent hover:bg-secondary transition-colors shadow-none"
            aria-label="Đơn mua"
            title="Đơn hàng của tôi"
            onClick={() => {
              if (isInitializing) return;
              navigate(isAuthenticated ? '/account/orders' : '/login');
            }}
          >
            <ClipboardList className="h-4 w-4 text-foreground" />
          </Button>

          {/* User Account Dropdown Section */}
          <div className="relative" ref={userMenuRef}>
            {isAuthenticated ? (
              <>
                <button
                  type="button"
                  onClick={() => setIsMenuOpen((prev) => !prev)}
                  className="flex items-center gap-1.5 h-9 px-2 rounded-md hover:bg-secondary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring text-left"
                  aria-label="Menu tài khoản"
                  aria-expanded={isMenuOpen}
                  aria-haspopup="true"
                >
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-foreground text-background font-semibold text-[11px] shadow-none">
                    {initials || <User className="h-3.5 w-3.5" />}
                  </div>
                  <span className="hidden md:inline-block text-xs font-medium text-foreground max-w-[110px] truncate">
                    {displayName}
                  </span>
                  <ChevronDown className="h-3 w-3 text-muted-foreground shrink-0" />
                </button>

                {isMenuOpen && (
                  <div
                    className="absolute right-0 top-full mt-2 w-56 rounded-md border border-border bg-background p-1.5 shadow-md z-50 animate-in fade-in-50 zoom-in-95 duration-150"
                    role="menu"
                  >
                    <div className="px-3 py-2 border-b border-border mb-1">
                      <p className="text-xs font-semibold text-foreground truncate">{displayName}</p>
                      {user?.email && (
                        <p className="text-[11px] text-muted-foreground truncate">{user.email}</p>
                      )}
                      {user?.phone && !user?.email && (
                        <p className="text-[11px] text-muted-foreground truncate">{user.phone}</p>
                      )}
                    </div>

                    <div className="space-y-0.5">
                      <button
                        type="button"
                        onClick={() => handleNavigate('/account')}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                        role="menuitem"
                      >
                        <LayoutDashboard className="h-4 w-4 text-muted-foreground" />
                        <span>Tổng quan tài khoản</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => handleNavigate('/account/profile')}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                        role="menuitem"
                      >
                        <User className="h-4 w-4 text-muted-foreground" />
                        <span>Thông tin cá nhân</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => handleNavigate('/account/orders')}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                        role="menuitem"
                      >
                        <ClipboardList className="h-4 w-4 text-muted-foreground" />
                        <span>Đơn hàng của tôi</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => handleNavigate('/account/addresses')}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                        role="menuitem"
                      >
                        <MapPin className="h-4 w-4 text-muted-foreground" />
                        <span>Sổ địa chỉ</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => handleNavigate('/account/change-password')}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                        role="menuitem"
                      >
                        <KeyRound className="h-4 w-4 text-muted-foreground" />
                        <span>Đổi mật khẩu</span>
                      </button>

                      {isAdminOrStaff && (
                        <button
                          type="button"
                          onClick={() => handleNavigate('/admin')}
                          className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                          role="menuitem"
                        >
                          <ShieldCheck className="h-4 w-4 text-muted-foreground" />
                          <span>Trang quản trị</span>
                        </button>
                      )}
                    </div>

                    <div className="border-t border-border mt-1 pt-1">
                      <button
                        type="button"
                        onClick={handleLogout}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-destructive rounded-md hover:bg-destructive/10 transition-colors text-left"
                        role="menuitem"
                      >
                        <LogOut className="h-4 w-4 text-destructive" />
                        <span>Đăng xuất</span>
                      </button>
                    </div>
                  </div>
                )}
              </>
            ) : (
              <>
                {/* Desktop Guest Actions */}
                <div className="hidden sm:flex items-center gap-1.5 text-xs font-medium">
                  <Link
                    to="/login"
                    className="px-2.5 py-1.5 rounded-md text-foreground hover:bg-secondary transition-colors"
                  >
                    Đăng nhập
                  </Link>
                  <Link
                    to="/register"
                    className="px-2.5 py-1.5 rounded-md bg-foreground text-background hover:bg-neutral-800 transition-colors"
                  >
                    Đăng ký
                  </Link>
                </div>

                {/* Mobile Guest Icon */}
                <div className="sm:hidden">
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-9 w-9 shrink-0 rounded-md border-border bg-transparent hover:bg-secondary transition-colors shadow-none"
                    onClick={() => setIsMenuOpen((prev) => !prev)}
                    aria-label="Tài khoản cá nhân"
                    aria-expanded={isMenuOpen}
                  >
                    <User className="h-4 w-4 text-foreground" />
                  </Button>

                  {isMenuOpen && (
                    <div
                      className="absolute right-0 top-full mt-2 w-48 rounded-md border border-border bg-background p-1.5 shadow-md z-50 animate-in fade-in-50 zoom-in-95 duration-150"
                      role="menu"
                    >
                      <div className="px-3 py-2 border-b border-border mb-1">
                        <p className="text-xs font-semibold text-foreground">Tài khoản</p>
                        <p className="text-[11px] text-muted-foreground">Đăng nhập để xem đơn hàng</p>
                      </div>
                      <div className="space-y-0.5">
                        <button
                          type="button"
                          onClick={() => handleNavigate('/login')}
                          className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                          role="menuitem"
                        >
                          <LogIn className="h-4 w-4 text-muted-foreground" />
                          <span>Đăng nhập</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => handleNavigate('/register')}
                          className="flex w-full items-center gap-2.5 px-3 py-2 text-xs font-medium text-foreground rounded-md hover:bg-secondary transition-colors text-left"
                          role="menuitem"
                        >
                          <UserPlus className="h-4 w-4 text-muted-foreground" />
                          <span>Đăng ký</span>
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Desktop Mega Menu Dropdown */}
      {activeMegaMenu && (
        <div ref={megaMenuPanelRef}>
          <MegaMenu
            type={activeMegaMenu}
            isOpen={Boolean(activeMegaMenu)}
            onClose={handleCloseMegaMenu}
            categories={categories}
            isLoading={isCategoriesLoading}
            onMouseEnter={() => {
              if (closeTimerRef.current) {
                clearTimeout(closeTimerRef.current);
                closeTimerRef.current = null;
              }
            }}
            onMouseLeave={handleMouseLeave}
          />
        </div>
      )}

      {/* Mobile Drawer Navigation */}
      <Sheet open={isMobileNavOpen} onOpenChange={setIsMobileNavOpen}>
        <SheetContent side="left" className="w-[310px] sm:w-[350px] p-0 flex flex-col justify-between">
          <div className="flex-1 overflow-y-auto">
            <SheetHeader className="p-4 border-b border-border text-left">
              <SheetTitle className="text-base font-bold tracking-tight text-foreground font-sans">
                ATELIER
              </SheetTitle>
              <SheetDescription className="sr-only">
                Menu điều hướng và danh mục sản phẩm ATELIER
              </SheetDescription>
            </SheetHeader>

            {/* Mobile Search inside drawer */}
            <div className="p-3 border-b border-border">
              <form
                role="search"
                onSubmit={(e) => handleSearchFormSubmit(e, true)}
                className="relative"
              >
                <button
                  type="submit"
                  aria-label="Tìm kiếm"
                  className="absolute left-0 top-0 bottom-0 px-2.5 flex items-center justify-center text-muted-foreground hover:text-foreground transition-colors"
                >
                  <Search className="h-3.5 w-3.5" />
                </button>
                <Input
                  type="search"
                  placeholder="Tìm kiếm sản phẩm..."
                  value={effectiveSearchQuery}
                  onChange={(e) => handleSearchInputChange(e.target.value)}
                  onKeyDown={(e) => handleSearchKeyDown(e, true)}
                  className="w-full pl-8 h-8 text-xs bg-secondary/50 border-input focus:bg-background"
                />
              </form>
            </div>

            {/* Navigation links & Dynamic Collapsibles */}
            <div className="py-2 px-3 space-y-1 text-sm font-medium">
              {/* 1. Hàng mới */}
              <Link
                to="/new-arrivals"
                onClick={() => setIsMobileNavOpen(false)}
                className={`flex items-center justify-between px-3 py-2.5 rounded-md transition-colors ${
                  currentNavId === 'hang-moi'
                    ? 'bg-secondary text-foreground font-semibold'
                    : 'text-foreground hover:bg-secondary/60'
                }`}
              >
                <span>Hàng Mới</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Link>

              {/* 2. Nam (Accordion) */}
              <div>
                <button
                  type="button"
                  onClick={() => setMobileExpanded((prev) => (prev === 'men' ? null : 'men'))}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-md transition-colors text-left ${
                    currentNavId === 'nam'
                      ? 'bg-secondary text-foreground font-semibold'
                      : 'text-foreground hover:bg-secondary/60'
                  }`}
                  aria-expanded={mobileExpanded === 'men'}
                >
                  <span>Thời trang Nam</span>
                  <ChevronDown
                    className={`h-4 w-4 text-muted-foreground transition-transform duration-150 ${
                      mobileExpanded === 'men' ? 'rotate-180 text-foreground' : ''
                    }`}
                  />
                </button>
                {mobileExpanded === 'men' && (
                  <div className="pl-4 pr-2 py-1 space-y-1 border-l-2 border-border ml-3 my-1 text-xs">
                    <Link
                      to="/collections/men"
                      onClick={() => setIsMobileNavOpen(false)}
                      className="block px-2 py-1.5 rounded font-semibold text-foreground hover:bg-secondary/50"
                    >
                      Tất cả thời trang nam
                    </Link>
                    {/* Áo Nam & Children */}
                    {aoNamCat && (
                      <>
                        <Link
                          to={`/products?categoryId=${aoNamCat.id}`}
                          onClick={() => setIsMobileNavOpen(false)}
                          className="pt-1 pb-0.5 px-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground hover:text-foreground flex items-center justify-between"
                        >
                          <span>{aoNamCat.name}</span>
                          <ChevronRight className="h-3 w-3" />
                        </Link>
                        {aoNamCat.children?.map((child) => (
                          <Link
                            key={child.id}
                            to={`/products?categoryId=${child.id}`}
                            onClick={() => setIsMobileNavOpen(false)}
                            className="block px-2 py-1 text-muted-foreground hover:text-foreground"
                          >
                            {formatCategoryName(child.slug, child.name)}
                          </Link>
                        ))}
                      </>
                    )}
                    {/* Quần Nam & Children */}
                    {quanNamCat && (
                      <>
                        <Link
                          to={`/products?categoryId=${quanNamCat.id}`}
                          onClick={() => setIsMobileNavOpen(false)}
                          className="pt-2 pb-0.5 px-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground hover:text-foreground flex items-center justify-between"
                        >
                          <span>{quanNamCat.name}</span>
                          <ChevronRight className="h-3 w-3" />
                        </Link>
                        {quanNamCat.children?.map((child) => (
                          <Link
                            key={child.id}
                            to={`/products?categoryId=${child.id}`}
                            onClick={() => setIsMobileNavOpen(false)}
                            className="block px-2 py-1 text-muted-foreground hover:text-foreground"
                          >
                            {child.name}
                          </Link>
                        ))}
                      </>
                    )}
                  </div>
                )}
              </div>

              {/* 3. Nữ (Accordion) */}
              <div>
                <button
                  type="button"
                  onClick={() => setMobileExpanded((prev) => (prev === 'women' ? null : 'women'))}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-md transition-colors text-left ${
                    currentNavId === 'nu'
                      ? 'bg-secondary text-foreground font-semibold'
                      : 'text-foreground hover:bg-secondary/60'
                  }`}
                  aria-expanded={mobileExpanded === 'women'}
                >
                  <span>Thời trang Nữ</span>
                  <ChevronDown
                    className={`h-4 w-4 text-muted-foreground transition-transform duration-150 ${
                      mobileExpanded === 'women' ? 'rotate-180 text-foreground' : ''
                    }`}
                  />
                </button>
                {mobileExpanded === 'women' && (
                  <div className="pl-4 pr-2 py-1 space-y-1 border-l-2 border-border ml-3 my-1 text-xs">
                    <Link
                      to="/collections/women"
                      onClick={() => setIsMobileNavOpen(false)}
                      className="block px-2 py-1.5 rounded font-semibold text-foreground hover:bg-secondary/50"
                    >
                      Tất cả thời trang nữ
                    </Link>
                    {/* Áo Nữ & Children */}
                    {aoNuCat && (
                      <>
                        <Link
                          to={`/products?categoryId=${aoNuCat.id}`}
                          onClick={() => setIsMobileNavOpen(false)}
                          className="pt-1 pb-0.5 px-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground hover:text-foreground flex items-center justify-between"
                        >
                          <span>{aoNuCat.name}</span>
                          <ChevronRight className="h-3 w-3" />
                        </Link>
                        {aoNuCat.children?.map((child) => (
                          <Link
                            key={child.id}
                            to={`/products?categoryId=${child.id}`}
                            onClick={() => setIsMobileNavOpen(false)}
                            className="block px-2 py-1 text-muted-foreground hover:text-foreground"
                          >
                            {child.name}
                          </Link>
                        ))}
                      </>
                    )}
                    {/* Quần Nữ & Children */}
                    {quanNuCat && (
                      <>
                        <Link
                          to={`/products?categoryId=${quanNuCat.id}`}
                          onClick={() => setIsMobileNavOpen(false)}
                          className="pt-2 pb-0.5 px-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground hover:text-foreground flex items-center justify-between"
                        >
                          <span>{quanNuCat.name}</span>
                          <ChevronRight className="h-3 w-3" />
                        </Link>
                        {quanNuCat.children?.map((child) => (
                          <Link
                            key={child.id}
                            to={`/products?categoryId=${child.id}`}
                            onClick={() => setIsMobileNavOpen(false)}
                            className="block px-2 py-1 text-muted-foreground hover:text-foreground"
                          >
                            {child.name}
                          </Link>
                        ))}
                      </>
                    )}
                  </div>
                )}
              </div>

              {/* 4. Phụ kiện (Accordion) */}
              <div>
                <button
                  type="button"
                  onClick={() => setMobileExpanded((prev) => (prev === 'accessories' ? null : 'accessories'))}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-md transition-colors text-left ${
                    currentNavId === 'phu-kien'
                      ? 'bg-secondary text-foreground font-semibold'
                      : 'text-foreground hover:bg-secondary/60'
                  }`}
                  aria-expanded={mobileExpanded === 'accessories'}
                >
                  <span>Phụ Kiện</span>
                  <ChevronDown
                    className={`h-4 w-4 text-muted-foreground transition-transform duration-150 ${
                      mobileExpanded === 'accessories' ? 'rotate-180 text-foreground' : ''
                    }`}
                  />
                </button>
                {mobileExpanded === 'accessories' && (
                  <div className="pl-4 pr-2 py-1 space-y-1 border-l-2 border-border ml-3 my-1 text-xs">
                    <Link
                      to={phuKienCat?.id ? `/products?categoryId=${phuKienCat.id}` : '/collections/accessories'}
                      onClick={() => setIsMobileNavOpen(false)}
                      className="block px-2 py-1.5 rounded font-semibold text-foreground hover:bg-secondary/50"
                    >
                      Tất cả phụ kiện
                    </Link>
                    {phuKienCat?.children?.map((child) => (
                      <Link
                        key={child.id}
                        to={`/products?categoryId=${child.id}`}
                        onClick={() => setIsMobileNavOpen(false)}
                        className="block px-2 py-1 text-muted-foreground hover:text-foreground"
                      >
                        {child.name}
                      </Link>
                    ))}
                  </div>
                )}
              </div>

              {/* 5. Bộ sưu tập */}
              <Link
                to="/collections"
                onClick={() => setIsMobileNavOpen(false)}
                className={`flex items-center justify-between px-3 py-2.5 rounded-md transition-colors ${
                  currentNavId === 'bo-suu-tap'
                    ? 'bg-secondary text-foreground font-semibold'
                    : 'text-foreground hover:bg-secondary/60'
                }`}
              >
                <span>Bộ Sưu Tập</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Link>

              {/* 6. Ưu đãi */}
              <Link
                to="/sale"
                onClick={() => setIsMobileNavOpen(false)}
                className={`flex items-center justify-between px-3 py-2.5 rounded-md transition-colors ${
                  currentNavId === 'uu-dai'
                    ? 'bg-secondary text-foreground font-semibold'
                    : 'text-foreground hover:bg-secondary/60'
                }`}
              >
                <span>Ưu Đãi</span>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Link>
            </div>
          </div>

          {/* Bottom account status */}
          <div className="p-4 border-t border-border bg-secondary/20">
            {isAuthenticated ? (
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 min-w-0">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-foreground text-background font-semibold text-[11px]">
                    {initials || <User className="h-3.5 w-3.5" />}
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-semibold text-foreground truncate">{displayName}</p>
                    <p className="text-[10px] text-muted-foreground truncate">{user?.email || user?.phone}</p>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setIsMobileNavOpen(false);
                    navigate('/account');
                  }}
                  className="text-xs h-7 px-2"
                >
                  Tài khoản
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-2 text-xs">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setIsMobileNavOpen(false);
                    navigate('/login');
                  }}
                  className="w-full h-8 text-xs rounded-md shadow-none"
                >
                  Đăng nhập
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    setIsMobileNavOpen(false);
                    navigate('/register');
                  }}
                  className="w-full h-8 text-xs rounded-md shadow-none"
                >
                  Đăng ký
                </Button>
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </header>
  );
};
