import { useRef, useState, type ChangeEvent } from 'react'
import { MAX_PHOTOS, type PickedPhoto } from '../lib/pickedPhotos'
import { buttonSecondary } from '../lib/styles'

interface Props {
  /** Photos already saved on the server (edit page). */
  existing?: { id: number; thumbnailUrl: string }[]
  /** Photos chosen but not uploaded yet. */
  picked: PickedPhoto[]
  onPick: (photos: PickedPhoto[]) => void
  onRemovePicked: (key: string) => void
  onRemoveExisting?: (photoId: number) => void
  busy?: boolean
}

/** Grid of photo thumbnails with an "add" tile. Up to MAX_PHOTOS in total. */
export function PhotoPicker({ existing = [], picked, onPick, onRemovePicked, onRemoveExisting, busy }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [note, setNote] = useState<string | null>(null)
  const room = MAX_PHOTOS - existing.length - picked.length

  function choose(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = '' // let the same file be chosen again later
    const accepted = files.slice(0, Math.max(0, room))
    setNote(files.length > accepted.length ? `最多 ${MAX_PHOTOS} 張，多的沒有加入` : null)
    onPick(accepted.map((file) => ({ key: crypto.randomUUID(), file, previewUrl: URL.createObjectURL(file) })))
  }

  return (
    <div>
      <span className="mb-1 block text-sm font-medium text-slate-700">照片（選填，最多 {MAX_PHOTOS} 張）</span>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
        {existing.map((photo) => (
          <Tile key={photo.id} src={photo.thumbnailUrl} onRemove={onRemoveExisting && (() => onRemoveExisting(photo.id))} busy={busy} />
        ))}
        {picked.map((photo) => (
          <Tile key={photo.key} src={photo.previewUrl} onRemove={() => onRemovePicked(photo.key)} busy={busy} pending />
        ))}
        {room > 0 && (
          <button
            type="button"
            disabled={busy}
            onClick={() => input.current?.click()}
            className={`${buttonSecondary} aspect-square flex-col gap-1 border-dashed text-slate-500`}
          >
            <span className="text-2xl leading-none">＋</span>
            <span className="text-xs">加入照片</span>
          </button>
        )}
      </div>
      <input ref={input} type="file" accept="image/jpeg,image/png,image/*" multiple hidden onChange={choose} />
      <p className="mt-1 text-xs text-slate-500">
        {note ?? '上傳前會自動縮小，並移除照片裡的拍攝位置等資訊'}
      </p>
    </div>
  )
}

function Tile({ src, onRemove, busy, pending }: { src: string; onRemove?: () => void; busy?: boolean; pending?: boolean }) {
  return (
    <div className="relative aspect-square overflow-hidden rounded-lg border border-slate-200 bg-slate-100">
      <img src={src} alt="" className="size-full object-cover" />
      {pending && <span className="absolute bottom-1 left-1 rounded bg-black/50 px-1.5 text-[10px] text-white">待上傳</span>}
      {onRemove && (
        <button
          type="button"
          disabled={busy}
          onClick={onRemove}
          aria-label="移除照片"
          className="absolute top-1 right-1 flex size-6 items-center justify-center rounded-full bg-black/60 text-xs text-white hover:bg-black/80"
        >
          ✕
        </button>
      )}
    </div>
  )
}
