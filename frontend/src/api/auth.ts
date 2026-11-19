import { request } from './client'
import type { TokenResponse, User } from './types'

export const authApi = {
  register: (body: { name: string; email: string; password: string }) =>
    request<User>('/auth/register', { method: 'POST', body }),
  login: (body: { email: string; password: string }) =>
    request<TokenResponse>('/auth/login', { method: 'POST', body }),
  me: () => request<User>('/users/me'),
}
