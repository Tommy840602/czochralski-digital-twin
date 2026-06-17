import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  define: { global: 'globalThis' },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8091',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'http://localhost:8091',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
