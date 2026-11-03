import { z } from 'zod';

export const VOUCHER_CODE_REGEX = /^[A-Z0-9_-]{3,50}$/;
export const MAX_VOUCHER_MONEY = 9_999_999_999;

export function normalizeVoucherCode(input: string): string {
  return input.trim().toUpperCase();
}

export interface VoucherRawFormValues {
  code?: string;
  discountType?: 'PERCENT' | 'AMOUNT';
  discountValue?: number | null;
  minOrder?: number | null;
  maxDiscount?: number | null;
  usageLimit?: number | null;
  perUserLimit?: number | null;
  startAt?: string;
  endAt?: string;
  isActive?: boolean;
}

const voucherBaseFieldsSchema = z.object({
  discountType: z.enum(['PERCENT', 'AMOUNT'], {
    message: 'Loại giảm giá không được để trống',
  }),
  discountValue: z
    .number({ message: 'Giá trị giảm không được để trống' })
    .int('Giá trị giảm phải là số nguyên')
    .min(1, 'Giá trị giảm phải lớn hơn 0')
    .max(MAX_VOUCHER_MONEY, 'Giá trị giảm vượt quá giới hạn cho phép'),
  minOrder: z
    .number({ message: 'Đơn tối thiểu phải là số hợp lệ' })
    .int('Đơn tối thiểu phải là số nguyên')
    .min(0, 'Đơn tối thiểu không được âm')
    .max(MAX_VOUCHER_MONEY, 'Đơn tối thiểu vượt quá giới hạn cho phép')
    .nullable()
    .optional(),
  maxDiscount: z
    .number({ message: 'Mức giảm tối đa phải là số hợp lệ' })
    .int('Mức giảm tối đa phải là số nguyên')
    .min(1, 'Mức giảm tối đa phải lớn hơn 0')
    .max(MAX_VOUCHER_MONEY, 'Mức giảm tối đa vượt quá giới hạn cho phép')
    .nullable()
    .optional(),
  usageLimit: z
    .number({ message: 'Tổng số lượt dùng phải là số hợp lệ' })
    .int('Tổng số lượt dùng phải là số nguyên')
    .min(1, 'Tổng số lượt dùng phải từ 1 trở lên')
    .nullable()
    .optional(),
  perUserLimit: z
    .number({ message: 'Số lượt dùng mỗi khách phải là số hợp lệ' })
    .int('Số lượt dùng mỗi khách phải là số nguyên')
    .min(1, 'Số lượt dùng mỗi khách phải từ 1 trở lên')
    .default(1),
  startAt: z
    .string({ message: 'Thời gian bắt đầu không được để trống' })
    .trim()
    .min(1, 'Thời gian bắt đầu không được để trống'),
  endAt: z
    .string({ message: 'Thời gian kết thúc không được để trống' })
    .trim()
    .min(1, 'Thời gian kết thúc không được để trống'),
  isActive: z.boolean().default(true),
});

function applySharedBusinessRules(
  data: z.infer<typeof voucherBaseFieldsSchema>,
  ctx: z.RefinementCtx,
  isCreate: boolean
) {
  if (data.discountType === 'PERCENT') {
    if (data.discountValue > 100) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['discountValue'],
        message: 'Giá trị giảm theo phần trăm phải từ 1 đến 100',
      });
    }
  } else if (data.maxDiscount !== null && data.maxDiscount !== undefined) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['maxDiscount'],
      message: 'Mức giảm tối đa chỉ áp dụng cho loại giảm theo phần trăm',
    });
  }

  const startDate = new Date(data.startAt);
  const endDate = new Date(data.endAt);

  if (Number.isNaN(startDate.getTime())) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['startAt'],
      message: 'Thời gian bắt đầu không đúng định dạng',
    });
    return;
  }

  if (Number.isNaN(endDate.getTime())) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['endAt'],
      message: 'Thời gian kết thúc không đúng định dạng',
    });
    return;
  }

  if (startDate.getTime() >= endDate.getTime()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['endAt'],
      message: 'Thời gian bắt đầu phải trước thời gian kết thúc',
    });
  }

  if (isCreate && endDate.getTime() <= Date.now()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['endAt'],
      message: 'Thời gian kết thúc phải ở tương lai khi tạo voucher mới',
    });
  }
}

export const voucherCreateSchema = voucherBaseFieldsSchema
  .extend({
    code: z
      .string({ message: 'Mã voucher không được để trống' })
      .transform(normalizeVoucherCode)
      .pipe(
        z
          .string()
          .min(1, 'Mã voucher không được để trống')
          .max(50, 'Mã voucher tối đa 50 ký tự')
          .regex(
            VOUCHER_CODE_REGEX,
            'Mã voucher chỉ gồm chữ in hoa, số, dấu gạch ngang hoặc gạch dưới, dài 3-50 ký tự'
          )
      ),
  })
  .superRefine((data, ctx) => applySharedBusinessRules(data, ctx, true));

export const voucherUpdateSchema = voucherBaseFieldsSchema.superRefine(
  (data, ctx) => applySharedBusinessRules(data, ctx, false)
);

export type VoucherCreateValidated = z.infer<typeof voucherCreateSchema>;
export type VoucherUpdateValidated = z.infer<typeof voucherUpdateSchema>;
