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
      // /api/auth/*, /api/oauth2/*, /api/login/* → 剝掉 /api 前綴
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
      // 其他 /api/**（furnaces / alarms / twin / spc / oee）保留 /api，讓 gateway 的 StripPrefix 處理
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      // WebSocket
      '/ws': {
        target: 'ws://localhost:8085',
        ws: true,
        changeOrigin: true,
        // 頁面重整、HMR、後端重啟時，SockJS/STOMP 連線會被切斷，
        // proxy 對已關閉的 socket 寫入就會噴 EPIPE / ECONNRESET。
        // 這是重連過程的正常現象（client 會自己重連），不需要洗版；
        // 其他錯誤仍然印出來，不要盲目吞掉。
        configure: (proxy) => {
          const benign = new Set(['EPIPE', 'ECONNRESET', 'ECONNABORTED'])
          proxy.on('error', (err) => {
            if (benign.has(err.code)) return
            console.error('[ws proxy]', err)
          })
        }
      }
    }
  }
})
