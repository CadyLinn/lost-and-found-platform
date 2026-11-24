import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import { api } from '../api/endpoints'
import type { ItemInput } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ItemForm } from '../components/ItemForm'
import { ErrorBox, Spinner } from '../components/ui'

export function EditItemPage() {
  const id = Number(useParams().id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const item = useQuery({ queryKey: ['item', id], queryFn: () => api.item(id) })

  const update = useMutation({
    mutationFn: (input: ItemInput) => api.updateItem(id, input),
    onSuccess: (updated) => {
      queryClient.setQueryData(['item', id], updated)
      queryClient.invalidateQueries({ queryKey: ['items'] })
      queryClient.invalidateQueries({ queryKey: ['my-items'] })
      queryClient.invalidateQueries({ queryKey: ['matches'] })
      navigate(`/items/${id}`)
    },
  })

  if (item.isPending || !user) return <Spinner />
  if (item.isError) return <ErrorBox message={item.error.message} />
  if (item.data.ownerId !== user.id) return <ErrorBox message="你只能編輯自己的貼文" />

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">編輯貼文</h1>
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
        <ItemForm
          initial={item.data}
          submitLabel="儲存"
          isSubmitting={update.isPending}
          error={update.error?.message}
          onSubmit={(input) => update.mutate(input)}
          onCancel={() => navigate(`/items/${id}`)}
        />
      </div>
    </div>
  )
}
