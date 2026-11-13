import { describe, expect, it } from 'vitest'
import { isValidDocument, normalizeDocumentInput } from '../utils/documentIdentity'

describe('document identity rules', () => {
  it('accepts only eight ASCII digits for DNI', () => {
    expect(isValidDocument('DNI', '12345678')).toBe(true)
    for (const value of ['1234567', '123456789', '1234AB78', '1234-678']) expect(isValidDocument('DNI', value)).toBe(false)
  })

  it('accepts only CE digits up to twelve and strips letters and symbols', () => {
    expect(normalizeDocumentInput('CE', '001234')).toBe('001234')
    expect(isValidDocument('CE', '001234')).toBe(true)
    expect(isValidDocument('CE', '1'.repeat(12))).toBe(true)
    expect(isValidDocument('CE', '1'.repeat(13))).toBe(false)
    expect(isValidDocument('CE', '12AB34')).toBe(false)
    expect(normalizeDocumentInput('CE', 'ab-12 c!34')).toBe('1234')
  })

  it('enforces input limits for typing and paste and reinterprets on type change', () => {
    expect(normalizeDocumentInput('DNI', '1234567890123')).toBe('12345678')
    expect(normalizeDocumentInput('CE', 'ab1234567890123')).toBe('123456789012')
    expect(normalizeDocumentInput('CE', normalizeDocumentInput('DNI', '12345678'))).toBe('12345678')
    expect(normalizeDocumentInput('DNI', normalizeDocumentInput('CE', 'a12345678'))).toBe('12345678')
  })
})
