import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import type { User } from '../api/types'
import { AuthContext, type AuthState } from '../auth/AuthContext'

/** A logged-in or logged-out auth state for tests, without touching the real login code. */
export function fakeAuth(user: User | null, overrides: Partial<AuthState> = {}): AuthState {
  return { isLoggedIn: user !== null, isRestoring: false, user, login: () => {}, logout: () => {}, ...overrides }
}

export const ALICE: User = { id: 1, email: 'alice@test.com', displayName: 'Alice', role: 'USER' }
export const BOB: User = { id: 2, email: 'bob@test.com', displayName: 'Bob', role: 'USER' }

/**
 * Renders a component with everything pages expect around them: React Query, auth and a router.
 * `routes` lets a test add extra pages (e.g. "/login") to check where navigation ends up.
 */
export function renderPage(
  element: ReactNode,
  { auth = fakeAuth(null), path = '/', routes = {} }: { auth?: AuthState; path?: string; routes?: Record<string, ReactNode> } = {},
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(
    [{ path, element }, ...Object.entries(routes).map(([p, el]) => ({ path: p, element: el }))],
    { initialEntries: [path] },
  )
  return {
    queryClient,
    router,
    ...render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={auth}>
          <RouterProvider router={router} />
        </AuthContext.Provider>
      </QueryClientProvider>,
    ),
  }
}
