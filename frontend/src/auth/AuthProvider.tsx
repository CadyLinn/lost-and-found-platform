import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/endpoints'
import type { TokenResponse } from '../api/types'
import { AuthContext, type AuthState } from './AuthContext'
import { clearSession, loadSession, saveSession, UNAUTHORIZED_EVENT, type Session } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())

  // The API client fires this event when the backend rejects our token (expired or invalid).
  useEffect(() => {
    const onUnauthorized = () => setSession(null)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const me = useQuery({
    queryKey: ['me', session?.token],
    queryFn: api.me,
    enabled: session !== null,
    staleTime: Infinity,
  })

  const login = useCallback((token: TokenResponse) => {
    setSession(saveSession(token.accessToken, token.expiresIn))
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
    queryClient.removeQueries({ queryKey: ['me'] })
    queryClient.removeQueries({ queryKey: ['my-items'] })
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({ isLoggedIn: session !== null, user: me.data ?? null, login, logout }),
    [session, me.data, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
