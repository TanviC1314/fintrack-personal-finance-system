import { LineChart } from 'lucide-react'
import type { ReactNode } from 'react'

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-brand-800 via-brand-700 to-slate-900 px-4 py-10">
      <div className="mb-6 flex items-center gap-2 text-white">
        <LineChart className="h-7 w-7" aria-hidden="true" />
        <span className="text-2xl font-semibold tracking-tight">FinTrack</span>
      </div>
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl sm:p-8">
        <h1 className="text-xl font-semibold text-slate-900">{title}</h1>
        <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
        <div className="mt-6">{children}</div>
      </div>
      <p className="mt-6 max-w-md text-center text-xs text-brand-100">
        FinTrack is a portfolio project that uses simulated data only. It does not connect to real banks.
      </p>
    </main>
  )
}
