import { request } from './client'
import type { CategorySpendingReport, DashboardSummary, MonthlyTrend } from './types'

export const dashboardApi = {
  summary: (month: string, currency?: string) =>
    request<DashboardSummary>('/dashboard/summary', { query: { month, currency } }),
  monthly: (to: string, months: number, currency?: string) =>
    request<MonthlyTrend>('/dashboard/monthly', { query: { to, months, currency } }),
  categories: (month: string, currency?: string) =>
    request<CategorySpendingReport>('/dashboard/categories', { query: { month, currency } }),
}
