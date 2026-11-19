import { describe, expect, it } from 'vitest'
import { loginSchema, registerSchema } from './schemas'

const valid = { name: 'Alice', email: 'alice@example.com', password: 's3curePass' }

function firstMessage(result: ReturnType<typeof registerSchema.safeParse>, field: string) {
  if (result.success) return undefined
  return result.error.issues.find((issue) => issue.path[0] === field)?.message
}

describe('registerSchema', () => {
  it('accepts a valid registration', () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })

  it.each([
    ['', 'Name is required'],
    ['   ', 'Name is required'],
    ['n'.repeat(101), 'Name must be at most 100 characters'],
  ])('rejects the name %j', (name, message) => {
    expect(firstMessage(registerSchema.safeParse({ ...valid, name }), 'name')).toBe(message)
  })

  it.each(['not-an-email', 'missing@domain', '@no-local.com', 'has space@example.com', 'user@localhost'])(
    'rejects the email %j',
    (email) => {
      expect(firstMessage(registerSchema.safeParse({ ...valid, email }), 'email')).toBeDefined()
    },
  )

  it.each([
    ['short1', 'Password must be at least 8 characters'],
    ['onlyletterspassword', 'Password must contain at least one digit'],
    ['1234567890', 'Password must contain at least one letter'],
    ['a1'.repeat(37), 'Password must be at most 72 characters'],
  ])('rejects the password %j', (password, message) => {
    expect(firstMessage(registerSchema.safeParse({ ...valid, password }), 'password')).toBe(message)
  })
})

describe('loginSchema', () => {
  it('only requires both fields and the length cap the encoder allows', () => {
    expect(loginSchema.safeParse({ email: 'a@b.c', password: 'x' }).success).toBe(true)
    expect(loginSchema.safeParse({ email: '', password: 'x' }).success).toBe(false)
    expect(loginSchema.safeParse({ email: 'a@b.c', password: '' }).success).toBe(false)
    expect(loginSchema.safeParse({ email: 'a@b.c', password: 'x'.repeat(73) }).success).toBe(false)
  })
})
