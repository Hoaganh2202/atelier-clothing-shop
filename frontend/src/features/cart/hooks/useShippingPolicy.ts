import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useCartStore } from '../cart.store';
import type { ShippingPolicy } from '../cart.types';

export function useShippingPolicy(options?: { refetchOnMount?: boolean | 'always' }) {
  const setShippingPolicy = useCartStore((s) => s.setShippingPolicy);

  return useQuery<ShippingPolicy>({
    queryKey: ['settings', 'shipping-policy'],
    queryFn: async () => {
      const response = await apiClient.get<ShippingPolicy>('/settings/shipping');
      setShippingPolicy(response.data);
      return response.data;
    },
    staleTime: 60 * 1000,
    refetchOnMount: options?.refetchOnMount ?? true,
  });
}
