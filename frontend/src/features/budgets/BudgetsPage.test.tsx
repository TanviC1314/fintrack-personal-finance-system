import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/ui/Toast'
import BudgetsPage from './BudgetsPage'

const budget = (overrides = {}) => ({
  id: 'b1', categoryId: 'c1', categoryName: 'Groceries', month: '2025-03', currency: 'EUR',
  amount: 200, spent: 150.5, remaining: 49.5, utilisationPercent: 75.3, exceeded: false, ...overrides,
})
const categories = [{ id: 'c1', name: 'Groceries', createdAt: '' }, { id: 'c2', name: 'Travel', createdAt: '' }]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <BudgetsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('BudgetsPage', () => {
  const fetchMock = vi.fn()
  let budgets: unknown[]

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    budgets = [budget()]
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/v1/categories')) return json(categories)
      if (url.startsWith('/api/v1/accounts')) return json([])
      if (url.startsWith('/api/v1/budgets') && init?.method === 'POST') return json(budget(), 201)
      return json(budgets)
    })
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('shows how much of each budget is used, with an accessible progress bar', async () => {
    renderPage()

    expect(await screen.findByText('€150.50 of €200.00')).toBeInTheDocument()
    expect(screen.getByText(/75.3% used/)).toBeInTheDocument()
    expect(screen.getByText(/€49.50 left/)).toBeInTheDocument()
    const bar = screen.getByRole('progressbar', { name: 'Groceries budget used' })
    expect(bar).toHaveAttribute('aria-valuenow', '75.3')
  })

  it('flags an overspent budget in words, not just colour, and caps the bar at full', async () => {
    budgets = [budget({ spent: 250, remaining: -50, utilisationPercent: 125, exceeded: true })]

    renderPage()

    expect(await screen.findByText('Over budget')).toBeInTheDocument()
    expect(screen.getByText(/€50.00 over/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
  })

  it('shows a Warning label and amber summary text when a budget is between 80 and 100 percent used', async () => {
    budgets = [budget({ spent: 170, remaining: 30, utilisationPercent: 85, exceeded: false })]

    renderPage()

    expect(await screen.findByText('Warning')).toBeInTheDocument()
    expect(screen.queryByText('Over budget')).not.toBeInTheDocument()
    const summary = screen.getByText(/85% used/)
    expect(summary).toHaveClass('text-amber-700')
  })

  it('shows no state label when a budget is below the warning threshold', async () => {
    renderPage()

    await screen.findByText('€150.50 of €200.00')

    expect(screen.queryByText('Warning')).not.toBeInTheDocument()
    expect(screen.queryByText('Over budget')).not.toBeInTheDocument()
  })

  it('guides the user when there are no budgets', async () => {
    budgets = []

    renderPage()

    expect(await screen.findByText('No budgets for this month')).toBeInTheDocument()
  })

  it('creates a budget for the shown month with a numeric amount', async () => {
    renderPage()
    await screen.findByText('€150.50 of €200.00')

    await userEvent.click(screen.getByRole('button', { name: 'New budget' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.selectOptions(within(dialog).getByLabelText('Category'), 'c2')
    await userEvent.type(within(dialog).getByLabelText('Monthly limit'), '300')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create budget' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1].body)).toMatchObject({ categoryId: 'c2', amount: 300, currency: 'EUR' })
    expect(JSON.parse(post[1].body).month).toMatch(/^\d{4}-\d{2}$/)
  })

  it('validates the limit and shows a duplicate-budget message from the server', async () => {
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/v1/categories')) return json(categories)
      if (url.startsWith('/api/v1/accounts')) return json([])
      if (init?.method === 'POST') return json({ title: 'Conflict', detail: 'A budget for this category, month and currency already exists. Update it instead.' }, 409)
      return json(budgets)
    })
    renderPage()
    await screen.findByText('€150.50 of €200.00')

    await userEvent.click(screen.getByRole('button', { name: 'New budget' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create budget' }))
    // The "Choose a category" option label would also match a text query, so read the error messages by role.
    const messages = (await within(dialog).findAllByRole('alert')).map((el) => el.textContent)
    expect(messages).toEqual(['Choose a category', 'Amount is required'])

    await userEvent.selectOptions(within(dialog).getByLabelText('Category'), 'c1')
    await userEvent.type(within(dialog).getByLabelText('Monthly limit'), '100')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create budget' }))

    expect(await within(dialog).findByText(/already exists/)).toBeInTheDocument()
  })
})
