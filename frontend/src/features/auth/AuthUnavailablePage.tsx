import { ErrorState } from '@/shared/ui'
import { AuthPage } from './AuthPage'

export function AuthUnavailablePage({ onRetry }: { onRetry: () => void }) {
  return (
    <AuthPage>
      <ErrorState message="登录状态检查失败，请检查网络后重试。" onRetry={onRetry} />
    </AuthPage>
  )
}
