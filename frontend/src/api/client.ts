import { tokenStore } from '../auth/tokenStore'

export interface FieldError {
  field: string
  message: string
}

/** An error response from the API (RFC 9457 problem details), or a failure to reach it (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, title: string, detail: string, fieldErrors: FieldError[] = []) {
    super(detail || title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.fieldErrors = fieldErrors
  }
}

type QueryValue = string | number | boolean | null | undefined

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, QueryValue>
  headers?: Record<string, string>
  signal?: AbortSignal
}

const BASE_URL = '/api/v1'

let unauthorizedHandler: (() => void) | null = null

/** Called when the server rejects a request that carried a token (expired or revoked session). */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

function withQuery(path: string, query?: Record<string, QueryValue>): string {
  if (!query) return `${BASE_URL}${path}`
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const text = params.toString()
  return `${BASE_URL}${path}${text ? `?${text}` : ''}`
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: { title?: string; detail?: string; errors?: FieldError[] } = {}
  try {
    problem = await response.json()
  } catch {
    // The body was empty or not JSON; fall back to the status line.
  }
  return new ApiError(
    response.status,
    problem.title ?? response.statusText ?? 'Request failed',
    problem.detail ?? '',
    problem.errors ?? [],
  )
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = tokenStore.get()
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(withQuery(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Cannot reach the server', 'Check your connection and try again.')
  }

  if (!response.ok) {
    if (response.status === 401 && token) unauthorizedHandler?.()
    throw await toApiError(response)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** A readable message for any error, suitable for showing to the user. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Something went wrong. Please try again.'
}
