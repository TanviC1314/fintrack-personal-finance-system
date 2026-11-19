import { Pencil } from 'lucide-react'
import type { Budget } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { formatMoney } from '../../lib/format'

function barColour(percent: number): string {
  if (percent > 100) return 'bg-red-500'
  if (percent >= 80) return 'bg-amber-500'
  return 'bg-brand-600'
}

export function BudgetCard({ budget, onEdit }: { budget: Budget; onEdit: () => void }) {
  const percent = budget.utilisationPercent
  const money = (value: number) => formatMoney(value, budget.currency)
  return (
    <li className="py-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
            {budget.categoryName}
            <span className="text-xs font-normal text-slate-400">{budget.currency}</span>
            {budget.exceeded && <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">Over budget</span>}
          </p>
          <p className="mt-0.5 text-sm text-slate-600">
            {money(budget.spent)} of {money(budget.amount)}
          </p>
        </div>
        <Button variant="ghost" aria-label={`Edit ${budget.categoryName} budget`} onClick={onEdit}>
          <Pencil className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
      <div
        role="progressbar"
        aria-label={`${budget.categoryName} budget used`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.min(percent, 100)}
        aria-valuetext={`${percent}% used`}
        className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100"
      >
        <div className={`h-full rounded-full ${barColour(percent)}`} style={{ width: `${Math.min(percent, 100)}%` }} />
      </div>
      <p className={`mt-1.5 text-xs ${budget.exceeded ? 'font-medium text-red-700' : 'text-slate-500'}`}>
        {percent}% used ·{' '}
        {budget.exceeded ? `${money(Math.abs(budget.remaining))} over` : `${money(budget.remaining)} left`}
      </p>
    </li>
  )
}
