import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../api/endpoints'
import type { ItemInput } from '../api/types'
import { ItemForm } from '../components/ItemForm'

export function NewItemPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const create = useMutation({
    mutationFn: (input: ItemInput) => api.createItem(input),
    onSuccess: (item) => {
      queryClient.setQueryData(['item', item.id], item)
      queryClient.invalidateQueries({ queryKey: ['items'] })
      queryClient.invalidateQueries({ queryKey: ['my-items'] })
      navigate(`/items/${item.id}`)
    },
  })

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">發布貼文</h1>
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
        <ItemForm
          submitLabel="發布"
          isSubmitting={create.isPending}
          error={create.error?.message}
          onSubmit={(input) => create.mutate(input)}
          onCancel={() => navigate(-1)}
        />
      </div>
    </div>
  )
}
