import type {
  DateRangeFilter,
  RevenueTrendParams,
  TopProductsParams,
} from '../reports.types';

export const adminReportKeys = {
  all: ['admin', 'reports'] as const,
  summary: (filter: DateRangeFilter) =>
    [...adminReportKeys.all, 'summary', filter.startDate, filter.endDate] as const,
  revenueTrend: (params: RevenueTrendParams) =>
    [
      ...adminReportKeys.all,
      'revenue-trend',
      params.startDate,
      params.endDate,
      params.granularity ?? 'day',
    ] as const,
  topProducts: (params: TopProductsParams) =>
    [
      ...adminReportKeys.all,
      'top-products',
      params.startDate,
      params.endDate,
      params.limit ?? 10,
    ] as const,
  categorySales: (filter: DateRangeFilter) =>
    [...adminReportKeys.all, 'category-sales', filter.startDate, filter.endDate] as const,
  orderStatusDistribution: (filter: DateRangeFilter) =>
    [
      ...adminReportKeys.all,
      'order-status-distribution',
      filter.startDate,
      filter.endDate,
    ] as const,
};
