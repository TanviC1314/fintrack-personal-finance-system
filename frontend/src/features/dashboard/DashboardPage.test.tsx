import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage'

const account = {
  id: 'a1', name: 'Current', type: 'CURRENT', currency: 'EUR',
  openingBalance: 0, currentBalance: 3800, status: 'ACTIVE', createdAt: '2025-01-01T00:00:00Z',
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderDashboard() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('shows the four headline numbers from the API', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url.startsWith('/api/v1/accounts')) return json([account])
      if (url.startsWith('/api/v1/dashboard/summary')) {
        return json({
          currency: 'EUR', month: '2025-03', totalBalance: 3800, monthlyIncome: 3000,
          monthlyExpenses: 260, monthlySavings: 2740, recentTransactions: [],
        })
      }
      if (url.startsWith('/api/v1/dashboard/monthly')) return json({ currency: 'EUR', months: [] })
      return json({ currency: 'EUR', month: '2025-03', totalSpent: 0, categories: [] })
    })

    renderDashboard()

    expect(await screen.findByText('€3,800.00')).toBeInTheDocument()
    expect(screen.getByText('€3,000.00')).toBeInTheDocument()
    expect(screen.getByText('€260.00')).toBeInTheDocument()
    expect(screen.getByText('€2,740.00')).toBeInTheDocument()
    expect(await screen.findByText('No transactions yet')).toBeInTheDocument()
    expect(await screen.findByText('No spending this month')).toBeInTheDocument()
  })

  it('explains a failure and offers a retry instead of showing an empty page', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.startsWith('/api/v1/dashboard/summary')
        ? json({ title: 'Internal server error', detail: 'An unexpected error occurred' }, 500)
        : json([]),
    )

    renderDashboard()

    expect(await screen.findByText('We could not load this')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
