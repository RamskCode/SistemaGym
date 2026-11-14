import { describe, expect, it } from 'vitest'
import { parseLimaDateTime } from '../utils/limaDateTime'

describe('parseLimaDateTime', () => {
  it('interpreta una hora local sin zona como hora de Lima, independientemente de la zona del navegador', () => {
    const parsed = parseLimaDateTime('2026-11-10T17:20:00')

    expect(parsed.toISOString()).toBe('2026-11-10T22:20:00.000Z')
    expect(new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'America/Lima',
    }).format(parsed)).toBe('17:20')
  })

  it('preserva un desplazamiento explícito sin volver a aplicarlo', () => {
    const parsed = parseLimaDateTime('2026-11-10T17:20:00-05:00')

    expect(parsed.toISOString()).toBe('2026-11-10T22:20:00.000Z')
    expect(new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'America/Lima',
    }).format(parsed)).toBe('17:20')
  })
})
