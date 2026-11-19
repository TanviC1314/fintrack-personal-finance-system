import { z } from 'zod'
import { MONEY_FORMAT_MESSAGE, MONEY_PATTERN } from '../../lib/money'

const amount = z
  .string()
  .trim()
  .min(1, 'Amount is required')
  .regex(MONEY_PATTERN, MONEY_FORMAT_MESSAGE)
  .refine((value) => Number(value) >= 0.01, 'Amount must be at least 0.01')

export const createBudgetSchema = z.object({
  categoryId: z.string().min(1, 'Choose a category'),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Choose a currency'),
  amount,
})

export const updateBudgetSchema = z.object({ amount })

export const categoryNameSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(50, 'Name must be at most 50 characters'),
})

export type CreateBudgetForm = z.infer<typeof createBudgetSchema>
export type UpdateBudgetForm = z.infer<typeof updateBudgetSchema>
export type CategoryNameForm = z.infer<typeof categoryNameSchema>
