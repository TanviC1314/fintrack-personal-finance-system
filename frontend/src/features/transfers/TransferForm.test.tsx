import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Account } from '../../api/types'
import { ToastProvider } from '../../components/ui/Toast'
import { TransferForm } from './TransferForm'

const account = (id: string, name: string, currency = 'EUR', balance = 100): Account => ({
  id, name, type: 'CURRENT', currency, openingBalance: 0, currentBalance: balance, status: 'ACTIVE', createdAt: '2025-01-01T00:00:00Z',
})
const accounts = [account('a1', 'Current'), account('a2', 'Savings'), account('a3', 'Dollars', 'USD')]

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderForm() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <TransferForm accounts={accounts} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const posts = (fetchMock: ReturnType<typeof vi.fn>) =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).includes('/transfers') && init?.method === 'POST')

describe('TransferForm', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockResolvedValue(json([]))
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('only offers destinations in the same currency as the source account', async () => {
    renderForm()

    const destination = screen.getByLabelText('To')
    const options = Array.from(destination.querySelectorAll('option')).map((o) => o.textContent)

    expect(options).toEqual(['Choose an account', 'Savings'])
  })

  it('asks for a destination and an amount before calling the server', async () => {
    renderForm()

    await userEvent.click(screen.getByRole('button', { name: 'Transfer money' }))

    expect(await screen.findByText('Choose the account to move money to')).toBeInTheDocument()
    expect(screen.getByText('Amount is required')).toBeInTheDocument()
    expect(posts(fetchMock)).toHaveLength(0)
  })

  it('sends the transfer with an Idempotency-Key header', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST' ? json({ id: 't1' }, 201) : json([]),
    )
    renderForm()

    await userEvent.selectOptions(screen.getByLabelText('To'), 'a2')
    await userEvent.type(screen.getByLabelText('Amount'), '40.25')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer money' }))

    await waitFor(() => expect(posts(fetchMock)).toHaveLength(1))
    const [, init] = posts(fetchMock)[0]
    expect(init.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/)
    expect(JSON.parse(init.body)).toMatchObject({ sourceAccountId: 'a1', destinationAccountId: 'a2', amount: 40.25, description: null })
  })

  it('reuses the same key when the identical request is retried after a failure', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST' ? json({ title: 'Internal server error', detail: 'An unexpected error occurred' }, 500) : json([]),
    )
    renderForm()

    await userEvent.selectOptions(screen.getByLabelText('To'), 'a2')
    await userEvent.type(screen.getByLabelText('Amount'), '10')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer money' }))
    await screen.findByText('An unexpected error occurred')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer money' }))

    await waitFor(() => expect(posts(fetchMock)).toHaveLength(2))
    const keys = posts(fetchMock).map(([, init]) => init.headers['Idempotency-Key'])
    expect(keys[0]).toBe(keys[1])
  })

  it('shows "insufficient funds" on the amount field', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST'
        ? json({ title: 'Business rule violated', detail: 'Insufficient funds in the account' }, 422)
        : json([]),
    )
    renderForm()

    await userEvent.selectOptions(screen.getByLabelText('To'), 'a2')
    await userEvent.type(screen.getByLabelText('Amount'), '999')
    await userEvent.click(screen.getByRole('button', { name: 'Transfer money' }))

    expect(await screen.findByText('Insufficient funds in the account')).toBeInTheDocument()
  })
})
