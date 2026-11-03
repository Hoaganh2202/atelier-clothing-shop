import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAdminCustomerById,
  getAdminCustomers,
  updateAdminCustomerStatus,
} from './customer.api';
import type {
  CustomerListParams,
  UpdateCustomerStatusPayload,
} from './customer.types';

export const adminCustomerKeys = {
  all: ['admin', 'customers'] as const,
  lists: () => [...adminCustomerKeys.all, 'list'] as const,
  list: (params?: CustomerListParams) => [...adminCustomerKeys.lists(), params] as const,
  details: () => [...adminCustomerKeys.all, 'detail'] as const,
  detail: (id?: number) => [...adminCustomerKeys.details(), id] as const,
};

export function useAdminCustomers(params?: CustomerListParams) {
  return useQuery({
    queryKey: adminCustomerKeys.list(params),
    queryFn: () => getAdminCustomers(params),
    placeholderData: keepPreviousData,
  });
}

export function useAdminCustomer(id?: number) {
  return useQuery({
    queryKey: adminCustomerKeys.detail(id),
    queryFn: () => getAdminCustomerById(id!),
    enabled: Boolean(id && id > 0),
  });
}

interface UpdateCustomerStatusVariables {
  id: number;
  payload: UpdateCustomerStatusPayload;
}

export function useUpdateCustomerStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdateCustomerStatusVariables) =>
      updateAdminCustomerStatus(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminCustomerKeys.all });
      queryClient.invalidateQueries({ queryKey: adminCustomerKeys.detail(variables.id) });
    },
  });
}
