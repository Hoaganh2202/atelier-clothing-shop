import { z } from 'zod';

export const VIETNAMESE_PHONE_REGEX = /^(0\d{9}|\+84\d{9})$/;

export const addressFormSchema = z.object({
  receiverName: z
    .string()
    .trim()
    .min(1, 'Họ tên người nhận không được để trống')
    .max(150, 'Họ tên người nhận không được vượt quá 150 ký tự'),
  phone: z
    .string()
    .trim()
    .min(1, 'Số điện thoại không được để trống')
    .max(20, 'Số điện thoại không được vượt quá 20 ký tự')
    .regex(
      VIETNAMESE_PHONE_REGEX,
      'Số điện thoại không hợp lệ (10 số bắt đầu bằng 0 hoặc +84 theo sau 9 số)'
    ),
  province: z
    .string()
    .trim()
    .min(1, 'Tỉnh/Thành phố không được để trống')
    .max(100, 'Tỉnh/Thành phố không được vượt quá 100 ký tự'),
  district: z
    .string()
    .trim()
    .max(100, 'Quận/Huyện không được vượt quá 100 ký tự'),
  ward: z
    .string()
    .trim()
    .max(100, 'Phường/Xã không được vượt quá 100 ký tự'),
  address: z
    .string()
    .trim()
    .min(1, 'Địa chỉ chi tiết không được để trống')
    .max(255, 'Địa chỉ chi tiết không được vượt quá 255 ký tự'),
  isDefault: z.boolean(),
});

export type AddressFormValues = z.infer<typeof addressFormSchema>;
