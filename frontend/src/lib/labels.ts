import type { ClaimStatus, ItemStatus, ItemType, MatchReason, NotificationType } from '../api/types'

// Category names are stored in English in the database; this is how we display them.
const CATEGORY_LABELS: Record<string, string> = {
  Phone: '手機',
  Wallet: '錢包',
  Keys: '鑰匙',
  Bag: '包包',
  'ID Card': '證件',
  Other: '其他',
}

export function categoryLabel(name: string): string {
  return CATEGORY_LABELS[name] ?? name
}

export const TYPE_LABELS: Record<ItemType, string> = {
  LOST: '遺失',
  FOUND: '拾獲',
}

export const STATUS_LABELS: Record<ItemStatus, string> = {
  OPEN: '尋找中',
  CLAIMED: '已被認領',
  RETURNED: '已歸還',
  CLOSED: '已關閉',
}

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  PENDING: '等待回覆',
  APPROVED: '已接受',
  REJECTED: '未被接受',
}

export function notificationText(type: NotificationType, itemTitle: string | null): string {
  const title = itemTitle ? `「${itemTitle}」` : '一篇已刪除的貼文'
  switch (type) {
    case 'CLAIM_RECEIVED':
      return `有人回應了你的貼文${title}`
    case 'CLAIM_APPROVED':
      return `你對${title}的認領已被接受，可以聯絡對方了`
    case 'CLAIM_REJECTED':
      return `你對${title}的認領未被接受`
    case 'ITEM_RETURNED':
      return `${title}已標記為歸還 🎉`
    case 'MATCH_FOUND':
      return `新貼文${title}可能和你的貼文是同一件東西，去看看吧`
  }
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-TW', { dateStyle: 'medium', timeStyle: 'short' })
}

export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${year}/${month}/${day}`
}

/** Today's date as YYYY-MM-DD in the user's own time zone (for <input type="date" max=…>). */
export function todayIso(): string {
  return new Date().toLocaleDateString('sv-SE')
}

export function formatDistance(km: number): string {
  return km < 1 ? `${Math.round(km * 1000)} 公尺` : `${km.toFixed(1)} 公里`
}

export const MATCH_REASON_LABELS: Record<MatchReason, string> = {
  SAME_CATEGORY: '同分類',
  CLOSE_IN_TIME: '時間接近',
  NEARBY: '地點很近',
  SIMILAR_DESCRIPTION: '描述相似',
}
