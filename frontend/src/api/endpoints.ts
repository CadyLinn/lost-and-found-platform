import { request } from './client'
import type {
  Category,
  Claim,
  Item,
  ItemInput,
  ItemType,
  NotificationList,
  Page,
  TokenResponse,
  User,
} from './types'

export interface ItemFilters {
  type?: ItemType
  categoryId?: number
  page?: number
}

function toQuery(filters: ItemFilters): string {
  const params = new URLSearchParams()
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
  me: () => request<User>('/me'),

  categories: () => request<Category[]>('/categories'),

  items: (filters: ItemFilters) => request<Page<Item>>(`/items?${toQuery(filters)}`),
  item: (id: number) => request<Item>(`/items/${id}`),
  myItems: (page: number) => request<Page<Item>>(`/me/items?page=${page}&size=12`),
  createItem: (input: ItemInput) => request<Item>('/items', { method: 'POST', body: input }),
  updateItem: (id: number, input: ItemInput) => request<Item>(`/items/${id}`, { method: 'PUT', body: input }),
  deleteItem: (id: number) => request<void>(`/items/${id}`, { method: 'DELETE' }),

  submitClaim: (itemId: number, answer: string | null, message: string) =>
    request<Claim>(`/items/${itemId}/claims`, { method: 'POST', body: { answer, message } }),
  itemClaims: (itemId: number) => request<Claim[]>(`/items/${itemId}/claims`),
  myClaims: () => request<Claim[]>('/me/claims'),
  approveClaim: (claimId: number) => request<Claim>(`/claims/${claimId}/approve`, { method: 'POST' }),
  rejectClaim: (claimId: number) => request<Claim>(`/claims/${claimId}/reject`, { method: 'POST' }),
  markReturned: (itemId: number) => request<Item>(`/items/${itemId}/returned`, { method: 'POST' }),

  notifications: () => request<NotificationList>('/notifications'),
  markNotificationRead: (id: number) => request<void>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllNotificationsRead: () => request<void>('/notifications/read-all', { method: 'POST' }),
}
