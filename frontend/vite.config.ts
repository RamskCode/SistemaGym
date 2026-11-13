import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { createSameOriginApiProxy } from './src/config/apiProxy.js'

const e2e = process.env.SISTEMAGYM_E2E === '1'
const backendTarget = e2e ? process.env.SISTEMAGYM_E2E_BACKEND : undefined

if (e2e) {
  if (!backendTarget) throw new Error('E2E requiere SISTEMAGYM_E2E_BACKEND con el backend aislado.')
  const target = new URL(backendTarget)
  if (target.protocol !== 'http:' || target.hostname !== '127.0.0.1'
      || !target.port || target.username || target.password || target.pathname !== '/') {
    throw new Error('El proxy E2E solo acepta un backend HTTP de loopback en 127.0.0.1 con puerto explícito.')
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // The Reportes route is lazy-loaded. Prebundle its export dependencies so
  // first navigation cannot receive Vite's stale "Outdated Optimize Dep" 504.
  optimizeDeps: {
    include: ['fflate', 'jspdf', 'jspdf-autotable'],
  },
  server: {
    ...(e2e ? { host: '127.0.0.1' } : {}),
    proxy: {
      '/api': {
        ...createSameOriginApiProxy(backendTarget ?? 'http://localhost:8080'),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    clearMocks: true,
    include: ['src/test/**/*.test.{ts,tsx}'],
  },
})
