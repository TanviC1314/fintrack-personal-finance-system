import { request } from './client'
import type { AuditEntry, Page } from './types'

export const auditApi = {
  list: (page: number, size: number) => request<Page<AuditEntry>>('/audit-logs', { query: { page, size } }),
}
