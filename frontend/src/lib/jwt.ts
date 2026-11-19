/** Reads the expiry (in milliseconds since the epoch) from a JWT without verifying it; the server does that. */
export function tokenExpiry(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const { exp } = JSON.parse(json) as { exp?: number }
    return typeof exp === 'number' ? exp * 1000 : null
  } catch {
    return null
  }
}

export function isTokenExpired(token: string, now: number = Date.now()): boolean {
  const expiry = tokenExpiry(token)
  return expiry === null || expiry <= now
}
