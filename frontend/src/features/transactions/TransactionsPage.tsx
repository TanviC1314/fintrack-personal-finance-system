import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Pencil, Plus, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { accountsApi } from '../../api/accounts'
import { categoriesApi } from '../../api/categories'
import { describeError } from '../../api/client'
import { transactionsApi, type TransactionFilters } from '../../api/transactions'
import type { Transaction, TransactionType } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { formatDate, formatMoney } from '../../lib/format'
import { signFor, signedAmountClasses } from '../../lib/transactionDisplay'
import { useRefreshMoneyData } from '../../lib/useRefreshMoneyData'
import { TransactionTypeBadge } from '../dashboard/TransactionTypeBadge'
import { EditTransactionModal } from './EditTransactionModal'
import { TransactionFilters as FilterBar, type FilterValues } from './TransactionFilters'

const PAGE_SIZE = 20
const DEFAULT_SORT = 'transactionDate,desc'

/** Filters, page and sort live in the URL, so a filtered view can be bookmarked, shared and survives Back. */
function readParams(params: URLSearchParams) {
  const filters: FilterValues = {
    accountId: params.get('accountId') ?? '',
    type: params.get('type') ?? '',
    categoryId: params.get('categoryId') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    includeReversed: params.get('includeReversed') === 'true',
    q: params.get('q') ?? '',
  }
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const sort = params.get('sort') ?? DEFAULT_SORT
  return { filters, page, sort }
}

function toApiFilters(filters: FilterValues, page: number, sort: string): TransactionFilters {
  return {
    accountId: filters.accountId || undefined,
    type: (filters.type || undefined) as TransactionType | undefined,
    categoryId: filters.categoryId || undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
    includeReversed: filters.includeReversed || undefined,
    q: filters.q.trim() || undefined,
    page,
    size: PAGE_SIZE,
    sort,
  }
}

function SortHeader({ label, field, sort, onSort, align = 'left' }: {
  label: string
  field: 'transactionDate' | 'amount'
  sort: string
  onSort: (sort: string) => void
  align?: 'left' | 'right'
}) {
  const [currentField, direction] = sort.split(',')
  const active = currentField === field
  const next = active && direction === 'desc' ? `${field},asc` : `${field},desc`
  const Icon = direction === 'asc' ? ArrowUp : ArrowDown
  return (
    <th scope="col" aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'} className={`px-5 py-2 font-medium ${align === 'right' ? 'text-right' : 'text-left'}`}>
      <button type="button" onClick={() => onSort(next)} className="inline-flex items-center gap-1 uppercase tracking-wide hover:text-slate-800">
        {label}
        {active && <Icon className="h-3 w-3" aria-hidden="true" />}
      </button>
    </th>
  )
}

export default function TransactionsPage() {
  const toast = useToast()
  const refresh = useRefreshMoneyData()
  const [params, setParams] = useSearchParams()
  const { filters, page, sort } = readParams(params)
  const [editing, setEditing] = useState<Transaction | null>(null)
  const [reversing, setReversing] = useState<Transaction | null>(null)

  const apiFilters = toApiFilters(filters, page, sort)
  const transactions = useQuery({
    queryKey: ['transactions', apiFilters],
    queryFn: () => transactionsApi.list(apiFilters),
    placeholderData: (previous) => previous,
  })
  const accounts = useQuery({ queryKey: ['accounts', { includeArchived: true }], queryFn: () => accountsApi.list(true) })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list() })

  const reverse = useMutation({
    mutationFn: (transaction: Transaction) => transactionsApi.reverse(transaction.id),
    onSuccess: async () => {
      await refresh()
      toast.success('Transaction reversed')
      setReversing(null)
    },
    onError: (error) => {
      toast.error(describeError(error))
      setReversing(null)
    },
  })

  const update = (patch: Partial<FilterValues>, nextSort = sort) => {
    const merged = { ...filters, ...patch }
    const next = new URLSearchParams()
    if (merged.accountId) next.set('accountId', merged.accountId)
    if (merged.type) next.set('type', merged.type)
    if (merged.categoryId) next.set('categoryId', merged.categoryId)
    if (merged.from) next.set('from', merged.from)
    if (merged.to) next.set('to', merged.to)
    if (merged.includeReversed) next.set('includeReversed', 'true')
    if (merged.q) next.set('q', merged.q)
    if (nextSort !== DEFAULT_SORT) next.set('sort', nextSort)
    setParams(next) // changing a filter or the sort starts again from the first page
  }
  const goToPage = (target: number) => {
    const next = new URLSearchParams(params)
    if (target === 0) next.delete('page')
    else next.set('page', String(target))
    setParams(next)
  }

  const accountName = (id: string) => accounts.data?.find((a) => a.id === id)
  const isFiltered = Object.entries(filters).some(([key, value]) => (key === 'includeReversed' ? value : value !== ''))
  const data = transactions.data

  return (
    <>
      <PageHeader
        title="Transactions"
        subtitle="Everything that has moved in or out of your accounts."
        actions={
          <Link to="/transactions/new">
            <Button>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Add transaction
            </Button>
          </Link>
        }
      />

      <div className="space-y-4">
        <Card>
          <FilterBar
            values={filters}
            accounts={accounts.data ?? []}
            categories={categories.data ?? []}
            onChange={(patch) => update(patch)}
            onClear={() => setParams(new URLSearchParams())}
            isFiltered={isFiltered}
          />
        </Card>

        <section className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200" aria-busy={transactions.isFetching}>
          {transactions.isPending ? (
            <div className="space-y-3 p-5" aria-label="Loading transactions">
              {[0, 1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : transactions.isError ? (
            <ErrorState error={transactions.error} onRetry={() => transactions.refetch()} />
          ) : data && data.totalElements === 0 ? (
            isFiltered ? (
              <EmptyState title="No transactions match these filters" message="Try different words, widening the dates or clearing a filter." action={<Button variant="secondary" onClick={() => setParams(new URLSearchParams())}>Clear filters</Button>} />
            ) : (
              <EmptyState title="No transactions yet" message="Add your first income or expense to see it here." action={<Link to="/transactions/new"><Button>Add transaction</Button></Link>} />
            )
          ) : (
            data && (
              <>
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead className="bg-slate-50 text-xs text-slate-500">
                      <tr>
                        <SortHeader label="Date" field="transactionDate" sort={sort} onSort={(s) => update({}, s)} />
                        <th scope="col" className="px-5 py-2 text-left font-medium uppercase tracking-wide">Description</th>
                        <th scope="col" className="hidden px-5 py-2 text-left font-medium uppercase tracking-wide sm:table-cell">Account</th>
                        <th scope="col" className="hidden px-5 py-2 text-left font-medium uppercase tracking-wide md:table-cell">Type</th>
                        <SortHeader label="Amount" field="amount" sort={sort} onSort={(s) => update({}, s)} align="right" />
                        <th scope="col" className="px-5 py-2"><span className="sr-only">Actions</span></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {data.content.map((t) => {
                        const account = accountName(t.accountId)
                        const reversed = t.reversedAt !== null
                        const editable = !reversed && (t.type === 'INCOME' || t.type === 'EXPENSE')
                        return (
                          <tr key={t.id} className={reversed ? 'bg-slate-50/60 text-slate-400' : undefined}>
                            <td className="whitespace-nowrap px-5 py-3">{formatDate(t.transactionDate)}</td>
                            <td className="px-5 py-3">
                              <p className={`font-medium ${reversed ? 'line-through' : 'text-slate-800'}`}>{t.description ?? t.categoryName ?? '-'}</p>
                              <p className="text-xs text-slate-500">
                                {t.categoryName && t.description ? t.categoryName : null}
                                {reversed && <span className="ml-1 rounded-full bg-slate-200 px-2 py-0.5 font-medium text-slate-600 no-underline">Reversed</span>}
                              </p>
                            </td>
                            <td className="hidden px-5 py-3 sm:table-cell">{account?.name ?? '-'}</td>
                            <td className="hidden px-5 py-3 md:table-cell"><TransactionTypeBadge type={t.type} /></td>
                            <td className={`whitespace-nowrap px-5 py-3 text-right font-medium tabular-nums ${reversed ? 'line-through' : signedAmountClasses(t.type)}`}>
                              {signFor(t.type)}
                              {formatMoney(t.amount, account?.currency)}
                            </td>
                            <td className="whitespace-nowrap px-5 py-3 text-right">
                              {editable && (
                                <>
                                  <Button variant="ghost" aria-label={`Edit transaction ${t.description ?? t.categoryName ?? t.id}`} onClick={() => setEditing(t)}>
                                    <Pencil className="h-4 w-4" aria-hidden="true" />
                                  </Button>
                                  <Button variant="ghost" aria-label={`Reverse transaction ${t.description ?? t.categoryName ?? t.id}`} onClick={() => setReversing(t)}>
                                    <Undo2 className="h-4 w-4" aria-hidden="true" />
                                  </Button>
                                </>
                              )}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
                <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} noun="transaction" onPageChange={goToPage} />
              </>
            )
          )}
        </section>
      </div>

      {editing && <EditTransactionModal transaction={editing} onClose={() => setEditing(null)} />}
      {reversing && (
        <ConfirmDialog
          title="Reverse transaction"
          message="The opposite balance change is applied and the transaction is kept in your history, marked as reversed. This cannot be undone."
          confirmLabel="Reverse"
          loading={reverse.isPending}
          onConfirm={() => reverse.mutate(reversing)}
          onCancel={() => setReversing(null)}
        />
      )}
    </>
  )
}
