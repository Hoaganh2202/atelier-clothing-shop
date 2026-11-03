import React, { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import {
  addressFormSchema,
  type AddressFormValues,
} from './address.schema';
import type {
  AddressItem,
  AddressRequestPayload,
} from './address.types';

interface AddressFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialAddress?: AddressItem | null;
  isFirstAddress: boolean;
  isSubmitting: boolean;
  apiErrorMessage?: string | null;
  apiFieldErrors?: Record<string, string>;
  onSubmit: (payload: AddressRequestPayload) => void;
}

const VALID_FORM_FIELDS: Array<keyof AddressFormValues> = [
  'receiverName',
  'phone',
  'province',
  'district',
  'ward',
  'address',
  'isDefault',
];

export const AddressFormDialog: React.FC<AddressFormDialogProps> = ({
  open,
  onOpenChange,
  initialAddress,
  isFirstAddress,
  isSubmitting,
  apiErrorMessage,
  apiFieldErrors,
  onSubmit,
}) => {
  const isEditing = Boolean(initialAddress);
  const isCurrentDefault = Boolean(initialAddress?.isDefault);
  const isCheckboxLocked = isCurrentDefault || (!isEditing && isFirstAddress);

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<AddressFormValues>({
    resolver: zodResolver(addressFormSchema),
    defaultValues: {
      receiverName: '',
      phone: '',
      province: '',
      district: '',
      ward: '',
      address: '',
      isDefault: false,
    },
  });

  useEffect(() => {
    if (!open) {
      return;
    }

    if (initialAddress) {
      reset({
        receiverName: initialAddress.receiverName,
        phone: initialAddress.phone,
        province: initialAddress.province,
        district: initialAddress.district ?? '',
        ward: initialAddress.ward ?? '',
        address: initialAddress.address,
        isDefault: initialAddress.isDefault,
      });
    } else {
      reset({
        receiverName: '',
        phone: '',
        province: '',
        district: '',
        ward: '',
        address: '',
        isDefault: isFirstAddress,
      });
    }
  }, [open, initialAddress, isFirstAddress, reset]);

  useEffect(() => {
    if (!apiFieldErrors) {
      return;
    }
    Object.entries(apiFieldErrors).forEach(([field, message]) => {
      if (VALID_FORM_FIELDS.includes(field as keyof AddressFormValues)) {
        setError(field as keyof AddressFormValues, {
          type: 'server',
          message,
        });
      }
    });
  }, [apiFieldErrors, setError]);

  const handleFormSubmit = (values: AddressFormValues) => {
    const trimmedDistrict = values.district.trim();
    const trimmedWard = values.ward.trim();

    onSubmit({
      receiverName: values.receiverName.trim(),
      phone: values.phone.trim(),
      province: values.province.trim(),
      district: trimmedDistrict.length > 0 ? trimmedDistrict : null,
      ward: trimmedWard.length > 0 ? trimmedWard : null,
      address: values.address.trim(),
      isDefault: isCheckboxLocked ? true : values.isDefault,
    });
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!isSubmitting) {
          onOpenChange(nextOpen);
        }
      }}
    >
      <DialogContent className="max-h-screen overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? 'Cập nhật địa chỉ' : 'Thêm địa chỉ mới'}
          </DialogTitle>
          <DialogDescription>
            Nhập thông tin người nhận và địa chỉ nhận hàng chính xác để thuận tiện khi giao đơn.
          </DialogDescription>
        </DialogHeader>

        {apiErrorMessage && (
          <div
            role="alert"
            className="rounded-md border border-destructive bg-secondary px-4 py-3 text-[13px] font-medium text-destructive"
          >
            {apiErrorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit(handleFormSubmit)} className="flex flex-col gap-4" noValidate>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-2">
              <Label htmlFor="address-receiver-name">Họ và tên người nhận *</Label>
              <Input
                id="address-receiver-name"
                placeholder="Nguyễn Văn A"
                disabled={isSubmitting}
                className="bg-card text-[15px]"
                {...register('receiverName')}
              />
              {errors.receiverName && (
                <p className="text-[13px] text-destructive">
                  {errors.receiverName.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="address-phone">Số điện thoại *</Label>
              <Input
                id="address-phone"
                type="tel"
                placeholder="0901234567"
                disabled={isSubmitting}
                className="bg-card text-[15px]"
                {...register('phone')}
              />
              {errors.phone && (
                <p className="text-[13px] text-destructive">
                  {errors.phone.message}
                </p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="flex flex-col gap-2">
              <Label htmlFor="address-province">Tỉnh/Thành phố *</Label>
              <Input
                id="address-province"
                placeholder="TP. Hồ Chí Minh"
                disabled={isSubmitting}
                className="bg-card text-[15px]"
                {...register('province')}
              />
              {errors.province && (
                <p className="text-[13px] text-destructive">
                  {errors.province.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="address-district">Quận/Huyện</Label>
              <Input
                id="address-district"
                placeholder="Quận 1"
                disabled={isSubmitting}
                className="bg-card text-[15px]"
                {...register('district')}
              />
              {errors.district && (
                <p className="text-[13px] text-destructive">
                  {errors.district.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <Label htmlFor="address-ward">Phường/Xã</Label>
              <Input
                id="address-ward"
                placeholder="Phường Bến Nghé"
                disabled={isSubmitting}
                className="bg-card text-[15px]"
                {...register('ward')}
              />
              {errors.ward && (
                <p className="text-[13px] text-destructive">
                  {errors.ward.message}
                </p>
              )}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="address-line">Địa chỉ cụ thể *</Label>
            <Input
              id="address-line"
              placeholder="Số nhà, tên đường, tòa nhà..."
              disabled={isSubmitting}
              className="bg-card text-[15px]"
              {...register('address')}
            />
            {errors.address && (
              <p className="text-[13px] text-destructive">
                {errors.address.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1 pt-1">
            <Controller
              control={control}
              name="isDefault"
              render={({ field }) => (
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="address-is-default"
                    checked={isCheckboxLocked ? true : field.value}
                    disabled={isSubmitting || isCheckboxLocked}
                    onCheckedChange={(checked) => field.onChange(checked)}
                  />
                  <Label
                    htmlFor="address-is-default"
                    className="cursor-pointer font-normal"
                  >
                    Đặt làm địa chỉ mặc định
                  </Label>
                </div>
              )}
            />
            {isCurrentDefault && (
              <p className="pl-6 text-[13px] text-muted-foreground">
                Hãy đặt địa chỉ khác làm mặc định để đổi
              </p>
            )}
            {!isEditing && isFirstAddress && (
              <p className="pl-6 text-[13px] text-muted-foreground">
                Địa chỉ đầu tiên sẽ tự động được đặt làm mặc định
              </p>
            )}
          </div>

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="outline"
              disabled={isSubmitting}
              onClick={() => onOpenChange(false)}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting} className="font-semibold">
              {isSubmitting ? 'Đang lưu...' : isEditing ? 'Lưu thay đổi' : 'Thêm địa chỉ'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
};

export default AddressFormDialog;
