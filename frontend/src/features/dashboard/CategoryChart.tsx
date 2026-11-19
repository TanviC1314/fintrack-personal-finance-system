import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { CategorySpendingReport } from '../../api/types'
import { EmptyState } from '../../components/ui/States'
import { formatMoney } from '../../lib/format'

// Distinct, colour-blind-friendly hues. The legend beside the chart repeats every value as text,
// so the colours are never the only way to read the data.
const COLORS = ['#0f766e', '#2563eb', '#d97706', '#7c3aed', '#db2777', '#0891b2', '#65a30d', '#64748b']

export function CategoryChart({ report }: { report: CategorySpendingReport }) {
  if (report.categories.length === 0) {
    return <EmptyState title="No spending this month" message="Expenses you record will be split by category here." />
  }

  const data = report.categories.map((c, i) => ({ ...c, color: COLORS[i % COLORS.length] }))
  return (
    <div className="flex flex-col items-center gap-5">
      <div className="h-52 w-52" role="img" aria-label="Donut chart of spending by category">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="amount" nameKey="categoryName" innerRadius="60%" outerRadius="100%" paddingAngle={2} stroke="none">
              {data.map((entry) => (
                <Cell key={entry.categoryName} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip formatter={(value, name) => [formatMoney(Number(value), report.currency), String(name)]} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="w-full space-y-2 text-sm">
        {data.map((entry) => (
          <li key={entry.categoryName} className="flex items-center justify-between gap-3">
            <span className="flex min-w-0 items-center gap-2">
              <span className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: entry.color }} aria-hidden="true" />
              <span className="truncate text-slate-700">{entry.categoryName}</span>
            </span>
            <span className="shrink-0 text-slate-900">
              {formatMoney(entry.amount, report.currency)} <span className="text-slate-400">({entry.percentage}%)</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
