import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useIdempotencyKey } from './useIdempotencyKey'

describe('useIdempotencyKey', () => {
  it('reuses the key for an identical payload so a retry is recognised', () => {
    const { result } = renderHook(() => useIdempotencyKey())

    const first = result.current.keyFor({ amount: 10, to: 'b' })
    const retry = result.current.keyFor({ amount: 10, to: 'b' })

    expect(retry).toBe(first)
  })

  it('uses a new key when the payload changes', () => {
    const { result } = renderHook(() => useIdempotencyKey())

    const first = result.current.keyFor({ amount: 10 })
    const changed = result.current.keyFor({ amount: 11 })

    expect(changed).not.toBe(first)
  })

  it('uses a new key after a success, even for the same payload', () => {
    const { result } = renderHook(() => useIdempotencyKey())

    const first = result.current.keyFor({ amount: 10 })
    result.current.reset()

    expect(result.current.keyFor({ amount: 10 })).not.toBe(first)
  })
})
