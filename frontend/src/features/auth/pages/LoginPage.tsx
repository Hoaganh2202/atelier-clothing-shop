import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { loginSchema, type LoginFormValues } from '../schemas/auth.schema'
import { loginApi } from '../api/auth.api'
import { useAuthStore } from '@/stores/auth.store'

export default function LoginPage() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)
  const { register, handleSubmit, formState: { errors } } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  })

  const mutation = useMutation({
    mutationFn: loginApi,
    onSuccess: (data) => {
      if (data.accessToken && data.user) {
        setAuth(data.accessToken, data.user)
        const role = data.user.role?.toLowerCase()
        if (role === 'admin' || role === 'staff') {
          navigate('/admin')
        } else {
          navigate('/')
        }
      }
    },
  })

  const errorMessage = (mutation.error as any)?.response?.data?.message || 'Sai email hoặc mật khẩu.'

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm bg-white p-8 rounded-xl shadow-md">
        <h1 className="text-2xl font-semibold text-center mb-6">Đăng nhập</h1>

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Email</label>
            <input {...register('email')} type="text" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black" placeholder="ban@example.com" />
            {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email.message}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Mật khẩu</label>
            <input {...register('password')} type="password" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black" placeholder="••••••••" />
            {errors.password && <p className="text-red-500 text-sm mt-1">{errors.password.message}</p>}
          </div>

          {mutation.isError && <p className="text-red-500 text-sm">{errorMessage}</p>}

          <button type="submit" disabled={mutation.isPending} className="w-full bg-black text-white rounded-lg py-2 font-medium hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Đang xử lý...' : 'Đăng nhập'}
          </button>
        </form>

        <p className="text-center text-sm mt-6">
          Bạn chưa có tài khoản?{' '}
          <Link to="/register" className="text-black font-medium underline">Đăng ký</Link>
        </p>
      </div>
    </div>
  )
}