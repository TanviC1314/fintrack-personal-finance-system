import { describe, expect, it } from 'vitest'
import type { AuditEntry } from '../api/types'
import { describeAudit } from './describeAudit'

const entry = (entityType: AuditEntry['entityType'], action: AuditEntry['action'], details: Record<string, unknown>): AuditEntry => ({
  id: '1', entityType, entityId: 'x', action, details, createdAt: '2025-03-05T10:00:00Z',
})

describe('describeAudit', () => {
  it.each([
    [entry('ACCOUNT', 'CREATED', { name: 'Savings' }), 'Created account "Savings"'],
    [entry('ACCOUNT', 'RENAMED', { from: 'Old', to: 'New' }), 'Renamed account "Old" to "New"'],
    [entry('ACCOUNT', 'ARCHIVED', { name: 'Old' }), 'Archived account "Old"'],
    [entry('TRANSACTION', 'CREATED', { type: 'EXPENSE', amount: 12.5 }), 'Recorded expense of €12.50'],
    [entry('TRANSACTION', 'UPDATED', { previousAmount: 10, newAmount: 15.25 }), 'Changed a transaction from €10.00 to €15.25'],
    [entry('TRANSACTION', 'REVERSED', { type: 'INCOME', amount: 1000 }), 'Reversed income of €1,000.00'],
    [entry('TRANSFER', 'CREATED', { amount: 300 }), 'Transferred €300.00 between accounts'],
  ])('summarises %#', (e, expected) => {
    expect(describeAudit(e)).toBe(expected)
  })

  it('falls back to a generic line for an event it does not know', () => {
    expect(describeAudit(entry('TRANSFER', 'ARCHIVED', {}))).toBe('transfer archived')
  })
})
