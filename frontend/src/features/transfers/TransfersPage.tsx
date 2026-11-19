import { useQuery } from '@tanstack/react-query'
import { ArrowRight } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { accountsApi } from '../../api/accounts'
import { transfersApi } from '../../api/transfers'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { formatDate, formatMoney } from '../../lib/format'
import { TransferForm } from './TransferForm'

const PAGE_SIZE = 10

export default function TransfersPage() {
  const [page, setPage] = useState(0)
  const accounts = useQuery({ queryKey: ['accounts', { includeArchived: true }], queryFn: () => accountsApi.list(true) })
  const transfers = useQuery({
    queryKey: ['transfers', page],
    queryFn: () => transfersApi.list(page, PAGE_SIZE),
    placeholderData: (previous) => previous,
  })

  const active = (accounts.data ?? []).filter((a) => a.status === 'ACTIVE')
  const account = (id: string) => accounts.data?.find((a) => a.id === id)

  return (
    <>
      <PageHeader title="Transfers" subtitle="Move money between your own accounts." />
      <div className="grid gap-6 lg:grid-cols-5">
        <Card title="New transfer" className="lg:col-span-2 lg:self-start">
          {accounts.isPending ? (
            <Skeleton className="h-64 w-full" />
          ) : accounts.isError ? (
            <ErrorState error={accounts.error} onRetry={() => accounts.refetch()} />
          ) : active.length < 2 ? (
            <EmptyState
              title="You need two accounts"
              message="A transfer moves money from one of your accounts to another."
              action={
                <Link to="/accounts">
                  <Button>Go to accounts</Button>
                </Link>
              }
            />
          ) : (
            <TransferForm accounts={active} />
          )}
        </Card>

        <section className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200 lg:col-span-3" aria-label="Transfer history">
          <header className="border-b border-slate-100 px-5 py-3">
            <h2 className="text-sm font-semibold text-slate-800">History</h2>
          </header>
          {transfers.isPending ? (
            <div className="space-y-3 p-5">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : transfers.isError ? (
            <ErrorState error={transfers.error} onRetry={() => transfers.refetch()} />
          ) : transfers.data.totalElements === 0 ? (
            <EmptyState title="No transfers yet" message="Transfers you make will be listed here." />
          ) : (
            <>
              <ul className="divide-y divide-slate-100">
                {transfers.data.content.map((t) => {
                  const from = account(t.sourceAccountId)
                  const to = account(t.destinationAccountId)
                  return (
                    <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-sm">
                      <div className="min-w-0">
                        <p className="flex flex-wrap items-center gap-1.5 font-medium text-slate-800">
                          <span>{from?.name ?? 'Unknown'}</span>
                          <ArrowRight className="h-3.5 w-3.5 text-slate-400" aria-label="to" />
                          <span>{to?.name ?? 'Unknown'}</span>
                        </p>
                        <p className="text-xs text-slate-500">
                          {formatDate(t.transferDate)}
                          {t.description ? ` · ${t.description}` : ''}
                        </p>
                      </div>
                      <p className="font-medium tabular-nums text-slate-900">{formatMoney(t.amount, from?.currency)}</p>
                    </li>
                  )
                })}
              </ul>
              <Pagination page={transfers.data.page} totalPages={transfers.data.totalPages} totalElements={transfers.data.totalElements} noun="transfer" onPageChange={setPage} />
            </>
          )}
        </section>
      </div>
    </>
  )
}
