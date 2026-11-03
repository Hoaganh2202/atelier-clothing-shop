import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAdminShippingOrderDetail,
  getAdminShippingOrders,
  getAdminShippingSummary,
  updateAdminShippingStatus,
} from './shipping.api';
import type {
  ShippingListParams,
  ShippingStatusUpdateRequest,
} from '../shipping.types';

export const adminShippingKeys = {
  all: ['admin', 'shipping'] as const,
  summaries: () => [...adminShippingKeys.all, 'summary'] as const,
  lists: () => [...adminShippingKeys.all, 'list'] as const,
  list: (params?: ShippingListParams) => [...adminShippingKeys.lists(), params] as const,
  details: () => [...adminShippingKeys.all, 'detail'] as const,
  detail: (id?: number) => [...adminShippingKeys.details(), id] as const,
};

export function useAdminShippingSummary() {
  return useQuery({
    queryKey: adminShippingKeys.summaries(),
    queryFn: getAdminShippingSummary,
  });
}

export function useAdminShippingOrders(params?: ShippingListParams) {
  return useQuery({
    queryKey: adminShippingKeys.list(params),
    queryFn: () => getAdminShippingOrders(params),
    placeholderData: keepPreviousData,
  });
}

export function useAdminShippingOrderDetail(orderId?: number) {
  return useQuery({
    queryKey: adminShippingKeys.detail(orderId),
    queryFn: () => getAdminShippingOrderDetail(orderId!),
    enabled: Boolean(orderId && orderId > 0),
  });
}

interface UpdateShippingStatusVariables {
  orderId: number;
  payload: ShippingStatusUpdateRequest;
}

export function useUpdateAdminShippingStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, payload }: UpdateShippingStatusVariables) =>
      updateAdminShippingStatus(orderId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminShippingKeys.all });
      queryClient.invalidateQueries({ queryKey: adminShippingKeys.detail(variables.orderId) });
    },
  });
}
