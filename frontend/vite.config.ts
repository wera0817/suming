import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  server: { port: 9099, strictPort: true, allowedHosts: ['yun.byca.cn'], proxy: { '/api': { target: process.env.BACKEND_URL || 'http://127.0.0.1:18080', changeOrigin: false } } },
})
