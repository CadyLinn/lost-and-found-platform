import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { api, type ItemFilters } from '../api/endpoints'
import type { ItemType } from '../api/types'
import { ItemCard } from '../components/ItemCard'
import { Pagination } from '../components/Pagination'
import { ErrorBox, Spinner } from '../components/ui'
import { categoryLabel } from '../lib/labels'
import { buttonPrimary, buttonSecondary, inputClass } from '../lib/styles'

const TABS: { value: ItemType | undefined; label: string }[] = [
  { value: undefined, label: '全部' },
  { value: 'LOST', label: '遺失' },
  { value: 'FOUND', label: '拾獲' },
]

const RADIUS_OPTIONS = [1, 3, 5, 10]

/** Reads the filters from the URL, so a search can be bookmarked or shared. */
function filtersFromUrl(params: URLSearchParams): ItemFilters {
  const lat = params.get('lat')
  const lng = params.get('lng')
  const radiusKm = params.get('radiusKm')
  return {
    q: params.get('q') ?? undefined,
    type: (params.get('type') as ItemType | null) ?? undefined,
    categoryId: params.get('categoryId') ? Number(params.get('categoryId')) : undefined,
    near: lat && lng && radiusKm ? { lat: Number(lat), lng: Number(lng), radiusKm: Number(radiusKm) } : undefined,
    page: Number(params.get('page') ?? 0),
  }
}

export function ItemsPage() {
  const [params, setParams] = useSearchParams()
  const filters = filtersFromUrl(params)
  const [geoMessage, setGeoMessage] = useState<string | null>(null)

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories, staleTime: Infinity })
  const items = useQuery({
    queryKey: ['items', filters],
    queryFn: () => api.items(filters),
    placeholderData: keepPreviousData,
  })

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!('page' in changes)) next.delete('page') // a new filter starts from the first page
    setParams(next)
  }

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = new FormData(event.currentTarget).get('q')
    update({ q: typeof text === 'string' ? text.trim() : undefined })
  }

  function searchNearMe() {
    if (!('geolocation' in navigator)) return setGeoMessage('這個瀏覽器不支援定位')
    setGeoMessage('定位中…')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setGeoMessage(null)
        update({
          lat: position.coords.latitude.toFixed(5),
          lng: position.coords.longitude.toFixed(5),
          radiusKm: String(filters.near?.radiusKm ?? 3),
        })
      },
      () => setGeoMessage('無法取得你的位置，請確認瀏覽器有允許定位'),
      { timeout: 10000 },
    )
  }

  const isFiltered = Boolean(filters.q || filters.near || filters.type || filters.categoryId)

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">失物招領</h1>
        <p className="mt-1 text-sm text-slate-600">掉了東西，或撿到別人的東西？在這裡發布、搜尋。</p>
      </div>

      {/* key resets the box when the URL changes (e.g. back button or "clear") */}
      <form key={filters.q ?? ''} onSubmit={onSearch} className="mb-4 flex gap-2" role="search">
        <input
          name="q"
          type="search"
          defaultValue={filters.q}
          maxLength={100}
          placeholder="搜尋標題、描述或地點，例如：錢包、圖書館"
          aria-label="搜尋"
          className={inputClass}
        />
        <button type="submit" className={`${buttonPrimary} shrink-0`}>
          搜尋
        </button>
      </form>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <div className="inline-flex rounded-lg border border-slate-200 bg-white p-1" role="tablist">
          {TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={filters.type === tab.value}
              onClick={() => update({ type: tab.value })}
              className={`rounded-md px-4 py-1.5 text-sm font-medium ${
                filters.type === tab.value ? 'bg-teal-700 text-white' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <select
          aria-label="分類"
          className={`${inputClass} w-auto`}
          value={filters.categoryId ?? ''}
          onChange={(event) => update({ categoryId: event.target.value })}
        >
          <option value="">所有分類</option>
          {categories.data?.map((category) => (
            <option key={category.id} value={category.id}>
              {categoryLabel(category.name)}
            </option>
          ))}
        </select>

        {filters.near ? (
          <div className="inline-flex items-center gap-2 rounded-lg border border-teal-200 bg-teal-50 py-1 pr-1 pl-3 text-sm text-teal-900">
            📍 附近
            <select
              aria-label="搜尋範圍"
              className="rounded-md border border-teal-200 bg-white px-2 py-1 text-sm"
              value={filters.near.radiusKm}
              onChange={(event) => update({ radiusKm: event.target.value })}
            >
              {RADIUS_OPTIONS.map((km) => (
                <option key={km} value={km}>
                  {km} 公里內
                </option>
              ))}
            </select>
            <button
              type="button"
              aria-label="取消附近搜尋"
              className="rounded-md px-2 py-1 text-teal-700 hover:bg-teal-100"
              onClick={() => update({ lat: undefined, lng: undefined, radiusKm: undefined })}
            >
              ✕
            </button>
          </div>
        ) : (
          <button type="button" className={buttonSecondary} onClick={searchNearMe}>
            📍 附近
          </button>
        )}

        {items.data && <span className="ml-auto text-sm text-slate-500">共 {items.data.totalElements} 筆</span>}
      </div>

      {geoMessage && <p className="-mt-3 mb-4 text-sm text-slate-500">{geoMessage}</p>}

      {items.isPending ? (
        <Spinner />
      ) : items.isError ? (
        <ErrorBox message={items.error.message} />
      ) : items.data.content.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center text-sm text-slate-500">
          {isFiltered ? '找不到符合條件的貼文，換個關鍵字或放寬範圍試試' : '目前還沒有貼文'}
          {filters.near && <p className="mt-2 text-xs">附近搜尋只會找到有記錄座標的貼文</p>}
          {isFiltered && (
            <button type="button" className="mt-4 block w-full text-teal-700" onClick={() => setParams(new URLSearchParams())}>
              清除所有條件
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.data.content.map((item) => (
              <ItemCard key={item.id} item={item} />
            ))}
          </div>
          <Pagination
            page={items.data.page}
            totalPages={items.data.totalPages}
            onChange={(next) => update({ page: String(next) })}
          />
        </>
      )}
    </div>
  )
}
