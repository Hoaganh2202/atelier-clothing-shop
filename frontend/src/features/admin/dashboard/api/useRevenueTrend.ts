import { useQuery } from '@tanstack/react-query';
import { getRevenueTrend, type RevenueTrendPeriod } from './dashboard.api';
import { adminDashboardKeys } from './queryKeys';

export function useRevenueTrend(period?: RevenueTrendPeriod) {
  return useQuery({
    queryKey: adminDashboardKeys.revenueTrend(period),
    queryFn: () => getRevenueTrend(period),
  });
}
