import { z } from 'zod'
import { MONEY_FORMAT_MESSAGE, MONEY_PATTERN } from '../../lib/money'
import { latestAllowedDate } from '../transactions/schemas'

export const transferSchema = z
  .object({
    sourceAccountId: z.string().min(1, 'Choose the account to move money from'),
    destinationAccountId: z.string().min(1, 'Choose the account to move money to'),
    amount: z
      .string()
      .trim()
      .min(1, 'Amount is required')
      .regex(MONEY_PATTERN, MONEY_FORMAT_MESSAGE)
      .refine((value) => Number(value) >= 0.01, 'Amount must be at least 0.01'),
    transferDate: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'Choose a date')
      .refine((value) => value <= latestAllowedDate(), 'The date cannot be in the future'),
    description: z.string().trim().max(255, 'Description must be at most 255 characters'),
  })
  .refine((v) => !v.sourceAccountId || v.sourceAccountId !== v.destinationAccountId, {
    message: 'Choose two different accounts',
    path: ['destinationAccountId'],
  })

export type TransferFormValues = z.infer<typeof transferSchema>
