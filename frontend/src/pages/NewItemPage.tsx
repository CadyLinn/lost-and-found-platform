import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api/endpoints'
import type { ItemInput } from '../api/types'
import { ItemForm } from '../components/ItemForm'
import { PhotoPicker } from '../components/PhotoPicker'
import { usePickedPhotos } from '../lib/pickedPhotos'
import { uploadPhotos } from '../lib/uploadPhotos'

export function NewItemPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const photos = usePickedPhotos()
  const [progress, setProgress] = useState<string | null>(null)

  // First create the post, then upload its photos (they need the post's id).
  const create = useMutation({
    mutationFn: async (input: ItemInput) => {
      const item = await api.createItem(input)
      const photoErrors = await uploadPhotos(item.id, photos.picked, (done, total) =>
        setProgress(total > 0 && done < total ? `上傳照片 ${done + 1}/${total}…` : null),
      )
      return { item, photoErrors }
    },
    onSuccess: ({ item, photoErrors }) => {
      for (const key of ['items', 'my-items', 'matches']) queryClient.invalidateQueries({ queryKey: [key] })
      queryClient.removeQueries({ queryKey: ['item', item.id] }) // reload it with its photos
      photos.clear()
      if (photoErrors.length > 0) {
        window.alert(`貼文已發布，但有照片沒有上傳成功：\n${photoErrors.join('\n')}\n\n可以之後在「編輯」裡再加。`)
      }
      navigate(`/items/${item.id}`)
    },
    onSettled: () => setProgress(null),
  })

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">發布貼文</h1>
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
        <ItemForm
          submitLabel={progress ?? '發布'}
          isSubmitting={create.isPending}
          error={create.error?.message}
          onSubmit={(input) => create.mutate(input)}
          onCancel={() => navigate(-1)}
          photos={
            <PhotoPicker
              picked={photos.picked}
              onPick={photos.add}
              onRemovePicked={photos.remove}
              busy={create.isPending}
            />
          }
        />
      </div>
    </div>
  )
}
