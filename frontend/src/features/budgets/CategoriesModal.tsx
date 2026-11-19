import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Check, Pencil, Trash2, X } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { categoriesApi } from '../../api/categories'
import { ApiError, describeError } from '../../api/client'
import type { Category } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { useRefreshMoneyData } from '../../lib/useRefreshMoneyData'
import { categoryNameSchema, type CategoryNameForm } from './schemas'

function CategoryRow({ category, onChanged }: { category: Category; onChanged: () => void }) {
  const toast = useToast()
  const [editing, setEditing] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CategoryNameForm>({ resolver: zodResolver(categoryNameSchema), defaultValues: { name: category.name } })

  const rename = useMutation({
    mutationFn: (values: CategoryNameForm) => categoriesApi.rename(category.id, values.name.trim()),
    onSuccess: () => {
      setEditing(false)
      onChanged()
      toast.success('Category renamed')
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) setError('name', { message: error.message })
      else toast.error(describeError(error))
    },
  })
  const remove = useMutation({
    mutationFn: () => categoriesApi.remove(category.id),
    onSuccess: () => {
      onChanged()
      toast.success(`Category "${category.name}" deleted`)
    },
    // A category that transactions or budgets use cannot be deleted; the server explains why.
    onError: (error) => toast.error(describeError(error)),
  })

  if (editing) {
    return (
      <li className="py-2">
        <form onSubmit={handleSubmit((values) => rename.mutate(values))} noValidate className="flex items-start gap-2">
          <div className="flex-1">
            <TextField id={`rename-${category.id}`} label="Category name" error={errors.name?.message} {...register('name')} />
          </div>
          <Button type="submit" variant="ghost" aria-label="Save name" loading={rename.isPending} className="mt-6">
            <Check className="h-4 w-4" aria-hidden="true" />
          </Button>
          <Button variant="ghost" aria-label="Cancel rename" onClick={() => setEditing(false)} className="mt-6">
            <X className="h-4 w-4" aria-hidden="true" />
          </Button>
        </form>
      </li>
    )
  }
  return (
    <li className="flex items-center justify-between gap-2 py-2">
      <span className="truncate text-sm text-slate-800">{category.name}</span>
      <span className="flex shrink-0">
        <Button variant="ghost" aria-label={`Rename ${category.name}`} onClick={() => setEditing(true)}>
          <Pencil className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button variant="ghost" aria-label={`Delete ${category.name}`} loading={remove.isPending} onClick={() => remove.mutate()}>
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </Button>
      </span>
    </li>
  )
}

export function CategoriesModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const refresh = useRefreshMoneyData()
  const [formError, setFormError] = useState<string | null>(null)
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list() })
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<CategoryNameForm>({ resolver: zodResolver(categoryNameSchema), defaultValues: { name: '' } })

  const create = useMutation({
    mutationFn: (values: CategoryNameForm) => categoriesApi.create(values.name.trim()),
    onSuccess: async () => {
      reset({ name: '' })
      await refresh()
      toast.success('Category added')
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) setError('name', { message: error.message })
      else setFormError(describeError(error))
    },
  })

  return (
    <Modal title="Categories" onClose={onClose}>
      <form onSubmit={handleSubmit((values) => { setFormError(null); create.mutate(values) })} noValidate className="flex items-start gap-2">
        <div className="flex-1">
          <TextField id="new-category" label="New category" placeholder="e.g. Groceries" error={errors.name?.message} {...register('name')} />
        </div>
        <Button type="submit" loading={create.isPending} className="mt-6">
          Add
        </Button>
      </form>
      {formError && <div className="mt-3"><Alert>{formError}</Alert></div>}

      <div className="mt-5 max-h-72 overflow-y-auto">
        {categories.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : categories.isError ? (
          <ErrorState error={categories.error} onRetry={() => categories.refetch()} />
        ) : categories.data.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">No categories yet. Add one above.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {categories.data.map((c) => (
              <CategoryRow key={c.id} category={c} onChanged={() => void refresh()} />
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
