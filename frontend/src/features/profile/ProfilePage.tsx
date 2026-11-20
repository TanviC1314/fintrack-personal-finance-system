import { useQuery } from '@tanstack/react-query'
import { LogOut } from 'lucide-react'
import { useState } from 'react'
import { auditApi } from '../../api/audit'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { describeAudit } from '../../lib/describeAudit'
import { formatDate } from '../../lib/format'

const PAGE_SIZE = 10

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const [page, setPage] = useState(0)
  const activity = useQuery({
    queryKey: ['audit', page],
    queryFn: () => auditApi.list(page, PAGE_SIZE),
    placeholderData: (previous) => previous,
  })

  return (
    <>
      <PageHeader title="Profile" subtitle="Your details and a record of what has changed." />
      <div className="grid gap-6 lg:grid-cols-5">
        <Card title="Your details" className="lg:col-span-2 lg:self-start">
          <dl className="space-y-4 text-sm">
            <div>
              <dt className="text-slate-500">Name</dt>
              <dd className="mt-0.5 font-medium text-slate-900">{user?.name}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Email</dt>
              <dd className="mt-0.5 font-medium text-slate-900">{user?.email}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Member since</dt>
              <dd className="mt-0.5 font-medium text-slate-900">{user ? formatDate(user.createdAt) : ''}</dd>
            </div>
          </dl>
          <Button variant="secondary" className="mt-6" onClick={logout}>
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sign out
          </Button>
          <p className="mt-6 text-xs text-slate-500">
            FinTrack is a portfolio project. It uses simulated data only and is not connected to any bank.
          </p>
        </Card>

        <section className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200 lg:col-span-3" aria-label="Recent activity">
          <header className="border-b border-slate-100 px-5 py-3">
            <h2 className="text-sm font-semibold text-slate-800">Recent activity</h2>
            <p className="text-xs text-slate-500">An append-only log of the changes to your accounts and money.</p>
          </header>
          {activity.isPending ? (
            <div className="space-y-3 p-5">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : activity.isError ? (
            <ErrorState error={activity.error} onRetry={() => activity.refetch()} />
          ) : activity.data.totalElements === 0 ? (
            <EmptyState title="No activity yet" message="Changes to your accounts and transactions will be recorded here." />
          ) : (
            <>
              <ul className="divide-y divide-slate-100">
                {activity.data.content.map((entry) => (
                  <li key={entry.id} className="flex items-baseline justify-between gap-3 px-5 py-3 text-sm">
                    <span className="text-slate-800">{describeAudit(entry)}</span>
                    <time dateTime={entry.createdAt} className="shrink-0 text-xs text-slate-500">
                      {formatDate(entry.createdAt)}
                    </time>
                  </li>
                ))}
              </ul>
              <Pagination page={activity.data.page} totalPages={activity.data.totalPages} totalElements={activity.data.totalElements} noun="entry" onPageChange={setPage} />
            </>
          )}
        </section>
      </div>
    </>
  )
}
