/** Up to 17 digits before the decimal point and at most two after it, matching the database's NUMERIC(19,2). */
export const MONEY_PATTERN = /^\d{1,17}(\.\d{1,2})?$/

export const MONEY_FORMAT_MESSAGE = 'Enter an amount like 12.50 (at most two decimals)'

/**
 * Converts text that already passed MONEY_PATTERN into a number for the API. The API reads the number
 * as an exact decimal, and a value with at most two decimals prints back to the same text, so nothing
 * is lost on the way.
 */
export function parseMoney(text: string): number {
  return Number(text.trim())
}
