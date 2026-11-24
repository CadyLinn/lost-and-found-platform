// Prepares a photo in the browser before uploading it:
//  - rotates it the way the phone intended (EXIF orientation),
//  - shrinks it to at most 1600 px, so uploads are small and fast,
//  - re-draws it on a canvas, which drops ALL metadata, including the GPS location phones store in photos.
// The server re-encodes it again anyway, so this is about speed and privacy, not trust.

const MAX_SIZE = 1600
const MAX_ORIGINAL_BYTES = 30 * 1024 * 1024 // refuse absurd files before even decoding them

export class ImageError extends Error {}

export async function prepareImage(file: File): Promise<Blob> {
  if (!file.type.startsWith('image/')) throw new ImageError('請選擇圖片檔')
  if (file.size > MAX_ORIGINAL_BYTES) throw new ImageError('這張圖片太大了')

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    throw new ImageError('無法讀取這張圖片，請換成 JPEG 或 PNG')
  }

  const scale = Math.min(1, MAX_SIZE / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) throw new ImageError('這個瀏覽器無法處理圖片')
  context.fillStyle = '#fff' // transparent PNG areas become white, like on the server
  context.fillRect(0, 0, width, height)
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new ImageError('無法處理這張圖片'))),
      'image/jpeg',
      0.85,
    ),
  )
}
