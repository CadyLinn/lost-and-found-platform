import { useQuery } from '@tanstack/react-query'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { api } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { buttonPrimary } from '../lib/styles'

const navLink = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-teal-50 text-teal-800' : 'text-slate-600 hover:text-slate-900'}`

/** Bell with the number of unread notifications; checks for new ones every 30 seconds. */
function NotificationBell() {
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: api.notifications,
    refetchInterval: 30_000,
  })
  const unread = notifications.data?.unreadCount ?? 0

  return (
    <Link
      to="/notifications"
      className="relative rounded-md p-2 text-slate-600 hover:bg-slate-100 hover:text-slate-900"
      aria-label={unread > 0 ? `通知（${unread} 則未讀）` : '通知'}
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-5" aria-hidden>
        <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" strokeLinecap="round" />
      </svg>
      {unread > 0 && (
        <span className="absolute -top-0.5 -right-0.5 flex min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] leading-4 font-semibold text-white">
          {unread > 9 ? '9+' : unread}
        </span>
      )}
    </Link>
  )
}

export function Layout() {
  const { isLoggedIn, isRestoring, user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 font-bold text-slate-900">
            <img src="/favicon.svg" alt="" className="size-7" />
            失物招領
          </Link>

          <nav className="flex items-center gap-1">
            <NavLink to="/" end className={navLink}>
              瀏覽
            </NavLink>
            {isLoggedIn && (
              <>
                <NavLink to="/me/items" className={navLink}>
                  我的貼文
                </NavLink>
                <NavLink to="/me/claims" className={navLink}>
                  我的認領
                </NavLink>
              </>
            )}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            {isRestoring ? null : isLoggedIn ? (
              <>
                <NotificationBell />
                <Link to="/items/new" className={buttonPrimary}>
                  ＋ 發布
                </Link>
                {user && <span className="hidden text-sm text-slate-600 sm:inline">{user.displayName}</span>}
                <button
                  type="button"
                  className="text-sm text-slate-500 hover:text-slate-900"
                  onClick={() => {
                    logout()
                    navigate('/')
                  }}
                >
                  登出
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="text-sm font-medium text-slate-600 hover:text-slate-900">
                  登入
                </Link>
                <Link to="/register" className={buttonPrimary}>
                  註冊
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 py-6 text-center text-xs text-slate-400">
        Lost &amp; Found Platform
      </footer>
    </div>
  )
}
