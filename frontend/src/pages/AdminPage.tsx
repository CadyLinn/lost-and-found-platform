import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/endpoints'
import type { Report } from '../api/types'
import { AdminItemActions } from '../components/AdminItemActions'
import { ErrorBox, Spinner } from '../components/ui'
import { formatDateTime, REPORT_REASON_LABELS } from '../lib/labels'
import { refreshAfterModeration } from '../lib/moderation'
import { buttonSecondary } from '../lib/styles'

type Tab = 'open' | 'hidden' | 'history'

const TABS: { value: Tab; label: string }[] = [
  { value: 'open', label: '待處理檢舉' },
  { value: 'hidden', label: '已隱藏的貼文' },
  { value: 'history', label: '處理紀錄' },
]

export function AdminPage() {
  const [tab, setTab] = useState<Tab>('open')
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: api.admin.stats })

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-900">管理後台</h1>

      <dl className="mb-8 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="使用者" value={stats.data?.users} />
        <Stat label="貼文" value={stats.data?.items} />
        <Stat label="待處理檢舉" value={stats.data?.openReports} highlight={(stats.data?.openReports ?? 0) > 0} />
        <Stat label="已隱藏貼文" value={stats.data?.hiddenItems} />
      </dl>

      <div className="mb-6 inline-flex rounded-lg border border-slate-200 bg-white p-1" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            onClick={() => setTab(t.value)}
            className={`rounded-md px-4 py-1.5 text-sm font-medium ${
              tab === t.value ? 'bg-teal-700 text-white' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'open' && <OpenReports />}
      {tab === 'hidden' && <HiddenItems />}
      {tab === 'history' && <History />}
    </div>
  )
}

function Stat({ label, value, highlight }: { label: string; value?: number; highlight?: boolean }) {
  return (
    <div className={`rounded-xl border p-4 ${highlight ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white'}`}>
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="mt-1 text-2xl font-bold text-slate-900">{value ?? '–'}</dd>
    </div>
  )
}

function Empty({ text }: { text: string }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center text-sm text-slate-500">{text}</div>
  )
}

/** Open reports, grouped by post: one card per reported post with every report on it. */
function OpenReports() {
  const queryClient = useQueryClient()
  const reports = useQuery({ queryKey: ['admin', 'reports', 'OPEN'], queryFn: () => api.admin.reports('OPEN') })
  const dismiss = useMutation({
    mutationFn: (itemId: number) => api.admin.dismissReports(itemId),
    onSuccess: () => refreshAfterModeration(queryClient),
  })

  if (reports.isPending) return <Spinner />
  if (reports.isError) return <ErrorBox message={reports.error.message} />
  if (reports.data.length === 0) return <Empty text="沒有待處理的檢舉 🎉" />

  const byItem = new Map<number, Report[]>()
  for (const report of reports.data) byItem.set(report.itemId, [...(byItem.get(report.itemId) ?? []), report])

  return (
    <ul className="space-y-4">
      {[...byItem.entries()].map(([itemId, itemReports]) => {
        const first = itemReports[0]
        return (
          <li key={itemId} className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <Link to={`/items/${itemId}`} className="font-semibold text-slate-900 hover:text-teal-800">
                  {first.itemTitle}
                </Link>
                <p className="mt-0.5 text-sm text-slate-500">
                  作者：{first.itemOwnerDisplayName} · <span className="font-medium text-amber-700">{itemReports.length} 則檢舉</span>
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <AdminItemActions item={{ id: itemId, hidden: first.itemHidden }} />
                <button type="button" className={buttonSecondary} disabled={dismiss.isPending} onClick={() => dismiss.mutate(itemId)}>
                  沒問題，忽略檢舉
                </button>
              </div>
            </div>
            <ul className="mt-4 space-y-2 border-t border-slate-100 pt-4">
              {itemReports.map((report) => (
                <li key={report.id} className="text-sm">
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-700">
                    {REPORT_REASON_LABELS[report.reason]}
                  </span>
                  <span className="ml-2 text-slate-700">{report.details ?? '（沒有說明）'}</span>
                  <span className="ml-2 text-xs text-slate-400">
                    {report.reporterDisplayName} · {formatDateTime(report.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          </li>
        )
      })}
      {dismiss.isError && <ErrorBox message={dismiss.error.message} />}
    </ul>
  )
}

function HiddenItems() {
  const items = useQuery({ queryKey: ['admin', 'hidden'], queryFn: api.admin.hiddenItems })

  if (items.isPending) return <Spinner />
  if (items.isError) return <ErrorBox message={items.error.message} />
  if (items.data.length === 0) return <Empty text="沒有被隱藏的貼文" />

  return (
    <ul className="space-y-3">
      {items.data.map((item) => (
        <li key={item.id} className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
          <div>
            <Link to={`/items/${item.id}`} className="font-medium text-slate-900 hover:text-teal-800">
              {item.title}
            </Link>
            <p className="mt-0.5 text-sm text-slate-500">
              作者：{item.ownerDisplayName}
              {item.hiddenReason && ` · 原因：${item.hiddenReason}`}
            </p>
          </div>
          <AdminItemActions item={item} />
        </li>
      ))}
    </ul>
  )
}

/** Reports that were already handled, newest first. */
function History() {
  const resolved = useQuery({ queryKey: ['admin', 'reports', 'RESOLVED'], queryFn: () => api.admin.reports('RESOLVED') })
  const dismissed = useQuery({ queryKey: ['admin', 'reports', 'DISMISSED'], queryFn: () => api.admin.reports('DISMISSED') })

  if (resolved.isPending || dismissed.isPending) return <Spinner />
  if (resolved.isError) return <ErrorBox message={resolved.error.message} />
  if (dismissed.isError) return <ErrorBox message={dismissed.error.message} />

  const all = [...resolved.data, ...dismissed.data].sort((a, b) => (b.handledAt ?? '').localeCompare(a.handledAt ?? ''))
  if (all.length === 0) return <Empty text="還沒有處理過的檢舉" />

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-slate-500">
          <tr>
            <th className="px-4 py-2 font-medium">貼文</th>
            <th className="px-4 py-2 font-medium">原因</th>
            <th className="px-4 py-2 font-medium">結果</th>
            <th className="px-4 py-2 font-medium">處理者</th>
            <th className="px-4 py-2 font-medium">時間</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {all.map((report) => (
            <tr key={report.id}>
              <td className="px-4 py-2">
                <Link to={`/items/${report.itemId}`} className="text-slate-900 hover:text-teal-800">
                  {report.itemTitle}
                </Link>
              </td>
              <td className="px-4 py-2 text-slate-600">{REPORT_REASON_LABELS[report.reason]}</td>
              <td className="px-4 py-2">
                {report.status === 'RESOLVED' ? (
                  <span className="text-red-700">已隱藏貼文</span>
                ) : (
                  <span className="text-slate-500">已忽略</span>
                )}
              </td>
              <td className="px-4 py-2 text-slate-600">{report.handledByDisplayName}</td>
              <td className="px-4 py-2 whitespace-nowrap text-slate-500">
                {report.handledAt && formatDateTime(report.handledAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
