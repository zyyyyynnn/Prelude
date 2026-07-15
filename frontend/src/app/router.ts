import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/features/auth'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior() {
    return { left: 0, top: 0 }
  },
  routes: [
    {
      path: '/',
      redirect: '/interview',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/page').then(({ LoginPage }) => LoginPage),
      meta: {
        public: true,
      },
    },
    {
      path: '/interview',
      name: 'interview',
      component: () =>
        import('@/features/interview/page').then(({ InterviewPage }) => InterviewPage),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/resumes',
      name: 'resumes',
      component: () =>
        import('@/features/resume/page').then(({ ResumeManagementPage }) => ResumeManagementPage),
      meta: {
        requiresAuth: true,
      },
    },

    {
      path: '/analytics',
      name: 'analytics',
      component: () => import('@/features/insight/page').then(({ AnalyticsPage }) => AnalyticsPage),
      meta: {
        requiresAuth: true,
      },
    },
    // Component Lab is registered only in development; the lazy import is
    // tree-shaken from production builds.
    ...(import.meta.env.DEV
      ? [
          {
            path: '/components-lab',
            name: 'components-lab',
            component: () =>
              import('@/devtools/component-lab/ComponentLabView.vue').then(
                ({ default: ComponentLabView }) => ComponentLabView,
              ),
            meta: {
              // Development-only public route for isolated component checks.
              public: true,
              devOnly: true,
            },
          },
        ]
      : []),
    {
      path: '/:pathMatch(.*)*',
      redirect: '/interview',
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.path === '/login' && authStore.isLoggedIn) {
    return {
      path: '/interview',
    }
  }

  return true
})

export default router
