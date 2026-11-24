import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/endpoints'
import { ItemCard } from '../components/ItemCard'
import { Pagination } from '../components/Pagination'
import { ErrorBox, Spinner } from '../components/ui'
import { buttonPrimary } from '../lib/styles'

export function MyItemsPage() {
  const [page, setPage] = useState(0)
  const items = useQuery({
    queryKey: ['my-items', page],
    queryFn: () => api.myItems(page),
    placeholderData: keepPreviousData,
  })

  return (
    <div>
      <div className="mb-6 flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">我的貼文</h1>
        <Link to="/items/new" className={buttonPrimary}>
          ＋ 發布
        </Link>
      </div>

      {items.isPending ? (
        <Spinner />
      ) : items.isError ? (
        <ErrorBox message={items.error.message} />
      ) : items.data.content.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center text-sm text-slate-500">
          你還沒有發布任何貼文
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.data.content.map((item) => (
              <ItemCard key={item.id} item={item} />
            ))}
          </div>
          <Pagination page={items.data.page} totalPages={items.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  )
}
