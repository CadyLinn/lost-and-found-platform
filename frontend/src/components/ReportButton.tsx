import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api } from '../api/endpoints'
import type { ReportReason } from '../api/types'
import { REPORT_REASON_LABELS } from '../lib/labels'
import { buttonPrimary, buttonSecondary, inputClass } from '../lib/styles'
import { ErrorBox } from './ui'

const REASONS = Object.keys(REPORT_REASON_LABELS) as ReportReason[]

/** A small "report this post" link that opens a short form. Shown to logged-in users on others' posts. */
export function ReportButton({ itemId }: { itemId: number }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<ReportReason>('SPAM')
  const [details, setDetails] = useState('')

  const report = useMutation({
    mutationFn: () => api.reportItem(itemId, reason, details.trim() || null),
  })

  if (report.isSuccess) {
    return <p className="text-sm text-slate-500">已收到你的檢舉，管理員會盡快處理。謝謝！</p>
  }
  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} className="text-sm text-slate-400 underline hover:text-slate-600">
        檢舉這篇貼文
      </button>
    )
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    report.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <fieldset>
        <legend className="mb-2 text-sm font-medium text-slate-700">檢舉原因</legend>
        <div className="flex flex-wrap gap-x-4 gap-y-2">
          {REASONS.map((value) => (
            <label key={value} className="flex items-center gap-1.5 text-sm text-slate-700">
              <input type="radio" name="reason" value={value} checked={reason === value} onChange={() => setReason(value)} />
              {REPORT_REASON_LABELS[value]}
            </label>
          ))}
        </div>
      </fieldset>
      <label className="block">
        <span className="mb-1 block text-sm text-slate-700">補充說明（選填）</span>
        <textarea rows={2} maxLength={500} className={inputClass} value={details} onChange={(e) => setDetails(e.target.value)} />
      </label>
      {report.isError && <ErrorBox message={report.error.message} />}
      <div className="flex justify-end gap-2">
        <button type="button" className={buttonSecondary} onClick={() => setOpen(false)}>
          取消
        </button>
        <button type="submit" className={buttonPrimary} disabled={report.isPending}>
          {report.isPending ? '送出中…' : '送出檢舉'}
        </button>
      </div>
    </form>
  )
}
