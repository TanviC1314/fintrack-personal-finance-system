import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { accountsApi } from '../../api/accounts'
import { dashboardApi } from '../../api/dashboard'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { currentMonth, formatMonth, shiftMonth } from '../../lib/format'
import { CategoryChart } from './CategoryChart'
import { IncomeExpenseChart } from './IncomeExpenseChart'
import { RecentTransactions } from './RecentTransactions'
import { StatCards } from './StatCards'

const TREND_MONTHS = 6

export default function DashboardPage() {
  const [month, setMonth] = useState(currentMonth())
  const [currency, setCurrency] = useState<string | undefined>(undefined)

  const accounts = useQuery({ queryKey: ['accounts', { includeArchived: false }], queryFn: () => accountsApi.list() })
  const summary = useQuery({ queryKey: ['dashboard', 'summary', month, currency], queryFn: () => dashboardApi.summary(month, currency) })
  const trend = useQuery({ queryKey: ['dashboard', 'monthly', month, currency], queryFn: () => dashboardApi.monthly(month, TREND_MONTHS, currency) })
  const categories = useQuery({ queryKey: ['dashboard', 'categories', month, currency], queryFn: () => dashboardApi.categories(month, currency) })

  const currencies = [...new Set((accounts.data ?? []).map((a) => a.currency))]
  const isCurrent = month === currentMonth()
  const noAccounts = accounts.isSuccess && accounts.data.length === 0

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={formatMonth(month)}
        actions={
          <>
            {currencies.length > 1 && (
              <select
                aria-label="Currency"
                value={currency ?? summary.data?.currency ?? currencies[0]}
                onChange={(e) => setCurrency(e.target.value)}
                className="rounded-lg border-0 bg-white py-2 pl-3 pr-8 text-sm ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-brand-600"
              >
                {currencies.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            )}
            <div className="flex items-center gap-1">
              <Button variant="secondary" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
                <ChevronLeft className="h-4 w-4" aria-hidden="true" />
              </Button>
              <Button variant="secondary" onClick={() => setMonth(currentMonth())} disabled={isCurrent}>
                This month
              </Button>
              <Button variant="secondary" aria-label="Next month" onClick={() => setMonth(shiftMonth(month, 1))} disabled={isCurrent}>
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </>
        }
      />

      {summary.isError ? (
        <Card>
          <ErrorState error={summary.error} onRetry={() => summary.refetch()} />
        </Card>
      ) : (
        <div className="space-y-6">
          <StatCards summary={summary.data} />

          <div className="grid gap-6 lg:grid-cols-5">
            <Card title="Income and expenses" className="lg:col-span-3">
              {trend.isError ? (
                <ErrorState error={trend.error} onRetry={() => trend.refetch()} />
              ) : trend.data ? (
                <IncomeExpenseChart trend={trend.data} />
              ) : (
                <Skeleton className="h-72 w-full" />
              )}
            </Card>
            <Card title="Spending by category" className="lg:col-span-2">
              {categories.isError ? (
                <ErrorState error={categories.error} onRetry={() => categories.refetch()} />
              ) : categories.data ? (
                <CategoryChart report={categories.data} />
              ) : (
                <Skeleton className="h-52 w-full" />
              )}
            </Card>
          </div>

          <Card title="Recent transactions">
            {summary.data && accounts.data ? (
              <RecentTransactions transactions={summary.data.recentTransactions} accounts={accounts.data} currency={summary.data.currency} />
            ) : (
              <Skeleton className="h-40 w-full" />
            )}
          </Card>
          {noAccounts && (
            <p className="text-center text-sm text-slate-500">
              You have no accounts yet, so there is nothing to show. Accounts are created on the Accounts page.
            </p>
          )}
        </div>
      )}
    </>
  )
}
