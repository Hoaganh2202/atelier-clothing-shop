import { z } from 'zod';

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .min(2, 'Họ tên phải có ít nhất 2 ký tự')
      .max(150, 'Họ tên không được vượt quá 150 ký tự'),
    email: z
      .string()
      .email('Email không hợp lệ')
      .max(150, 'Email không được vượt quá 150 ký tự')
      .optional()
      .or(z.literal('')),
    phone: z
      .string()
      .regex(/^(0[0-9]{9})?$/, 'Số điện thoại không hợp lệ')
      .optional()
      .or(z.literal('')),
    password: z
      .string()
      .min(6, 'Mật khẩu phải có ít nhất 6 ký tự')
      .max(72, 'Mật khẩu không được vượt quá 72 ký tự')
      .refine(
        (val) => new TextEncoder().encode(val).length <= 72,
        { message: 'Mật khẩu không được vượt quá 72 byte khi mã hóa UTF-8' }
      ),
  })
  .refine((data) => Boolean((data.email && data.email.trim()) || (data.phone && data.phone.trim())), {
    message: 'Phải cung cấp ít nhất email hoặc số điện thoại',
    path: ['email'],
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email: z.string().min(1, 'Vui lòng nhập email hoặc số điện thoại'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
