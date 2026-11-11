export const PASSWORD_MAX_UTF8_BYTES = 72

export type PasswordRequirement = {
  id: 'length' | 'letter' | 'number' | 'symbol'
  label: string
  valid: boolean
}

export function passwordRequirements(password: string): PasswordRequirement[] {
  const codePoints = Array.from(password)
  return [
    { id: 'length', label: 'Al menos 8 caracteres', valid: codePoints.length >= 8 },
    { id: 'letter', label: 'Al menos una letra', valid: /\p{L}/u.test(password) },
    { id: 'number', label: 'Al menos un número', valid: /\p{Nd}/u.test(password) },
    { id: 'symbol', label: 'Al menos un símbolo', valid: /[\p{P}\p{S}]/u.test(password) },
  ]
}

const common = new Set([
  'password', 'password1', 'password123', 'password123!', 'contrasena', 'contraseña', '12345678',
  '123456789', 'qwerty', 'qwerty123', 'admin123', 'welcome', 'letmein', 'iloveyou',
  'abc123', 'gym12345',
])

export function passwordPolicyError(password: string): string {
  if (Array.from(password).length < 8) {
    return 'La contraseña debe tener al menos 8 caracteres.'
  }
  if (password.length > PASSWORD_MAX_UTF8_BYTES
      || new TextEncoder().encode(password).length > PASSWORD_MAX_UTF8_BYTES) {
    return 'La contraseña supera el límite técnico de 72 bytes UTF-8.'
  }
  if (!password.trim()) return 'La contraseña no puede contener solo espacios.'
  if (passwordRequirements(password).some(({ valid }) => !valid)) {
    return 'La contraseña debe incluir al menos una letra, un número y un símbolo.'
  }
  const normalized = password.toLocaleLowerCase('es-PE')
  const compact = normalized.replace(/\s+/gu, '')
  if (common.has(normalized) || (normalized.trim().length > 0 && new Set(Array.from(normalized)).size <= 2)
      || /^(?:012345|123456|234567|345678|456789|abcdef|qwerty|asdfgh)/u.test(compact)
      || /(?:012345|123456|234567|345678|456789|abcdef|qwerty|asdfgh)$/u.test(compact)) {
    return 'Elige una contraseña menos común o predecible.'
  }
  return ''
}
