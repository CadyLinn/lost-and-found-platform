import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Spinner } from './ui'

/** Sends logged-out visitors to the login page, then back here after they log in. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoggedIn, isRestoring } = useAuth()
  const location = useLocation()
  if (isRestoring) return <Spinner label="恢復登入中…" />
  if (!isLoggedIn) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return children
}
