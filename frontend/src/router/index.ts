import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

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
      component: () => import('@/features/auth/pages/LoginPage.vue'),
      meta: {
        public: true,
      },
    },
    {
      path: '/interview',
      name: 'interview',
      component: () => import('@/features/interview/pages/InterviewPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/resumes',
      name: 'resumes',
      component: () => import('@/features/resume/pages/ResumeManagementPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },

    {
      path: '/analytics',
      name: 'analytics',
      component: () => import('@/features/insight/pages/AnalyticsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    // Component Lab is a dev-only route used by Phase 3 visual + a11y
    // exploration. It is NOT registered in production builds because
    // `import.meta.env.DEV` is false at build time, so Vite tree-shakes
    // both the route entry and the lazy import. Production deployments
    // therefore do not expose `/components-lab` at all.
    ...(import.meta.env.DEV
      ? [
          {
            path: '/components-lab',
            name: 'components-lab',
            component: () => import('../views/ComponentLabView.vue'),
            meta: {
              // The route is intentionally public so QA / devs can poke
              // at component states without going through the auth flow.
              // In production the route does not exist.
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
