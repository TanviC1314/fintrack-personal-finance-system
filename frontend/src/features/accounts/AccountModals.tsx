import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { accountsApi } from '../../api/accounts'
import { ApiError, describeError } from '../../api/client'
import type { Account } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { useToast } from '../../components/ui/Toast'
import { CURRENCIES } from '../../lib/currencies'
import { parseMoney } from '../../lib/money'
import { ACCOUNT_TYPES, createAccountSchema, renameAccountSchema, type CreateAccountForm, type RenameAccountForm } from './schemas'

/** Accounts feed the dashboard totals and every account picker, so refresh all of them after a change. */
function useRefreshAfterAccountChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['accounts'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
}

export function CreateAccountModal({ onClose }: { onClose: () => void }) {
  const toast = useToast()
  const refresh = useRefreshAfterAccountChange()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CreateAccountForm>({
    resolver: zodResolver(createAccountSchema),
    defaultValues: { name: '', type: 'CURRENT', currency: 'EUR', openingBalance: '' },
  })

  const create = useMutation({
    mutationFn: (values: CreateAccountForm) =>
      accountsApi.create({
        name: values.name.trim(),
        type: values.type,
        currency: values.currency,
        openingBalance: values.openingBalance ? parseMoney(values.openingBalance) : undefined,
      }),
    onSuccess: async (account) => {
      await refresh()
      toast.success(`Account "${account.name}" created`)
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) setError('name', { message: error.message })
      else setFormError(describeError(error))
    },
  })

  return (
    <Modal title="New account" onClose={onClose}>
      <form onSubmit={handleSubmit((values) => { setFormError(null); create.mutate(values) })} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="account-name" label="Name" placeholder="e.g. Everyday current account" error={errors.name?.message} {...register('name')} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField id="account-type" label="Type" error={errors.type?.message} {...register('type')}>
            {ACCOUNT_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </SelectField>
          <SelectField id="account-currency" label="Currency" error={errors.currency?.message} {...register('currency')}>
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </SelectField>
        </div>
        <TextField
          id="account-opening-balance"
          label="Opening balance"
          inputMode="decimal"
          placeholder="0.00"
          hint="Optional. The balance you start with; it cannot be changed later."
          error={errors.openingBalance?.message}
          {...register('openingBalance')}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Create account
          </Button>
        </div>
      </form>
    </Modal>
  )
}

export function RenameAccountModal({ account, onClose }: { account: Account; onClose: () => void }) {
  const toast = useToast()
  const refresh = useRefreshAfterAccountChange()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RenameAccountForm>({ resolver: zodResolver(renameAccountSchema), defaultValues: { name: account.name } })

  const rename = useMutation({
    mutationFn: (values: RenameAccountForm) => accountsApi.rename(account.id, values.name.trim()),
    onSuccess: async () => {
      await refresh()
      toast.success('Account renamed')
      onClose()
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) setError('name', { message: error.message })
      else setFormError(describeError(error))
    },
  })

  return (
    <Modal title="Rename account" onClose={onClose}>
      <form onSubmit={handleSubmit((values) => { setFormError(null); rename.mutate(values) })} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="rename-name" label="Name" error={errors.name?.message} {...register('name')} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={rename.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Modal>
  )
}
