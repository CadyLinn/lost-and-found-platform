import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { buttonPrimary } from '../lib/styles'

const navLink = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-teal-50 text-teal-800' : 'text-slate-600 hover:text-slate-900'}`

export function Layout() {
  const { isLoggedIn, user, logout } = useAuth()
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
              <NavLink to="/me/items" className={navLink}>
                我的貼文
              </NavLink>
            )}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            {isLoggedIn ? (
              <>
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
