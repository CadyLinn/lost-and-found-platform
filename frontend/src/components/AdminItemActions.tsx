import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/endpoints'
import type { Item } from '../api/types'
import { refreshAfterModeration } from '../lib/moderation'
import { buttonDanger, buttonSecondary } from '../lib/styles'
import { ErrorBox } from './ui'

/** Hide / unhide buttons, shown to admins on a post's page and in the admin list. */
export function AdminItemActions({ item }: { item: Pick<Item, 'id' | 'hidden'> }) {
  const queryClient = useQueryClient()

  const change = useMutation({
    mutationFn: async (action: 'hide' | 'unhide') => {
      if (action === 'unhide') return api.admin.unhide(item.id)
      const reason = window.prompt('隱藏的原因（作者會看到）：', '違反使用規範')
      if (reason === null) return null // cancelled
      return api.admin.hide(item.id, reason)
    },
    onSuccess: () => refreshAfterModeration(queryClient, item.id),
  })

  return (
    <div className="space-y-2">
      {item.hidden ? (
        <button type="button" className={buttonSecondary} disabled={change.isPending} onClick={() => change.mutate('unhide')}>
          取消隱藏
        </button>
      ) : (
        <button type="button" className={buttonDanger} disabled={change.isPending} onClick={() => change.mutate('hide')}>
          隱藏貼文
        </button>
      )}
      {change.isError && <ErrorBox message={change.error.message} />}
    </div>
  )
}
