import { request } from './client'
import type { Page, Transfer } from './types'

export interface CreateTransferInput {
  sourceAccountId: string
  destinationAccountId: string
  amount: number
  description: string | null
  transferDate: string
}

export const transfersApi = {
  list: (page: number, size: number) => request<Page<Transfer>>('/transfers', { query: { page, size } }),
  /** The idempotency key makes a retry of the same request safe: the server never moves the money twice. */
  create: (body: CreateTransferInput, idempotencyKey: string) =>
    request<Transfer>('/transfers', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),
}
