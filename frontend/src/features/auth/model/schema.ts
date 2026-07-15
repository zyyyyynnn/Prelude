import { z } from 'zod'

export const loginSchema = z.object({
  username: z.string().min(1, '请输入用户名'),
  password: z.string().min(1, '请输入密码'),
})

export const registerSchema = loginSchema.extend({
  email: z.string().email('请输入有效邮箱').or(z.literal('')),
})
