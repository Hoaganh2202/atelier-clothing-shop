export interface CartItem {
  variantId: number;
  productId: number;
  productName: string;
  productSlug: string;
  color: string;
  size: string;
  price: number;
  imageUrl?: string;
  quantity: number;
  stock: number;
}

export interface ShippingPolicy {
  shippingFee: number;
  freeShippingThreshold: number;
}

export interface CartState {
  items: CartItem[];
  shippingPolicy: ShippingPolicy | null;
  setShippingPolicy: (policy: ShippingPolicy) => void;
  addItem: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;
  updateQuantity: (variantId: number, quantity: number) => void;
  removeItem: (variantId: number) => void;
  removeOrderedItems: (orderedItems: Array<{ variantId: number; quantity: number }>) => void;
  clearCart: () => void;
  getTotalItems: () => number;
  getEstimatedSubtotal: () => number;
  getEstimatedShippingFee: () => number;
  getEstimatedTotal: () => number;
}
