import { AlertTriangle, Inbox } from 'lucide-react'
import type { ReactNode } from 'react'
import { describeError } from '../../api/client'
import { Button } from './Button'

export function EmptyState({ title, message, action }: { title: string; message?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-10 text-center">
      <Inbox className="h-9 w-9 text-slate-300" aria-hidden="true" />
      <p className="mt-3 text-sm font-medium text-slate-700">{title}</p>
      {message && <p className="mt-1 max-w-sm text-sm text-slate-500">{message}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center justify-center px-4 py-10 text-center">
      <AlertTriangle className="h-9 w-9 text-red-400" aria-hidden="true" />
      <p className="mt-3 text-sm font-medium text-slate-700">We could not load this</p>
      <p className="mt-1 max-w-sm text-sm text-slate-500">{describeError(error)}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-slate-200 ${className}`} aria-hidden="true" />
}
