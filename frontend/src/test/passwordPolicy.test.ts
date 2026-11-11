import { describe, expect, it } from 'vitest'
import { PASSWORD_MAX_UTF8_BYTES, passwordPolicyError, passwordRequirements } from '../utils/passwordPolicy'

describe('política común de contraseña', () => {
  it('evalúa las cuatro condiciones independientemente', () => {
    expect(passwordRequirements('Abc123!x').map(({ valid }) => valid)).toEqual([true, true, true, true])
    expect(passwordPolicyError('Abc123!')).toMatch(/8 caracteres/)
    expect(passwordPolicyError('1234567!')).toMatch(/letra/)
    expect(passwordPolicyError('Abcdefg!')).toMatch(/número/)
    expect(passwordPolicyError('Abcdefg1')).toMatch(/símbolo/)
  })

  it('no exige una combinación de mayúsculas/minúsculas ni limita a 24 caracteres', () => {
    expect(passwordPolicyError('abc123!x')).toBe('')
    expect(passwordPolicyError('Long passphrase with 123! symbols')).toBe('')
  })

  it('preserva el rechazo de contraseñas comunes y el máximo técnico BCrypt', () => {
    expect(passwordPolicyError('Password123!')).toMatch(/común o predecible/)
    expect(new TextEncoder().encode('A1!' + 'b'.repeat(PASSWORD_MAX_UTF8_BYTES - 3)).length)
      .toBe(PASSWORD_MAX_UTF8_BYTES)
    expect(passwordPolicyError('A1!' + 'b'.repeat(PASSWORD_MAX_UTF8_BYTES - 2))).toMatch(/72 bytes/)
  })
})
