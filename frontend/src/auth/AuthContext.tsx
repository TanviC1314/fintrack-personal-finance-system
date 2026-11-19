import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { setUnauthorizedHandler } from '../api/client'
import type { User } from '../api/types'
import { isTokenExpired } from '../lib/jwt'
import { tokenStore } from './tokenStore'

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthState {
  status: Status
  user: User | null
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

/** A stored token that has not yet expired means we should try to restore the session; otherwise forget it. */
function hasUsableStoredToken(): boolean {
  const token = tokenStore.get()
  if (token && !isTokenExpired(token)) return true
  tokenStore.clear()
  return false
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<Status>(() => (hasUsableStoredToken() ? 'loading' : 'anonymous'))
  const [user, setUser] = useState<User | null>(null)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    setStatus('anonymous')
    // Never leave one user's cached data around for the next person who signs in.
    queryClient.clear()
  }, [queryClient])

  // A request that the server rejects as unauthenticated ends the session.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  // Restore the session after a page reload.
  useEffect(() => {
    if (status !== 'loading') return
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        tokenStore.clear()
        setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
    // Runs once on mount: later status changes come from login and logout, not from restoring.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const { accessToken } = await authApi.login({ email, password })
    tokenStore.set(accessToken)
    try {
      setUser(await authApi.me())
      setStatus('authenticated')
    } catch (error) {
      tokenStore.clear()
      throw error
    }
  }, [])

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      await authApi.register({ name, email, password })
      await login(email, password)
    },
    [login],
  )

  const value = useMemo(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
