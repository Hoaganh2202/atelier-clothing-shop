import { z } from 'zod';

export const updateProfileSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Họ tên không được để trống')
    .max(150, 'Họ tên không được vượt quá 150 ký tự'),
  phone: z
    .string()
    .trim()
    .regex(/^(0[0-9]{9})?$/, 'Số điện thoại không hợp lệ (gồm 10 chữ số bắt đầu bằng số 0)')
    .optional()
    .or(z.literal('')),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']).optional().nullable().or(z.literal('')),
  birthDate: z
    .string()
    .optional()
    .nullable()
    .or(z.literal(''))
    .refine(
      (val) => {
        if (!val || !val.trim()) return true;
        const date = new Date(val);
        if (isNaN(date.getTime())) return false;
        const today = new Date();
        today.setHours(23, 59, 59, 999);
        return date <= today;
      },
      { message: 'Ngày sinh không được nằm trong tương lai' }
    ),
});

export type UpdateProfileFormValues = z.infer<typeof updateProfileSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Mật khẩu hiện tại không được để trống'),
    newPassword: z
      .string()
      .min(6, 'Mật khẩu mới phải có ít nhất 6 ký tự')
      .max(72, 'Mật khẩu mới không được vượt quá 72 ký tự')
      .refine(
        (val) => new TextEncoder().encode(val).length <= 72,
        { message: 'Mật khẩu mới không được vượt quá 72 byte khi mã hóa UTF-8' }
      ),
    confirmPassword: z.string().min(1, 'Xác nhận mật khẩu không được để trống'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Mật khẩu xác nhận không trùng khớp',
    path: ['confirmPassword'],
  });

export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
