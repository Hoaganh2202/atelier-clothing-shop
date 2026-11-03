import React, { useState } from 'react';
import {
  ConfigProvider,
  Layout,
  Menu,
  Breadcrumb,
  Button,
  Avatar,
  Dropdown,
  Space,
  Grid,
  type MenuProps,
} from 'antd';
import {
  DashboardOutlined,
  SkinOutlined,
  AppstoreOutlined,
  InboxOutlined,
  ShoppingOutlined,
  CarOutlined,
  FileTextOutlined,
  TeamOutlined,
  TagOutlined,
  PercentageOutlined,
  BarChartOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  LogoutOutlined,
  ShopOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth.store';

const { Header, Sider, Content } = Layout;
const { useBreakpoint } = Grid;

export const AdminLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const screens = useBreakpoint();
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);

  const getRoleLabel = (role?: string): string => {
    const normalized = role?.toLowerCase();
    if (normalized === 'admin') return 'Quản trị viên';
    if (normalized === 'staff') return 'Nhân viên vận hành';
    return role || 'Quản trị viên';
  };

  const userName = user?.name?.trim() || 'Quản trị viên';
  const roleLabel = getRoleLabel(user?.role);
  const avatarInitial = (userName.charAt(0) || 'A').toUpperCase();

  const getBreadcrumbItems = (pathname: string) => {
    const homeItem = {
      title: <Link to="/admin">Trang chủ</Link>,
    };

    if (pathname === '/admin' || pathname === '/admin/dashboard') {
      return [
        { title: 'Trang chủ' },
        { title: 'Tổng quan vận hành' },
      ];
    }

    if (pathname === '/admin/products') {
      return [
        homeItem,
        { title: 'Sản phẩm' },
      ];
    }

    if (pathname === '/admin/products/new') {
      return [
        homeItem,
        { title: <Link to="/admin/products">Sản phẩm</Link> },
        { title: 'Thêm mới sản phẩm' },
      ];
    }

    if (/^\/admin\/products\/[^/]+\/edit$/.test(pathname)) {
      return [
        homeItem,
        { title: <Link to="/admin/products">Sản phẩm</Link> },
        { title: 'Chỉnh sửa sản phẩm' },
      ];
    }

    if (pathname === '/admin/categories') {
      return [
        homeItem,
        { title: 'Danh mục' },
      ];
    }

    if (pathname.startsWith('/admin/orders')) {
      return [
        homeItem,
        { title: 'Quản lý đơn hàng' },
      ];
    }

    if (pathname.startsWith('/admin/invoices')) {
      return [
        homeItem,
        { title: 'Hóa đơn điện tử' },
      ];
    }

    if (pathname.startsWith('/admin/customers')) {
      return [
        homeItem,
        { title: 'Khách hàng' },
      ];
    }

    if (pathname.startsWith('/admin/promotions')) {
      return [
        homeItem,
        { title: 'Khuyến mãi' },
      ];
    }

    if (pathname.startsWith('/admin/inventory')) {
      return [
        homeItem,
        { title: 'Kho hàng & Tồn kho' },
      ];
    }

    if (pathname.startsWith('/admin/shipping')) {
      return [
        homeItem,
        { title: 'Vận chuyển & Giao nhận' },
      ];
    }

    if (pathname.startsWith('/admin/reports')) {
      return [
        homeItem,
        { title: 'Báo cáo & Thống kê' },
      ];
    }

    if (pathname.startsWith('/admin/settings')) {
      return [
        homeItem,
        { title: 'Cài đặt hệ thống' },
      ];
    }

    return [homeItem];
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'group-overview',
      type: 'group',
      label: 'TỔNG QUAN',
      children: [
        {
          key: '/admin',
          icon: <DashboardOutlined />,
          label: 'Tổng quan vận hành',
        },
      ],
    },
    {
      key: 'group-retail',
      type: 'group',
      label: 'BÁN LẺ & SẢN PHẨM',
      children: [
        {
          key: '/admin/products',
          icon: <SkinOutlined />,
          label: 'Sản phẩm',
        },
        {
          key: '/admin/categories',
          icon: <AppstoreOutlined />,
          label: 'Danh mục hàng hóa',
        },
        {
          key: '/admin/inventory',
          icon: <InboxOutlined />,
          label: 'Kho hàng & Tồn kho',
        },
        {
          key: '/admin/orders',
          icon: <ShoppingOutlined />,
          label: 'Quản lý đơn hàng',
        },
        {
          key: '/admin/invoices',
          icon: <FileTextOutlined />,
          label: 'Hóa đơn điện tử',
        },
        {
          key: '/admin/shipping',
          icon: <CarOutlined />,
          label: 'Vận chuyển & Giao nhận',
        },
      ],
    },
    {
      key: 'group-customers',
      type: 'group',
      label: 'KHÁCH HÀNG & PHÂN TÍCH',
      children: [
        {
          key: '/admin/customers',
          icon: <TeamOutlined />,
          label: 'Khách hàng',
        },
        {
          key: '/admin/vouchers',
          icon: <TagOutlined />,
          label: 'Mã khuyến mãi',
        },
        {
          key: '/admin/promotions',
          icon: <PercentageOutlined />,
          label: 'Khuyến mãi',
        },
        {
          key: '/admin/reports',
          icon: <BarChartOutlined />,
          label: 'Báo cáo & Thống kê',
        },
      ],
    },
    {
      key: 'group-system',
      type: 'group',
      label: 'HỆ THỐNG',
      children: [
        {
          key: '/admin/settings',
          icon: <SettingOutlined />,
          label: 'Cài đặt hệ thống',
        },
      ],
    },
  ];

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'storefront',
      icon: <ShopOutlined />,
      label: 'Về trang cửa hàng',
      onClick: () => navigate('/'),
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      danger: true,
      label: 'Đăng xuất',
      onClick: () => {
        logout();
        navigate('/login', { replace: true });
      },
    },
  ];

  const getActiveMenuKey = () => {
    const path = location.pathname;
    if (path.startsWith('/admin/products')) return '/admin/products';
    if (path.startsWith('/admin/categories')) return '/admin/categories';
    if (path.startsWith('/admin/inventory')) return '/admin/inventory';
    if (path.startsWith('/admin/orders')) return '/admin/orders';
    if (path.startsWith('/admin/invoices')) return '/admin/invoices';
    if (path.startsWith('/admin/shipping')) return '/admin/shipping';
    if (path.startsWith('/admin/customers')) return '/admin/customers';
    if (path.startsWith('/admin/vouchers')) return '/admin/vouchers';
    if (path.startsWith('/admin/promotions')) return '/admin/promotions';
    if (path.startsWith('/admin/reports')) return '/admin/reports';
    if (path.startsWith('/admin/settings')) return '/admin/settings';
    return '/admin';
  };

  const activeKey = getActiveMenuKey();

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 6,
          colorPrimary: '#18181B',
          fontFamily:
            'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        },
        components: {
          Layout: {
            siderBg: '#FFFFFF',
            headerBg: '#FFFFFF',
            bodyBg: '#F4F4F5',
          },
          Menu: {
            itemBorderRadius: 6,
            subMenuItemBorderRadius: 6,
            activeBarBorderWidth: 0,
            itemSelectedBg: '#18181B',
            itemSelectedColor: '#FFFFFF',
          },
          Card: {
            borderRadiusLG: 6,
          },
          Table: {
            borderRadiusLG: 6,
          },
        },
      }}
    >
      <Layout style={{ minHeight: '100vh' }}>
        {/* Sider Sidebar */}
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={(value) => setCollapsed(value)}
          breakpoint="lg"
          onBreakpoint={(broken) => {
            setCollapsed(broken);
          }}
          trigger={null}
          width={250}
          collapsedWidth={80}
          theme="light"
          style={{
            borderRight: '1px solid #E4E4E7',
            position: 'sticky',
            top: 0,
            left: 0,
            height: '100vh',
            overflowY: 'auto',
            zIndex: 20,
          }}
        >
          {/* Logo / Brand Header */}
          <div
            style={{
              height: 64,
              display: 'flex',
              alignItems: 'center',
              padding: collapsed ? '0 16px' : '0 20px',
              borderBottom: '1px solid #E4E4E7',
              gap: 12,
            }}
          >
            <div
              style={{
                width: 32,
                height: 32,
                backgroundColor: '#18181B',
                color: '#FFFFFF',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: 16,
                letterSpacing: '0.05em',
                flexShrink: 0,
              }}
            >
              A
            </div>
            {!collapsed && (
              <div style={{ overflow: 'hidden', whiteSpace: 'nowrap' }}>
                <div
                  style={{
                    fontWeight: 700,
                    fontSize: 15,
                    letterSpacing: '0.04em',
                    color: '#09090B',
                    lineHeight: 1.2,
                  }}
                >
                  ATELIER
                </div>
                <div style={{ fontSize: 11, color: '#71717A' }}>
                  Hệ thống Quản trị
                </div>
              </div>
            )}
          </div>

          {/* Navigation Menu */}
          <Menu
            mode="inline"
            selectedKeys={[activeKey]}
            items={menuItems}
            onClick={({ key }) => {
              if (key.startsWith('/admin')) {
                navigate(key);
              }
            }}
            style={{ borderRight: 0, padding: '8px 4px' }}
          />


        </Sider>

        {/* Main Section */}
        <Layout style={{ minWidth: 0, overflow: 'hidden' }}>
          {/* Header Topbar */}
          <Header
            style={{
              padding: screens.lg ? '0 24px' : '0 16px',
              borderBottom: '1px solid #E4E4E7',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              height: 64,
              position: 'sticky',
              top: 0,
              zIndex: 10,
              minWidth: 0,
              gap: 12,
            }}
          >
            {/* Left Header: Toggle Button & Breadcrumb */}
            <Space size={screens.lg ? 'middle' : 'small'} style={{ minWidth: 0, flexShrink: 0 }}>
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
                style={{ fontSize: 16 }}
              />
              <Breadcrumb items={getBreadcrumbItems(location.pathname)} />
            </Space>

            {/* Right Header: User */}
            <Space
              orientation="horizontal"
              size={screens.lg ? 'middle' : 'small'}
              style={{ minWidth: 0, flexShrink: 0, justifyContent: 'flex-end' }}
            >
              <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
                <Space style={{ cursor: 'pointer', padding: screens.lg ? '4px 8px' : '4px 2px' }}>
                  <Avatar
                    style={{ backgroundColor: '#18181B' }}
                    icon={!user?.name?.trim() ? <UserOutlined /> : undefined}
                  >
                    {user?.name?.trim() ? avatarInitial : null}
                  </Avatar>
                  {screens.lg && (
                    <div style={{ textAlign: 'left', lineHeight: 1.2 }}>
                      <div style={{ fontWeight: 600, fontSize: 13, color: '#09090B' }}>
                        {userName}
                      </div>
                      <div style={{ fontSize: 11, color: '#71717A' }}>
                        {roleLabel}
                      </div>
                    </div>
                  )}
                </Space>
              </Dropdown>
            </Space>
          </Header>

          {/* Page Content */}
          <Content
            style={{
              margin: screens.lg ? '24px 24px 32px 24px' : '16px 16px 24px 16px',
              minWidth: 0,
            }}
          >
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
};

export default AdminLayout;
