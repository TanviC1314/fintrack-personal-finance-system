import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/ui/Toast'
import AccountsPage from './AccountsPage'

const current = {
  id: 'a1', name: 'Current', type: 'CURRENT', currency: 'EUR', openingBalance: 100, currentBalance: 250.5,
  status: 'ACTIVE', createdAt: '2025-01-01T00:00:00Z',
}
const empty = { ...current, id: 'a2', name: 'Old wallet', type: 'CASH', currentBalance: 0, openingBalance: 0 }

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <AccountsPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AccountsPage', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('lists accounts with their balances and only lets an empty account be archived', async () => {
    fetchMock.mockResolvedValue(json([current, empty]))

    renderPage()

    expect(await screen.findByText('€250.50')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archive Current' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Archive Old wallet' })).toBeEnabled()
  })

  it('shows an empty state with a way forward when there are no accounts', async () => {
    fetchMock.mockResolvedValue(json([]))

    renderPage()

    expect(await screen.findByText('No accounts yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create an account' })).toBeInTheDocument()
  })

  it('validates the new-account form before calling the server', async () => {
    fetchMock.mockResolvedValue(json([]))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'New account' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    expect(await within(dialog).findByText('Name is required')).toBeInTheDocument()
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Savings')
    await userEvent.type(within(dialog).getByLabelText('Opening balance'), '12.345')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    expect(await within(dialog).findByText(/at most two decimals/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
  })

  it('sends the opening balance as a number and closes on success', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST' ? json({ ...current, id: 'a3', name: 'Savings', currentBalance: 1200.5 }, 201) : json([]),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'New account' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Name'), '  Savings ')
    await userEvent.type(within(dialog).getByLabelText('Opening balance'), '1200.50')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
    expect(JSON.parse(post[1].body)).toEqual({ name: 'Savings', type: 'CURRENT', currency: 'EUR', openingBalance: 1200.5 })
  })

  it('shows the server message on the name field when the name is already taken', async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST'
        ? json({ title: 'Conflict', detail: "You already have an active account named 'Savings'" }, 409)
        : json([]),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'New account' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Savings')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create account' }))

    expect(await within(dialog).findByText(/already have an active account/)).toBeInTheDocument()
  })
})
