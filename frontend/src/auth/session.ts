// Where the login state lives in the browser. Every read/write is guarded,
// because localStorage can be unavailable (private mode, blocked storage).
//
// - The short-lived access token (15 min) is kept here and sent as "Authorization: Bearer …".
// - The long-lived refresh token is NOT here: the backend keeps it in an HttpOnly cookie that
//   JavaScript can't read. We only remember *that* we have one, so we know when to try refreshing.

const KEY = 'lostfound.session'
const CAN_REFRESH_KEY = 'lostfound.canRefresh'

/** The backend rejected us and refreshing failed: the user is logged out. */
export const UNAUTHORIZED_EVENT = 'lostfound:unauthorized'
/** A new access token was obtained in the background. */
export const SESSION_EVENT = 'lostfound:session'

export interface Session {
  token: string
  expiresAt: number // epoch milliseconds
}

/** A few seconds of margin, so we don't send a token that expires on the way to the server. */
const EXPIRY_MARGIN_MS = 10_000

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (!session.token || Date.now() >= session.expiresAt - EXPIRY_MARGIN_MS) {
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
    localStorage.setItem(CAN_REFRESH_KEY, '1')
  } catch {
    // Storage unavailable: the user stays logged in for this tab only.
  }
  return session
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY)
    localStorage.removeItem(CAN_REFRESH_KEY)
  } catch {
    // ignore
  }
}

export function canRefresh(): boolean {
  try {
    return localStorage.getItem(CAN_REFRESH_KEY) === '1'
  } catch {
    return false
  }
}
