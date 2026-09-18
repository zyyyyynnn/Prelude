import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { Navigate, RouterProvider, createBrowserRouter } from 'react-router'
import { Tooltip } from '@base-ui/react'
import '@fontsource-variable/inter'
import '@fontsource-variable/jetbrains-mono'
import '@fontsource-variable/lora'
import '@fontsource-variable/noto-serif-sc'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { initializeTheme } from '@/features/settings/theme'
import { FeedbackProvider } from '@/shared/ui/feedback'
import { queryClient } from './query-client'
import { NotFoundPage } from './routes/NotFoundPage'
import { RequireAuth } from './RequireAuth'
import './styles.css'

const router = createBrowserRouter([
  ...(import.meta.env.DEV
    ? [
        {
          path: '/components-lab',
          lazy: async () => ({
            Component: (await import('@/shared/lab/ComponentLab')).ComponentLab,
          }),
        },
      ]
    : []),
  {
    path: '/login',
    lazy: async () => ({ Component: (await import('@/features/auth/LoginPage')).LoginPage }),
  },
  {
    Component: RequireAuth,
    children: [
      {
        path: '/',
        lazy: async () => {
          const [
            { AppShell },
            { SettingsProvider },
            { ResumeManagementPanel },
            { PositionManagementPanel },
          ] = await Promise.all([
            import('./shell/AppShell'),
            import('@/features/settings/SettingsModal'),
            import('@/features/resume/ResumeManagementPanel'),
            import('@/features/position/PositionManagementPanel'),
          ])
          return {
            Component: () => (
              <SettingsProvider
                renderResourcePanel={(request) =>
                  request.section === 'resumes' ? (
                    <ResumeManagementPanel
                      uploadRequest={
                        request.intent === 'upload-resume' ? request.requestId : undefined
                      }
                    />
                  ) : (
                    <PositionManagementPanel
                      key={
                        request.intent === 'create-position'
                          ? `create-${request.requestId}`
                          : 'manage'
                      }
                    />
                  )
                }
              >
                <AppShell />
              </SettingsProvider>
            ),
          }
        },
        children: [
          { index: true, element: <Navigate to="/interview" replace /> },
          {
            path: 'interview',
            lazy: async () => ({
              Component: (await import('@/features/interview/InterviewPage')).InterviewPage,
            }),
          },
          {
            path: 'analytics',
            lazy: async () => ({
              Component: (await import('@/features/insight/AnalyticsPage')).AnalyticsPage,
            }),
          },
        ],
      },
    ],
  },
  { path: '*', Component: NotFoundPage },
])

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
