import { useQuery } from '@tanstack/react-query';
import { getCustomerSegments } from './dashboard.api';
import { adminDashboardKeys } from './queryKeys';

export function useCustomerSegments() {
  return useQuery({
    queryKey: adminDashboardKeys.customerSegments(),
    queryFn: () => getCustomerSegments(),
  });
}
