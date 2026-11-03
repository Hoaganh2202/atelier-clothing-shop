import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isAxiosError } from 'axios';
import {
  ShieldCheck,
  Truck,
  ArrowLeft,
  AlertCircle,
  Plus,
  Check,
  CheckCircle2,
  Loader2,
  MapPin,
  Tag,
  RefreshCw,
  Home,
  ShoppingBag,
} from 'lucide-react';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useAuthStore } from '@/stores/auth.store';
import { useCartStore } from '@/features/cart/cart.store';
import { useAddresses, useCreateAddress } from '@/features/address/address.hooks';
import { AddressFormDialog } from '@/features/address/AddressFormDialog';
import { formatCurrencyVND } from '@/lib/format';
import { generateIdempotencyKey } from '@/lib/uuid';
import { useCheckout } from '../hooks/useCheckout';
import { useShippingPolicy } from '@/features/cart/hooks/useShippingPolicy';
import { getCheckoutErrorMessage } from '../api/order.api';
import type { AddressItem, AddressRequestPayload } from '@/features/address/address.types';
import type { CheckoutRequest } from '../order.types';

type CheckoutStatus = 'idle' | 'submitting' | 'indeterminate' | 'succeeded' | 'definitive_failure';

interface PendingCheckoutSnapshot {
  payload: CheckoutRequest;
  timestamp: number;
}

const STORAGE_PENDING_SNAPSHOT_KEY = 'pending_checkout_snapshot';
const STORAGE_IDEMPOTENCY_KEY = 'checkout_idempotency_key';

export const CheckoutPage: React.FC = () => {
  const { data: shippingPolicy, isLoading: isPolicyLoading, isError: isPolicyError } = useShippingPolicy({ refetchOnMount: 'always' });
  const hasPolicy = Boolean(shippingPolicy && !isPolicyLoading && !isPolicyError);
  const navigate = useNavigate();
  const { accessToken, isInitializing } = useAuthStore();
  const {
    items,
    removeOrderedItems,
    getTotalItems,
    getEstimatedSubtotal,
    getEstimatedShippingFee,
    getEstimatedTotal,
  } = useCartStore();

  const totalItems = getTotalItems();
  const estimatedSubtotal = getEstimatedSubtotal();
  const estimatedShippingFee = getEstimatedShippingFee();
  const estimatedTotal = getEstimatedTotal();

  // Authentication guard
  useEffect(() => {
    if (!isInitializing && !accessToken) {
      navigate('/login?redirect=/checkout');
    }
  }, [isInitializing, accessToken, navigate]);

  // Form input states
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [isAddressModalOpen, setIsAddressModalOpen] = useState(false);
  const [addressApiError, setAddressApiError] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [voucherCode, setVoucherCode] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // State machine & pending snapshot
  const [status, setStatus] = useState<CheckoutStatus>('idle');
  const [pendingSnapshot, setPendingSnapshot] = useState<PendingCheckoutSnapshot | null>(null);

  // Ref to block concurrent double clicks at event loop level
  const isSubmittingRef = useRef(false);

  // Stable cart items signature
  const itemsSignature = useMemo(() => {
    return items
      .map((i) => `${i.variantId}:${i.quantity}`)
      .sort()
      .join('|');
  }, [items]);

  // Active idempotency key state
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => {
    if (typeof window !== 'undefined') {
      const savedSnapshot = sessionStorage.getItem(STORAGE_PENDING_SNAPSHOT_KEY);
      if (savedSnapshot) {
        try {
          const parsed = JSON.parse(savedSnapshot) as PendingCheckoutSnapshot;
          if (parsed?.payload?.idempotencyKey) {
            return parsed.payload.idempotencyKey;
          }
        } catch {
          // Ignore parse errors
        }
      }
      const savedKey = sessionStorage.getItem(STORAGE_IDEMPOTENCY_KEY);
      if (savedKey) {
        return savedKey;
      }
    }
    const newKey = generateIdempotencyKey();
    if (typeof window !== 'undefined') {
      sessionStorage.setItem(STORAGE_IDEMPOTENCY_KEY, newKey);
    }
    return newKey;
  });

  // Restore pending snapshot on initial mount if browser session holds an indeterminate attempt
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const raw = sessionStorage.getItem(STORAGE_PENDING_SNAPSHOT_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as PendingCheckoutSnapshot;
        if (parsed?.payload) {
          setPendingSnapshot(parsed);
          setStatus('indeterminate');
          setSelectedAddressId(parsed.payload.addressId);
          setNote(parsed.payload.note || '');
          setVoucherCode(parsed.payload.voucherCode || '');
          setIdempotencyKey(parsed.payload.idempotencyKey || generateIdempotencyKey());
          setErrorMessage(
            'Giao dịch trước đó chưa nhận được phản hồi xác nhận từ máy chủ (có thể do gián đoạn kết nối mạng hoặc timeout). Dữ liệu đơn hàng và khóa giao dịch ban đầu đang được khóa an toàn để tránh tạo đơn trùng lặp.'
          );
        }
      } catch {
        sessionStorage.removeItem(STORAGE_PENDING_SNAPSHOT_KEY);
      }
    }
  }, []);

  // Cart empty guard: Only redirect to /cart when in idle state without pending indeterminate attempt
  useEffect(() => {
    if (status === 'idle' && !pendingSnapshot && items.length === 0) {
      navigate('/cart');
    }
  }, [items, status, pendingSnapshot, navigate]);

  // Addresses query
  const { data: addresses = [], isLoading: isLoadingAddresses } = useAddresses(Boolean(accessToken));
  const createAddressMutation = useCreateAddress();

  // Auto-select default address
  useEffect(() => {
    if (addresses.length > 0 && selectedAddressId === null && status !== 'indeterminate') {
      const defaultAddr = addresses.find((a: AddressItem) => a.isDefault);
      setSelectedAddressId(defaultAddr ? defaultAddr.id : addresses[0].id);
    }
  }, [addresses, selectedAddressId, status]);

  // Cycle key on definitive failure when user modifies cart items
  useEffect(() => {
    if (status === 'indeterminate') {
      return;
    }
    if (typeof window === 'undefined') return;

    if (status === 'definitive_failure') {
      const newKey = generateIdempotencyKey();
      setIdempotencyKey(newKey);
      sessionStorage.setItem(STORAGE_IDEMPOTENCY_KEY, newKey);
      setStatus('idle');
      setErrorMessage(null);
    }
  }, [itemsSignature, status]);

  const handleSelectAddress = (id: number) => {
    if (status === 'indeterminate') return;
    setSelectedAddressId(id);
    if (status === 'definitive_failure') {
      const newKey = generateIdempotencyKey();
      setIdempotencyKey(newKey);
      sessionStorage.setItem(STORAGE_IDEMPOTENCY_KEY, newKey);
      setStatus('idle');
      setErrorMessage(null);
    }
  };

  const handleVoucherChange = (val: string) => {
    if (status === 'indeterminate') return;
    setVoucherCode(val.toUpperCase());
    if (status === 'definitive_failure') {
      const newKey = generateIdempotencyKey();
      setIdempotencyKey(newKey);
      sessionStorage.setItem(STORAGE_IDEMPOTENCY_KEY, newKey);
      setStatus('idle');
      setErrorMessage(null);
    }
  };

  const handleNoteChange = (val: string) => {
    if (status === 'indeterminate') return;
    setNote(val);
    if (status === 'definitive_failure') {
      const newKey = generateIdempotencyKey();
      setIdempotencyKey(newKey);
      sessionStorage.setItem(STORAGE_IDEMPOTENCY_KEY, newKey);
      setStatus('idle');
      setErrorMessage(null);
    }
  };

  const checkoutMutation = useCheckout();

  const handleCreateAddressSubmit = async (payload: AddressRequestPayload) => {
    try {
      setAddressApiError(null);
      const newAddr = await createAddressMutation.mutateAsync(payload);
      setSelectedAddressId(newAddr.id);
      setIsAddressModalOpen(false);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setAddressApiError(err.message);
      } else {
        setAddressApiError('Không thể tạo địa chỉ mới. Vui lòng thử lại.');
      }
    }
  };

  // Execute checkout request with snapshot persistence
  const executeCheckout = async (payload: CheckoutRequest) => {
    if (isSubmittingRef.current || checkoutMutation.isPending) {
      return;
    }

    isSubmittingRef.current = true;
    setStatus('submitting');
    setErrorMessage(null);

    // Save pending snapshot before network dispatch
    const snapshot: PendingCheckoutSnapshot = {
      payload,
      timestamp: Date.now(),
    };
    setPendingSnapshot(snapshot);
    if (typeof window !== 'undefined') {
      sessionStorage.setItem(STORAGE_PENDING_SNAPSHOT_KEY, JSON.stringify(snapshot));
    }

    try {
      const result = await checkoutMutation.mutateAsync(payload);

      // On 201 Created: clear snapshot and session keys, persist result for success page
      setStatus('succeeded');
      if (typeof window !== 'undefined') {
        sessionStorage.removeItem(STORAGE_PENDING_SNAPSHOT_KEY);
        sessionStorage.removeItem(STORAGE_IDEMPOTENCY_KEY);
        sessionStorage.setItem('last_successful_order', JSON.stringify(result));
      }

      // Remove only the items that were actually ordered, preserving any newly added cart items
      removeOrderedItems(payload.items);

      navigate(`/checkout/success/${result.orderId}`, {
        state: { order: result },
        replace: true,
      });
    } catch (err: unknown) {
      // Classification of error: All network failures, timeouts, and HTTP 5xx responses are indeterminate
      const isIndeterminate =
        !isAxiosError(err) ||
        !err.response ||
        err.code === 'ECONNABORTED' ||
        err.response.status >= 500;

      if (isIndeterminate) {
        // Indeterminate state: preserve pending snapshot, lock form, strictly require retry with exact snapshot
        setStatus('indeterminate');
        setErrorMessage(
          'Kết nối máy chủ bị gián đoạn hoặc quá thời gian chờ (Timeout/5xx). Để đảm bảo không tạo đơn trùng lặp và không bị trừ kho hai lần, dữ liệu đơn hàng và khóa giao dịch ban đầu đang được lưu giữ nguyên vẹn. Vui lòng bấm "Thử lại gửi đơn hàng ban đầu" để đối soát kết quả.'
        );
      } else {
        // Definitive failure: HTTP 4xx errors (400 Bad Request, OutOfStock, Invalid Voucher, etc.)
        // Backend contracts guarantee that transactions are rolled back; no order created and no stock deducted
        setStatus('definitive_failure');
        setPendingSnapshot(null);
        if (typeof window !== 'undefined') {
          sessionStorage.removeItem(STORAGE_PENDING_SNAPSHOT_KEY);
        }
        const msg = getCheckoutErrorMessage(err);
        setErrorMessage(msg);
      }
    } finally {
      isSubmittingRef.current = false;
    }
  };

  const handleCheckout = async (e: React.FormEvent) => {
    e.preventDefault();

    if (status === 'indeterminate') {
      return;
    }

    if (!selectedAddressId) {
      setErrorMessage('Vui lòng chọn địa chỉ giao hàng để tiếp tục.');
      return;
    }

    if (items.length === 0) {
      setErrorMessage('Giỏ hàng của bạn đang trống.');
      return;
    }

    const payload: CheckoutRequest = {
      addressId: selectedAddressId,
      paymentMethod: 'COD',
      note: note.trim() || undefined,
      voucherCode: voucherCode.trim() || undefined,
      idempotencyKey,
      items: items.map((i) => ({
        variantId: i.variantId,
        quantity: i.quantity,
      })),
    };

    await executeCheckout(payload);
  };

  // Retry sending the exact original snapshot payload
  const handleRetryPendingSnapshot = async () => {
    if (!pendingSnapshot) return;
    await executeCheckout(pendingSnapshot.payload);
  };

  const isFormLocked = status === 'indeterminate' || status === 'submitting';

  if (isInitializing || !accessToken) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground">
      <Header cartCount={totalItems} />

      <main className="flex-1 mx-auto w-full max-w-[1280px] px-4 sm:px-6 py-8">
        {/* Breadcrumb */}
        <nav className="mb-6 flex items-center gap-2 text-xs text-muted-foreground">
          <Link to="/" className="hover:text-foreground transition-colors">
            Trang chủ
          </Link>
          <span>/</span>
          <Link to="/cart" className="hover:text-foreground transition-colors">
            Giỏ hàng
          </Link>
          <span>/</span>
          <span className="text-foreground font-medium">Thanh toán</span>
        </nav>

        <h1 className="text-2xl font-bold tracking-tight text-foreground mb-8">
          Xác nhận đơn hàng & Thanh toán
        </h1>

        {/* Indeterminate Warning & Action Box */}
        {status === 'indeterminate' && (
          <div className="mb-8 p-5 rounded-md border border-amber-300 bg-amber-50 text-amber-900 shadow-none">
            <div className="flex items-start gap-3">
              <AlertCircle className="h-5 w-5 shrink-0 mt-0.5 text-amber-600" />
              <div className="flex-1 space-y-2">
                <h3 className="font-semibold text-sm">
                  Yêu cầu đặt hàng đang chờ xác nhận kết quả từ máy chủ
                </h3>
                <p className="text-xs leading-relaxed text-amber-800">
                  {errorMessage}
                </p>
                <div className="pt-2 flex flex-wrap items-center gap-3">
                  <Button
                    type="button"
                    onClick={handleRetryPendingSnapshot}
                    disabled={checkoutMutation.isPending}
                    className="h-9 px-4 text-xs font-medium rounded-md gap-1.5 bg-amber-700 hover:bg-amber-800 text-white shadow-none"
                  >
                    {checkoutMutation.isPending ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        Đang đối soát...
                      </>
                    ) : (
                      <>
                        <RefreshCw className="h-3.5 w-3.5" />
                        Thử lại gửi đơn hàng ban đầu
                      </>
                    )}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => navigate('/')}
                    disabled={checkoutMutation.isPending}
                    className="h-9 px-4 text-xs font-medium rounded-md gap-1.5 border-amber-300 bg-white hover:bg-amber-100/50 text-amber-900 shadow-none"
                  >
                    <Home className="h-3.5 w-3.5" />
                    Tiếp tục sau (Về trang chủ)
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => navigate('/cart')}
                    disabled={checkoutMutation.isPending}
                    className="h-9 px-3 text-xs font-medium rounded-md gap-1.5 text-amber-900 hover:bg-amber-100/50 shadow-none"
                  >
                    <ShoppingBag className="h-3.5 w-3.5" />
                    Xem lại giỏ hàng
                  </Button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Definitive Error Banner */}
        {status === 'definitive_failure' && errorMessage && (
          <div className="mb-6 p-4 rounded-md border border-destructive/20 bg-destructive/10 text-destructive text-sm flex items-start gap-3">
            <AlertCircle className="h-5 w-5 shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-semibold text-xs">Đặt hàng chưa thành công</p>
              <p className="text-xs mt-0.5 leading-relaxed">{errorMessage}</p>
            </div>
          </div>
        )}

        <form onSubmit={handleCheckout}>
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Delivery Address & Payment Method */}
            <div className="lg:col-span-8 space-y-6">
              {/* Delivery Address Section */}
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-3 border-b border-border flex flex-row items-center justify-between">
                  <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
                    <MapPin className="h-4 w-4 text-primary" />
                    Địa chỉ nhận hàng
                  </CardTitle>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={isFormLocked}
                    onClick={() => setIsAddressModalOpen(true)}
                    className="h-8 text-xs gap-1.5 rounded-md border-border shadow-none"
                  >
                    <Plus className="h-3.5 w-3.5" />
                    Thêm địa chỉ mới
                  </Button>
                </CardHeader>
                <CardContent className="pt-4">
                  {isLoadingAddresses ? (
                    <div className="py-6 flex justify-center text-sm text-muted-foreground">
                      <Loader2 className="h-5 w-5 animate-spin mr-2" />
                      Đang tải danh sách địa chỉ...
                    </div>
                  ) : addresses.length === 0 ? (
                    <div className="py-8 text-center space-y-3">
                      <p className="text-sm text-muted-foreground">
                        Bạn chưa có địa chỉ nhận hàng nào. Vui lòng thêm địa chỉ để tiếp tục đặt hàng.
                      </p>
                      <Button
                        type="button"
                        disabled={isFormLocked}
                        onClick={() => setIsAddressModalOpen(true)}
                        className="h-9 px-4 text-xs rounded-md shadow-none"
                      >
                        <Plus className="h-3.5 w-3.5 mr-1" />
                        Thêm địa chỉ giao hàng
                      </Button>
                    </div>
                  ) : (
                    <div className={`grid grid-cols-1 md:grid-cols-2 gap-3 ${isFormLocked ? 'opacity-70 pointer-events-none' : ''}`}>
                      {addresses.map((addr: AddressItem) => {
                        const isSelected = selectedAddressId === addr.id;
                        return (
                          <div
                            key={addr.id}
                            onClick={() => handleSelectAddress(addr.id)}
                            className={`p-3.5 rounded-md border transition-all cursor-pointer relative ${
                              isSelected
                                ? 'border-foreground bg-secondary/30 ring-1 ring-foreground'
                                : 'border-border bg-background hover:border-muted-foreground/60'
                            }`}
                          >
                            <div className="flex items-start justify-between gap-2">
                              <span className="text-sm font-semibold text-foreground">
                                {addr.receiverName}
                              </span>
                              {addr.isDefault && (
                                <span className="text-[10px] uppercase font-semibold px-1.5 py-0.5 rounded-md bg-secondary text-secondary-foreground border border-border">
                                  Mặc định
                                </span>
                              )}
                            </div>
                            <div className="text-xs text-muted-foreground mt-1">
                              {addr.phone}
                            </div>
                            <div className="text-xs text-foreground mt-2 line-clamp-2 leading-relaxed">
                              {addr.address}
                              {addr.ward && `, ${addr.ward}`}
                              {addr.district && `, ${addr.district}`}
                              {addr.province && `, ${addr.province}`}
                            </div>
                            {isSelected && (
                              <div className="absolute top-3 right-3 text-foreground">
                                <CheckCircle2 className="h-4 w-4" />
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Payment Method Section */}
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-3 border-b border-border">
                  <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Truck className="h-4 w-4 text-primary" />
                    Phương thức thanh toán
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4 space-y-3">
                  <div className="p-4 rounded-md border border-foreground bg-secondary/30 ring-1 ring-foreground flex items-start gap-3">
                    <div className="flex h-5 w-5 items-center justify-center rounded-full bg-foreground text-background shrink-0 mt-0.5">
                      <Check className="h-3 w-3 stroke-[3]" />
                    </div>
                    <div className="space-y-1">
                      <div className="text-sm font-semibold text-foreground">
                        Thanh toán khi nhận hàng (COD)
                      </div>
                      <p className="text-xs text-muted-foreground leading-relaxed">
                        Bạn sẽ thanh toán bằng tiền mặt trực tiếp cho nhân viên giao hàng khi nhận được kiện hàng.
                      </p>
                    </div>
                  </div>

                  <p className="text-[11px] text-muted-foreground italic">
                    * Các phương thức thanh toán trực tuyến (VNPAY, Thẻ thanh toán quốc tế) đang được phát triển và sẽ sớm được cập nhật.
                  </p>
                </CardContent>
              </Card>

              {/* Voucher & Note Section */}
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-3 border-b border-border">
                  <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Tag className="h-4 w-4 text-primary" />
                    Mã giảm giá & Ghi chú đơn hàng
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4 space-y-4">
                  {/* Voucher Input */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-foreground">
                      Mã giảm giá (Voucher)
                    </label>
                    <div className="flex gap-2 max-w-md">
                      <Input
                        type="text"
                        placeholder="Nhập mã voucher (ví dụ: SALE10)..."
                        value={voucherCode}
                        disabled={isFormLocked}
                        onChange={(e) => handleVoucherChange(e.target.value)}
                        className="h-9 text-xs rounded-md uppercase border-border bg-background shadow-none disabled:opacity-60"
                      />
                    </div>
                    <p className="text-[11px] text-muted-foreground">
                      * Mã giảm giá hợp lệ sẽ được máy chủ áp dụng và tính toán trừ trực tiếp vào đơn hàng.
                    </p>
                  </div>

                  {/* Delivery Note */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-foreground">
                      Ghi chú giao hàng (Tùy chọn)
                    </label>
                    <textarea
                      rows={3}
                      maxLength={500}
                      disabled={isFormLocked}
                      placeholder="Ví dụ: Giao hàng giờ hành chính, gọi trước khi giao..."
                      value={note}
                      onChange={(e) => handleNoteChange(e.target.value)}
                      className="w-full text-xs p-3 rounded-md border border-border bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-foreground resize-none disabled:opacity-60"
                    />
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* Right Column: Order Items Summary & Submit */}
            <div className="lg:col-span-4 sticky top-24">
              <Card className="rounded-md border-border bg-card shadow-none">
                <CardHeader className="pb-3 border-b border-border">
                  <CardTitle className="text-base font-semibold text-foreground">
                    {status === 'indeterminate' && pendingSnapshot
                      ? `Đơn hàng đang chờ (${pendingSnapshot.payload.items.reduce((acc, i) => acc + i.quantity, 0)})`
                      : `Đơn hàng của bạn (${totalItems})`}
                  </CardTitle>
                </CardHeader>
                <CardContent className="pt-4 space-y-4">
                  {/* Items list preview */}
                  <div className="max-h-64 overflow-y-auto divide-y divide-border pr-1 space-y-2">
                    {items.map((item) => (
                      <div key={item.variantId} className="pt-2 first:pt-0 flex items-center gap-3">
                        <div className="h-12 w-12 shrink-0 overflow-hidden rounded-md border border-border bg-secondary">
                          {item.imageUrl ? (
                            <img
                              src={item.imageUrl}
                              alt={item.productName}
                              className="h-full w-full object-cover object-center"
                            />
                          ) : (
                            <div className="h-full w-full flex items-center justify-center text-[9px] text-muted-foreground">
                              Ảnh
                            </div>
                          )}
                        </div>
                        <div className="flex-1 min-w-0 text-xs">
                          <p className="font-medium text-foreground truncate">
                            {item.productName}
                          </p>
                          <p className="text-[11px] text-muted-foreground">
                            {item.color} / {item.size} × {item.quantity}
                          </p>
                        </div>
                        <div className="text-xs font-semibold text-foreground shrink-0">
                          {formatCurrencyVND(item.price * item.quantity)}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Pricing Breakdown */}
                  <div className="space-y-2.5 pt-3 border-t border-border text-sm">
                    <div className="flex justify-between text-muted-foreground">
                      <span>Tạm tính ước tính</span>
                      <span className="font-medium text-foreground">
                        {formatCurrencyVND(estimatedSubtotal)}
                      </span>
                    </div>

                    <div className="flex justify-between text-muted-foreground">
                      <span>Phí vận chuyển ước tính</span>
                      {hasPolicy ? (
                        <span className={estimatedShippingFee === 0 ? 'font-medium text-green-600' : 'font-medium text-foreground'}>
                          {estimatedShippingFee === 0 ? 'Miễn phí' : formatCurrencyVND(estimatedShippingFee)}
                        </span>
                      ) : (
                        <span className="font-normal text-muted-foreground text-xs italic">
                          Sẽ được tính khi đặt hàng
                        </span>
                      )}
                    </div>

                    <div className="pt-3 border-t border-border flex justify-between items-baseline">
                      <span className="font-semibold text-foreground text-base">Tổng thanh toán ước tính</span>
                      <div className="text-right">
                        <div className="text-lg font-bold text-foreground">
                          {hasPolicy ? formatCurrencyVND(estimatedTotal) : formatCurrencyVND(estimatedSubtotal)}
                        </div>
                        {!hasPolicy && (
                          <span className="text-[11px] text-muted-foreground block">
                            (Chưa bao gồm phí vận chuyển)
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <p className="text-[11px] text-muted-foreground italic leading-relaxed">
                    * Bằng việc bấm đặt hàng, bạn đồng ý mua hàng và nhận hàng với hình thức thanh toán COD.
                  </p>

                  {status === 'indeterminate' ? (
                    <div className="space-y-2 mt-2">
                      <Button
                        type="button"
                        onClick={handleRetryPendingSnapshot}
                        disabled={checkoutMutation.isPending}
                        className="w-full h-11 gap-2 rounded-md font-medium text-sm shadow-none bg-amber-700 hover:bg-amber-800 text-white"
                      >
                        {checkoutMutation.isPending ? (
                          <>
                            <Loader2 className="h-4 w-4 animate-spin" />
                            Đang đối soát đơn hàng...
                          </>
                        ) : (
                          <>
                            <RefreshCw className="h-4 w-4" />
                            Thử lại gửi đơn hàng ban đầu
                          </>
                        )}
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => navigate('/')}
                        disabled={checkoutMutation.isPending}
                        className="w-full h-9 rounded-md text-xs border-border shadow-none"
                      >
                        <Home className="h-3.5 w-3.5 mr-1 text-muted-foreground" />
                        Tiếp tục sau (Về trang chủ)
                      </Button>
                    </div>
                  ) : (
                    <Button
                      type="submit"
                      disabled={status === 'submitting' || checkoutMutation.isPending || !selectedAddressId}
                      className="w-full h-11 gap-2 rounded-md font-medium text-sm shadow-none mt-2"
                    >
                      {status === 'submitting' || checkoutMutation.isPending ? (
                        <>
                          <Loader2 className="h-4 w-4 animate-spin" />
                          Đang xử lý đặt hàng...
                        </>
                      ) : (
                        <>
                          <ShieldCheck className="h-4 w-4" />
                          Xác nhận đặt hàng (COD)
                        </>
                      )}
                    </Button>
                  )}

                  <Button
                    type="button"
                    variant="ghost"
                    disabled={isFormLocked}
                    onClick={() => navigate('/cart')}
                    className="w-full h-9 rounded-md text-xs text-muted-foreground hover:text-foreground"
                  >
                    <ArrowLeft className="h-3.5 w-3.5 mr-1" />
                    Quay lại giỏ hàng
                  </Button>
                </CardContent>
              </Card>
            </div>
          </div>
        </form>
      </main>

      <Footer />

      {/* Dialog create address if user has none */}
      <AddressFormDialog
        open={isAddressModalOpen}
        onOpenChange={setIsAddressModalOpen}
        isFirstAddress={addresses.length === 0}
        isSubmitting={createAddressMutation.isPending}
        apiErrorMessage={addressApiError}
        onSubmit={handleCreateAddressSubmit}
      />
    </div>
  );
};
