import { describe, expect, it } from 'vitest'
import { createSameOriginApiProxy } from '../config/apiProxy'

describe('same-origin API proxy', () => {
  it('preserves the browser Host so refresh Origin validation succeeds', () => {
    expect(createSameOriginApiProxy('http://localhost:8080'))
      .toEqual({ target: 'http://localhost:8080', changeOrigin: false })
  })
})
