export type RevenueGranularity = 'day' | 'week' | 'month';

export interface DateRangeFilter {
  startDate: string; // YYYY-MM-DD
  endDate: string;   // YYYY-MM-DD
}

export interface RevenueTrendParams extends DateRangeFilter {
  granularity?: RevenueGranularity;
}

export interface TopProductsParams extends DateRangeFilter {
  limit?: number; // 1 - 100, default 10
}

export interface ReportSummaryResponse {
  grossRevenue: number;
  netRevenue: number;
  totalDiscount: number;
  totalShippingFee: number;
  completedOrderCount: number;
  averageOrderValue: number;
}

export interface RevenueReportPointResponse {
  period: string; // "YYYY-MM-DD" for day, "YYYY-Www" for week, "YYYY-MM" for month
  netRevenue: number;
  grossRevenue: number;
  completedOrderCount: number;
}

export interface TopProductReportResponse {
  productId: number;
  productName: string;
  totalQuantitySold: number;
  totalRevenue: number;
}

export interface CategorySalesReportResponse {
  categoryId: number;
  categoryName: string;
  totalQuantitySold: number;
  totalRevenue: number;
  percentage: number;
}

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'SHIPPING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface OrderStatusDistributionResponse {
  status: OrderStatus;
  statusLabel: string;
  orderCount: number;
  percentage: number;
}

export interface ApiErrorResponse {
  status?: number;
  error?: string;
  message?: string;
  timestamp?: string;
  path?: string;
}
