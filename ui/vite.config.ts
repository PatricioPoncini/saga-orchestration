import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const service = (port: number, prefix: string) => ({
  target: `http://localhost:${port}`,
  changeOrigin: true,
  rewrite: (path: string) => path.replace(prefix, ''),
})

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api/order': service(8081, '/api/order'),
      '/api/payment': service(8082, '/api/payment'),
      '/api/inventory': service(8083, '/api/inventory'),
      '/api/orchestrator': service(8084, '/api/orchestrator'),
    },
  },
})
