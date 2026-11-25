import { useEffect, useState } from 'react'
import type { Account, Category } from '../../api/types'
import { Button } from '../../components/ui/Button'

export interface FilterValues {
  accountId: string
  type: string
  categoryId: string
  from: string
  to: string
  includeReversed: boolean
  q: string
}

const selectClasses =
  'block w-full rounded-lg border-0 bg-white py-2 pl-3 pr-8 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-inset focus:ring-brand-600'

export function TransactionFilters({
  values,
  accounts,
  categories,
  onChange,
  onClear,
  isFiltered,
}: {
  values: FilterValues
  accounts: Account[]
  categories: Category[]
  onChange: (patch: Partial<FilterValues>) => void
  onClear: () => void
  isFiltered: boolean
}) {
  // The box keeps its own text so typing is instant; the URL (and so the API call) follows after a pause.
  const [text, setText] = useState(values.q)
  const [seenQ, setSeenQ] = useState(values.q)
  if (seenQ !== values.q) {
    // The URL changed from elsewhere (for example Clear filters): show what it now says.
    setSeenQ(values.q)
    setText(values.q)
  }
  useEffect(() => {
    if (text === values.q) return
    const timer = setTimeout(() => onChange({ q: text }), 300)
    return () => clearTimeout(timer)
  }, [text, values.q, onChange])

  return (
    <form
      onSubmit={(e) => e.preventDefault()}
      aria-label="Filter transactions"
      className="grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6"
    >
      <label className="text-xs font-medium text-slate-600 sm:col-span-2 md:col-span-3 xl:col-span-6">
        Search description
        <input
          type="search"
          maxLength={100}
          placeholder="e.g. coffee"
          className={`${selectClasses} mt-1`}
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
      </label>
      <label className="text-xs font-medium text-slate-600">
        Account
        <select className={`${selectClasses} mt-1`} value={values.accountId} onChange={(e) => onChange({ accountId: e.target.value })}>
          <option value="">All accounts</option>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
      </label>
      <label className="text-xs font-medium text-slate-600">
        Type
        <select className={`${selectClasses} mt-1`} value={values.type} onChange={(e) => onChange({ type: e.target.value })}>
          <option value="">All types</option>
          <option value="INCOME">Income</option>
          <option value="EXPENSE">Expense</option>
          <option value="TRANSFER_IN">Transfer in</option>
          <option value="TRANSFER_OUT">Transfer out</option>
        </select>
      </label>
      <label className="text-xs font-medium text-slate-600">
        Category
        <select className={`${selectClasses} mt-1`} value={values.categoryId} onChange={(e) => onChange({ categoryId: e.target.value })}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <label className="text-xs font-medium text-slate-600">
        From
        <input type="date" className={`${selectClasses} mt-1`} value={values.from} max={values.to || undefined} onChange={(e) => onChange({ from: e.target.value })} />
      </label>
      <label className="text-xs font-medium text-slate-600">
        To
        <input type="date" className={`${selectClasses} mt-1`} value={values.to} min={values.from || undefined} onChange={(e) => onChange({ to: e.target.value })} />
      </label>
      <div className="flex items-end justify-between gap-2 xl:flex-col xl:items-start xl:justify-end">
        <label className="flex items-center gap-2 pb-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={values.includeReversed}
            onChange={(e) => onChange({ includeReversed: e.target.checked })}
            className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600"
          />
          Show reversed
        </label>
        {isFiltered && (
          <Button variant="ghost" onClick={onClear}>
            Clear filters
          </Button>
        )}
      </div>
    </form>
  )
}
