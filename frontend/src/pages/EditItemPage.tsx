import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api } from '../api/endpoints'
import type { ItemInput } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ItemForm } from '../components/ItemForm'
import { PhotoPicker } from '../components/PhotoPicker'
import { usePickedPhotos } from '../lib/pickedPhotos'
import { ErrorBox, Spinner } from '../components/ui'
import { uploadPhotos } from '../lib/uploadPhotos'

export function EditItemPage() {
  const id = Number(useParams().id)
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const photos = usePickedPhotos()
  const [progress, setProgress] = useState<string | null>(null)

  const item = useQuery({ queryKey: ['item', id], queryFn: () => api.item(id) })

  function refresh() {
    for (const key of [['item', id], ['items'], ['my-items'], ['matches']]) {
      queryClient.invalidateQueries({ queryKey: key })
    }
  }

  // Removing an existing photo happens right away (with a confirmation).
  const removePhoto = useMutation({
    mutationFn: (photoId: number) => api.deletePhoto(id, photoId),
    onSuccess: refresh,
  })

  const update = useMutation({
    mutationFn: async (input: ItemInput) => {
      await api.updateItem(id, input)
      return uploadPhotos(id, photos.picked, (done, total) =>
        setProgress(total > 0 && done < total ? `上傳照片 ${done + 1}/${total}…` : null),
      )
    },
    onSuccess: (photoErrors) => {
      refresh()
      photos.clear()
      if (photoErrors.length > 0) window.alert(`已儲存，但有照片沒有上傳成功：\n${photoErrors.join('\n')}`)
      navigate(`/items/${id}`)
    },
    onSettled: () => setProgress(null),
  })

  if (item.isPending || !user) return <Spinner />
  if (item.isError) return <ErrorBox message={item.error.message} />
  if (item.data.ownerId !== user.id) return <ErrorBox message="你只能編輯自己的貼文" />

  const busy = update.isPending || removePhoto.isPending
  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">編輯貼文</h1>
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">
        <ItemForm
          initial={item.data}
          submitLabel={progress ?? '儲存'}
          isSubmitting={update.isPending}
          error={update.error?.message ?? removePhoto.error?.message}
          onSubmit={(input) => update.mutate(input)}
          onCancel={() => navigate(`/items/${id}`)}
          photos={
            <PhotoPicker
              existing={item.data.photos}
              picked={photos.picked}
              onPick={photos.add}
              onRemovePicked={photos.remove}
              onRemoveExisting={(photoId) => {
                if (window.confirm('確定要刪除這張照片嗎？')) removePhoto.mutate(photoId)
              }}
              busy={busy}
            />
          }
        />
      </div>
    </div>
  )
}
