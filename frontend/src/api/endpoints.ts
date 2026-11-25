import { request } from './client'
import type {
  AdminStats,
  Category,
  Claim,
  Item,
  ItemInput,
  ItemType,
  Match,
  NotificationList,
  Page,
  Photo,
  Report,
  ReportReason,
  ReportStatus,
  TokenResponse,
  User,
} from './types'

export interface ItemFilters {
  q?: string
  type?: ItemType
  categoryId?: number
  /** "Near me": all three must be set together. */
  near?: { lat: number; lng: number; radiusKm: number }
  page?: number
}

function toQuery(filters: ItemFilters): string {
  const params = new URLSearchParams()
  if (filters.q) params.set('q', filters.q)
  if (filters.near) {
    params.set('lat', String(filters.near.lat))
    params.set('lng', String(filters.near.lng))
    params.set('radiusKm', String(filters.near.radiusKm))
  }
  if (filters.type) params.set('type', filters.type)
  if (filters.categoryId) params.set('categoryId', String(filters.categoryId))
  params.set('page', String(filters.page ?? 0))
  params.set('size', '12')
  return params.toString()
}

export const api = {
  register: (email: string, password: string, displayName: string) =>
    request<User>('/auth/register', { method: 'POST', body: { email, password, displayName } }),
  login: (email: string, password: string) =>
    request<TokenResponse>('/auth/login', { method: 'POST', body: { email, password } }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  me: () => request<User>('/me'),

  categories: () => request<Category[]>('/categories'),

  items: (filters: ItemFilters) => request<Page<Item>>(`/items?${toQuery(filters)}`),
  item: (id: number) => request<Item>(`/items/${id}`),
  myItems: (page: number) => request<Page<Item>>(`/me/items?page=${page}&size=12`),
  createItem: (input: ItemInput) => request<Item>('/items', { method: 'POST', body: input }),
  updateItem: (id: number, input: ItemInput) => request<Item>(`/items/${id}`, { method: 'PUT', body: input }),
  deleteItem: (id: number) => request<void>(`/items/${id}`, { method: 'DELETE' }),
  matches: (id: number) => request<Match[]>(`/items/${id}/matches`),
  uploadPhoto: (itemId: number, image: Blob) => {
    const form = new FormData()
    form.append('file', image, 'photo.jpg')
    return request<Photo>(`/items/${itemId}/photos`, { method: 'POST', body: form })
  },
  deletePhoto: (itemId: number, photoId: number) =>
    request<void>(`/items/${itemId}/photos/${photoId}`, { method: 'DELETE' }),

  submitClaim: (itemId: number, answer: string | null, message: string) =>
    request<Claim>(`/items/${itemId}/claims`, { method: 'POST', body: { answer, message } }),
  itemClaims: (itemId: number) => request<Claim[]>(`/items/${itemId}/claims`),
  myClaims: () => request<Claim[]>('/me/claims'),
  approveClaim: (claimId: number) => request<Claim>(`/claims/${claimId}/approve`, { method: 'POST' }),
  rejectClaim: (claimId: number) => request<Claim>(`/claims/${claimId}/reject`, { method: 'POST' }),
  markReturned: (itemId: number) => request<Item>(`/items/${itemId}/returned`, { method: 'POST' }),

  reportItem: (itemId: number, reason: ReportReason, details: string | null) =>
    request<void>(`/items/${itemId}/reports`, { method: 'POST', body: { reason, details } }),

  admin: {
    stats: () => request<AdminStats>('/admin/stats'),
    reports: (status: ReportStatus) => request<Report[]>(`/admin/reports?status=${status}`),
    hiddenItems: () => request<Item[]>('/admin/items/hidden'),
    hide: (itemId: number, reason: string | null) =>
      request<Item>(`/admin/items/${itemId}/hide`, { method: 'POST', body: { reason } }),
    unhide: (itemId: number) => request<Item>(`/admin/items/${itemId}/unhide`, { method: 'POST' }),
    dismissReports: (itemId: number) =>
      request<{ dismissed: number }>(`/admin/items/${itemId}/dismiss-reports`, { method: 'POST' }),
  },

  notifications: () => request<NotificationList>('/notifications'),
  markNotificationRead: (id: number) => request<void>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllNotificationsRead: () => request<void>('/notifications/read-all', { method: 'POST' }),
}
