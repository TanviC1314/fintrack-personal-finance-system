import { request } from './client'
import type { Account, AccountType } from './types'

export interface CreateAccountInput {
  name: string
  type: AccountType
  currency: string
  openingBalance?: number
}

export const accountsApi = {
  list: (includeArchived = false) => request<Account[]>('/accounts', { query: { includeArchived } }),
  create: (body: CreateAccountInput) => request<Account>('/accounts', { method: 'POST', body }),
  rename: (id: string, name: string) => request<Account>(`/accounts/${id}`, { method: 'PUT', body: { name } }),
  archive: (id: string) => request<void>(`/accounts/${id}`, { method: 'DELETE' }),
}
