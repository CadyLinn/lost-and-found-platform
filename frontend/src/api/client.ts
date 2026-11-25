import {
  canRefresh,
  clearSession,
  loadSession,
  saveSession,
  SESSION_EVENT,
  UNAUTHORIZED_EVENT,
  type Session,
} from '../auth/session'
import type { TokenResponse } from './types'

/** An HTTP error from the backend, with a message that is safe to show to the user. */
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

// Backend messages (English) → what we show in the UI.
const MESSAGES: Record<string, string> = {
  'Email is already registered': '這個 Email 已經註冊過了',
  'Invalid email or password': 'Email 或密碼錯誤',
  'Session expired, please log in again': '登入已過期，請重新登入',
  'Item not found': '找不到這篇貼文',
  'You can only change your own items': '你只能修改自己的貼文',
  'Unknown category': '分類不存在',
  'You cannot claim your own item': '不能認領自己的貼文',
  'This item is no longer open': '這篇貼文已經結束，無法再認領',
  'You have already responded to this item': '你已經回應過這篇貼文了',
  'Please answer the verification question': '請回答驗證問題',
  'Only the poster can manage claims': '只有發文者可以處理認領',
  'This claim has already been decided': '這個認領已經處理過了',
  'Only a claimed item can be marked as returned': '要先接受一個認領，才能標記為已歸還',
  'Claim not found': '找不到這個認領',
  'Notification not found': '找不到這則通知',
  'Search text is too long': '搜尋文字太長了（最多 100 個字）',
  'Location search needs lat, lng and radiusKm': '附近搜尋需要位置和範圍',
  'Invalid location: radiusKm must be between 0 and 50': '搜尋範圍要在 50 公里以內',
  'Only JPEG and PNG images are allowed': '只能上傳 JPEG 或 PNG 圖片',
  'This file is not a readable image': '無法讀取這張圖片',
  'Image dimensions are too large': '圖片尺寸太大了',
  'A post can have at most 5 photos': '每篇貼文最多 5 張照片',
  'Photos must be 5 MB or smaller': '照片不能超過 5 MB',
  'Photo not found': '找不到這張照片',
  'You cannot report your own item': '不能檢舉自己的貼文',
  'You have already reported this item': '你已經檢舉過這篇貼文了',
  'This item is already hidden': '這篇貼文已經是隱藏的',
  'This item is not hidden': '這篇貼文沒有被隱藏',
}

function fallbackMessage(status: number): string {
  if (status === 400) return '資料格式不正確，請檢查後再試一次'
  if (status === 401) return '請先登入'
  if (status === 403) return '你沒有權限這樣做'
  if (status === 404) return '找不到資料'
  if (status === 413) return '檔案太大了'
  if (status >= 500) return '伺服器發生錯誤，請稍後再試'
  return '發生錯誤，請稍後再試'
}

function tooManyRequestsMessage(response: Response): string {
  const seconds = Number(response.headers.get('Retry-After'))
  if (!seconds) return '嘗試太多次了，請稍後再試'
  return seconds < 60 ? `嘗試太多次了，請 ${seconds} 秒後再試` : `嘗試太多次了，請 ${Math.ceil(seconds / 60)} 分鐘後再試`
}

// ---------- Refreshing the access token ----------

let refreshInFlight: Promise<Session | null> | null = null

/**
 * Trades the refresh cookie for a new access token. If several requests need this at the same
 * time they share one call, because each refresh token works only once.
 * Returns null when the user is no longer logged in.
 */
export function refreshSession(): Promise<Session | null> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function doRefresh(): Promise<Session | null> {
  let response: Response
  try {
    response = await fetch('/api/auth/refresh', { method: 'POST' })
  } catch {
    return null // network problem: don't log the user out, just fail this request
  }
  if (response.status === 401) {
    // The refresh token is gone or used up: the login is really over.
    clearSession()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    return null
  }
  if (!response.ok) return null // e.g. rate limited or server error: keep the login, just fail this time
  const token = (await response.json()) as TokenResponse
  const session = saveSession(token.accessToken, token.expiresIn)
  window.dispatchEvent(new Event(SESSION_EVENT))
  return session
}

// ---------- Requests ----------

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const isAuthCall = path.startsWith('/auth/')
  let session = loadSession()
  // Access token expired while the page was open (or closed): get a new one first.
  if (!session && !isAuthCall && canRefresh()) session = await refreshSession()

  let response = await send(path, options, session)

  // The server rejected the token anyway (e.g. it expired in flight): refresh once and retry.
  if (response.status === 401 && session && !isAuthCall) {
    const renewed = await refreshSession()
    if (renewed) response = await send(path, options, renewed)
  }

  if (!response.ok) {
    if (response.status === 429) throw new ApiError(429, tooManyRequestsMessage(response))
    let message = fallbackMessage(response.status)
    try {
      const data = (await response.json()) as { detail?: string }
      if (data.detail && MESSAGES[data.detail]) message = MESSAGES[data.detail]
    } catch {
      // No JSON body: keep the fallback message.
    }
    throw new ApiError(response.status, message)
  }

  // Some successful responses have no body (204 No Content, or 201 Created for a report).
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function send(path: string, { method = 'GET', body }: RequestOptions, session: Session | null) {
  const headers: Record<string, string> = {}
  const isForm = body instanceof FormData
  // For FormData the browser sets the multipart Content-Type (with its boundary) itself.
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (session) headers['Authorization'] = `Bearer ${session.token}`
  try {
    return await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, '連不上伺服器，請確認後端有在執行')
  }
}
