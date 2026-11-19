import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MonthlyTrend } from '../../api/types'
import { EmptyState } from '../../components/ui/States'
import { formatMoney, formatMonth, formatMonthShort } from '../../lib/format'

export const INCOME_COLOR = '#059669'
export const EXPENSE_COLOR = '#e11d48'

export function IncomeExpenseChart({ trend }: { trend: MonthlyTrend }) {
  const hasActivity = trend.months.some((m) => m.income > 0 || m.expenses > 0)
  if (!hasActivity) {
    return <EmptyState title="No income or expenses yet" message="Record a transaction and your monthly trend will appear here." />
  }

  const data = trend.months.map((m) => ({ ...m, label: formatMonthShort(m.month) }))
  return (
    <div className="h-72" role="img" aria-label="Bar chart comparing income and expenses for each of the last months">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
          <XAxis dataKey="label" tickLine={false} axisLine={false} fontSize={12} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} width={56} tickFormatter={(v) => formatMoney(Number(v), trend.currency).replace(/\.00$/, '')} />
          <Tooltip
            cursor={{ fill: '#f1f5f9' }}
            labelFormatter={(_, payload) => (payload?.[0] ? formatMonth(payload[0].payload.month as string) : '')}
            formatter={(value, name) => [formatMoney(Number(value), trend.currency), String(name)]}
          />
          <Legend iconType="circle" />
          <Bar dataKey="income" name="Income" fill={INCOME_COLOR} radius={[4, 4, 0, 0]} />
          <Bar dataKey="expenses" name="Expenses" fill={EXPENSE_COLOR} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
