import { useQuery } from '@tanstack/react-query'
import { accountsApi } from '../../api/accounts'
import { categoriesApi } from '../../api/categories'
import type { Transaction } from '../../api/types'
import { Modal } from '../../components/ui/Modal'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { TransactionForm } from './TransactionForm'

export function EditTransactionModal({ transaction, onClose }: { transaction: Transaction; onClose: () => void }) {
  // Archived accounts are included so the account name of an old transaction can still be shown.
  const accounts = useQuery({ queryKey: ['accounts', { includeArchived: true }], queryFn: () => accountsApi.list(true) })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list() })

  return (
    <Modal title="Edit transaction" onClose={onClose}>
      {accounts.isPending || categories.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : accounts.isError || categories.isError ? (
        <ErrorState error={accounts.error ?? categories.error} />
      ) : (
        <TransactionForm accounts={accounts.data} categories={categories.data} transaction={transaction} onDone={onClose} onCancel={onClose} />
      )}
    </Modal>
  )
}
