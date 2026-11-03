import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { CartItem, CartState, ShippingPolicy } from './cart.types';

export const FREE_SHIPPING_THRESHOLD = 500000;
export const DEFAULT_SHIPPING_FEE = 30000;

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      shippingPolicy: null,

      setShippingPolicy: (policy: ShippingPolicy) => {
        set({ shippingPolicy: policy });
      },

      addItem: (newItem, quantity = 1) => {
        if (quantity < 1) return;
        set((state) => {
          const existingIndex = state.items.findIndex(
            (item) => item.variantId === newItem.variantId
          );

          if (existingIndex > -1) {
            const updatedItems = [...state.items];
            const currentItem = updatedItems[existingIndex];
            const nextQuantity = currentItem.quantity + quantity;
            updatedItems[existingIndex] = {
              ...currentItem,
              quantity: nextQuantity,
            };
            return { items: updatedItems };
          }

          return {
            items: [...state.items, { ...newItem, quantity }],
          };
        });
      },

      updateQuantity: (variantId: number, quantity: number) => {
        if (quantity < 1) return;
        set((state) => ({
          items: state.items.map((item) =>
            item.variantId === variantId ? { ...item, quantity } : item
          ),
        }));
      },

      removeItem: (variantId: number) => {
        set((state) => ({
          items: state.items.filter((item) => item.variantId !== variantId),
        }));
      },

      removeOrderedItems: (orderedItems: Array<{ variantId: number; quantity: number }>) => {
        set((state) => {
          const orderedMap = new Map<number, number>();
          orderedItems.forEach((item) => {
            orderedMap.set(item.variantId, item.quantity);
          });

          const remainingItems = state.items
            .map((item) => {
              const orderedQty = orderedMap.get(item.variantId);
              if (!orderedQty) return item;
              const remainingQty = item.quantity - orderedQty;
              return remainingQty > 0 ? { ...item, quantity: remainingQty } : null;
            })
            .filter((item): item is CartItem => item !== null);

          return { items: remainingItems };
        });
      },

      clearCart: () => {
        set({ items: [] });
      },

      getTotalItems: () => {
        return get().items.reduce((total, item) => total + item.quantity, 0);
      },

      getEstimatedSubtotal: () => {
        return get().items.reduce(
          (total, item) => total + item.price * item.quantity,
          0
        );
      },

      getEstimatedShippingFee: () => {
        const subtotal = get().getEstimatedSubtotal();
        if (subtotal === 0) return 0;
        const policy = get().shippingPolicy;
        if (!policy) return 0;
        return subtotal >= policy.freeShippingThreshold ? 0 : policy.shippingFee;
      },

      getEstimatedTotal: () => {
        const subtotal = get().getEstimatedSubtotal();
        const shippingFee = get().getEstimatedShippingFee();
        return subtotal + shippingFee;
      },
    }),
    {
      name: 'clothing_shop_cart',
      version: 2,
      partialize: (state) => ({
        items: state.items,
      }),
      migrate: (persistedState: unknown, version: number) => {
        const state = persistedState as { items?: CartItem[] } | undefined;
        if (version < 2) {
          return {
            items: state?.items || [],
            shippingPolicy: null,
          };
        }
        return persistedState as CartState;
      },
    }
  )
);
