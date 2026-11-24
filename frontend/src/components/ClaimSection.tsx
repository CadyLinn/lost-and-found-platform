import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { api } from '../api/endpoints'
import type { Claim, Item } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../lib/labels'
import { buttonPrimary, buttonSecondary, inputClass } from '../lib/styles'
import { ClaimStatusBadge, ErrorBox, Field } from './ui'

/** After any claim change, refresh everything that shows claim or item status. */
function refreshAfterClaimChange(queryClient: QueryClient, itemId: number) {
  for (const key of [['item', itemId], ['item-claims', itemId], ['my-claims'], ['items'], ['my-items'], ['notifications']]) {
    queryClient.invalidateQueries({ queryKey: key })
  }
}

/** The bottom of an item's page: who can do what depends on whether you posted it. */
export function ClaimSection({ item }: { item: Item }) {
  const { isLoggedIn, user } = useAuth()
  const location = useLocation()

  if (!isLoggedIn) {
    return (
      <Panel>
        <p className="text-sm text-slate-600">
          {item.type === 'FOUND' ? '這是你的東西嗎？' : '你撿到這個東西了嗎？'}
          <Link to="/login" state={{ from: location.pathname }} className="ml-1 font-medium text-teal-700">
            登入
          </Link>
          後就可以回應。
        </p>
      </Panel>
    )
  }
  if (!user) return null // still loading who we are
  return user.id === item.ownerId ? <PosterView item={item} /> : <VisitorView item={item} />
}

function Panel({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
      {title && <h2 className="mb-4 font-semibold text-slate-900">{title}</h2>}
      {children}
    </section>
  )
}

function ContactBox({ email, who }: { email: string; who: string }) {
  return (
    <div className="mt-3 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
      {who}的 Email：
      <a href={`mailto:${email}`} className="font-medium underline">
        {email}
      </a>
    </div>
  )
}

// ---------- Someone else's post: respond to it, or see how your response went ----------

function VisitorView({ item }: { item: Item }) {
  const myClaims = useQuery({ queryKey: ['my-claims'], queryFn: api.myClaims })
  const mine = myClaims.data?.find((claim) => claim.itemId === item.id)

  if (myClaims.isPending) return null
  if (mine) {
    return (
      <Panel title="你的回應">
        <div className="flex items-center gap-2">
          <ClaimStatusBadge status={mine.status} />
          <span className="text-xs text-slate-500">{formatDateTime(mine.createdAt)}</span>
        </div>
        <p className="mt-3 whitespace-pre-line text-sm text-slate-700">{mine.message}</p>
        {mine.status === 'PENDING' && <p className="mt-3 text-sm text-slate-500">發文者回覆後，你會收到通知。</p>}
        {mine.contactEmail && <ContactBox email={mine.contactEmail} who="發文者" />}
      </Panel>
    )
  }
  if (item.status !== 'OPEN') {
    return (
      <Panel>
        <p className="text-sm text-slate-600">這篇貼文已經結束，無法再回應。</p>
      </Panel>
    )
  }
  return <ClaimForm item={item} />
}

function ClaimForm({ item }: { item: Item }) {
  const queryClient = useQueryClient()
  const [answer, setAnswer] = useState('')
  const [message, setMessage] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const submit = useMutation({
    mutationFn: () => api.submitClaim(item.id, answer.trim() || null, message.trim()),
    onSuccess: () => refreshAfterClaimChange(queryClient, item.id),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (item.verificationQuestion && !answer.trim()) return setFormError('請回答驗證問題')
    if (!message.trim()) return setFormError('請留言給發文者')
    setFormError(null)
    submit.mutate()
  }

  const isFound = item.type === 'FOUND'
  return (
    <Panel title={isFound ? '這是我的東西！' : '我撿到了！'}>
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        {(formError || submit.error) && <ErrorBox message={formError ?? submit.error!.message} />}
        {item.verificationQuestion && (
          <Field label={`驗證問題：${item.verificationQuestion}`} hint="只有發文者看得到你的答案">
            <input className={inputClass} value={answer} maxLength={500} onChange={(e) => setAnswer(e.target.value)} />
          </Field>
        )}
        <Field
          label="留言給發文者"
          hint={isFound ? '說明一下為什麼這是你的，例如特徵、遺失的時間' : '說明你在哪裡撿到、現在放在哪裡'}
        >
          <textarea
            rows={3}
            className={inputClass}
            value={message}
            maxLength={1000}
            onChange={(e) => setMessage(e.target.value)}
          />
        </Field>
        <p className="text-xs text-slate-500">發文者接受後，你們才會看到彼此的 Email。</p>
        <div className="flex justify-end">
          <button type="submit" className={buttonPrimary} disabled={submit.isPending}>
            {submit.isPending ? '送出中…' : '送出'}
          </button>
        </div>
      </form>
    </Panel>
  )
}

// ---------- Your own post: review responses ----------

function PosterView({ item }: { item: Item }) {
  const queryClient = useQueryClient()
  const claims = useQuery({ queryKey: ['item-claims', item.id], queryFn: () => api.itemClaims(item.id) })

  const returned = useMutation({
    mutationFn: () => api.markReturned(item.id),
    onSuccess: () => refreshAfterClaimChange(queryClient, item.id),
  })

  return (
    <Panel title={`收到的回應${claims.data ? `（${claims.data.length}）` : ''}`}>
      {item.status === 'CLAIMED' && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg bg-teal-50 px-4 py-3">
          <p className="text-sm text-teal-900">東西還給對方了嗎？確認後這篇貼文就會結案。</p>
          <button type="button" className={buttonPrimary} disabled={returned.isPending} onClick={() => returned.mutate()}>
            標記為已歸還
          </button>
        </div>
      )}
      {item.status === 'RETURNED' && (
        <p className="mb-4 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-900">🎉 這件物品已經歸還，貼文已結案。</p>
      )}
      {returned.isError && <ErrorBox message={returned.error.message} />}

      {claims.isPending ? (
        <p className="text-sm text-slate-500">載入中…</p>
      ) : claims.isError ? (
        <ErrorBox message={claims.error.message} />
      ) : claims.data.length === 0 ? (
        <p className="text-sm text-slate-500">還沒有人回應。有人回應時，你會收到通知。</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {claims.data.map((claim) => (
            <ClaimRow key={claim.id} claim={claim} item={item} />
          ))}
        </ul>
      )}
    </Panel>
  )
}

function ClaimRow({ claim, item }: { claim: Claim; item: Item }) {
  const queryClient = useQueryClient()
  const decide = useMutation({
    mutationFn: (approve: boolean) => (approve ? api.approveClaim(claim.id) : api.rejectClaim(claim.id)),
    onSuccess: () => refreshAfterClaimChange(queryClient, item.id),
  })

  return (
    <li className="py-4 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-slate-900">{claim.claimantDisplayName}</span>
        <ClaimStatusBadge status={claim.status} />
        <span className="text-xs text-slate-500">{formatDateTime(claim.createdAt)}</span>
      </div>
      {claim.answer && (
        <p className="mt-2 text-sm text-slate-700">
          <span className="text-slate-500">驗證答案：</span>
          {claim.answer}
        </p>
      )}
      <p className="mt-1 whitespace-pre-line text-sm text-slate-700">{claim.message}</p>
      {claim.contactEmail && <ContactBox email={claim.contactEmail} who="對方" />}

      {claim.status === 'PENDING' && item.status === 'OPEN' && (
        <div className="mt-3 flex gap-2">
          <button
            type="button"
            className={buttonPrimary}
            disabled={decide.isPending}
            onClick={() => {
              if (window.confirm(`接受 ${claim.claimantDisplayName} 的認領？其他等待中的回應會自動被婉拒。`)) decide.mutate(true)
            }}
          >
            接受
          </button>
          <button type="button" className={buttonSecondary} disabled={decide.isPending} onClick={() => decide.mutate(false)}>
            婉拒
          </button>
        </div>
      )}
      {decide.isError && (
        <div className="mt-3">
          <ErrorBox message={decide.error.message} />
        </div>
      )}
    </li>
  )
}
