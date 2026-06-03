import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const port = Number(env.VITE_PORT || 5173)
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'
  const host = env.VITE_HOST || '127.0.0.1'

  return {
    plugins: [vue(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    build: {
      chunkSizeWarningLimit: 520,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (/[\\/]node_modules[\\/]zrender[\\/]/.test(id)) {
              return 'vendor-zrender'
            }
            if (/[\\/]node_modules[\\/]echarts[\\/]/.test(id)) {
              return 'vendor-echarts'
            }
            if (/[\\/]node_modules[\\/]element-plus[\\/]/.test(id)) {
              return 'vendor-element-plus'
            }
            if (/[\\/]node_modules[\\/](@paper-design|react|react-dom)[\\/]/.test(id)) {
              return 'vendor-brand'
            }
            if (/[\\/]node_modules[\\/]markdown-it[\\/]/.test(id)) {
              return 'vendor-markdown-it'
            }
          },
        },
      },
    },
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
