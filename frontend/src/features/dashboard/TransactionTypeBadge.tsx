import type { TransactionType } from '../../api/types'

const STYLES: Record<TransactionType, { label: string; classes: string }> = {
  INCOME: { label: 'Income', classes: 'bg-emerald-50 text-emerald-700' },
  EXPENSE: { label: 'Expense', classes: 'bg-rose-50 text-rose-700' },
  TRANSFER_IN: { label: 'Transfer in', classes: 'bg-sky-50 text-sky-700' },
  TRANSFER_OUT: { label: 'Transfer out', classes: 'bg-sky-50 text-sky-700' },
}

export function TransactionTypeBadge({ type }: { type: TransactionType }) {
  const { label, classes } = STYLES[type]
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${classes}`}>{label}</span>
}
