import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { categoriesApi } from '../../api/categories'
import { ApiError, describeError } from '../../api/client'
import { transactionsApi } from '../../api/transactions'
import type { Account, Category, Transaction } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import { useToast } from '../../components/ui/Toast'
import { formatMoney } from '../../lib/format'
import { parseMoney } from '../../lib/money'
import { useRefreshMoneyData } from '../../lib/useRefreshMoneyData'
import { NEW_CATEGORY, latestAllowedDate, todayIso, transactionSchema, type TransactionFormValues } from './schemas'

interface Props {
  accounts: Account[]
  categories: Category[]
  /** When given, the form edits this transaction; its type and account cannot change. */
  transaction?: Transaction
  onDone: () => void
  onCancel: () => void
}

export function TransactionForm({ accounts, categories, transaction, onDone, onCancel }: Props) {
  const toast = useToast()
  const refresh = useRefreshMoneyData()
  const editing = transaction !== undefined
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: {
      type: transaction ? (transaction.type === 'INCOME' ? 'INCOME' : 'EXPENSE') : 'EXPENSE',
      accountId: transaction?.accountId ?? accounts[0]?.id ?? '',
      amount: transaction ? String(transaction.amount) : '',
      transactionDate: transaction?.transactionDate ?? todayIso(),
      categoryId: transaction?.categoryId ?? '',
      newCategory: '',
      description: transaction?.description ?? '',
    },
  })

  const selectedCategory = useWatch({ control, name: 'categoryId' })
  const selectedAccountId = useWatch({ control, name: 'accountId' })
  const selectedAccount = accounts.find((a) => a.id === selectedAccountId)

  const save = useMutation({
    mutationFn: async (values: TransactionFormValues) => {
      // A brand-new category is created first so the transaction can point at its id.
      const categoryId =
        values.categoryId === NEW_CATEGORY
          ? (await categoriesApi.create(values.newCategory)).id
          : values.categoryId || null
      const description = values.description || null
      const amount = parseMoney(values.amount)
      if (transaction) {
        return transactionsApi.update(transaction.id, {
          amount,
          categoryId,
          description,
          transactionDate: values.transactionDate,
        })
      }
      return transactionsApi.create({
        accountId: values.accountId,
        type: values.type,
        amount,
        categoryId,
        description,
        transactionDate: values.transactionDate,
      })
    },
    onSuccess: async () => {
      await refresh()
      toast.success(editing ? 'Transaction updated' : 'Transaction added')
      onDone()
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 422) {
        setError('amount', { message: error.message })
      } else if (error instanceof ApiError && error.status === 409 && error.title.toLowerCase().includes('conflict')) {
        setError('newCategory', { message: error.message })
      } else if (error instanceof ApiError && error.fieldErrors.length > 0) {
        for (const { field, message } of error.fieldErrors) {
          if (field === 'amount' || field === 'description' || field === 'transactionDate') setError(field, { message })
        }
      } else {
        setFormError(describeError(error))
      }
    },
  })

  return (
    <form
      onSubmit={handleSubmit((values) => {
        setFormError(null)
        save.mutate(values)
      })}
      noValidate
      className="space-y-4"
    >
      {formError && <Alert>{formError}</Alert>}

      {editing ? (
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
          {transaction.type === 'INCOME' ? 'Income' : 'Expense'} on{' '}
          <span className="font-medium">{accounts.find((a) => a.id === transaction.accountId)?.name ?? 'this account'}</span>. The type and
          account cannot be changed; reverse the transaction and add a new one instead.
        </p>
      ) : (
        <>
          <fieldset>
            <legend className="mb-1 block text-sm font-medium text-slate-700">Type</legend>
            <div className="grid grid-cols-2 gap-2">
              {(['EXPENSE', 'INCOME'] as const).map((type) => (
                <label key={type} className="cursor-pointer">
                  <input type="radio" value={type} className="peer sr-only" {...register('type')} />
                  <span className="block rounded-lg px-3 py-2 text-center text-sm font-medium text-slate-600 ring-1 ring-inset ring-slate-300 peer-checked:bg-brand-50 peer-checked:text-brand-800 peer-checked:ring-2 peer-checked:ring-brand-600 peer-focus-visible:outline-2 peer-focus-visible:outline-brand-600">
                    {type === 'EXPENSE' ? 'Expense' : 'Income'}
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
          <SelectField
            id="tx-account"
            label="Account"
            error={errors.accountId?.message}
            hint={selectedAccount ? `Balance: ${formatMoney(selectedAccount.currentBalance, selectedAccount.currency)}` : undefined}
            {...register('accountId')}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} ({a.currency})
              </option>
            ))}
          </SelectField>
        </>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="tx-amount" label="Amount" inputMode="decimal" placeholder="0.00" error={errors.amount?.message} {...register('amount')} />
        <TextField id="tx-date" label="Date" type="date" max={latestAllowedDate()} error={errors.transactionDate?.message} {...register('transactionDate')} />
      </div>

      <SelectField id="tx-category" label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
        <option value="">No category</option>
        {categories.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
        <option value={NEW_CATEGORY}>+ New category…</option>
      </SelectField>
      {selectedCategory === NEW_CATEGORY && (
        <TextField id="tx-new-category" label="New category name" placeholder="e.g. Groceries" error={errors.newCategory?.message} {...register('newCategory')} />
      )}

      <TextField id="tx-description" label="Description" placeholder="Optional" error={errors.description?.message} {...register('description')} />

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={save.isPending}>
          {editing ? 'Save changes' : 'Add transaction'}
        </Button>
      </div>
    </form>
  )
}
