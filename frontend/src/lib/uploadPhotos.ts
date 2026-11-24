import { ApiError } from '../api/client'
import { api } from '../api/endpoints'
import type { PickedPhoto } from './pickedPhotos'
import { ImageError, prepareImage } from './image'

/**
 * Uploads photos one by one (keeps memory low and the order stable).
 * Returns a list of error messages; an empty list means everything worked.
 */
export async function uploadPhotos(
  itemId: number,
  photos: PickedPhoto[],
  onProgress?: (done: number, total: number) => void,
): Promise<string[]> {
  const errors: string[] = []
  for (const [index, photo] of photos.entries()) {
    onProgress?.(index, photos.length)
    try {
      await api.uploadPhoto(itemId, await prepareImage(photo.file))
    } catch (error) {
      const reason = error instanceof ImageError || error instanceof ApiError ? error.message : '上傳失敗'
      errors.push(`第 ${index + 1} 張：${reason}`)
    }
  }
  onProgress?.(photos.length, photos.length)
  return errors
}
