import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { api } from '../api/endpoints'
import type { Item, ItemInput } from '../api/types'
import { categoryLabel, todayIso } from '../lib/labels'
import { buttonPrimary, buttonSecondary, inputClass } from '../lib/styles'
import { ErrorBox, Field } from './ui'

// Same limits as ItemRequest.java, so most mistakes are caught before reaching the server.
const schema = z.object({
  type: z.enum(['LOST', 'FOUND']),
  title: z.string().trim().min(1, '請輸入標題').max(100, '標題最多 100 個字'),
  categoryId: z.string().min(1, '請選擇分類'),
  description: z.string().max(2000, '描述最多 2000 個字'),
  locationText: z.string().trim().min(1, '請輸入地點').max(200, '地點最多 200 個字'),
  eventDate: z
    .string()
    .min(1, '請選擇日期')
    .refine((date) => date <= todayIso(), '日期不能是未來'),
  verificationQuestion: z.string().max(200, '問題最多 200 個字'),
})

type FormValues = z.infer<typeof schema>

interface Coordinates {
  lat: number
  lng: number
}

interface Props {
  initial?: Item
  submitLabel: string
  isSubmitting: boolean
  error?: string
  onSubmit: (input: ItemInput) => void
  onCancel: () => void
  /** Extra fields shown just above the buttons (the photo picker). */
  photos?: ReactNode
}

export function ItemForm({ initial, submitLabel, isSubmitting, error, onSubmit, onCancel, photos }: Props) {
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories, staleTime: Infinity })

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: initial?.type ?? 'LOST',
      title: initial?.title ?? '',
      categoryId: initial ? String(initial.categoryId) : '',
      description: initial?.description ?? '',
      locationText: initial?.locationText ?? '',
      eventDate: initial?.eventDate ?? todayIso(),
      verificationQuestion: initial?.verificationQuestion ?? '',
    },
  })

  const [coords, setCoords] = useState<Coordinates | null>(
    initial?.lat != null && initial?.lng != null ? { lat: initial.lat, lng: initial.lng } : null,
  )
  const [geoMessage, setGeoMessage] = useState<string | null>(null)

  const type = useWatch({ control, name: 'type' })

  function fillMyLocation() {
    if (!('geolocation' in navigator)) {
      setGeoMessage('這個瀏覽器不支援定位')
      return
    }
    setGeoMessage('定位中…')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setCoords({ lat: position.coords.latitude, lng: position.coords.longitude })
        setGeoMessage(null)
      },
      () => setGeoMessage('無法取得位置，可以只填寫地點文字'),
      { timeout: 10000 },
    )
  }

  function submit(values: FormValues) {
    onSubmit({
      type: values.type,
      title: values.title,
      categoryId: Number(values.categoryId),
      description: values.description.trim() || null,
      locationText: values.locationText,
      lat: coords?.lat ?? null,
      lng: coords?.lng ?? null,
      eventDate: values.eventDate,
      // Only the finder asks a verification question; the owner answers it when claiming.
      verificationQuestion: values.type === 'FOUND' ? values.verificationQuestion.trim() || null : null,
    })
  }

  return (
    <form onSubmit={handleSubmit(submit)} className="space-y-5" noValidate>
      {error && <ErrorBox message={error} />}

      <fieldset>
        <legend className="mb-1 block text-sm font-medium text-slate-700">類型</legend>
        <div className="grid grid-cols-2 gap-2">
          {(['LOST', 'FOUND'] as const).map((value) => (
            <label
              key={value}
              className="flex cursor-pointer items-center justify-center rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm font-medium text-slate-700 has-checked:border-teal-600 has-checked:bg-teal-50 has-checked:text-teal-800"
            >
              <input type="radio" value={value} {...register('type')} className="sr-only" />
              {value === 'LOST' ? '我遺失了東西' : '我撿到了東西'}
            </label>
          ))}
        </div>
      </fieldset>

      <Field label="標題" error={errors.title?.message}>
        <input
          className={inputClass}
          placeholder={type === 'LOST' ? '例如：黑色 iPhone 15' : '例如：在圖書館撿到一個錢包'}
          {...register('title')}
        />
      </Field>

      <div className="grid gap-5 sm:grid-cols-2">
        <Field label="分類" error={errors.categoryId?.message}>
          <select className={inputClass} {...register('categoryId')} disabled={categories.isPending}>
            <option value="">請選擇</option>
            {categories.data?.map((category) => (
              <option key={category.id} value={category.id}>
                {categoryLabel(category.name)}
              </option>
            ))}
          </select>
        </Field>

        <Field label={type === 'LOST' ? '遺失日期' : '拾獲日期'} error={errors.eventDate?.message}>
          <input type="date" max={todayIso()} className={inputClass} {...register('eventDate')} />
        </Field>
      </div>

      <Field label="地點" error={errors.locationText?.message}>
        <input className={inputClass} placeholder="例如：總圖書館一樓入口" {...register('locationText')} />
      </Field>

      <div className="flex flex-wrap items-center gap-3 text-sm">
        <button type="button" className={buttonSecondary} onClick={fillMyLocation}>
          📍 使用目前位置
        </button>
        {coords && (
          <span className="text-slate-600">
            已記錄座標 {coords.lat.toFixed(4)}, {coords.lng.toFixed(4)}
            <button type="button" className="ml-2 text-slate-500 underline" onClick={() => setCoords(null)}>
              清除
            </button>
          </span>
        )}
        {geoMessage && <span className="text-slate-500">{geoMessage}</span>}
      </div>

      <Field label="描述（選填）" error={errors.description?.message} hint="顏色、品牌、特徵…越詳細越容易找到">
        <textarea rows={4} className={inputClass} {...register('description')} />
      </Field>

      {type === 'FOUND' && (
        <Field
          label="驗證問題（選填）"
          error={errors.verificationQuestion?.message}
          hint="只有真正的失主才答得出來的問題，例如「手機殼是什麼圖案？」"
        >
          <input className={inputClass} {...register('verificationQuestion')} />
        </Field>
      )}

      {photos}

      <div className="flex justify-end gap-3 pt-2">
        <button type="button" className={buttonSecondary} onClick={onCancel}>
          取消
        </button>
        <button type="submit" className={buttonPrimary} disabled={isSubmitting}>
          {isSubmitting ? '儲存中…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
