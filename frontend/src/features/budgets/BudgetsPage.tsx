import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Plus, Tags } from 'lucide-react'
import { useState } from 'react'
import { accountsApi } from '../../api/accounts'
import { budgetsApi } from '../../api/budgets'
import { categoriesApi } from '../../api/categories'
import type { Budget } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { currentMonth, formatMonth, shiftMonth } from '../../lib/format'
import { BudgetCard } from './BudgetCard'
import { CreateBudgetModal, EditBudgetModal } from './BudgetModals'
import { CategoriesModal } from './CategoriesModal'

export default function BudgetsPage() {
  const [month, setMonth] = useState(currentMonth())
  const [onlyExceeded, setOnlyExceeded] = useState(false)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Budget | null>(null)
  const [managingCategories, setManagingCategories] = useState(false)

  const budgets = useQuery({ queryKey: ['budgets', month, onlyExceeded], queryFn: () => budgetsApi.list(month, onlyExceeded) })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list() })
  const accounts = useQuery({ queryKey: ['accounts', { includeArchived: false }], queryFn: () => accountsApi.list() })

  const defaultCurrency = accounts.data?.[0]?.currency ?? 'EUR'
  const isCurrent = month === currentMonth()
  const noCategories = categories.isSuccess && categories.data.length === 0

  return (
    <>
      <PageHeader
        title="Budgets"
        subtitle={`Monthly spending limits for ${formatMonth(month)}.`}
        actions={
          <>
            <div className="flex items-center gap-1">
              <Button variant="secondary" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
                <ChevronLeft className="h-4 w-4" aria-hidden="true" />
              </Button>
              <Button variant="secondary" onClick={() => setMonth(currentMonth())} disabled={isCurrent}>
                This month
              </Button>
              <Button variant="secondary" aria-label="Next month" onClick={() => setMonth(shiftMonth(month, 1))}>
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
            <Button variant="secondary" onClick={() => setManagingCategories(true)}>
              <Tags className="h-4 w-4" aria-hidden="true" />
              Categories
            </Button>
            <Button onClick={() => setCreating(true)} disabled={noCategories || !categories.isSuccess} title={noCategories ? 'Create a category first' : undefined}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              New budget
            </Button>
          </>
        }
      />

      <Card
        action={
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={onlyExceeded} onChange={(e) => setOnlyExceeded(e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600" />
            Only over budget
          </label>
        }
        title="Budgets"
      >
        {budgets.isPending ? (
          <div className="space-y-4" aria-busy="true" aria-label="Loading budgets">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : budgets.isError ? (
          <ErrorState error={budgets.error} onRetry={() => budgets.refetch()} />
        ) : budgets.data.length === 0 ? (
          onlyExceeded ? (
            <EmptyState title="Nothing is over budget" message="Every budget for this month is within its limit." />
          ) : noCategories ? (
            <EmptyState
              title="Start with a category"
              message="Budgets limit what you spend in a category, such as Groceries."
              action={<Button onClick={() => setManagingCategories(true)}>Add categories</Button>}
            />
          ) : (
            <EmptyState
              title="No budgets for this month"
              message="Set a limit for a category and watch how much of it you have used."
              action={<Button onClick={() => setCreating(true)}>Create a budget</Button>}
            />
          )
        ) : (
          <ul className="-my-4 divide-y divide-slate-100">
            {budgets.data.map((b) => (
              <BudgetCard key={b.id} budget={b} onEdit={() => setEditing(b)} />
            ))}
          </ul>
        )}
      </Card>

      {creating && categories.data && <CreateBudgetModal month={month} categories={categories.data} defaultCurrency={defaultCurrency} onClose={() => setCreating(false)} />}
      {editing && <EditBudgetModal budget={editing} onClose={() => setEditing(null)} />}
      {managingCategories && <CategoriesModal onClose={() => setManagingCategories(false)} />}
    </>
  )
}
