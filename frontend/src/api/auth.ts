import { apiRequest, HttpError } from './http'

export type LoginRequest = {
  correo: string
  contrasena: string
}

export type LoginResponse = {
  token: string
  tipo: 'Bearer'
  expiraEnMs: number
}

export type LoginErrorCode =
  | 'validation'
  | 'unauthorized'
  | 'server'
  | 'network'
  | 'invalid-response'
  | 'http'
  | 'unexpected'

export class LoginError extends Error {
  readonly code: LoginErrorCode
  readonly status?: number

  constructor(code: LoginErrorCode, message: string, status?: number) {
    super(message)
    this.name = 'LoginError'
    this.code = code
    this.status = status
  }
}

const LOGIN_ERRORS: Record<LoginErrorCode, string> = {
  validation: 'Revisa el correo y la contraseña e inténtalo de nuevo.',
  unauthorized: 'Correo o contraseña incorrectos.',
  server: 'El servidor no pudo completar el inicio de sesión. Inténtalo más tarde.',
  network: 'No se pudo conectar con el servidor. Comprueba tu conexión.',
  'invalid-response': 'El servidor devolvió una respuesta de inicio de sesión inválida.',
  http: 'No fue posible iniciar sesión. Inténtalo más tarde.',
  unexpected: 'Ocurrió un error al iniciar sesión. Inténtalo más tarde.',
}

function isLoginResponse(value: unknown): value is LoginResponse {
  if (typeof value !== 'object' || value === null) return false

  const response = value as Record<string, unknown>
  return typeof response.token === 'string'
    && response.token.length > 0
    && response.token.trim() === response.token
    && response.tipo === 'Bearer'
    && typeof response.expiraEnMs === 'number'
    && Number.isSafeInteger(response.expiraEnMs)
    && response.expiraEnMs > 0
}

function toLoginError(error: unknown): LoginError {
  if (error instanceof LoginError) return error

  if (error instanceof HttpError) {
    if (error.status === 400) {
      return new LoginError('validation', LOGIN_ERRORS.validation, error.status)
    }
    if (error.status === 401) {
      return new LoginError('unauthorized', LOGIN_ERRORS.unauthorized, error.status)
    }
    if (error.status >= 500) {
      return new LoginError('server', LOGIN_ERRORS.server, error.status)
    }
    return new LoginError('http', LOGIN_ERRORS.http, error.status)
  }

  if (error instanceof TypeError) {
    return new LoginError('network', LOGIN_ERRORS.network)
  }

  if (error instanceof SyntaxError) {
    return new LoginError('invalid-response', LOGIN_ERRORS['invalid-response'])
  }

  return new LoginError('unexpected', LOGIN_ERRORS.unexpected)
}

/** Sends credentials to the same-origin API; it does not retain or persist the token. */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  try {
    const response = await apiRequest<unknown>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!isLoginResponse(response)) {
      throw new LoginError('invalid-response', LOGIN_ERRORS['invalid-response'])
    }

    return response
  } catch (error) {
    throw toLoginError(error)
  }
}

function csrfCookie(): string | null {
  const item = document.cookie.split('; ').find((part) => part.startsWith('SG_CSRF='))
  return item ? decodeURIComponent(item.slice('SG_CSRF='.length)) : null
}

export async function refreshLogin(): Promise<LoginResponse> {
  const csrf = csrfCookie()
  if (!csrf) throw new HttpError('No hay una sesión renovable.', 401)
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'same-origin',
    cache: 'no-store',
    headers: { Accept: 'application/json', 'X-CSRF-Token': csrf },
  })
  if (!response.ok) throw new HttpError('No fue posible renovar la sesión.', response.status)
  const body: unknown = await response.json()
  if (!isLoginResponse(body)) throw new HttpError('Respuesta de renovación inválida.', 500)
  return body
}

export function revokeLogin(): void {
  const csrf = csrfCookie()
  if (!csrf) return
  void fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    cache: 'no-store',
    headers: { 'X-CSRF-Token': csrf },
    keepalive: true,
  }).catch(() => undefined)
}
