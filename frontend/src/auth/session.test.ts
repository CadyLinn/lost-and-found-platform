import { afterEach, describe, expect, it, vi } from 'vitest'
import { canRefresh, clearSession, loadSession, saveSession } from './session'

describe('session storage', () => {
  afterEach(() => vi.useRealTimers())

  it('keeps a token until shortly before it expires', () => {
    vi.useFakeTimers()
    saveSession('token-1', 900) // 15 minutes

    vi.advanceTimersByTime(14 * 60 * 1000)
    expect(loadSession()?.token).toBe('token-1')

    // Within the last 10 seconds it's treated as expired, so we refresh instead of sending a dying token.
    vi.advanceTimersByTime(55 * 1000)
    expect(loadSession()).toBeNull()
  })

  it('remembers that a refresh is possible until logout', () => {
    saveSession('token-1', 900)
    expect(canRefresh()).toBe(true)

    clearSession()
    expect(canRefresh()).toBe(false)
    expect(loadSession()).toBeNull()
  })

  it('ignores corrupted storage instead of crashing', () => {
    localStorage.setItem('lostfound.session', '{not json')
    expect(loadSession()).toBeNull()
  })
})
