import { z } from 'zod';

export const MAX_PROMOTION_AMOUNT = 999_999_999;
export const MAX_PROMOTION_PRODUCTS = 100;

export interface PromotionRawFormValues {
  name?: string;
  discountType?: 'PERCENT' | 'AMOUNT';
  discountValue?: number | null;
  startAt?: string;
  endAt?: string;
  isActive?: boolean;
  productIds?: number[];
}

const promotionBaseFieldsSchema = z.object({
  name: z
    .string({ message: 'Tên khuyến mãi không được để trống' })
    .trim()
    .min(1, 'Tên khuyến mãi không được để trống')
    .max(150, 'Tên khuyến mãi tối đa 150 ký tự'),
  discountType: z.enum(['PERCENT', 'AMOUNT'], {
    message: 'Loại giảm giá không được để trống',
  }),
  discountValue: z
    .number({ message: 'Giá trị giảm không được để trống' })
    .int('Giá trị giảm phải là số nguyên')
    .min(1, 'Giá trị giảm phải lớn hơn 0'),
  startAt: z
    .string({ message: 'Thời gian bắt đầu không được để trống' })
    .trim()
    .min(1, 'Thời gian bắt đầu không được để trống'),
  endAt: z
    .string({ message: 'Thời gian kết thúc không được để trống' })
    .trim()
    .min(1, 'Thời gian kết thúc không được để trống'),
  isActive: z.boolean().default(true),
  productIds: z
    .array(z.number().int().positive())
    .max(MAX_PROMOTION_PRODUCTS, `Khuyến mãi áp dụng tối đa ${MAX_PROMOTION_PRODUCTS} sản phẩm`)
    .default([]),
});

function applySharedBusinessRules(
  data: z.infer<typeof promotionBaseFieldsSchema>,
  ctx: z.RefinementCtx
) {
  if (data.discountType === 'PERCENT') {
    if (data.discountValue > 100) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['discountValue'],
        message: 'Giá trị giảm theo phần trăm phải từ 1 đến 100',
      });
    }
  } else if (data.discountValue > MAX_PROMOTION_AMOUNT) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['discountValue'],
      message: 'Giá trị giảm không được vượt quá 999.999.999 đ',
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
}

export const promotionCreateSchema = promotionBaseFieldsSchema.superRefine(
  (data, ctx) => applySharedBusinessRules(data, ctx)
);

export const promotionUpdateSchema = promotionBaseFieldsSchema.superRefine(
  (data, ctx) => applySharedBusinessRules(data, ctx)
);

export type PromotionCreateValidated = z.infer<typeof promotionCreateSchema>;
export type PromotionUpdateValidated = z.infer<typeof promotionUpdateSchema>;
