import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const port = Number(env.VITE_PORT || 5173)
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'
  const host = env.VITE_HOST || '127.0.0.1'

  return {
    plugins: [vue()],
    server: {
      host,
      port,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
