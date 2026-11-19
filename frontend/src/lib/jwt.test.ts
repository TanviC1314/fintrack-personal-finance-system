import { describe, expect, it } from 'vitest'
import { isTokenExpired, tokenExpiry } from './jwt'

function tokenWithExpiry(exp: number): string {
  const payload = btoa(JSON.stringify({ sub: 'u', exp })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `header.${payload}.signature`
}

describe('jwt expiry', () => {
  it('reads the expiry in milliseconds', () => {
    expect(tokenExpiry(tokenWithExpiry(1_700_000_000))).toBe(1_700_000_000_000)
  })

  it('treats a token as valid until its expiry and expired from then on', () => {
    const token = tokenWithExpiry(1_700_000_000)

    expect(isTokenExpired(token, 1_700_000_000_000 - 1)).toBe(false)
    expect(isTokenExpired(token, 1_700_000_000_000)).toBe(true)
  })

  it('treats anything unreadable as expired rather than trusting it', () => {
    expect(isTokenExpired('not-a-jwt')).toBe(true)
    expect(isTokenExpired('a.%%%.c')).toBe(true)
    expect(isTokenExpired(`a.${btoa('{}')}.c`)).toBe(true)
  })
})
