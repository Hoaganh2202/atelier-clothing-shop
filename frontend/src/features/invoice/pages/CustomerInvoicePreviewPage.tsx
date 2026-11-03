import React from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  FileQuestion,
  FileText,
  Printer,
  RotateCcw,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuthStore } from '@/stores/auth.store';
import { getCustomerInvoiceErrorMessage } from '../api/customerInvoice.api';
import { useOrderInvoice } from '../hooks/customerInvoice.hooks';
import { InvoicePrintView } from '../components/InvoicePrintView';

export const CustomerInvoicePreviewPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  const user = useAuthStore((state) => state.user);
  const isInitializing = useAuthStore((state) => state.isInitializing);

  const numericOrderId = orderId ? parseInt(orderId, 10) : NaN;
  const isIdValid = !Number.isNaN(numericOrderId) && numericOrderId > 0;

  const isCustomer = user?.role?.toLowerCase() === 'customer';
  const {
    data: invoice,
    isLoading,
    isError,
    error,
    refetch,
    isFetching,
  } = useOrderInvoice(isIdValid ? numericOrderId : 0, {
    enabled: isIdValid && isCustomer && !isInitializing,
  });

  const handlePrint = () => {
    window.print();
  };

  if (isInitializing) {
    return (
      <div className="min-h-screen flex flex-col bg-background text-foreground">
        <Header />
        <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
          <div className="space-y-4">
            <Skeleton className="h-6 w-60 rounded-[6px]" />
            <Skeleton className="h-12 w-full rounded-[6px]" />
            <Skeleton className="h-[500px] w-full rounded-[6px]" />
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

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      {/* Print Stylesheet injection to format A4 cleanly */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #invoice-printable-area, #invoice-printable-area * {
            visibility: visible;
          }
          #invoice-printable-area {
            position: absolute;
            left: 0;
            top: 0;
            width: 100% !important;
            margin: 0 !important;
            padding: 10mm !important;
            border: none !important;
            box-shadow: none !important;
            background: #ffffff !important;
          }
          @page {
            size: A4 portrait;
            margin: 10mm;
          }
        }
      `}</style>

      <div className="print:hidden">
        <Header />
      </div>

      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
        <div className="flex flex-col gap-6">
          {/* Breadcrumb Navigation */}
          <nav
            aria-label="Breadcrumb"
            className="flex items-center gap-2 text-[13px] text-muted-foreground flex-wrap print:hidden"
          >
            <Link to="/" className="hover:text-foreground transition-colors">
              Trang chủ
            </Link>
            <span>/</span>
            <Link to="/account/orders" className="hover:text-foreground transition-colors">
              Đơn hàng của tôi
            </Link>
            <span>/</span>
            <Link
              to={`/account/orders/${numericOrderId}`}
              className="hover:text-foreground transition-colors"
            >
              #{numericOrderId}
            </Link>
            <span>/</span>
            <span className="font-medium text-foreground">Hóa đơn điện tử</span>
          </nav>

          {/* Action Toolbar */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-5 print:hidden">
            <div>
              <div className="flex items-center gap-2.5">
                <FileText className="h-5 w-5 text-foreground" />
                <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground">
                  Hóa đơn điện tử — Đơn hàng #{numericOrderId}
                </h1>
              </div>
              <p className="text-xs text-muted-foreground mt-1">
                Xem và in chứng từ hóa đơn điện tử phục vụ lưu trữ hoặc đối soát thông tin
              </p>
            </div>

            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => navigate(`/account/orders/${numericOrderId}`)}
                className="rounded-[6px] text-xs h-8 px-3 shadow-none border-border gap-1.5"
              >
                <ArrowLeft className="h-3.5 w-3.5" />
                Quay lại đơn hàng
              </Button>

              {invoice && (
                <Button
                  type="button"
                  size="sm"
                  onClick={handlePrint}
                  className="rounded-[6px] text-xs h-8 px-3 shadow-none gap-1.5"
                >
                  <Printer className="h-3.5 w-3.5" />
                  In hóa đơn / Lưu PDF
                </Button>
              )}
            </div>
          </div>

          {/* Body Content */}
          {isLoading ? (
            <div className="space-y-4 print:hidden">
              <Skeleton className="h-20 w-full rounded-[6px]" />
              <Skeleton className="h-[400px] w-full rounded-[6px]" />
              <Skeleton className="h-24 w-full rounded-[6px]" />
            </div>
          ) : isError || !invoice ? (
            <Card className="rounded-[6px] border-border bg-card shadow-none py-14 text-center print:hidden">
              <CardContent className="flex flex-col items-center gap-3">
                <FileQuestion className="h-10 w-10 text-muted-foreground" />
                <h2 className="text-base font-semibold text-foreground">
                  Chưa tìm thấy hóa đơn điện tử cho đơn hàng #{numericOrderId}
                </h2>
                <p className="text-xs text-muted-foreground max-w-md">
                  {getCustomerInvoiceErrorMessage(
                    error,
                    'Đơn hàng này chưa có hóa đơn điện tử hoặc hóa đơn đang trong quá trình phát hành. Nếu bạn vừa thanh toán, vui lòng chờ trong giây lát và làm mới lại trang.'
                  )}
                </p>
                <div className="flex items-center gap-2 mt-3">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={isFetching}
                    onClick={() => refetch()}
                    className="rounded-[6px] gap-2 shadow-none"
                  >
                    <RotateCcw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
                    Tải lại trang
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => navigate(`/account/orders/${numericOrderId}`)}
                    className="rounded-[6px] gap-2 shadow-none"
                  >
                    <ArrowLeft className="h-3.5 w-3.5" />
                    Quay lại đơn hàng
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="w-full">
              <InvoicePrintView invoice={invoice} />
            </div>
          )}
        </div>
      </main>

      <div className="print:hidden">
        <Footer />
      </div>
    </div>
  );
};
