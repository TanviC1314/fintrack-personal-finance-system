import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'

// Placeholder until the application layout and dashboard are built.
export default function HomePage() {
  const { user, logout } = useAuth()
  return (
    <main className="mx-auto max-w-2xl p-8">
      <h1 className="text-2xl font-semibold">Signed in as {user?.name}</h1>
      <p className="mt-1 text-slate-600">{user?.email}</p>
      <Button variant="secondary" className="mt-6" onClick={logout}>
        Sign out
      </Button>
    </main>
  )
}
