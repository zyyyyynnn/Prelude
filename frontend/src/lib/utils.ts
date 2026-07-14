import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * 强制保证 Promise 至少执行 minDelay 毫秒，防止 UI 闪烁 (默认 300ms)
 */
export async function withMinDelay<T>(promise: Promise<T>, minDelay: number = 300): Promise<T> {
  const [result] = await Promise.all([
    promise,
    new Promise((resolve) => setTimeout(resolve, minDelay)),
  ])
  return result
}
