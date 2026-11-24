// Where the login token lives in the browser. Every read/write is guarded,
// because localStorage can be unavailable (private mode, blocked storage).

const KEY = 'lostfound.session'
export const UNAUTHORIZED_EVENT = 'lostfound:unauthorized'

export interface Session {
  token: string
  expiresAt: number // epoch milliseconds
}

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.token || Date.now() >= session.expiresAt) {
      localStorage.removeItem(KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(token: string, expiresInSeconds: number): Session {
  const session = { token, expiresAt: Date.now() + expiresInSeconds * 1000 }
  try {
    localStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    // Storage unavailable: the user stays logged in for this tab only.
  }
  return session
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // ignore
  }
}
