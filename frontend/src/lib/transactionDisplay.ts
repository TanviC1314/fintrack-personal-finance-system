import type { TransactionType } from '../api/types'

/** Money coming in is shown with a plus sign and going out with a minus, so direction is never colour-only. */
export function signedAmountClasses(type: TransactionType): string {
  return type === 'INCOME' || type === 'TRANSFER_IN' ? 'text-emerald-700' : 'text-slate-900'
}

export function signFor(type: TransactionType): string {
  return type === 'INCOME' || type === 'TRANSFER_IN' ? '+' : '-'
}
