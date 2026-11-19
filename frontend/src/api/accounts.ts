import { request } from './client'
import type { Account } from './types'

export const accountsApi = {
  list: (includeArchived = false) => request<Account[]>('/accounts', { query: { includeArchived } }),
}
