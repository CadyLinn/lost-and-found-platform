import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { refreshSession } from '../api/client'
import { api } from '../api/endpoints'
import type { TokenResponse } from '../api/types'
import { AuthContext, type AuthState } from './AuthContext'
import {
  canRefresh,
  clearSession,
  loadSession,
  saveSession,
  SESSION_EVENT,
  UNAUTHORIZED_EVENT,
  type Session,
} from './session'

const PRIVATE_QUERIES = ['me', 'my-items', 'my-claims', 'item-claims', 'notifications']

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())
  // The access token is gone but a refresh cookie may still be valid: try it before deciding.
  const [isRestoring, setIsRestoring] = useState(() => session === null && canRefresh())

  useEffect(() => {
    if (!isRestoring) return
    refreshSession().finally(() => setIsRestoring(false))
  }, [isRestoring])

  // The API client tells us when it got a new token, or when the login is definitely over.
  useEffect(() => {
    const onSession = () => setSession(loadSession())
    const onUnauthorized = () => setSession(null)
    window.addEventListener(SESSION_EVENT, onSession)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => {
      window.removeEventListener(SESSION_EVENT, onSession)
      window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    }
  }, [])

  const me = useQuery({
    queryKey: ['me'],
    queryFn: api.me,
    enabled: session !== null,
    staleTime: Infinity,
  })

  const login = useCallback(
    (token: TokenResponse) => {
      setSession(saveSession(token.accessToken, token.expiresIn))
      queryClient.invalidateQueries({ queryKey: ['me'] })
    },
    [queryClient],
  )

  const logout = useCallback(() => {
    api.logout().catch(() => undefined) // revoke the refresh token on the server; ignore network errors
    clearSession()
    setSession(null)
    for (const key of PRIVATE_QUERIES) queryClient.removeQueries({ queryKey: [key] })
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({ isLoggedIn: session !== null, isRestoring, user: me.data ?? null, login, logout }),
    [session, isRestoring, me.data, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
