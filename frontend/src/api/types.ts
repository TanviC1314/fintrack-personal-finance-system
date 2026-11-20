export interface User {
  id: string
  name: string
  email: string
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export type AccountType = 'CURRENT' | 'SAVINGS' | 'CASH'
export type AccountStatus = 'ACTIVE' | 'ARCHIVED'

export interface Account {
  id: string
  name: string
  type: AccountType
  currency: string
  openingBalance: number
  currentBalance: number
  status: AccountStatus
  createdAt: string
}

export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER_IN' | 'TRANSFER_OUT'

export interface Transaction {
  id: string
  accountId: string
  type: TransactionType
  amount: number
  categoryId: string | null
  categoryName: string | null
  description: string | null
  transactionDate: string
  reversedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface DashboardSummary {
  currency: string | null
  month: string
  totalBalance: number
  monthlyIncome: number
  monthlyExpenses: number
  monthlySavings: number
  recentTransactions: Transaction[]
}

export interface MonthlyTrend {
  currency: string | null
  months: { month: string; income: number; expenses: number; savings: number }[]
}

export interface CategorySpendingReport {
  currency: string | null
  month: string
  totalSpent: number
  categories: { categoryId: string | null; categoryName: string; amount: number; percentage: number }[]
}

export interface Page<T> {
  content: T[]
  /** Zero-based page number. */
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Category {
  id: string
  name: string
  createdAt: string
}

export interface Transfer {
  id: string
  sourceAccountId: string
  destinationAccountId: string
  amount: number
  description: string | null
  transferDate: string
  createdAt: string
}

export interface Budget {
  id: string
  categoryId: string
  categoryName: string
  /** The month the budget applies to, as yyyy-MM. */
  month: string
  currency: string
  amount: number
  spent: number
  /** Negative once the budget is exceeded. */
  remaining: number
  /** Spent as a percentage of the amount; may be above 100. */
  utilisationPercent: number
  exceeded: boolean
}

export interface AuditEntry {
  id: string
  entityType: 'ACCOUNT' | 'TRANSACTION' | 'TRANSFER'
  entityId: string
  action: 'CREATED' | 'UPDATED' | 'REVERSED' | 'RENAMED' | 'ARCHIVED'
  details: Record<string, unknown>
  createdAt: string
}
