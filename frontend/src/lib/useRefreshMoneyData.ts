import { useQueryClient } from '@tanstack/react-query'

/**
 * Anything that moves money changes balances, history, the dashboard and budget usage at once, so all
 * of those cached views are marked stale together rather than each page guessing what it affects.
 */
export function useRefreshMoneyData() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['accounts', 'transactions', 'transfers', 'dashboard', 'budgets', 'categories'].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )
}
