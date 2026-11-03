import { z } from 'zod';

export const inventoryStockUpdateSchema = z.object({
  newStock: z
    .number()
    .int('Số lượng tồn kho phải là số nguyên')
    .min(0, 'Số lượng tồn kho không được âm')
    .max(1000000, 'Số lượng tồn kho không được vượt quá 1.000.000'),
  expectedStock: z
    .number()
    .int('Số lượng tồn kho phải là số nguyên')
    .min(0, 'Số lượng tồn kho kỳ vọng không được âm'),
});

export type InventoryStockFormValues = z.infer<typeof inventoryStockUpdateSchema>;
