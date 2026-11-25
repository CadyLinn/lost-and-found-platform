import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, Link } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { Layout } from './components/Layout'
import { RequireAdmin } from './components/RequireAdmin'
import { RequireAuth } from './components/RequireAuth'
import { AdminPage } from './pages/AdminPage'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { EditItemPage } from './pages/EditItemPage'
import { ItemDetailPage } from './pages/ItemDetailPage'
import { ItemsPage } from './pages/ItemsPage'
import { MyClaimsPage } from './pages/MyClaimsPage'
import { MyItemsPage } from './pages/MyItemsPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { NewItemPage } from './pages/NewItemPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Retrying a 4xx (not found, forbidden…) never helps; retry network/server errors once.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1,
      refetchOnWindowFocus: false,
    },
  },
})

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <ItemsPage /> },
      { path: '/items/:id', element: <ItemDetailPage /> },
      { path: '/items/new', element: <RequireAuth><NewItemPage /></RequireAuth> },
      { path: '/items/:id/edit', element: <RequireAuth><EditItemPage /></RequireAuth> },
      { path: '/me/items', element: <RequireAuth><MyItemsPage /></RequireAuth> },
      { path: '/me/claims', element: <RequireAuth><MyClaimsPage /></RequireAuth> },
      { path: '/notifications', element: <RequireAuth><NotificationsPage /></RequireAuth> },
      { path: '/admin', element: <RequireAdmin><AdminPage /></RequireAdmin> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '*', element: <NotFound /> },
    ],
  },
])

function NotFound() {
  return (
    <div className="py-16 text-center">
      <h1 className="text-xl font-semibold text-slate-900">找不到這個頁面</h1>
      <Link to="/" className="mt-4 inline-block text-sm text-teal-700">
        回到首頁
      </Link>
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  )
}
