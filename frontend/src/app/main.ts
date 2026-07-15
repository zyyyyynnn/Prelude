import { createApp } from 'vue'
import App from './App.vue'
import { configureHttp } from '@/shared/api'
import { useAuthStore } from '@/features/auth'
import router from './router'
import pinia from './pinia'
import '@/shared/ui/styles/index.css'
import 'vue-sonner/style.css'

const app = createApp(App)

app.use(pinia)
app.use(router)

const authStore = useAuthStore(pinia)
configureHttp({
  getAccessToken: () => authStore.token,
  async onUnauthorized() {
    authStore.clearSession()
    if (router.currentRoute.value.path !== '/login') {
      await router.replace({ path: '/login', query: { reason: 'expired' } })
    }
  },
})

app.mount('#app')
