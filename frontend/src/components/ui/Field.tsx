import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'

interface FieldProps {
  label: string
  error?: string
  hint?: string
  children: ReactNode
  htmlFor: string
}

export function Field({ label, error, hint, children, htmlFor }: FieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && (
        <p role="alert" className="mt-1 text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

const controlClasses =
  'block w-full rounded-lg border-0 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm ring-1 ring-inset placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-brand-600 disabled:bg-slate-100'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  id: string
  error?: string
  hint?: string
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, id, error, hint, className = '', ...rest },
  ref,
) {
  return (
    <Field label={label} htmlFor={id} error={error} hint={hint}>
      <input
        ref={ref}
        id={id}
        aria-invalid={error ? true : undefined}
        className={`${controlClasses} ${error ? 'ring-red-400' : 'ring-slate-300'} ${className}`}
        {...rest}
      />
    </Field>
  )
})

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  id: string
  error?: string
  hint?: string
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, id, error, hint, className = '', children, ...rest },
  ref,
) {
  return (
    <Field label={label} htmlFor={id} error={error} hint={hint}>
      <select
        ref={ref}
        id={id}
        aria-invalid={error ? true : undefined}
        className={`${controlClasses} ${error ? 'ring-red-400' : 'ring-slate-300'} ${className}`}
        {...rest}
      >
        {children}
      </select>
    </Field>
  )
})
