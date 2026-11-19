import type { Account, Transaction } from '../../api/types'
import { EmptyState } from '../../components/ui/States'
import { formatDate, formatMoney } from '../../lib/format'
import { signFor, signedAmountClasses } from '../../lib/transactionDisplay'
import { TransactionTypeBadge } from './TransactionTypeBadge'

export function RecentTransactions({
  transactions,
  accounts,
  currency,
}: {
  transactions: Transaction[]
  accounts: Account[]
  currency: string | null
}) {
  if (transactions.length === 0) {
    return <EmptyState title="No transactions yet" message="Your newest transactions will be listed here." />
  }
  const accountName = (id: string) => accounts.find((a) => a.id === id)?.name ?? 'Unknown account'

  return (
    <div className="-mx-5 -my-5 overflow-x-auto">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-5 py-2 font-medium">Date</th>
            <th scope="col" className="px-5 py-2 font-medium">Description</th>
            <th scope="col" className="hidden px-5 py-2 font-medium sm:table-cell">Account</th>
            <th scope="col" className="hidden px-5 py-2 font-medium md:table-cell">Type</th>
            <th scope="col" className="px-5 py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {transactions.map((t) => (
            <tr key={t.id}>
              <td className="whitespace-nowrap px-5 py-3 text-slate-600">{formatDate(t.transactionDate)}</td>
              <td className="px-5 py-3">
                <p className="font-medium text-slate-800">{t.description ?? t.categoryName ?? '-'}</p>
                {t.categoryName && t.description && <p className="text-xs text-slate-500">{t.categoryName}</p>}
              </td>
              <td className="hidden px-5 py-3 text-slate-600 sm:table-cell">{accountName(t.accountId)}</td>
              <td className="hidden px-5 py-3 md:table-cell">
                <TransactionTypeBadge type={t.type} />
              </td>
              <td className={`whitespace-nowrap px-5 py-3 text-right font-medium ${signedAmountClasses(t.type)}`}>
                {signFor(t.type)}
                {formatMoney(t.amount, currency)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
