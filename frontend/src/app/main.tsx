import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router/dom'
import { Tooltip } from '@base-ui/react'
import { AuthProvider } from '@/features/auth'
import { initializeTheme } from '@/features/settings'
import { FeedbackProvider } from '@/shared/ui/feedback'
import { queryClient } from './query-client'
import { router } from './router'
import '@/shared/styles/index.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Prelude root element is missing')
}

initializeTheme()

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <FeedbackProvider>
          <Tooltip.Provider delay={350}>
            <RouterProvider router={router} />
          </Tooltip.Provider>
        </FeedbackProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
