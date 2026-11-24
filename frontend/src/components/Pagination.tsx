import { buttonSecondary } from '../lib/styles'

interface Props {
  page: number // zero-based
  totalPages: number
  onChange: (page: number) => void
}

export function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null
  return (
    <nav className="mt-8 flex items-center justify-center gap-3" aria-label="分頁">
      <button type="button" className={buttonSecondary} disabled={page === 0} onClick={() => onChange(page - 1)}>
        上一頁
      </button>
      <span className="text-sm text-slate-600">
        第 {page + 1} / {totalPages} 頁
      </span>
      <button
        type="button"
        className={buttonSecondary}
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        下一頁
      </button>
    </nav>
  )
}
