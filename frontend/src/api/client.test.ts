import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadSession, saveSession, UNAUTHORIZED_EVENT } from '../auth/session'
import { ApiError, request } from './client'

/** A fake backend: each call is answered by the handler, which sees the URL and the token sent. */
function fakeFetch(handler: (url: string, token: string | undefined) => Response) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const headers = (init?.headers ?? {}) as Record<string, string>
    return handler(url, headers['Authorization']?.replace('Bearer ', ''))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })

const newToken = (accessToken: string) => json({ accessToken, tokenType: 'Bearer', expiresIn: 900 })

describe('API client', () => {
  beforeEach(() => vi.unstubAllGlobals())

  it('sends the access token and parses JSON', async () => {
    saveSession('good', 900)
    fakeFetch((_url, token) => (token === 'good' ? json({ ok: true }) : json({}, 401)))

    await expect(request('/me')).resolves.toEqual({ ok: true })
  })

  it('refreshes once on 401, then retries the request with the new token', async () => {
    saveSession('expired-on-server', 900)
    const fetchMock = fakeFetch((url, token) => {
      if (url === '/api/auth/refresh') return newToken('fresh')
      return token === 'fresh' ? json({ name: 'Alice' }) : json({}, 401)
    })

    await expect(request('/me')).resolves.toEqual({ name: 'Alice' })
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/me', '/api/auth/refresh', '/api/me'])
    expect(loadSession()?.token).toBe('fresh')
  })

  it('shares one refresh between requests that fail at the same time', async () => {
    saveSession('stale', 900)
    let refreshes = 0
    fakeFetch((url, token) => {
      if (url === '/api/auth/refresh') {
        refreshes++
        return newToken('fresh')
      }
      return token === 'fresh' ? json([]) : json({}, 401)
    })

    await Promise.all([request('/items'), request('/me/items'), request('/notifications')])
    expect(refreshes).toBe(1) // each refresh token works only once, so a second refresh would log us out
  })

  it('logs out when the refresh token is rejected', async () => {
    saveSession('stale', 900)
    const loggedOut = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, loggedOut)
    fakeFetch(() => json({}, 401))

    await expect(request('/me')).rejects.toMatchObject({ status: 401 })
    expect(loggedOut).toHaveBeenCalledOnce()
    expect(loadSession()).toBeNull()
    window.removeEventListener(UNAUTHORIZED_EVENT, loggedOut)
  })

  it('stays logged in if the refresh endpoint is only rate-limited', async () => {
    saveSession('stale', 900)
    fakeFetch((url) => (url === '/api/auth/refresh' ? json({}, 429) : json({}, 401)))

    await expect(request('/me')).rejects.toBeInstanceOf(ApiError)
    expect(localStorage.getItem('lostfound.canRefresh')).toBe('1')
  })

  it('translates known backend messages and shows how long to wait when rate-limited', async () => {
    fakeFetch((url) =>
      url === '/api/auth/login'
        ? json({ detail: 'Too many failed logins, please try again later' }, 429, { 'Retry-After': '900' })
        : json({ detail: 'Item not found' }, 404),
    )

    await expect(request('/items/1')).rejects.toThrow('找不到這篇貼文')
    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toThrow('請 15 分鐘後再試')
  })

  it('never tries to refresh for the login call itself', async () => {
    saveSession('x', 900)
    const fetchMock = fakeFetch(() => json({ detail: 'Invalid email or password' }, 401))

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toThrow('Email 或密碼錯誤')
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('accepts successful responses that have no body', async () => {
    fakeFetch(() => new Response(null, { status: 201 }))
    await expect(request('/items/1/reports', { method: 'POST', body: {} })).resolves.toBeUndefined()

    fakeFetch(() => new Response(null, { status: 204 }))
    await expect(request('/notifications/read-all', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('reports a friendly message when the server is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(request('/items')).rejects.toThrow('連不上伺服器')
  })
})
