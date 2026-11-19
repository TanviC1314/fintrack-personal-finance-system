import { request } from './client'
import type { Category } from './types'

export const categoriesApi = {
  list: () => request<Category[]>('/categories'),
  create: (name: string) => request<Category>('/categories', { method: 'POST', body: { name } }),
  rename: (id: string, name: string) => request<Category>(`/categories/${id}`, { method: 'PUT', body: { name } }),
  remove: (id: string) => request<void>(`/categories/${id}`, { method: 'DELETE' }),
}
