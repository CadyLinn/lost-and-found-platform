import { Link } from 'react-router'
import type { Item } from '../api/types'
import { categoryLabel, formatDate, formatDistance } from '../lib/labels'
import { StatusBadge, TypeBadge } from './ui'

export function ItemCard({ item }: { item: Item }) {
  return (
    <Link
      to={`/items/${item.id}`}
      className="group flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs transition hover:border-teal-300 hover:shadow-md"
    >
      {item.photos[0] && (
        <img
          src={item.photos[0].thumbnailUrl}
          alt=""
          loading="lazy"
          className="aspect-[4/3] w-full bg-slate-100 object-cover"
        />
      )}
      <div className="flex flex-1 flex-col p-4">
      <div className="flex items-start justify-between gap-2">
        <h3 className="line-clamp-2 font-semibold text-slate-900 group-hover:text-teal-800">{item.title}</h3>
        <TypeBadge type={item.type} />
      </div>
      {item.description && <p className="mt-2 line-clamp-2 text-sm text-slate-600">{item.description}</p>}
      <dl className="mt-auto grid gap-1 pt-4 text-sm text-slate-500">
        <div className="flex gap-2">
          <dt className="sr-only">分類</dt>
          <dd>{categoryLabel(item.categoryName)}</dd>
          <span aria-hidden>·</span>
          <dt className="sr-only">日期</dt>
          <dd>{formatDate(item.eventDate)}</dd>
        </div>
        <div className="flex items-center justify-between gap-2">
          <dt className="sr-only">地點</dt>
          <dd className="truncate">
            📍 {item.locationText}
            {item.distanceKm != null && <span className="ml-1 text-teal-700">· {formatDistance(item.distanceKm)}</span>}
          </dd>
          {item.hidden ? (
            <span className="shrink-0 rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">已隱藏</span>
          ) : (
            <StatusBadge status={item.status} />
          )}
        </div>
      </dl>
      </div>
    </Link>
  )
}
