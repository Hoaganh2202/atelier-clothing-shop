import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getLowStockVariants, type LowStockVariantsParams } from './dashboard.api';
import { adminDashboardKeys } from './queryKeys';

export function useLowStockVariants(params?: LowStockVariantsParams) {
  return useQuery({
    queryKey: adminDashboardKeys.lowStock(params),
    queryFn: () => getLowStockVariants(params),
    placeholderData: keepPreviousData,
  });
}
