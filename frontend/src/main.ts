import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { bindHttpInterceptors } from './api/http'
import router from './router'
import pinia from './stores'
import './styles/index.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
bindHttpInterceptors(router)
app.mount('#app')
