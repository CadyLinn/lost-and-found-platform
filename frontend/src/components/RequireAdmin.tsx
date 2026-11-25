import type { ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { RequireAuth } from './RequireAuth'
import { ErrorBox, Spinner } from './ui'

/**
 * Only admins see the children. This only decides what to *show*: the server checks the role
 * again on every /api/admin request, so hiding this page is not what keeps it safe.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AdminOnly>{children}</AdminOnly>
    </RequireAuth>
  )
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Spinner />
  if (user.role !== 'ADMIN') return <ErrorBox message="這個頁面只有管理員可以使用" />
  return children
}
