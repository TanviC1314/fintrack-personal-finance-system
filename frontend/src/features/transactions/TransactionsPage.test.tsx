import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/ui/Toast'
import TransactionsPage from './TransactionsPage'

const account = { id: 'a1', name: 'Current', type: 'CURRENT', currency: 'EUR', openingBalance: 0, currentBalance: 100, status: 'ACTIVE', createdAt: '2025-01-01T00:00:00Z' }
const base = { accountId: 'a1', categoryId: null, categoryName: null, description: null, reversedAt: null, createdAt: '', updatedAt: '' }

const rows = [
  { ...base, id: 't1', type: 'EXPENSE', amount: 12.5, description: 'Lunch', categoryName: 'Food', transactionDate: '2025-03-05' },
  { ...base, id: 't2', type: 'INCOME', amount: 3000, description: 'Salary', transactionDate: '2025-03-01' },
  { ...base, id: 't3', type: 'TRANSFER_OUT', amount: 500, description: 'Transfer to Savings', transactionDate: '2025-03-02' },
  { ...base, id: 't4', type: 'EXPENSE', amount: 9.99, description: 'Mistake', transactionDate: '2025-03-03', reversedAt: '2025-03-04T10:00:00Z' },
]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function page(content: unknown[], overrides = {}) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides }
}

function renderPage(url = '/transactions') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <MemoryRouter initialEntries={[url]}>
          <TransactionsPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('TransactionsPage', () => {
  const fetchMock = vi.fn()
  const listCalls = () => fetchMock.mock.calls.map(([url]) => String(url)).filter((u) => u.startsWith('/api/v1/transactions'))

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation(async (url: string) => {
      if (url.startsWith('/api/v1/accounts')) return json([account])
      if (url.startsWith('/api/v1/categories')) return json([])
      return json(page(rows))
    })
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('shows signed amounts, marks reversed rows and offers actions only where they are allowed', async () => {
    renderPage()

    expect(await screen.findByText('Lunch')).toBeInTheDocument()
    expect(screen.getByText('-€12.50')).toBeInTheDocument()
    expect(screen.getByText('+€3,000.00')).toBeInTheDocument()
    expect(screen.getByText('Reversed')).toBeInTheDocument()

    const lunchRow = screen.getByText('Lunch').closest('tr')!
    expect(within(lunchRow).getByRole('button', { name: /Edit transaction/ })).toBeInTheDocument()
    const transferRow = screen.getByText('Transfer to Savings').closest('tr')!
    expect(within(transferRow).queryByRole('button')).toBeNull()
    const reversedRow = screen.getByText('Mistake').closest('tr')!
    expect(within(reversedRow).queryByRole('button')).toBeNull()
  })

  it('sends the filters from the address bar to the API', async () => {
    renderPage('/transactions?type=EXPENSE&from=2025-03-01&to=2025-03-31&page=2&sort=amount,asc')

    await screen.findByText('Lunch')

    const url = new URL(listCalls()[0], 'http://localhost')
    expect(url.searchParams.get('type')).toBe('EXPENSE')
    expect(url.searchParams.get('from')).toBe('2025-03-01')
    expect(url.searchParams.get('to')).toBe('2025-03-31')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('20')
    expect(url.searchParams.get('sort')).toBe('amount,asc')
  })

  it('requests a new list when a filter changes and offers to clear it', async () => {
    renderPage()
    await screen.findByText('Lunch')

    await userEvent.selectOptions(screen.getByLabelText('Type'), 'INCOME')

    await screen.findByRole('button', { name: 'Clear filters' })
    const last = new URL(listCalls().at(-1)!, 'http://localhost')
    expect(last.searchParams.get('type')).toBe('INCOME')
    expect(last.searchParams.get('page')).toBe('0')
  })

  it('reports totals and lets the user move between pages', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url.startsWith('/api/v1/accounts')) return json([account])
      if (url.startsWith('/api/v1/categories')) return json([])
      return json(page(rows, { page: 1, totalElements: 45, totalPages: 3 }))
    })
    renderPage('/transactions?page=1')

    expect(await screen.findByText(/Page 2 of 3/)).toBeInTheDocument()
    expect(screen.getByText(/45 transactions/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))

    await screen.findByText('Lunch')
    expect(new URL(listCalls().at(-1)!, 'http://localhost').searchParams.get('page')).toBe('2')
  })

  it('distinguishes "nothing yet" from "nothing matches the filters"', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.startsWith('/api/v1/accounts') ? json([account]) : url.startsWith('/api/v1/categories') ? json([]) : json(page([])),
    )

    const first = renderPage('/transactions')
    expect(await screen.findByText('No transactions yet')).toBeInTheDocument()
    first.unmount()

    renderPage('/transactions?type=INCOME')
    expect(await screen.findByText('No transactions match these filters')).toBeInTheDocument()
  })

  it('explains a failure and offers a retry', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.startsWith('/api/v1/transactions') ? json({ title: 'Internal server error', detail: 'An unexpected error occurred' }, 500) : json([]),
    )

    renderPage()

    expect(await screen.findByText('We could not load this')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
