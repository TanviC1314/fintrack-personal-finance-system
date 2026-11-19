import { ArrowDownRight, ArrowUpRight, PiggyBank, Wallet, type LucideIcon } from 'lucide-react'
import type { DashboardSummary } from '../../api/types'
import { Skeleton } from '../../components/ui/States'
import { formatMoney } from '../../lib/format'

interface StatProps {
  label: string
  value: string
  icon: LucideIcon
  tone: string
  caption?: string
}

function Stat({ label, value, icon: Icon, tone, caption }: StatProps) {
  return (
    <div className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <span className={`rounded-lg p-2 ${tone}`}>
          <Icon className="h-5 w-5" aria-hidden="true" />
        </span>
      </div>
      <p className="mt-3 text-2xl font-semibold tracking-tight text-slate-900">{value}</p>
      {caption && <p className="mt-1 text-xs text-slate-500">{caption}</p>}
    </div>
  )
}

export function StatCards({ summary }: { summary: DashboardSummary | undefined }) {
  if (!summary) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-busy="true" aria-label="Loading totals">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-4 h-8 w-32" />
          </div>
        ))}
      </div>
    )
  }

  const money = (value: number) => formatMoney(value, summary.currency)
  const saved = summary.monthlySavings >= 0
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Stat label="Total balance" value={money(summary.totalBalance)} icon={Wallet} tone="bg-brand-50 text-brand-700" caption="Across your active accounts" />
      <Stat label="Income" value={money(summary.monthlyIncome)} icon={ArrowUpRight} tone="bg-emerald-50 text-emerald-700" caption="This month" />
      <Stat label="Expenses" value={money(summary.monthlyExpenses)} icon={ArrowDownRight} tone="bg-rose-50 text-rose-700" caption="This month" />
      <Stat
        label="Savings"
        value={money(summary.monthlySavings)}
        icon={PiggyBank}
        tone={saved ? 'bg-sky-50 text-sky-700' : 'bg-amber-50 text-amber-700'}
        caption={saved ? 'Income minus expenses' : 'You spent more than you earned'}
      />
    </div>
  )
}
