import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
Object.defineProperty(globalThis, 'crypto', { configurable: true, value: {
  randomUUID: () => '00000000-0000-4000-8000-000000000001',
  subtle: { digest: async (_algorithm: AlgorithmIdentifier, data: BufferSource) => {
    const bytes = new Uint8Array(data as ArrayBuffer)
    const result = new Uint8Array(32)
    for (const byte of bytes) result[byte % result.length] = (result[byte % result.length] + byte) % 256
    return result.buffer
  } },
} as unknown as Crypto })
afterEach(() => {
  cleanup()
  window.localStorage.clear()
  server.resetHandlers()
})
afterAll(() => server.close())
