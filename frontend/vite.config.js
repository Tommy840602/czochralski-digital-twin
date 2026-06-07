import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    proxy: {
      '/ws': {
        target: 'http://localhost:8091',
        ws: true,
        changeOrigin: true
      },
      '/topic': {
        target: 'http://localhost:8091',
        ws: true,
        changeOrigin: true
      },
      '/api/simulator': {
        target: 'http://localhost:8099',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/simulator/, '/simulator')
      },
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true
      }
    }
  }
});
