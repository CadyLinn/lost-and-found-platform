import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/endpoints'
import type { Item } from '../api/types'
import { categoryLabel, formatDate, formatDistance, MATCH_REASON_LABELS } from '../lib/labels'
import { TypeBadge } from './ui'

/** "These posts might be the same object": shown under an open post. */
export function MatchesSection({ item }: { item: Item }) {
  const matches = useQuery({
    queryKey: ['matches', item.id],
    queryFn: () => api.matches(item.id),
    enabled: item.status === 'OPEN',
  })

  if (item.status !== 'OPEN' || !matches.data || matches.data.length === 0) return null

  return (
    <section className="mt-6">
      <h2 className="font-semibold text-slate-900">
        {item.type === 'LOST' ? '有人撿到的東西，可能就是這個' : '可能是失主發的貼文'}
      </h2>
      <p className="mt-1 text-sm text-slate-500">依分類、時間、地點和描述自動比對</p>

      <ul className="mt-4 space-y-3">
        {matches.data.map((match) => (
          <li key={match.item.id}>
            <Link
              to={`/items/${match.item.id}`}
              className="flex items-start gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-xs transition hover:border-teal-300 hover:shadow-md"
            >
              <ScoreRing score={match.score} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <TypeBadge type={match.item.type} />
                  <span className="truncate font-medium text-slate-900">{match.item.title}</span>
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {categoryLabel(match.item.categoryName)} · {formatDate(match.item.eventDate)} · {match.item.locationText}
                  {match.distanceKm != null && ` · ${formatDistance(match.distanceKm)}`}
                </p>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {match.reasons.map((reason) => (
                    <span key={reason} className="rounded-full bg-teal-50 px-2 py-0.5 text-xs text-teal-800">
                      {MATCH_REASON_LABELS[reason]}
                    </span>
                  ))}
                </div>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

function ScoreRing({ score }: { score: number }) {
  const color = score >= 70 ? 'text-teal-700' : score >= 50 ? 'text-amber-600' : 'text-slate-500'
  return (
    <div className="flex size-14 shrink-0 flex-col items-center justify-center rounded-full border-2 border-current/20 bg-slate-50">
      <span className={`text-lg leading-none font-bold ${color}`}>{score}</span>
      <span className="mt-0.5 text-[10px] text-slate-500">相似度</span>
    </div>
  )
}
