import { Navigate, createBrowserRouter } from 'react-router'
import { AppShell } from './shell/AppShell'
import { NotFoundPage } from './routes/NotFoundPage'
import { RequireAuth } from './RequireAuth'

export const router = createBrowserRouter([
  ...(import.meta.env.DEV
    ? [
        {
          path: '/components-lab',
          lazy: async () => ({ Component: (await import('@/devtools/ComponentLab')).ComponentLab }),
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
        Component: AppShell,
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
