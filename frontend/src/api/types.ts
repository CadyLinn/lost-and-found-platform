// Shapes of the JSON the backend sends and receives. Keep in sync with the Java DTOs.

export type ItemType = 'LOST' | 'FOUND'
export type ItemStatus = 'OPEN' | 'CLAIMED' | 'RETURNED' | 'CLOSED'

export interface User {
  id: number
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface Category {
  id: number
  name: string
}

export interface Item {
  id: number
  type: ItemType
  title: string
  description: string | null
  categoryId: number
  categoryName: string
  locationText: string
  lat: number | null
  lng: number | null
  eventDate: string
  status: ItemStatus
  verificationQuestion: string | null
  ownerId: number
  ownerDisplayName: string
  createdAt: string
  updatedAt: string
}

export interface ItemInput {
  type: ItemType
  title: string
  description: string | null
  categoryId: number
  locationText: string
  lat: number | null
  lng: number | null
  eventDate: string
  verificationQuestion: string | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
