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
      component: () => import('../views/LoginView.vue'),
      meta: {
        public: true,
      },
    },
    {
      path: '/interview',
      name: 'interview',
      component: () => import('../views/InterviewView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/interview/replay/:sessionId',
      name: 'interview-replay',
      component: () => import('../views/ReplayView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/resumes',
      name: 'resumes',
      component: () => import('../views/ResumeManagementView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/settings/llm',
      name: 'llm-settings',
      component: () => import('../views/LlmSettingsView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/settings/profile',
      name: 'profile-settings',
      component: () => import('../views/ProfileSettingsView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: () => import('../views/AnalyticsView.vue'),
      meta: {
        requiresAuth: true,
      },
    },
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
