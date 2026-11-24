import { clearSession, loadSession, UNAUTHORIZED_EVENT } from '../auth/session'

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
}

function fallbackMessage(status: number): string {
  if (status === 400) return '資料格式不正確，請檢查後再試一次'
  if (status === 401) return '請先登入'
  if (status === 403) return '你沒有權限這樣做'
  if (status === 404) return '找不到資料'
  if (status >= 500) return '伺服器發生錯誤，請稍後再試'
  return '發生錯誤，請稍後再試'
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
}

export async function request<T>(path: string, { method = 'GET', body }: RequestOptions = {}): Promise<T> {
  const session = loadSession()
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (session) headers['Authorization'] = `Bearer ${session.token}`

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, '連不上伺服器，請確認後端有在執行')
  }

  if (!response.ok) {
    // A rejected token means the session is over: tell the app to log out.
    if (response.status === 401 && session) {
      clearSession()
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }
    let message = fallbackMessage(response.status)
    try {
      const data = (await response.json()) as { detail?: string }
      if (data.detail && MESSAGES[data.detail]) message = MESSAGES[data.detail]
    } catch {
      // No JSON body: keep the fallback message.
    }
    throw new ApiError(response.status, message)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
