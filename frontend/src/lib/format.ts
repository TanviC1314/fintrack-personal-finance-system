const LOCALE = 'en-IE'

export function formatMoney(amount: number, currency: string | null | undefined = 'EUR'): string {
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency: currency ?? 'EUR' }).format(amount)
}

function parseDate(iso: string): Date {
  // Built from its parts so a plain date is never shifted by the viewer's timezone.
  const [year, month, day] = iso.slice(0, 10).split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** "2025-03-05" becomes "5 Mar 2025". */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'short', year: 'numeric' }).format(parseDate(iso))
}

/** "2025-03" becomes "March 2025". */
export function formatMonth(month: string): string {
  return new Intl.DateTimeFormat(LOCALE, { month: 'long', year: 'numeric' }).format(parseDate(`${month}-01`))
}

/** "2025-03" becomes "Mar". */
export function formatMonthShort(month: string): string {
  return new Intl.DateTimeFormat(LOCALE, { month: 'short' }).format(parseDate(`${month}-01`))
}

export function currentMonth(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** Moves a yyyy-MM month forwards or backwards by a number of months. */
export function shiftMonth(month: string, delta: number): string {
  const [year, number] = month.split('-').map(Number)
  const shifted = new Date(year, number - 1 + delta, 1)
  return currentMonth(shifted)
}

/** "transaction" becomes "transactions" and "entry" becomes "entries". */
export function plural(noun: string): string {
  return /[^aeiou]y$/.test(noun) ? `${noun.slice(0, -1)}ies` : `${noun}s`
}
