import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import {
  CheckCircle2,
  MapPin,
  Pencil,
  Plus,
  RotateCcw,
  Trash2,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useAuthStore } from '@/stores/auth.store';
import {
  extractAddressApiError,
  formatFullAddress,
} from './address.api';
import {
  useAddresses,
  useCreateAddress,
  useDeleteAddress,
  useSetDefaultAddress,
  useUpdateAddress,
} from './address.hooks';
import { AddressFormDialog } from './AddressFormDialog';
import {
  MAX_ADDRESSES_PER_USER,
  type AddressItem,
  type AddressRequestPayload,
} from './address.types';

interface ToastBanner {
  type: 'success' | 'error';
  message: string;
}

export const AddressBookPage: React.FC = () => {
  const { user, accessToken, isInitializing } = useAuthStore();
  const location = useLocation();

  const [isFormOpen, setIsFormOpen] = useState<boolean>(false);
  const [editingAddress, setEditingAddress] = useState<AddressItem | null>(null);
  const [deletingAddress, setDeletingAddress] = useState<AddressItem | null>(null);
  const [formApiError, setFormApiError] = useState<string | null>(null);
  const [formFieldErrors, setFormFieldErrors] = useState<Record<string, string> | undefined>(
    undefined
  );
  const [toast, setToast] = useState<ToastBanner | null>(null);

  useEffect(() => {
    document.title = 'Sổ địa chỉ | ATELIER';
  }, []);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timer = window.setTimeout(() => {
      setToast(null);
    }, 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const isAuthenticated = Boolean(accessToken && user);

  const {
    data: addresses = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useAddresses(isAuthenticated && !isInitializing);

  const createMutation = useCreateAddress();
  const updateMutation = useUpdateAddress();
  const setDefaultMutation = useSetDefaultAddress();
  const deleteMutation = useDeleteAddress();

  if (isInitializing) {
    return (
      <div className="flex flex-col gap-6">
        <Skeleton className="h-48 w-full rounded-md" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const isMaxReached = addresses.length >= MAX_ADDRESSES_PER_USER;
  const isFormSubmitting = createMutation.isPending || updateMutation.isPending;
  const isAnyActionPending =
    isFormSubmitting || setDefaultMutation.isPending || deleteMutation.isPending;

  const handleOpenCreate = () => {
    if (isMaxReached || isAnyActionPending) {
      return;
    }
    setEditingAddress(null);
    setFormApiError(null);
    setFormFieldErrors(undefined);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (item: AddressItem) => {
    if (isAnyActionPending) {
      return;
    }
    setEditingAddress(item);
    setFormApiError(null);
    setFormFieldErrors(undefined);
    setIsFormOpen(true);
  };

  const handleFormSubmit = (payload: AddressRequestPayload) => {
    setFormApiError(null);
    setFormFieldErrors(undefined);

    if (editingAddress) {
      updateMutation.mutate(
        { id: editingAddress.id, payload },
        {
          onSuccess: () => {
            setIsFormOpen(false);
            setEditingAddress(null);
            setToast({
              type: 'success',
              message: 'Đã cập nhật địa chỉ giao hàng',
            });
          },
          onError: (err) => {
            const parsed = extractAddressApiError(err, 'Không thể cập nhật địa chỉ');
            setFormApiError(parsed.message);
            setFormFieldErrors(parsed.fieldErrors);
          },
        }
      );
      return;
    }

    createMutation.mutate(payload, {
      onSuccess: () => {
        setIsFormOpen(false);
        setToast({
          type: 'success',
          message: 'Đã thêm địa chỉ giao hàng mới',
        });
      },
      onError: (err) => {
        const parsed = extractAddressApiError(err, 'Không thể thêm địa chỉ mới');
        setFormApiError(parsed.message);
        setFormFieldErrors(parsed.fieldErrors);
      },
    });
  };

  const handleSetDefault = (item: AddressItem) => {
    setDefaultMutation.mutate(item.id, {
      onSuccess: () => {
        setToast({
          type: 'success',
          message: `Đã đặt địa chỉ của "${item.receiverName}" làm mặc định`,
        });
      },
      onError: (err) => {
        const parsed = extractAddressApiError(err, 'Không thể đặt địa chỉ mặc định');
        setToast({
          type: 'error',
          message: parsed.message,
        });
      },
    });
  };

  const handleConfirmDelete = () => {
    if (!deletingAddress) {
      return;
    }

    deleteMutation.mutate(deletingAddress.id, {
      onSuccess: () => {
        setDeletingAddress(null);
        setToast({
          type: 'success',
          message: 'Đã xóa địa chỉ giao hàng',
        });
      },
      onError: (err) => {
        const parsed = extractAddressApiError(err, 'Không thể xóa địa chỉ');
        setDeletingAddress(null);
        setToast({
          type: 'error',
          message: parsed.message,
        });
      },
    });
  };

  const willPromoteAnotherOnDelete = Boolean(
    deletingAddress?.isDefault && addresses.length > 1
  );

  return (
    <div className="flex flex-col gap-6">

          <div className="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-col gap-1">
              <h1 className="text-[22px] font-bold text-foreground">
                Sổ địa chỉ
              </h1>
              <p className="text-[13px] text-muted-foreground">
                Quản lý địa chỉ nhận hàng cho các đơn đặt mua của bạn ({addresses.length}/{MAX_ADDRESSES_PER_USER})
              </p>
            </div>

            <div className="flex flex-col items-start gap-2 sm:items-end">
              <Button
                type="button"
                onClick={handleOpenCreate}
                disabled={isLoading || isMaxReached || isAnyActionPending}
                className="gap-2 font-semibold"
              >
                <Plus className="h-4 w-4" />
                Thêm địa chỉ
              </Button>
              {isMaxReached && (
                <p className="text-[13px] font-medium text-destructive">
                  Bạn chỉ có thể lưu tối đa 10 địa chỉ
                </p>
              )}
            </div>
          </div>

          {toast && (
            <div
              role="status"
              className="flex items-center justify-between gap-3 rounded-md border border-border bg-card px-4 py-3"
            >
              <div className="flex items-center gap-2">
                <CheckCircle2
                  className={
                    toast.type === 'error'
                      ? 'h-4 w-4 shrink-0 text-destructive'
                      : 'h-4 w-4 shrink-0 text-foreground'
                  }
                />
                <span
                  className={
                    toast.type === 'error'
                      ? 'text-[15px] font-medium text-destructive'
                      : 'text-[15px] font-medium text-foreground'
                  }
                >
                  {toast.message}
                </span>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={() => setToast(null)}
                aria-label="Đóng thông báo"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          )}

          {isLoading && (
            <div className="flex flex-col gap-4" aria-busy="true">
              {[1, 2, 3].map((item) => (
                <div
                  key={item}
                  className="flex flex-col gap-3 rounded-md border border-border bg-card p-6"
                >
                  <div className="flex items-center justify-between gap-4">
                    <Skeleton className="h-6 w-48" />
                    <Skeleton className="h-8 w-32" />
                  </div>
                  <Skeleton className="h-4 w-64" />
                  <Skeleton className="h-4 w-full max-w-md" />
                </div>
              ))}
            </div>
          )}

          {!isLoading && isError && (
            <div
              role="alert"
              className="flex flex-col items-start justify-between gap-4 rounded-md border border-border bg-card p-6 sm:flex-row sm:items-center"
            >
              <div className="flex flex-col gap-1">
                <h2 className="text-[22px] font-semibold text-foreground">
                  Không thể tải sổ địa chỉ
                </h2>
                <p className="text-[15px] text-muted-foreground">
                  {extractAddressApiError(error, 'Đã xảy ra lỗi khi tải danh sách địa chỉ.').message}
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                onClick={() => refetch()}
                className="gap-2"
              >
                <RotateCcw className="h-4 w-4" />
                Thử lại
              </Button>
            </div>
          )}

          {!isLoading && !isError && addresses.length === 0 && (
            <div className="flex flex-col items-center justify-center gap-4 rounded-md border border-border bg-card px-6 py-12 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-md border border-border bg-secondary text-foreground">
                <MapPin className="h-6 w-6" />
              </div>
              <div className="flex flex-col gap-1">
                <h2 className="text-[22px] font-semibold text-foreground">
                  Bạn chưa có địa chỉ giao hàng nào
                </h2>
                <p className="text-[15px] text-muted-foreground">
                  Thêm địa chỉ nhận hàng để đặt mua nhanh hơn ở các lần tiếp theo.
                </p>
              </div>
              <Button
                type="button"
                onClick={handleOpenCreate}
                disabled={isAnyActionPending}
                className="gap-2 font-semibold"
              >
                <Plus className="h-4 w-4" />
                Thêm địa chỉ đầu tiên
              </Button>
            </div>
          )}

          {!isLoading && !isError && addresses.length > 0 && (
            <div className="flex flex-col gap-4">
              {addresses.map((item) => {
                const isSettingThisDefault =
                  setDefaultMutation.isPending &&
                  setDefaultMutation.variables === item.id;
                const isDeletingThis =
                  deleteMutation.isPending &&
                  deleteMutation.variables === item.id;

                return (
                  <div
                    key={item.id}
                    className="flex flex-col justify-between gap-4 rounded-md border border-border bg-card p-6 sm:flex-row sm:items-start"
                  >
                    <div className="flex flex-col gap-2">
                      <div className="flex flex-wrap items-center gap-3">
                        <span className="text-[15px] font-semibold text-foreground">
                          {item.receiverName}
                        </span>
                        <span className="text-border">|</span>
                        <span className="text-[15px] font-medium text-muted-foreground">
                          {item.phone}
                        </span>
                        {item.isDefault && (
                          <Badge
                            variant="secondary"
                            className="rounded-full border border-border bg-secondary px-3 py-1 text-[13px] font-medium text-foreground"
                          >
                            Mặc định
                          </Badge>
                        )}
                      </div>

                      <p className="text-[15px] text-foreground">
                        {formatFullAddress(item)}
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 sm:justify-end">
                      {!item.isDefault && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={isAnyActionPending}
                          onClick={() => handleSetDefault(item)}
                        >
                          {isSettingThisDefault
                            ? 'Đang cập nhật...'
                            : 'Đặt làm mặc định'}
                        </Button>
                      )}

                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isAnyActionPending}
                        onClick={() => handleOpenEdit(item)}
                        className="gap-1"
                      >
                        <Pencil className="h-3 w-3" />
                        Sửa
                      </Button>

                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isAnyActionPending}
                        onClick={() => setDeletingAddress(item)}
                        className="gap-1 text-destructive hover:text-destructive"
                      >
                        <Trash2 className="h-3 w-3" />
                        {isDeletingThis ? 'Đang xóa...' : 'Xóa'}
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        <AddressFormDialog
        open={isFormOpen}
        onOpenChange={setIsFormOpen}
        initialAddress={editingAddress}
        isFirstAddress={addresses.length === 0}
        isSubmitting={isFormSubmitting}
        apiErrorMessage={formApiError}
        apiFieldErrors={formFieldErrors}
        onSubmit={handleFormSubmit}
      />

      <AlertDialog
        open={Boolean(deletingAddress)}
        onOpenChange={(open) => {
          if (!open && !isAnyActionPending) {
            setDeletingAddress(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa địa chỉ giao hàng?</AlertDialogTitle>
            <AlertDialogDescription>
              {deletingAddress
                ? willPromoteAnotherOnDelete
                  ? `Bạn sắp xóa địa chỉ mặc định của "${deletingAddress.receiverName}". Địa chỉ mới nhất còn lại trong sổ sẽ tự động được đặt làm mặc định.`
                  : `Bạn có chắc chắn muốn xóa địa chỉ của "${deletingAddress.receiverName}" khỏi sổ địa chỉ?`
                : ''}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel
              disabled={isAnyActionPending}
              onClick={() => setDeletingAddress(null)}
            >
              Hủy
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={isAnyActionPending}
              onClick={handleConfirmDelete}
            >
              {deleteMutation.isPending ? 'Đang xóa...' : 'Xóa địa chỉ'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default AddressBookPage;
