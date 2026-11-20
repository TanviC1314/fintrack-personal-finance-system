import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u1', name: 'Alice Example', email: 'alice@example.com', createdAt: '2025-01-15T09:00:00Z' },
    logout: vi.fn(),
  }),
}))

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ProfilePage />
    </QueryClientProvider>,
  )
}

describe('ProfilePage', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
  })

  it('shows the signed-in details and the activity log in plain language', async () => {
    fetchMock.mockResolvedValue(
      json({
        content: [{ id: 'e1', entityType: 'TRANSACTION', entityId: 't1', action: 'CREATED', details: { type: 'EXPENSE', amount: 12.5 }, createdAt: '2025-03-05T10:00:00Z' }],
        page: 0, size: 10, totalElements: 1, totalPages: 1,
      }),
    )

    renderPage()

    expect(screen.getByText('Alice Example')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('15 Jan 2025')).toBeInTheDocument()
    expect(await screen.findByText('Recorded expense of €12.50')).toBeInTheDocument()
  })

  it('says so when there is no activity yet', async () => {
    fetchMock.mockResolvedValue(json({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }))

    renderPage()

    expect(await screen.findByText('No activity yet')).toBeInTheDocument()
  })
})
