import { useState } from 'react'
import type { Photo } from '../api/types'

/** One large photo with small thumbnails underneath to switch between them. */
export function PhotoGallery({ photos, title }: { photos: Photo[]; title: string }) {
  const [selected, setSelected] = useState(0)
  if (photos.length === 0) return null
  const current = photos[Math.min(selected, photos.length - 1)]

  return (
    <div className="mb-6">
      <a href={current.url} target="_blank" rel="noreferrer" title="開啟原圖">
        <img
          src={current.url}
          alt={`${title}的照片 ${selected + 1}`}
          className="max-h-[28rem] w-full rounded-lg bg-slate-100 object-contain"
        />
      </a>
      {photos.length > 1 && (
        <div className="mt-2 flex gap-2 overflow-x-auto">
          {photos.map((photo, index) => (
            <button
              key={photo.id}
              type="button"
              onClick={() => setSelected(index)}
              aria-label={`第 ${index + 1} 張照片`}
              aria-current={index === selected}
              className={`size-16 shrink-0 overflow-hidden rounded-md border-2 ${
                index === selected ? 'border-teal-600' : 'border-transparent opacity-70 hover:opacity-100'
              }`}
            >
              <img src={photo.thumbnailUrl} alt="" className="size-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
