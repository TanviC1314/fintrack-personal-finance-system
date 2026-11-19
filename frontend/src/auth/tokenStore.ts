const KEY = 'fintrack.accessToken'

/**
 * Keeps the access token in localStorage so a refresh does not log the user out. Storage can be
 * unavailable (private windows, blocked cookies), so every access is guarded and the app then
 * simply asks for a login again.
 */
export const tokenStore = {
  get(): string | null {
    try {
      return window.localStorage.getItem(KEY)
    } catch {
      return null
    }
  },
  set(token: string) {
    try {
      window.localStorage.setItem(KEY, token)
    } catch {
      // Ignore: the session just will not survive a reload.
    }
  },
  clear() {
    try {
      window.localStorage.removeItem(KEY)
    } catch {
      // Ignore.
    }
  },
}
