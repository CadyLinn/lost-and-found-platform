import { createContext, useContext } from 'react'
import type { TokenResponse, User } from '../api/types'

export interface AuthState {
  /** True as soon as a valid token exists; `user` may still be loading. */
  isLoggedIn: boolean
  user: User | null
  login: (token: TokenResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}
