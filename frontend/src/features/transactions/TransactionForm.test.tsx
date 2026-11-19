import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Account, Category } from '../../api/types'
import { ToastProvider } from '../../components/ui/Toast'
import { TransactionForm } from './TransactionForm'
import { latestAllowedDate } from './schemas'

const accounts: Account[] = [
  { id: 'a1', name: 'Current', type: 'CURRENT', currency: 'EUR', openingBalance: 0, currentBalance: 100, status: 'ACTIVE', createdAt: '2025-01-01T00:00:00Z' },
]
const categories: Category[] = [{ id: 'c1', name: 'Food', createdAt: '2025-01-01T00:00:00Z' }]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderForm(onDone = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <TransactionForm accounts={accounts} categories={categories} onDone={onDone} onCancel={vi.fn()} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return onDone
}

const postedBodies = (fetchMock: ReturnType<typeof vi.fn>, urlPart: string) =>
  fetchMock.mock.calls
    .filter(([url, init]) => String(url).includes(urlPart) && init?.method === 'POST')
    .map(([, init]) => JSON.parse(init.body))

describe('TransactionForm', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue(json([]))
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it.each([
    ['', 'Amount is required'],
    ['0', 'Amount must be at least 0.01'],
    ['-5', 'Enter an amount like 12.50 (at most two decimals)'],
    ['12.345', 'Enter an amount like 12.50 (at most two decimals)'],
    ['abc', 'Enter an amount like 12.50 (at most two decimals)'],
  ])('rejects the amount %j before calling the server', async (amount, message) => {
    renderForm()

    if (amount) await userEvent.type(screen.getByLabelText('Amount'), amount)
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(postedBodies(fetchMock, '/transactions')).toHaveLength(0)
  })

  it('does not accept a date in the future', async () => {
    renderForm()
    const farFuture = `${Number(latestAllowedDate().slice(0, 4)) + 1}-01-01`

    await userEvent.type(screen.getByLabelText('Amount'), '10')
    const date = screen.getByLabelText('Date')
    await userEvent.clear(date)
    await userEvent.type(date, farFuture)
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    expect(await screen.findByText('The date cannot be in the future')).toBeInTheDocument()
  })

  it('sends an expense with the chosen category and an exact numeric amount', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST' ? json({ id: 't1' }, 201) : json([]),
    )
    const onDone = renderForm()

    await userEvent.type(screen.getByLabelText('Amount'), '12.50')
    await userEvent.selectOptions(screen.getByLabelText('Category'), 'c1')
    await userEvent.type(screen.getByLabelText('Description'), '  Lunch ')
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    const [body] = postedBodies(fetchMock, '/transactions')
    expect(body).toMatchObject({ accountId: 'a1', type: 'EXPENSE', amount: 12.5, categoryId: 'c1', description: 'Lunch' })
  })

  it('creates a new category first and then uses its id', async () => {
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === 'POST' && String(url).includes('/categories')) return json({ id: 'new-cat', name: 'Gifts' }, 201)
      if (init?.method === 'POST') return json({ id: 't1' }, 201)
      return json([])
    })
    renderForm()

    await userEvent.type(screen.getByLabelText('Amount'), '20')
    await userEvent.selectOptions(screen.getByLabelText('Category'), '__new__')
    await userEvent.type(await screen.findByLabelText('New category name'), 'Gifts')
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    await waitFor(() => expect(postedBodies(fetchMock, '/transactions')).toHaveLength(1))
    expect(postedBodies(fetchMock, '/categories')[0]).toEqual({ name: 'Gifts' })
    expect(postedBodies(fetchMock, '/transactions')[0].categoryId).toBe('new-cat')
  })

  it('asks for a name when "new category" is chosen but left empty', async () => {
    renderForm()

    await userEvent.type(screen.getByLabelText('Amount'), '20')
    await userEvent.selectOptions(screen.getByLabelText('Category'), '__new__')
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    expect(await screen.findByText('Enter a name for the new category')).toBeInTheDocument()
  })

  it('shows "insufficient funds" on the amount field', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST'
        ? json({ title: 'Business rule violated', detail: 'Insufficient funds in the account' }, 422)
        : json([]),
    )
    renderForm()

    await userEvent.type(screen.getByLabelText('Amount'), '500')
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    expect(await screen.findByText('Insufficient funds in the account')).toBeInTheDocument()
  })
})
