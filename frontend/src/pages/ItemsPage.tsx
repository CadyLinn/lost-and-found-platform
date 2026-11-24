import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { api } from '../api/endpoints'
import type { ItemType } from '../api/types'
import { ItemCard } from '../components/ItemCard'
import { Pagination } from '../components/Pagination'
import { ErrorBox, Spinner } from '../components/ui'
import { categoryLabel } from '../lib/labels'
import { inputClass } from '../lib/styles'

const TABS: { value: ItemType | undefined; label: string }[] = [
  { value: undefined, label: '全部' },
  { value: 'LOST', label: '遺失' },
  { value: 'FOUND', label: '拾獲' },
]

export function ItemsPage() {
  // Filters live in the URL, so a filtered list can be bookmarked or shared.
  const [params, setParams] = useSearchParams()
  const type = (params.get('type') as ItemType | null) ?? undefined
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined
  const page = Number(params.get('page') ?? 0)

  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories, staleTime: Infinity })
  const items = useQuery({
    queryKey: ['items', { type, categoryId, page }],
    queryFn: () => api.items({ type, categoryId, page }),
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

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-900">失物招領</h1>
        <p className="mt-1 text-sm text-slate-600">掉了東西，或撿到別人的東西？在這裡發布、搜尋。</p>
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <div className="inline-flex rounded-lg border border-slate-200 bg-white p-1" role="tablist">
          {TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={type === tab.value}
              onClick={() => update({ type: tab.value })}
              className={`rounded-md px-4 py-1.5 text-sm font-medium ${
                type === tab.value ? 'bg-teal-700 text-white' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <select
          aria-label="分類"
          className={`${inputClass} w-auto`}
          value={categoryId ?? ''}
          onChange={(event) => update({ categoryId: event.target.value })}
        >
          <option value="">所有分類</option>
          {categories.data?.map((category) => (
            <option key={category.id} value={category.id}>
              {categoryLabel(category.name)}
            </option>
          ))}
        </select>

        {items.data && <span className="ml-auto text-sm text-slate-500">共 {items.data.totalElements} 筆</span>}
      </div>

      {items.isPending ? (
        <Spinner />
      ) : items.isError ? (
        <ErrorBox message={items.error.message} />
      ) : items.data.content.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center text-sm text-slate-500">
          目前沒有符合條件的貼文
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
