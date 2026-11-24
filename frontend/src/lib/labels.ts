import type { ItemStatus, ItemType } from '../api/types'

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

export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${year}/${month}/${day}`
}

/** Today's date as YYYY-MM-DD in the user's own time zone (for <input type="date" max=…>). */
export function todayIso(): string {
  return new Date().toLocaleDateString('sv-SE')
}
