import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type {
  ApiErrorResponse,
  CategorySalesReportResponse,
  DateRangeFilter,
  OrderStatusDistributionResponse,
  ReportSummaryResponse,
  RevenueReportPointResponse,
  RevenueTrendParams,
  TopProductReportResponse,
  TopProductsParams,
} from '../reports.types';

export function getReportApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    if (error.response?.status === 403) {
      return 'Bạn không có quyền truy cập báo cáo này (yêu cầu ADMIN hoặc STAFF)';
    }
    const serverMessage = error.response?.data?.message;
    if (serverMessage && serverMessage.trim().length > 0) {
      return serverMessage;
    }
  }
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallbackMessage;
}

export async function getReportSummary(
  filter: DateRangeFilter
): Promise<ReportSummaryResponse> {
  const response = await apiClient.get<ReportSummaryResponse>(
    '/admin/reports/summary',
    {
      params: {
        startDate: filter.startDate,
        endDate: filter.endDate,
      },
    }
  );
  return response.data;
}

export async function getRevenueTrend(
  params: RevenueTrendParams
): Promise<RevenueReportPointResponse[]> {
  const response = await apiClient.get<RevenueReportPointResponse[]>(
    '/admin/reports/revenue',
    {
      params: {
        startDate: params.startDate,
        endDate: params.endDate,
        granularity: params.granularity || 'day',
      },
    }
  );
  return response.data;
}

export async function getTopProducts(
  params: TopProductsParams
): Promise<TopProductReportResponse[]> {
  const response = await apiClient.get<TopProductReportResponse[]>(
    '/admin/reports/top-products',
    {
      params: {
        startDate: params.startDate,
        endDate: params.endDate,
        limit: params.limit ?? 10,
      },
    }
  );
  return response.data;
}

export async function getCategorySales(
  filter: DateRangeFilter
): Promise<CategorySalesReportResponse[]> {
  const response = await apiClient.get<CategorySalesReportResponse[]>(
    '/admin/reports/category-sales',
    {
      params: {
        startDate: filter.startDate,
        endDate: filter.endDate,
      },
    }
  );
  return response.data;
}

export async function getOrderStatusDistribution(
  filter: DateRangeFilter
): Promise<OrderStatusDistributionResponse[]> {
  const response = await apiClient.get<OrderStatusDistributionResponse[]>(
    '/admin/reports/order-status-distribution',
    {
      params: {
        startDate: filter.startDate,
        endDate: filter.endDate,
      },
    }
  );
  return response.data;
}
