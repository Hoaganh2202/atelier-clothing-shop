import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { registerSchema, type RegisterFormValues } from '../schemas/auth.schema'
import { registerApi } from '../api/auth.api'

export default function RegisterPage() {
  const navigate = useNavigate()
  const { register, handleSubmit, formState: { errors } } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  })

  const mutation = useMutation({
    mutationFn: registerApi,
    onSuccess: () => navigate('/login'),
  })

  const backendError = (mutation.error as any)?.response?.data
  const errorMessage = backendError?.message || 'Đăng ký thất bại. Email hoặc số điện thoại có thể đã được sử dụng.'
  const fieldErrors = backendError?.errors

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm bg-white p-8 rounded-xl shadow-md">
        <h1 className="text-2xl font-semibold text-center mb-6">Tạo tài khoản</h1>

        <form onSubmit={handleSubmit((data) => mutation.mutate(data))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Họ và tên</label>
            <input {...register('fullName')} className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black" placeholder="Nguyễn Văn A" />
            {errors.fullName && <p className="text-red-500 text-sm mt-1">{errors.fullName.message}</p>}
            {fieldErrors?.name && <p className="text-red-500 text-sm mt-1">{fieldErrors.name}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Email</label>
            <input {...register('email')} type="email" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black" placeholder="ban@example.com" />
            {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email.message}</p>}
            {fieldErrors?.email && <p className="text-red-500 text-sm mt-1">{fieldErrors.email}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Số điện thoại</label>
            <input {...register('phone')} type="tel" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black" placeholder="0912345678" />
            {errors.phone && <p className="text-red-500 text-sm mt-1">{errors.phone.message}</p>}
            {fieldErrors?.phone && <p className="text-red-500 text-sm mt-1">{fieldErrors.phone}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Mật khẩu</label>
            <input {...register('password')} type="password" className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black" placeholder="••••••••" />
            {errors.password && <p className="text-red-500 text-sm mt-1">{errors.password.message}</p>}
            {fieldErrors?.password && <p className="text-red-500 text-sm mt-1">{fieldErrors.password}</p>}
          </div>

          {mutation.isError && <p className="text-red-500 text-sm">{errorMessage}</p>}

          <button type="submit" disabled={mutation.isPending} className="w-full bg-black text-white rounded-lg py-2 font-medium hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Đang xử lý...' : 'Đăng ký'}
          </button>
        </form>

        <p className="text-center text-sm mt-6">
          Bạn đã có tài khoản?{' '}
          <Link to="/login" className="text-black font-medium underline">Đăng nhập</Link>
        </p>
      </div>
    </div>
  )
}