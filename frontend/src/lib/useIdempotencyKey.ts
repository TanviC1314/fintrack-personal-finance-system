import { useRef } from 'react'

/**
 * Gives each distinct request one stable idempotency key. Submitting the same payload again (a retry after a
 * timeout, a double click) reuses the key so the server recognises it and does not repeat the action; a changed
 * payload, or a payload after a success, gets a fresh key.
 */
export function useIdempotencyKey() {
  const last = useRef<{ fingerprint: string; key: string } | null>(null)

  return {
    keyFor(payload: unknown): string {
      const fingerprint = JSON.stringify(payload)
      if (last.current?.fingerprint !== fingerprint) {
        last.current = { fingerprint, key: crypto.randomUUID() }
      }
      return last.current.key
    },
    /** Call after the request succeeded, so the next identical-looking request is a new one. */
    reset() {
      last.current = null
    },
  }
}
