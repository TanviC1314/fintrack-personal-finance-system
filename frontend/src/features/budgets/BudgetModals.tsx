import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { budgetsApi } from '../../api/budgets'
import { ApiError, describeError } from '../../api/client'
import type { Budget, Category } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { CURRENCIES } from '../../lib/currencies'
import { formatMonth, formatMoney } from '../../lib/format'
import { parseMoney } from '../../lib/money'
import { useRefreshMoneyData } from '../../lib/useRefreshMoneyData'
import { createBudgetSchema, updateBudgetSchema, type CreateBudgetForm, type UpdateBudgetForm } from './schemas'

export function CreateBudgetModal({
  month,
  categories,
  defaultCurrency,
  onClose,
}: {
  month: string
  categories: Category[]
  defaultCurrency: string
  onClose: () => void
}) {
  const toast = useToast()
  const refresh = useRefreshMoneyData()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CreateBudgetForm>({
    resolver: zodResolver(createBudgetSchema),
    defaultValues: { categoryId: '', currency: defaultCurrency, amount: '' },
  })

  const create = useMutation({
    mutationFn: (values: CreateBudgetForm) =>
      budgetsApi.create({ categoryId: values.categoryId, month, currency: values.currency, amount: parseMoney(values.amount) }),
    onSuccess: async () => {
      await refresh()
      toast.success('Budget created')
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) setError('categoryId', { message: error.message })
      else if (error instanceof ApiError && error.fieldErrors.some((f) => f.field === 'amount')) {
        setError('amount', { message: error.fieldErrors.find((f) => f.field === 'amount')!.message })
      } else setFormError(describeError(error))
    },
  })

  return (
    <Modal title={`New budget for ${formatMonth(month)}`} onClose={onClose}>
      <form onSubmit={handleSubmit((values) => { setFormError(null); create.mutate(values) })} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <SelectField id="budget-category" label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
          <option value="">Choose a category</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </SelectField>
        <div className="grid grid-cols-2 gap-4">
          <TextField id="budget-amount" label="Monthly limit" inputMode="decimal" placeholder="0.00" error={errors.amount?.message} {...register('amount')} />
          <SelectField id="budget-currency" label="Currency" error={errors.currency?.message} hint="Only spending in this currency counts." {...register('currency')}>
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Create budget
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export function EditBudgetModal({ budget, onClose }: { budget: Budget; onClose: () => void }) {
  const toast = useToast()
  const refresh = useRefreshMoneyData()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<UpdateBudgetForm>({ resolver: zodResolver(updateBudgetSchema), defaultValues: { amount: String(budget.amount) } })

  const update = useMutation({
    mutationFn: (values: UpdateBudgetForm) => budgetsApi.updateAmount(budget.id, parseMoney(values.amount)),
    onSuccess: async () => {
      await refresh()
      toast.success('Budget updated')
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError && error.fieldErrors.some((f) => f.field === 'amount')) {
        setError('amount', { message: error.fieldErrors.find((f) => f.field === 'amount')!.message })
      } else setFormError(describeError(error))
    },
  })

  return (
    <Modal title={`Edit ${budget.categoryName} budget`} onClose={onClose}>
      <p className="mb-4 text-sm text-slate-600">
        {formatMonth(budget.month)} · so far {formatMoney(budget.spent, budget.currency)} spent
      </p>
      <form onSubmit={handleSubmit((values) => { setFormError(null); update.mutate(values) })} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="edit-budget-amount" label={`Monthly limit (${budget.currency})`} inputMode="decimal" error={errors.amount?.message} {...register('amount')} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={update.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Modal>
  )
}
