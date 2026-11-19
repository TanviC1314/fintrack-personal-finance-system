import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { accountsApi } from '../../api/accounts'
import { categoriesApi } from '../../api/categories'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { TransactionForm } from './TransactionForm'

export default function AddTransactionPage() {
  const navigate = useNavigate()
  const accounts = useQuery({ queryKey: ['accounts', { includeArchived: false }], queryFn: () => accountsApi.list() })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list() })

  return (
    <>
      <PageHeader title="Add transaction" subtitle="Record money coming in or going out." />
      <Card className="max-w-2xl">
        {accounts.isPending || categories.isPending ? (
          <div className="space-y-4" aria-busy="true" aria-label="Loading form">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : accounts.isError || categories.isError ? (
          <ErrorState
            error={accounts.error ?? categories.error}
            onRetry={() => {
              void accounts.refetch()
              void categories.refetch()
            }}
          />
        ) : accounts.data.length === 0 ? (
          <EmptyState
            title="Create an account first"
            message="Transactions belong to an account, and you do not have one yet."
            action={
              <Link to="/accounts">
                <Button>Go to accounts</Button>
              </Link>
            }
          />
        ) : (
          <TransactionForm
            accounts={accounts.data}
            categories={categories.data}
            onDone={() => navigate('/transactions')}
            onCancel={() => navigate(-1)}
          />
        )}
      </Card>
    </>
  )
}
