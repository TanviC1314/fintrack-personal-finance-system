import { request } from './client'
import type { Budget } from './types'

export interface CreateBudgetInput {
  categoryId: string
  month: string
  currency: string
  amount: number
}

export const budgetsApi = {
  list: (month: string, exceededOnly = false) => request<Budget[]>('/budgets', { query: { month, exceededOnly } }),
  create: (body: CreateBudgetInput) => request<Budget>('/budgets', { method: 'POST', body }),
  updateAmount: (id: string, amount: number) => request<Budget>(`/budgets/${id}`, { method: 'PUT', body: { amount } }),
}
