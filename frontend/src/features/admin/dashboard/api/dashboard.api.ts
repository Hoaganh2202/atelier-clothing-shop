import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type { components } from '@/api/schema';

export type DashboardSummary = components['schemas']['DashboardSummaryResponse'] & {
  revenueTotal?: number;
  revenueChangeRate?: number;
  newOrdersCount?: number;
  newOrdersChangeRate?: number;
};

export type DashboardPeriod = 'today' | '7days' | '30days' | 'quarter';

export type RevenueTrendPeriod = '7days' | '30days' | 'year';

export interface RevenueTrendPoint {
  date: string;
  revenue: number;
  orders: number;
}

export interface CustomerSegmentItem {
  id: string;
  name: string;
  count: number;
  percentage: number;
  color: string;
  description: string;
}

export type LowStockVariantItem = components['schemas']['LowStockVariantResponse'];
export type LowStockVariantsPageResponse = components['schemas']['PageResponseLowStockVariantResponse'];

export interface LowStockVariantsParams {
  threshold?: number;
  page?: number;
  size?: number;
}

export type ApiErrorResponse = components['schemas']['ErrorResponse'];

export function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
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

export async function getDashboardSummary(period?: DashboardPeriod): Promise<DashboardSummary> {
  const response = await apiClient.get<DashboardSummary>('/admin/dashboard/summary', {
    params: period ? { period } : undefined,
  });
  return response.data;
}

export async function getRevenueTrend(
  period?: RevenueTrendPeriod
): Promise<RevenueTrendPoint[]> {
  const response = await apiClient.get<RevenueTrendPoint[]>('/admin/dashboard/revenue-trend', {
    params: period ? { period } : undefined,
  });
  return response.data;
}

export async function getCustomerSegments(): Promise<CustomerSegmentItem[]> {
  const response = await apiClient.get<CustomerSegmentItem[]>('/admin/dashboard/customer-segments');
  return response.data;
}

export async function getLowStockVariants(
  params?: LowStockVariantsParams
): Promise<LowStockVariantsPageResponse> {
  const response = await apiClient.get<LowStockVariantsPageResponse>(
    '/admin/products/variants/low-stock',
    { params }
  );
  return response.data;
}
