import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../api/endpoints'
import type { Notification } from '../api/types'
import { ErrorBox, Spinner } from '../components/ui'
import { formatDateTime, notificationText } from '../lib/labels'
import { buttonSecondary } from '../lib/styles'

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const list = useQuery({ queryKey: ['notifications'], queryFn: api.notifications })

  const readAll = useMutation({
    mutationFn: api.markAllNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  async function open(notification: Notification) {
    if (!notification.read) {
      await api.markNotificationRead(notification.id).catch(() => undefined)
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    }
    if (notification.itemId) navigate(`/items/${notification.itemId}`)
  }

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-6 flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-slate-900">通知</h1>
        {list.data && list.data.unreadCount > 0 && (
          <button type="button" className={buttonSecondary} disabled={readAll.isPending} onClick={() => readAll.mutate()}>
            全部標為已讀
          </button>
        )}
      </div>

      {list.isPending ? (
        <Spinner />
      ) : list.isError ? (
        <ErrorBox message={list.error.message} />
      ) : list.data.notifications.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center text-sm text-slate-500">
          目前沒有通知
        </div>
      ) : (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs">
          {list.data.notifications.map((notification) => (
            <li key={notification.id}>
              <button
                type="button"
                onClick={() => open(notification)}
                className={`flex w-full items-start gap-3 px-5 py-4 text-left hover:bg-slate-50 ${notification.read ? '' : 'bg-teal-50/40'}`}
              >
                <span
                  className={`mt-1.5 size-2 shrink-0 rounded-full ${notification.read ? 'bg-transparent' : 'bg-teal-600'}`}
                  aria-label={notification.read ? undefined : '未讀'}
                />
                <span className="flex-1">
                  <span className={`block text-sm ${notification.read ? 'text-slate-600' : 'font-medium text-slate-900'}`}>
                    {notificationText(notification.type, notification.itemTitle)}
                  </span>
                  <span className="mt-0.5 block text-xs text-slate-500">{formatDateTime(notification.createdAt)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
