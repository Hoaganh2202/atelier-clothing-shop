import type { DashboardPeriod, LowStockVariantsParams, RevenueTrendPeriod } from './dashboard.api';

export const adminDashboardKeys = {
  all: ['admin', 'dashboard'] as const,
  summary: (period?: DashboardPeriod) =>
    [...adminDashboardKeys.all, 'summary', period ?? '30days'] as const,
  revenueTrend: (period?: RevenueTrendPeriod) =>
    [...adminDashboardKeys.all, 'revenue-trend', period ?? '30days'] as const,
  customerSegments: () =>
    [...adminDashboardKeys.all, 'customer-segments'] as const,
  lowStock: (params?: LowStockVariantsParams) =>
    [...adminDashboardKeys.all, 'low-stock', params] as const,
};
