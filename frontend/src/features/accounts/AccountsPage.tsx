import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { accountsApi } from '../../api/accounts'
import { describeError } from '../../api/client'
import type { Account } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { formatMoney } from '../../lib/format'
import { CreateAccountModal, RenameAccountModal } from './AccountModals'
import { ACCOUNT_TYPES } from './schemas'

const typeLabel = (type: Account['type']) => ACCOUNT_TYPES.find((t) => t.value === type)?.label ?? type

function AccountRow({ account, onRename, onArchive }: { account: Account; onRename: () => void; onArchive: () => void }) {
  const archived = account.status === 'ARCHIVED'
  const hasBalance = account.currentBalance !== 0
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-4">
      <div className="min-w-0">
        <p className={`truncate font-medium ${archived ? 'text-slate-400' : 'text-slate-900'}`}>{account.name}</p>
        <p className="mt-0.5 flex items-center gap-2 text-xs text-slate-500">
          <span>{typeLabel(account.type)}</span>
          <span aria-hidden="true">·</span>
          <span>{account.currency}</span>
          {archived && <span className="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-600">Archived</span>}
        </p>
      </div>
      <div className="flex items-center gap-4">
        <p className={`text-lg font-semibold tabular-nums ${archived ? 'text-slate-400' : 'text-slate-900'}`}>
          {formatMoney(account.currentBalance, account.currency)}
        </p>
        {!archived && (
          <div className="flex gap-1">
            <Button variant="ghost" aria-label={`Rename ${account.name}`} onClick={onRename}>
              <Pencil className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              aria-label={`Archive ${account.name}`}
              disabled={hasBalance}
              title={hasBalance ? 'Move or spend the remaining balance before archiving' : 'Archive this account'}
              onClick={onArchive}
            >
              <Archive className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        )}
      </div>
    </li>
  )
}

export default function AccountsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [showArchived, setShowArchived] = useState(false)
  const [creating, setCreating] = useState(false)
  const [renaming, setRenaming] = useState<Account | null>(null)
  const [archiving, setArchiving] = useState<Account | null>(null)

  const accounts = useQuery({
    queryKey: ['accounts', { includeArchived: showArchived }],
    queryFn: () => accountsApi.list(showArchived),
  })

  const archive = useMutation({
    mutationFn: (account: Account) => accountsApi.archive(account.id),
    onSuccess: async (_, account) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['accounts'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
      toast.success(`Account "${account.name}" archived`)
      setArchiving(null)
    },
    onError: (error) => {
      toast.error(describeError(error))
      setArchiving(null)
    },
  })

  return (
    <>
      <PageHeader
        title="Accounts"
        subtitle="Where your money is kept."
        actions={
          <>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600" />
              Show archived
            </label>
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              New account
            </Button>
          </>
        }
      />

      <Card>
        {accounts.isPending ? (
          <div className="space-y-4" aria-busy="true" aria-label="Loading accounts">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : accounts.isError ? (
          <ErrorState error={accounts.error} onRetry={() => accounts.refetch()} />
        ) : accounts.data.length === 0 ? (
          <EmptyState
            title="No accounts yet"
            message="Create your first account to start recording income and spending."
            action={<Button onClick={() => setCreating(true)}>Create an account</Button>}
          />
        ) : (
          <ul className="-my-4 divide-y divide-slate-100">
            {accounts.data.map((account) => (
              <AccountRow key={account.id} account={account} onRename={() => setRenaming(account)} onArchive={() => setArchiving(account)} />
            ))}
          </ul>
        )}
      </Card>

      {creating && <CreateAccountModal onClose={() => setCreating(false)} />}
      {renaming && <RenameAccountModal account={renaming} onClose={() => setRenaming(null)} />}
      {archiving && (
        <ConfirmDialog
          title="Archive account"
          message={`"${archiving.name}" will be hidden from your accounts and can no longer receive transactions. Its history is kept.`}
          confirmLabel="Archive"
          loading={archive.isPending}
          onConfirm={() => archive.mutate(archiving)}
          onCancel={() => setArchiving(null)}
        />
      )}
    </>
  )
}
