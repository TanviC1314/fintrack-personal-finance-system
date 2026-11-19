import { z } from 'zod'
import { MONEY_FORMAT_MESSAGE, MONEY_PATTERN } from '../../lib/money'

export const NEW_CATEGORY = '__new__'

function isoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function todayIso(now: Date = new Date()): string {
  return isoDate(now)
}

/** The latest date the server accepts: tomorrow, which covers timezones ahead of UTC. */
export function latestAllowedDate(now: Date = new Date()): string {
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  return isoDate(tomorrow)
}

export const transactionSchema = z
  .object({
    type: z.enum(['INCOME', 'EXPENSE']),
    accountId: z.string().min(1, 'Choose an account'),
    amount: z
      .string()
      .trim()
      .min(1, 'Amount is required')
      .regex(MONEY_PATTERN, MONEY_FORMAT_MESSAGE)
      .refine((value) => Number(value) >= 0.01, 'Amount must be at least 0.01'),
    transactionDate: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'Choose a date')
      .refine((value) => value <= latestAllowedDate(), 'The date cannot be in the future'),
    categoryId: z.string(),
    newCategory: z.string().trim().max(50, 'Category name must be at most 50 characters'),
    description: z.string().trim().max(255, 'Description must be at most 255 characters'),
  })
  .refine((value) => value.categoryId !== NEW_CATEGORY || value.newCategory.length > 0, {
    message: 'Enter a name for the new category',
    path: ['newCategory'],
  })

export type TransactionFormValues = z.infer<typeof transactionSchema>
