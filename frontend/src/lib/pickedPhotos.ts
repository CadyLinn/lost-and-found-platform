import { useEffect, useRef, useState } from 'react'

export const MAX_PHOTOS = 5

/** A photo the user chose but that isn't uploaded yet. */
export interface PickedPhoto {
  key: string
  file: File
  previewUrl: string
}

/** Keeps picked photos in state and frees their preview URLs when they're no longer shown. */
export function usePickedPhotos() {
  const [picked, setPicked] = useState<PickedPhoto[]>([])
  const latest = useRef(picked)
  useEffect(() => {
    latest.current = picked
  }, [picked])
  useEffect(() => () => latest.current.forEach((p) => URL.revokeObjectURL(p.previewUrl)), [])

  return {
    picked,
    add: (photos: PickedPhoto[]) => setPicked((current) => [...current, ...photos]),
    remove: (key: string) =>
      setPicked((current) => {
        current.filter((p) => p.key === key).forEach((p) => URL.revokeObjectURL(p.previewUrl))
        return current.filter((p) => p.key !== key)
      }),
    clear: () =>
      setPicked((current) => {
        current.forEach((p) => URL.revokeObjectURL(p.previewUrl))
        return []
      }),
  }
}
