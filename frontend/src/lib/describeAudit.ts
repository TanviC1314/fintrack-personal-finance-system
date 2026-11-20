import type { AuditEntry } from '../api/types'
import { formatMoney } from './format'

function text(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function money(value: unknown): string {
  return typeof value === 'number' ? formatMoney(value) : ''
}

/** A one-line, human-readable summary of an audit entry. */
export function describeAudit(entry: AuditEntry): string {
  const d = entry.details
  switch (`${entry.entityType}:${entry.action}`) {
    case 'ACCOUNT:CREATED':
      return `Created account "${text(d.name)}"`
    case 'ACCOUNT:RENAMED':
      return `Renamed account "${text(d.from)}" to "${text(d.to)}"`
    case 'ACCOUNT:ARCHIVED':
      return `Archived account "${text(d.name)}"`
    case 'TRANSACTION:CREATED':
      return `Recorded ${text(d.type).toLowerCase()} of ${money(d.amount)}`
    case 'TRANSACTION:UPDATED':
      return `Changed a transaction from ${money(d.previousAmount)} to ${money(d.newAmount)}`
    case 'TRANSACTION:REVERSED':
      return `Reversed ${text(d.type).toLowerCase()} of ${money(d.amount)}`
    case 'TRANSFER:CREATED':
      return `Transferred ${money(d.amount)} between accounts`
    default:
      return `${entry.entityType.toLowerCase()} ${entry.action.toLowerCase()}`
  }
}
