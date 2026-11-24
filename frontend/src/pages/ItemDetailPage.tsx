import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ClaimSection } from '../components/ClaimSection'
import { ErrorBox, Spinner, StatusBadge, TypeBadge } from '../components/ui'
import { categoryLabel, formatDate, STATUS_LABELS } from '../lib/labels'
import { buttonDanger, buttonSecondary } from '../lib/styles'

export function ItemDetailPage() {
  const id = Number(useParams().id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const item = useQuery({ queryKey: ['item', id], queryFn: () => api.item(id) })

  const remove = useMutation({
    mutationFn: () => api.deleteItem(id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['item', id] })
      queryClient.invalidateQueries({ queryKey: ['items'] })
      queryClient.invalidateQueries({ queryKey: ['my-items'] })
      navigate('/me/items')
    },
  })

  if (item.isPending) return <Spinner />
  if (item.isError) return <ErrorBox message={item.error.message} />

  const data = item.data
  const isOwner = user?.id === data.ownerId

  return (
    <article className="mx-auto max-w-2xl">
      <Link to="/" className="text-sm text-slate-500 hover:text-slate-800">
        ← 回到列表
      </Link>

      <div className="mt-4 rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
        <div className="flex flex-wrap items-center gap-2">
          <TypeBadge type={data.type} />
          <StatusBadge status={data.status} />
        </div>
        <h1 className="mt-3 text-2xl font-bold text-slate-900">{data.title}</h1>

        <dl className="mt-6 grid gap-4 text-sm sm:grid-cols-2">
          <Detail label="分類" value={categoryLabel(data.categoryName)} />
          <Detail label={data.type === 'LOST' ? '遺失日期' : '拾獲日期'} value={formatDate(data.eventDate)} />
          <Detail label="地點" value={data.locationText} />
          <Detail label="狀態" value={STATUS_LABELS[data.status]} />
          <Detail label="發布者" value={data.ownerDisplayName} />
          <Detail label="發布時間" value={new Date(data.createdAt).toLocaleString('zh-TW')} />
        </dl>

        {data.description && (
          <div className="mt-6">
            <h2 className="text-sm font-medium text-slate-500">描述</h2>
            <p className="mt-1 whitespace-pre-line text-slate-800">{data.description}</p>
          </div>
        )}

        {data.verificationQuestion && (
          <div className="mt-6 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <span className="font-medium">驗證問題：</span>
            {data.verificationQuestion}
            <p className="mt-1 text-xs text-amber-800">認領時需要回答這個問題</p>
          </div>
        )}

        {isOwner && (
          <div className="mt-8 flex flex-wrap gap-3 border-t border-slate-100 pt-6">
            <Link to={`/items/${id}/edit`} className={buttonSecondary}>
              編輯
            </Link>
            <button
              type="button"
              className={buttonDanger}
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm('確定要刪除這篇貼文嗎？刪除後無法復原。')) remove.mutate()
              }}
            >
              {remove.isPending ? '刪除中…' : '刪除'}
            </button>
          </div>
        )}
        {remove.isError && (
          <div className="mt-4">
            <ErrorBox message={remove.error.message} />
          </div>
        )}
      </div>

      <ClaimSection item={data} />
    </article>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-800">{value}</dd>
    </div>
  )
}
