import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/stores/auth.store';
import { cancelMyOrder, getMyOrder, getMyOrders } from '../api/order.api';
import type { GetMyOrdersParams } from '../order.types';

export const orderKeys = {
  all: ['orders'] as const,
  lists: () => [...orderKeys.all, 'list'] as const,
  list: (params: GetMyOrdersParams) => [...orderKeys.lists(), params] as const,
  details: () => [...orderKeys.all, 'detail'] as const,
  detail: (id: number) => [...orderKeys.details(), id] as const,
};

export function useMyOrders(params: GetMyOrdersParams = {}) {
  const user = useAuthStore((state) => state.user);
  const isInitializing = useAuthStore((state) => state.isInitializing);
  const isCustomer = user?.role?.toLowerCase() === 'customer';

  return useQuery({
    queryKey: orderKeys.list(params),
    queryFn: () => getMyOrders(params),
    enabled: Boolean(user) && isCustomer && !isInitializing,
    placeholderData: keepPreviousData,
  });
}

export function useMyOrder(orderId: number) {
  const user = useAuthStore((state) => state.user);
  const isInitializing = useAuthStore((state) => state.isInitializing);
  const isCustomer = user?.role?.toLowerCase() === 'customer';

  return useQuery({
    queryKey: orderKeys.detail(orderId),
    queryFn: () => getMyOrder(orderId),
    enabled: Boolean(orderId && !Number.isNaN(orderId) && user && isCustomer && !isInitializing),
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderId: number) => cancelMyOrder(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
  });
}
