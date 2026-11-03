import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAdminOrderById,
  getAdminOrders,
  updateAdminOrderStatus,
} from './order.api';
import type {
  OrderListParams,
  OrderStatusUpdateRequest,
} from '../order.types';

export const adminOrderKeys = {
  all: ['admin', 'orders'] as const,
  lists: () => [...adminOrderKeys.all, 'list'] as const,
  list: (params?: OrderListParams) => [...adminOrderKeys.lists(), params] as const,
  details: () => [...adminOrderKeys.all, 'detail'] as const,
  detail: (id?: number) => [...adminOrderKeys.details(), id] as const,
};

export function useAdminOrders(params?: OrderListParams) {
  return useQuery({
    queryKey: adminOrderKeys.list(params),
    queryFn: () => getAdminOrders(params),
    placeholderData: keepPreviousData,
  });
}

export function useAdminOrder(id?: number) {
  return useQuery({
    queryKey: adminOrderKeys.detail(id),
    queryFn: () => getAdminOrderById(id!),
    enabled: Boolean(id && id > 0),
  });
}

interface UpdateOrderStatusVariables {
  id: number;
  payload: OrderStatusUpdateRequest;
}

export function useUpdateAdminOrderStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdateOrderStatusVariables) =>
      updateAdminOrderStatus(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminOrderKeys.all });
      queryClient.invalidateQueries({ queryKey: adminOrderKeys.detail(variables.id) });
    },
  });
}
