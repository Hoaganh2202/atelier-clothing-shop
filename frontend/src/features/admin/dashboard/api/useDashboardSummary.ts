import { useQuery } from '@tanstack/react-query';
import { getDashboardSummary, type DashboardPeriod } from './dashboard.api';
import { adminDashboardKeys } from './queryKeys';

export function useDashboardSummary(period?: DashboardPeriod) {
  return useQuery({
    queryKey: adminDashboardKeys.summary(period),
    queryFn: () => getDashboardSummary(period),
  });
}
