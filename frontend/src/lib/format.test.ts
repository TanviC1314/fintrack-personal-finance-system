import { describe, expect, it } from 'vitest'
import { currentMonth, formatDate, formatMoney, formatMonth, formatMonthShort, plural, shiftMonth } from './format'

describe('formatMoney', () => {
  it('shows two decimals and the currency symbol', () => {
    expect(formatMoney(1234.5, 'EUR')).toBe('€1,234.50')
    expect(formatMoney(0, 'EUR')).toBe('€0.00')
  })

  it('shows negative amounts with a minus sign', () => {
    expect(formatMoney(-150.25, 'EUR')).toContain('150.25')
    expect(formatMoney(-150.25, 'EUR')).toMatch(/^-/)
  })

  it('supports other currencies and falls back to EUR when there is none', () => {
    expect(formatMoney(5, 'USD')).toContain('5.00')
    expect(formatMoney(5, null)).toBe('€5.00')
  })
})

describe('dates', () => {
  it('formats a plain date without shifting it by timezone', () => {
    expect(formatDate('2025-03-05')).toBe('5 Mar 2025')
    expect(formatDate('2025-12-31')).toBe('31 Dec 2025')
  })

  it('formats months', () => {
    expect(formatMonth('2025-03')).toBe('March 2025')
    expect(formatMonthShort('2025-03')).toBe('Mar')
  })

  it('builds the current month from local date parts', () => {
    expect(currentMonth(new Date(2026, 0, 31))).toBe('2026-01')
    expect(currentMonth(new Date(2026, 11, 1))).toBe('2026-12')
  })

  it('shifts months across year boundaries', () => {
    expect(shiftMonth('2025-01', -1)).toBe('2024-12')
    expect(shiftMonth('2025-12', 1)).toBe('2026-01')
    expect(shiftMonth('2025-03', 0)).toBe('2025-03')
    expect(shiftMonth('2025-03', -15)).toBe('2023-12')
  })
})

describe('plural', () => {
  it.each([
    ['transaction', 'transactions'],
    ['transfer', 'transfers'],
    ['entry', 'entries'],
    ['day', 'days'], // a vowel before the y keeps a plain s
  ])('%s becomes %s', (noun, expected) => {
    expect(plural(noun)).toBe(expected)
  })
})
