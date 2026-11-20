import { lazy } from 'react'
import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute, PublicOnlyRoute } from './auth/RouteGuards'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import NotFoundPage from './pages/NotFoundPage'

// Pages load on demand so the charting library is not part of the login screen's download.
const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage'))
const AccountsPage = lazy(() => import('./features/accounts/AccountsPage'))
const TransactionsPage = lazy(() => import('./features/transactions/TransactionsPage'))
const AddTransactionPage = lazy(() => import('./features/transactions/AddTransactionPage'))
const TransfersPage = lazy(() => import('./features/transfers/TransfersPage'))
const BudgetsPage = lazy(() => import('./features/budgets/BudgetsPage'))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))

export default function App() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/transactions/new" element={<AddTransactionPage />} />
          <Route path="/transfers" element={<TransfersPage />} />
          <Route path="/budgets" element={<BudgetsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
