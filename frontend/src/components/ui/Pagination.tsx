import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'

interface Props {
  /** Zero-based current page. */
  page: number
  totalPages: number
  totalElements: number
  noun: string
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, noun, onPageChange }: Props) {
  if (totalElements === 0) return null
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 border-t border-slate-100 px-5 py-3 text-sm text-slate-600">
      <p>
        Page {page + 1} of {Math.max(totalPages, 1)} · {totalElements} {noun}
        {totalElements === 1 ? '' : 's'}
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" aria-label="Previous page" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button variant="secondary" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
