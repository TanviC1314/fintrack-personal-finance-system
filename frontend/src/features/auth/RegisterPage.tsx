import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, describeError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { AuthShell } from './AuthShell'
import { registerSchema, type RegisterForm } from './schemas'

export default function RegisterPage() {
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await registerUser(values.name.trim(), values.email.trim(), values.password)
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setError('email', { message: 'An account with this email already exists' })
      } else if (error instanceof ApiError && error.fieldErrors.length > 0) {
        for (const { field, message } of error.fieldErrors) {
          if (field === 'name' || field === 'email' || field === 'password') setError(field, { message })
        }
      } else {
        setFormError(describeError(error))
      }
    }
  })

  return (
    <AuthShell title="Create your account" subtitle="Track your money in one place.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField id="name" label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="8 to 72 characters, with at least one letter and one digit."
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" loading={isSubmitting} className="w-full">
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
