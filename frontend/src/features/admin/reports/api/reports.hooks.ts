import { useQuery } from '@tanstack/react-query';
import {
  getCategorySales,
  getOrderStatusDistribution,
  getReportSummary,
  getRevenueTrend,
  getTopProducts,
} from './reports.api';
import { adminReportKeys } from './reports.keys';
import type {
  DateRangeFilter,
  RevenueTrendParams,
  TopProductsParams,
} from '../reports.types';

export function useReportSummary(filter: DateRangeFilter) {
  const isEnabled = Boolean(filter.startDate && filter.endDate);
  return useQuery({
    queryKey: adminReportKeys.summary(filter),
    queryFn: () => getReportSummary(filter),
    enabled: isEnabled,
    staleTime: 60 * 1000,
  });
}

export function useRevenueTrend(params: RevenueTrendParams) {
  const isEnabled = Boolean(params.startDate && params.endDate);
  return useQuery({
    queryKey: adminReportKeys.revenueTrend(params),
    queryFn: () => getRevenueTrend(params),
    enabled: isEnabled,
    staleTime: 60 * 1000,
  });
}

export function useTopProducts(params: TopProductsParams) {
  const isEnabled = Boolean(params.startDate && params.endDate);
  return useQuery({
    queryKey: adminReportKeys.topProducts(params),
    queryFn: () => getTopProducts(params),
    enabled: isEnabled,
    staleTime: 60 * 1000,
  });
}

export function useCategorySales(filter: DateRangeFilter) {
  const isEnabled = Boolean(filter.startDate && filter.endDate);
  return useQuery({
    queryKey: adminReportKeys.categorySales(filter),
    queryFn: () => getCategorySales(filter),
    enabled: isEnabled,
    staleTime: 60 * 1000,
  });
}

export function useOrderStatusDistribution(filter: DateRangeFilter) {
  const isEnabled = Boolean(filter.startDate && filter.endDate);
  return useQuery({
    queryKey: adminReportKeys.orderStatusDistribution(filter),
    queryFn: () => getOrderStatusDistribution(filter),
    enabled: isEnabled,
    staleTime: 60 * 1000,
  });
}
