import { useMutation } from '@tanstack/react-query';
import { checkoutOrder } from '../api/order.api';
import type { CheckoutRequest, CheckoutResponse } from '../order.types';

export function useCheckout() {
  return useMutation<CheckoutResponse, unknown, CheckoutRequest>({
    mutationFn: (payload: CheckoutRequest) => checkoutOrder(payload),
  });
}
