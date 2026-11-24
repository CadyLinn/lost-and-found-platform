import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { api } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ErrorBox, Field } from '../components/ui'
import { buttonPrimary, inputClass } from '../lib/styles'

const loginSchema = z.object({
  email: z.email('請輸入正確的 Email'),
  password: z.string().min(1, '請輸入密碼'),
})

// Same rules as RegisterRequest.java.
const registerSchema = z.object({
  displayName: z.string().trim().min(1, '請輸入暱稱').max(50, '暱稱最多 50 個字'),
  email: z.email('請輸入正確的 Email'),
  password: z.string().min(8, '密碼至少 8 個字元').max(72, '密碼最多 72 個字元'),
})

type LoginValues = z.infer<typeof loginSchema>
type RegisterValues = z.infer<typeof registerSchema>

/** Where to go after logging in: the page that sent us here, or the home page. */
function useRedirectTarget(): string {
  const state = useLocation().state as { from?: string } | null
  return state?.from ?? '/'
}

export function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema) })

  const login = useMutation({
    mutationFn: (values: LoginValues) => api.login(values.email, values.password),
    onSuccess: (token) => {
      auth.login(token)
      navigate(target, { replace: true })
    },
  })

  if (auth.isLoggedIn && !login.isSuccess) return <Navigate to={target} replace />

  return (
    <AuthCard title="登入" footer={<>還沒有帳號？<Link to="/register" className="font-medium text-teal-700">註冊</Link></>}>
      <form onSubmit={form.handleSubmit((values) => login.mutate(values))} className="space-y-4" noValidate>
        {login.isError && <ErrorBox message={login.error.message} />}
        <Field label="Email" error={form.formState.errors.email?.message}>
          <input type="email" autoComplete="email" className={inputClass} {...form.register('email')} />
        </Field>
        <Field label="密碼" error={form.formState.errors.password?.message}>
          <input type="password" autoComplete="current-password" className={inputClass} {...form.register('password')} />
        </Field>
        <button type="submit" className={`${buttonPrimary} w-full`} disabled={login.isPending}>
          {login.isPending ? '登入中…' : '登入'}
        </button>
      </form>
    </AuthCard>
  )
}

export function RegisterPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const form = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) })

  // Register, then log in straight away so the user doesn't have to type their password twice.
  const signUp = useMutation({
    mutationFn: async (values: RegisterValues) => {
      await api.register(values.email, values.password, values.displayName)
      return api.login(values.email, values.password)
    },
    onSuccess: (token) => {
      auth.login(token)
      navigate(target, { replace: true })
    },
  })

  if (auth.isLoggedIn && !signUp.isSuccess) return <Navigate to={target} replace />

  return (
    <AuthCard title="註冊" footer={<>已經有帳號了？<Link to="/login" className="font-medium text-teal-700">登入</Link></>}>
      <form onSubmit={form.handleSubmit((values) => signUp.mutate(values))} className="space-y-4" noValidate>
        {signUp.isError && <ErrorBox message={signUp.error.message} />}
        <Field label="暱稱" error={form.formState.errors.displayName?.message}>
          <input autoComplete="nickname" className={inputClass} {...form.register('displayName')} />
        </Field>
        <Field label="Email" error={form.formState.errors.email?.message}>
          <input type="email" autoComplete="email" className={inputClass} {...form.register('email')} />
        </Field>
        <Field label="密碼" error={form.formState.errors.password?.message} hint="至少 8 個字元">
          <input type="password" autoComplete="new-password" className={inputClass} {...form.register('password')} />
        </Field>
        <button type="submit" className={`${buttonPrimary} w-full`} disabled={signUp.isPending}>
          {signUp.isPending ? '註冊中…' : '建立帳號'}
        </button>
      </form>
    </AuthCard>
  )
}

function AuthCard({ title, footer, children }: { title: string; footer: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-center text-2xl font-bold text-slate-900">{title}</h1>
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-xs">{children}</div>
      <p className="mt-4 text-center text-sm text-slate-600">{footer}</p>
    </div>
  )
}
