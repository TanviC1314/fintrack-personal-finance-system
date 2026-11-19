import { z } from 'zod'
import { MONEY_FORMAT_MESSAGE, MONEY_PATTERN } from '../../lib/money'

export const ACCOUNT_TYPES = [
  { value: 'CURRENT', label: 'Current' },
  { value: 'SAVINGS', label: 'Savings' },
  { value: 'CASH', label: 'Cash' },
] as const

const name = z.string().trim().min(1, 'Name is required').max(100, 'Name must be at most 100 characters')

export const createAccountSchema = z.object({
  name,
  type: z.enum(['CURRENT', 'SAVINGS', 'CASH']),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Choose a currency'),
  // Optional: left empty means a zero balance. It is text here and converted when submitted.
  openingBalance: z
    .string()
    .trim()
    .refine((value) => value === '' || MONEY_PATTERN.test(value), MONEY_FORMAT_MESSAGE),
})

export const renameAccountSchema = z.object({ name })

export type CreateAccountForm = z.infer<typeof createAccountSchema>
export type RenameAccountForm = z.infer<typeof renameAccountSchema>
