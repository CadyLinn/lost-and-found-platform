import { cloneElement, useId, type ReactElement } from 'react'
import type { ClaimStatus, ItemStatus, ItemType } from '../api/types'
import { CLAIM_STATUS_LABELS, STATUS_LABELS, TYPE_LABELS } from '../lib/labels'

export function ClaimStatusBadge({ status }: { status: ClaimStatus }) {
  const color = {
    PENDING: 'bg-amber-50 text-amber-800 ring-amber-200',
    APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    REJECTED: 'bg-slate-100 text-slate-600 ring-slate-200',
  }[status]
  return (
    <span className={`inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${color}`}>
      {CLAIM_STATUS_LABELS[status]}
    </span>
  )
}

export function TypeBadge({ type }: { type: ItemType }) {
  const color = type === 'LOST' ? 'bg-rose-50 text-rose-700 ring-rose-200' : 'bg-emerald-50 text-emerald-700 ring-emerald-200'
  return (
    <span className={`inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${color}`}>
      {TYPE_LABELS[type]}
    </span>
  )
}

export function StatusBadge({ status }: { status: ItemStatus }) {
  if (status === 'OPEN') return null
  return (
    <span className="inline-flex shrink-0 items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
      {STATUS_LABELS[status]}
    </span>
  )
}

/**
 * A labelled form field. The label holds only the field's name; the hint or error is linked with
 * aria-describedby, so screen readers say "Title, edit text … Please enter a title" instead of
 * reading everything as one long name. The child must be a single input, select or textarea.
 */
export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactElement }) {
  const id = useId()
  const noteId = `${id}-note`
  const note = error ?? hint
  const control = cloneElement(children as ReactElement<Record<string, unknown>>, {
    id,
    'aria-describedby': note ? noteId : undefined,
    'aria-invalid': error ? true : undefined,
  })
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      {control}
      {note && (
        <span id={noteId} className={`mt-1 block ${error ? 'text-sm text-red-600' : 'text-xs text-slate-500'}`}>
          {note}
        </span>
      )}
    </div>
  )
}

export function ErrorBox({ message }: { message: string }) {
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      {message}
    </div>
  )
}

export function Spinner({ label = '載入中…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500" role="status">
      <span className="size-4 animate-spin rounded-full border-2 border-slate-300 border-t-teal-700" />
      {label}
    </div>
  )
}
