import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { HomePage } from '@/features/product/pages/HomePage';
import { ProductCatalogPage } from '@/features/product/pages/ProductCatalogPage';
import { ProductDetailPage } from '@/features/product/pages/ProductDetailPage';
import { MenCollectionPage } from '@/features/product/pages/MenCollectionPage';
import { WomenCollectionPage } from '@/features/product/pages/WomenCollectionPage';
import { AccessoriesPage } from '@/features/product/pages/AccessoriesPage';
import { NewArrivalsPage } from '@/features/product/pages/NewArrivalsPage';
import { CollectionsLandingPage } from '@/features/product/pages/CollectionsLandingPage';
import { SalePage } from '@/features/product/pages/SalePage';
import { CartPage } from '@/features/cart/pages/CartPage';
import { CheckoutPage } from '@/features/order/pages/CheckoutPage';
import { CheckoutSuccessPage } from '@/features/order/pages/CheckoutSuccessPage';
import { OrderHistoryPage } from '@/features/order/pages/OrderHistoryPage';
import { OrderDetailPage } from '@/features/order/pages/OrderDetailPage';
import { CustomerInvoicePreviewPage } from '@/features/invoice';
import { AdminLayout } from '@/layouts/AdminLayout';
import { AdminRoute } from '@/routes/guards/AdminRoute';
import { DashboardPage } from '@/features/admin/dashboard/pages/DashboardPage';
import { ProductListPage } from '@/features/admin/product/pages/ProductListPage';
import { ProductFormPage } from '@/features/admin/product/pages/ProductFormPage';
import { CategoryListPage } from '@/features/admin/category/pages/CategoryListPage';
import { CustomerListPage } from '@/features/admin/customer/CustomerListPage';
import { VoucherListPage } from '@/features/admin/voucher/pages/VoucherListPage';
import { PromotionListPage } from '@/features/admin/promotion/pages/PromotionListPage';
import { InventoryListPage } from '@/features/admin/inventory';
import { OrderListPage } from '@/features/admin/order/pages/OrderListPage';
import { InvoiceListPage } from '@/features/admin/invoice';
import { ShippingListPage } from '@/features/admin/shipping';
import { ReportsPage } from '@/features/admin/reports';
import { SettingsPage } from '@/features/admin/settings';
import LoginPage from '@/features/auth/pages/LoginPage';
import RegisterPage from '@/features/auth/pages/RegisterPage';
import { AddressBookPage } from '@/features/address/AddressBookPage';
import { AccountLayout } from '@/features/account/layouts/AccountLayout';
import { AccountDashboardPage } from '@/features/account/pages/AccountDashboardPage';
import { ProfilePage } from '@/features/account/pages/ProfilePage';
import { ChangePasswordPage } from '@/features/account/pages/ChangePasswordPage';
import { useAuthStore } from '@/stores/auth.store';

export const App: React.FC = () => {
  const initSession = useAuthStore((state) => state.initSession);

  useEffect(() => {
    initSession();
  }, [initSession]);

  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        {/* Storefront customer routes */}
        <Route path="/" element={<HomePage />} />

        <Route path="/new-arrivals" element={<NewArrivalsPage />} />
        <Route path="/collections" element={<CollectionsLandingPage />} />
        <Route path="/collections/men" element={<MenCollectionPage />} />
        <Route path="/collections/women" element={<WomenCollectionPage />} />
        <Route path="/collections/accessories" element={<AccessoriesPage />} />
        <Route path="/sale" element={<SalePage />} />
        <Route path="/products" element={<ProductCatalogPage />} />
        <Route path="/products/:slug" element={<ProductDetailPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/checkout/success/:orderId" element={<CheckoutSuccessPage />} />
        {/* Customer Account routes with unified AccountLayout */}
        <Route path="/account" element={<AccountLayout />}>
          <Route index element={<AccountDashboardPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="change-password" element={<ChangePasswordPage />} />
          <Route path="addresses" element={<AddressBookPage />} />
          <Route path="orders" element={<OrderHistoryPage />} />
        </Route>
        <Route path="/account/orders/:orderId" element={<OrderDetailPage />} />
        <Route path="/account/orders/:orderId/invoice" element={<CustomerInvoicePreviewPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Admin management routes */}
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <AdminLayout />
            </AdminRoute>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="dashboard" element={<Navigate to="/admin" replace />} />
          
          {/* Admin Product Management */}
          <Route path="products" element={<ProductListPage />} />
          <Route path="products/new" element={<ProductFormPage />} />
          <Route path="products/:id/edit" element={<ProductFormPage />} />

          {/* Admin Category Management */}
          <Route path="categories" element={<CategoryListPage />} />
          <Route path="orders" element={<OrderListPage />} />
          <Route path="invoices" element={<InvoiceListPage />} />
          <Route path="shipping" element={<ShippingListPage />} />
          <Route path="customers" element={<CustomerListPage />} />
          <Route path="vouchers" element={<VoucherListPage />} />
          <Route path="promotions" element={<PromotionListPage />} />
          <Route path="inventory" element={<InventoryListPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="settings" element={<SettingsPage />} />

          {/* Catch-all admin fallback to dashboard */}
          <Route path="*" element={<DashboardPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};

export default App;
