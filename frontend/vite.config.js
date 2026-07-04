import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
  },
  define: {
    global: 'globalThis'
  },
  server: {
    port: 5173,
    proxy: {
      // /api/auth/*, /api/oauth2/*, /api/login/* → 剝掉 /api 前綴（因為 gateway 的認證路由沒 /api）
      '/api/auth': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/api/oauth2': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/api/login': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      // 其他 /api/**（furnaces / alarms / twin）保留 /api，讓 gateway 的 StripPrefix 處理
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      // WebSocket
      '/ws': {
        target: 'ws://localhost:8085',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
