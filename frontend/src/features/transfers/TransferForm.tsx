import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError, describeError } from '../../api/client'
import { transfersApi } from '../../api/transfers'
import type { Account } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import { useToast } from '../../components/ui/Toast'
import { formatMoney } from '../../lib/format'
import { parseMoney } from '../../lib/money'
import { useIdempotencyKey } from '../../lib/useIdempotencyKey'
import { useRefreshMoneyData } from '../../lib/useRefreshMoneyData'
import { latestAllowedDate, todayIso } from '../transactions/schemas'
import { transferSchema, type TransferFormValues } from './schemas'

export function TransferForm({ accounts }: { accounts: Account[] }) {
  const toast = useToast()
  const refresh = useRefreshMoneyData()
  const idempotency = useIdempotencyKey()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors },
  } = useForm<TransferFormValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: {
      sourceAccountId: accounts[0]?.id ?? '',
      destinationAccountId: '',
      amount: '',
      transferDate: todayIso(),
      description: '',
    },
  })

  const sourceId = useWatch({ control, name: 'sourceAccountId' })
  const source = accounts.find((a) => a.id === sourceId)
  // Money cannot be converted between currencies, so only same-currency accounts are offered as destinations.
  const destinations = accounts.filter((a) => a.id !== sourceId && a.currency === source?.currency)

  const send = useMutation({
    mutationFn: (values: TransferFormValues) => {
      const body = {
        sourceAccountId: values.sourceAccountId,
        destinationAccountId: values.destinationAccountId,
        amount: parseMoney(values.amount),
        description: values.description || null,
        transferDate: values.transferDate,
      }
      return transfersApi.create(body, idempotency.keyFor(body))
    },
    onSuccess: async () => {
      idempotency.reset()
      await refresh()
      toast.success('Transfer completed')
      reset({
        sourceAccountId: sourceId,
        destinationAccountId: '',
        amount: '',
        transferDate: todayIso(),
        description: '',
      })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 422) setError('amount', { message: error.message })
      else if (error instanceof ApiError && error.fieldErrors.length > 0) {
        for (const { field, message } of error.fieldErrors) {
          if (field === 'amount' || field === 'description' || field === 'transferDate') setError(field, { message })
        }
      } else setFormError(describeError(error))
    },
  })

  return (
    <form
      onSubmit={handleSubmit((values) => {
        setFormError(null)
        send.mutate(values)
      })}
      noValidate
      className="space-y-4"
    >
      {formError && <Alert>{formError}</Alert>}
      <div className="grid gap-4">
        <SelectField
          id="transfer-source"
          label="From"
          error={errors.sourceAccountId?.message}
          hint={source ? `Balance: ${formatMoney(source.currentBalance, source.currency)}` : undefined}
          {...register('sourceAccountId')}
        >
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name} ({a.currency})
            </option>
          ))}
        </SelectField>
        <SelectField
          id="transfer-destination"
          label="To"
          error={errors.destinationAccountId?.message}
          hint={destinations.length === 0 && source ? `You need another ${source.currency} account to transfer to.` : undefined}
          {...register('destinationAccountId')}
        >
          <option value="">Choose an account</option>
          {destinations.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="transfer-amount" label="Amount" inputMode="decimal" placeholder="0.00" error={errors.amount?.message} {...register('amount')} />
        <TextField id="transfer-date" label="Date" type="date" max={latestAllowedDate()} error={errors.transferDate?.message} {...register('transferDate')} />
      </div>
      <TextField id="transfer-description" label="Description" placeholder="Optional" error={errors.description?.message} {...register('description')} />
      <div className="flex justify-end">
        <Button type="submit" loading={send.isPending}>
          Transfer money
        </Button>
      </div>
    </form>
  )
}
