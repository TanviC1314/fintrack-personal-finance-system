import { request } from './client'
import type { Page, Transaction, TransactionType } from './types'

export interface TransactionFilters {
  accountId?: string
  type?: TransactionType
  categoryId?: string
  from?: string
  to?: string
  includeReversed?: boolean
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface CreateTransactionInput {
  accountId: string
  type: 'INCOME' | 'EXPENSE'
  amount: number
  categoryId: string | null
  description: string | null
  transactionDate: string
}

export type UpdateTransactionInput = Omit<CreateTransactionInput, 'accountId' | 'type'>

export const transactionsApi = {
  list: (filters: TransactionFilters) =>
    request<Page<Transaction>>('/transactions', { query: { ...filters } }),
  create: (body: CreateTransactionInput) => request<Transaction>('/transactions', { method: 'POST', body }),
  update: (id: string, body: UpdateTransactionInput) =>
    request<Transaction>(`/transactions/${id}`, { method: 'PUT', body }),
  reverse: (id: string) => request<void>(`/transactions/${id}`, { method: 'DELETE' }),
}
