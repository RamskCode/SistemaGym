export class HttpError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'HttpError'
    this.status = status
  }
}

export type ApiErrorCode =
  | 'unauthenticated'
  | 'forbidden'
  | 'not-found'
  | 'validation'
  | 'conflict'
  | 'server'
  | 'http'
  | 'network'
  | 'invalid-response'

const API_ERROR_MESSAGES: Record<ApiErrorCode, string> = {
  unauthenticated: 'Tu sesión no está disponible o ha expirado. Inicia sesión nuevamente.',
  forbidden: 'No tienes permiso para realizar esta acción.',
  'not-found': 'No se encontró el recurso solicitado.',
  validation: 'Revisa los datos ingresados e inténtalo de nuevo.',
  conflict: 'La operación entra en conflicto con el estado actual. Actualiza la información e inténtalo de nuevo.',
  server: 'El servidor no pudo completar la operación. Inténtalo más tarde.',
  http: 'No fue posible completar la solicitud.',
  network: 'No se pudo conectar con el servidor. Comprueba tu conexión.',
  'invalid-response': 'El servidor devolvió una respuesta no válida.',
}

export class ApiError extends HttpError {
  readonly code: ApiErrorCode
  readonly serverMessage?: string

  constructor(code: ApiErrorCode, status?: number, serverMessage?: string) {
    super(serverMessage ?? API_ERROR_MESSAGES[code], status ?? 0)
    this.name = 'ApiError'
    this.code = code
    this.serverMessage = serverMessage
  }
}

const configuredApiBase = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/+$/, '') ?? ''

/** Same-origin by default for local development; production may target Render directly. */
export function apiUrl(path: `/api/${string}`): string {
  if (!configuredApiBase) return path
  const base = new URL(configuredApiBase)
  if (base.protocol !== 'https:' && base.hostname !== 'localhost' && base.hostname !== '127.0.0.1') {
    throw new Error('VITE_API_BASE_URL debe usar HTTPS fuera de localhost.')
  }
  if (base.pathname !== '/' || base.search || base.hash || base.username || base.password) {
    throw new Error('VITE_API_BASE_URL debe ser un origen sin ruta ni credenciales.')
  }
  return `${base.origin}${path}`
}

/** Minimal same-origin JSON helper for public endpoints (including login). */
export async function apiRequest<T>(path: `/api/${string}`, init?: RequestInit): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...init,
    cache: 'no-store',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    throw new HttpError(`La solicitud a ${path} falló (${response.status}).`, response.status)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export type AuthenticatedRequestContext = {
  getAccessToken: () => string | null
  getSessionVersion: () => number
  logoutIfCurrentSession: (expectedVersion: number) => boolean
  refreshAccessToken?: () => Promise<string | null>
}

function errorCodeForStatus(status: number): ApiErrorCode {
  if (status === 400) return 'validation'
  if (status === 401) return 'unauthenticated'
  if (status === 403) return 'forbidden'
  if (status === 404) return 'not-found'
  if (status === 409) return 'conflict'
  if (status >= 500) return 'server'
  return 'http'
}

/** Sends a protected same-origin request using the current in-memory AuthContext session. */
export async function authenticatedApiRequest<T>(
  path: `/api/${string}`,
  auth: AuthenticatedRequestContext,
  init: RequestInit = {},
  includeServerMessage = false,
): Promise<T> {
  const sessionVersion = auth.getSessionVersion()
  let token = auth.getAccessToken()
  if (!token && auth.refreshAccessToken) token = await auth.refreshAccessToken()
  if (!token) {
    auth.logoutIfCurrentSession(sessionVersion)
    throw new ApiError('unauthenticated', 401)
  }

  const send = async (accessToken: string): Promise<Response> => {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Authorization', `Bearer ${accessToken}`)
    return fetch(path, { ...init, cache: 'no-store', headers })
  }

  let response: Response
  try {
    response = await send(token)
    if (response.status === 401 && auth.refreshAccessToken
        && auth.getSessionVersion() === sessionVersion) {
      const latest = auth.getAccessToken()
      if (latest && latest !== token) {
        response = await send(latest)
      } else {
        const refreshed = await auth.refreshAccessToken()
        if (refreshed) response = await send(refreshed)
      }
    }
  } catch {
    throw new ApiError('network')
  }

  if (!response.ok) {
    if (response.status === 401) auth.logoutIfCurrentSession(sessionVersion)
    let serverMessage: string | undefined
    if (includeServerMessage && response.status === 409) {
      try {
        const body: unknown = await response.clone().json()
        if (typeof body === 'object' && body !== null && 'mensaje' in body
            && typeof body.mensaje === 'string' && body.mensaje.trim()) {
          serverMessage = body.mensaje.trim()
        }
      } catch {
        // Fall back to the standard status-specific message if the body is unavailable.
      }
    }
    throw new ApiError(errorCodeForStatus(response.status), response.status, serverMessage)
  }

  if (response.status === 204) return undefined as T

  try {
    return await response.json() as T
  } catch {
    throw new ApiError('invalid-response', response.status)
  }
}

function jsonRequest(method: 'POST' | 'PUT' | 'PATCH', body: unknown, signal?: AbortSignal): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  }
}

export function apiGet<T>(
  path: `/api/${string}`,
  auth: AuthenticatedRequestContext,
): Promise<T> {
  return authenticatedApiRequest(path, auth, { method: 'GET' })
}

export function apiPost<T>(
  path: `/api/${string}`,
  auth: AuthenticatedRequestContext,
  body: unknown,
  signal?: AbortSignal,
  includeServerMessage = false,
  extraHeaders?: HeadersInit,
): Promise<T> {
  const headers = new Headers({ 'Content-Type': 'application/json' })
  new Headers(extraHeaders).forEach((value, key) => headers.set(key, value))
  return authenticatedApiRequest(path, auth, {
    ...jsonRequest('POST', body, signal),
    headers,
  }, includeServerMessage)
}

export function apiPut<T>(
  path: `/api/${string}`,
  auth: AuthenticatedRequestContext,
  body: unknown,
): Promise<T> {
  return authenticatedApiRequest(path, auth, jsonRequest('PUT', body))
}

export function apiPatch<T>(
  path: `/api/${string}`,
  auth: AuthenticatedRequestContext,
  body: unknown,
): Promise<T> {
  return authenticatedApiRequest(path, auth, jsonRequest('PATCH', body))
}
