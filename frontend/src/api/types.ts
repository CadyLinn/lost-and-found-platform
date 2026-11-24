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

export interface Photo {
  id: number
  /** Up to 1600 px on the longer side. */
  url: string
  /** Up to 400 px on the longer side. */
  thumbnailUrl: string
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
  /** Only present when searching near a point. */
  distanceKm: number | null
  photos: Photo[]
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

export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Claim {
  id: number
  itemId: number
  itemTitle: string
  itemType: ItemType
  itemStatus: ItemStatus
  status: ClaimStatus
  answer: string | null
  message: string
  claimantId: number
  claimantDisplayName: string
  /** The other person's email; only present once the claim is approved. */
  contactEmail: string | null
  createdAt: string
  decidedAt: string | null
}

export type NotificationType = 'CLAIM_RECEIVED' | 'CLAIM_APPROVED' | 'CLAIM_REJECTED' | 'ITEM_RETURNED' | 'MATCH_FOUND'

export interface Notification {
  id: number
  type: NotificationType
  itemId: number | null
  itemTitle: string | null
  read: boolean
  createdAt: string
}

export interface NotificationList {
  unreadCount: number
  notifications: Notification[]
}

export type MatchReason = 'SAME_CATEGORY' | 'CLOSE_IN_TIME' | 'NEARBY' | 'SIMILAR_DESCRIPTION'

export interface Match {
  item: Item
  /** 0–100 */
  score: number
  reasons: MatchReason[]
  distanceKm: number | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
