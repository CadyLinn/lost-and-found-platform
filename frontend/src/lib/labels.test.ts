import { describe, expect, it } from 'vitest'
import { categoryLabel, formatDate, formatDistance, notificationText } from './labels'

describe('labels', () => {
  it('shows distances under 1 km in metres, otherwise in km', () => {
    expect(formatDistance(0.04)).toBe('40 公尺')
    expect(formatDistance(0.999)).toBe('999 公尺')
    expect(formatDistance(1.25)).toBe('1.3 公里')
  })

  it('translates known categories and keeps unknown ones as they are', () => {
    expect(categoryLabel('Wallet')).toBe('錢包')
    expect(categoryLabel('Bicycle')).toBe('Bicycle')
  })

  it('formats ISO dates with slashes', () => {
    expect(formatDate('2026-11-05')).toBe('2026/11/05')
  })

  it('describes notifications, including for a deleted post', () => {
    expect(notificationText('CLAIM_RECEIVED', '黑色錢包')).toBe('有人回應了你的貼文「黑色錢包」')
    expect(notificationText('CLAIM_REJECTED', null)).toContain('一篇已刪除的貼文')
  })
})
