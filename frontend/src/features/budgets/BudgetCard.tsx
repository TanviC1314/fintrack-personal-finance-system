import { Pencil } from 'lucide-react'
import type { Budget } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { formatMoney } from '../../lib/format'

type BudgetState = 'ok' | 'warning' | 'over'

function budgetState(budget: Budget): BudgetState {
  if (budget.exceeded) return 'over'
  if (budget.utilisationPercent >= 80) return 'warning'
  return 'ok'
}

function barColour(state: BudgetState): string {
  if (state === 'over') return 'bg-red-500'
  if (state === 'warning') return 'bg-amber-500'
  return 'bg-brand-600'
}

function summaryClasses(state: BudgetState): string {
  if (state === 'over') return 'font-medium text-red-700'
  if (state === 'warning') return 'font-medium text-amber-700'
  return 'text-slate-500'
}

export function BudgetCard({ budget, onEdit }: { budget: Budget; onEdit: () => void }) {
  const percent = budget.utilisationPercent
  const money = (value: number) => formatMoney(value, budget.currency)
  const state = budgetState(budget)
  return (
    <li className="py-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
            {budget.categoryName}
            <span className="text-xs font-normal text-slate-400">{budget.currency}</span>
            {state === 'over' && <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">Over budget</span>}
            {state === 'warning' && <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">Warning</span>}
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
        <div className={`h-full rounded-full ${barColour(state)}`} style={{ width: `${Math.min(percent, 100)}%` }} />
      </div>
      <p className={`mt-1.5 text-xs ${summaryClasses(state)}`}>
        {percent}% used ·{' '}
        {budget.exceeded ? `${money(Math.abs(budget.remaining))} over` : `${money(budget.remaining)} left`}
      </p>
    </li>
  )
}
