import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '../auth/tokenStore'
import { ApiError, request, setUnauthorizedHandler } from './client'

function respond(status: number, body?: unknown, contentType = 'application/problem+json') {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': contentType },
  })
}

describe('api client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    tokenStore.clear()
  })

  afterEach(() => {
    fetchMock.mockReset()
    vi.unstubAllGlobals()
    setUnauthorizedHandler(null)
  })

  it('sends the bearer token and a JSON body, and builds the query string without empty values', async () => {
    tokenStore.set('abc')
    fetchMock.mockResolvedValue(respond(200, { ok: true }, 'application/json'))

    await request('/things', { method: 'POST', body: { a: 1 }, query: { page: 0, q: '', skip: undefined, flag: false } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/things?page=0&flag=false')
    expect(init.headers.Authorization).toBe('Bearer abc')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"a":1}')
  })

  it('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(request('/things/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('turns a problem response into an ApiError with its field errors', async () => {
    fetchMock.mockResolvedValue(
      respond(400, { title: 'Validation failed', detail: 'Request validation failed', errors: [{ field: 'email', message: 'bad' }] }),
    )

    const error = await request('/auth/register', { method: 'POST', body: {} }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).fieldErrors).toEqual([{ field: 'email', message: 'bad' }])
  })

  it('reports an unreachable server as status 0 instead of throwing a raw fetch error', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    const error = await request('/things').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(0)
    expect((error as ApiError).title).toBe('Cannot reach the server')
  })

  it('ends the session when a request that carried a token is rejected with 401', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    tokenStore.set('expired')
    fetchMock.mockResolvedValue(respond(401, { title: 'Authentication required' }))

    await request('/accounts').catch(() => undefined)

    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not treat a failed login (401 without a token) as an expired session', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    fetchMock.mockResolvedValue(respond(401, { title: 'Authentication failed' }))

    await request('/auth/login', { method: 'POST', body: {} }).catch(() => undefined)

    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
