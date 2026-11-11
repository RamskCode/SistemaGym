import { act, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  AuthProvider,
  InvalidSessionError,
  useAuth,
  type AuthContextValue,
} from '../auth/AuthContext'
import type { LoginRequest, LoginResponse } from '../api/auth'
import { server } from './server'

const API_PATH = '/api/auth/login'
const BASE_NOW = Date.UTC(2025, 0, 1, 12, 0, 0)
const credentials: LoginRequest = {
  correo: 'empleado@example.test',
  contrasena: 'credencial-solo-de-prueba',
}

let auth: AuthContextValue

function base64UrlJson(value: unknown): string {
  return btoa(JSON.stringify(value))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
}

function makeToken(payload: Record<string, unknown>): string {
  return `${base64UrlJson({ alg: 'HS256', typ: 'JWT' })}.${base64UrlJson(payload)}.c2lnbmF0dXJl`
}

function validReply(overrides: {
  roles?: unknown
  sub?: unknown
  iat?: number
  exp?: number
  expiraEnMs?: number
} = {}): LoginResponse {
  const nowSeconds = Math.floor(Date.now() / 1000)
  const iat = overrides.iat ?? nowSeconds
  const exp = overrides.exp ?? iat + 900
  const token = makeToken({
    sub: overrides.sub ?? 'empleado@example.test',
    roles: overrides.roles ?? ['ROLE_ENTRENADOR'],
    iat,
    exp,
  })

  return {
    token,
    tipo: 'Bearer',
    expiraEnMs: overrides.expiraEnMs ?? (exp - iat) * 1000,
  }
}

function AuthProbe() {
  auth = useAuth()
  return (
    <div>
      <span data-testid="auth-state">{auth.isAuthenticated ? 'authenticated' : 'anonymous'}</span>
      <span data-testid="auth-initializing">{auth.isInitializing ? 'initializing' : 'ready'}</span>
      <span data-testid="user-email">{auth.user?.correo ?? ''}</span>
      <span data-testid="roles">{auth.user?.roles.join(',') ?? ''}</span>
      <span data-testid="token-type">{auth.tokenType ?? ''}</span>
      <span data-testid="access-token">{auth.getAccessToken() ?? ''}</span>
      <span data-testid="expiry">{auth.expiresAt ?? ''}</span>
    </div>
  )
}

function renderProvider() {
  return render(<AuthProvider><AuthProbe /></AuthProvider>)
}

function mockLogin(response: unknown) {
  server.use(http.post(API_PATH, () => HttpResponse.json(response)))
}

async function authenticate(response: unknown = validReply()) {
  mockLogin(response)
  await act(async () => {
    await auth.login(credentials)
  })
}

afterEach(() => {
  vi.useRealTimers()
  document.cookie = 'SG_CSRF=; Max-Age=0; path=/'
})

describe('AuthProvider in-memory session', () => {
  it('leaves initialization in a usable anonymous state under React StrictMode when refresh is unavailable', async () => {
    render(<StrictMode><AuthProvider><AuthProbe /></AuthProvider></StrictMode>)

    await waitFor(() => expect(screen.getByTestId('auth-initializing')).toHaveTextContent('ready'))
    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
  })

  it('restores a valid cookie-backed session once under React StrictMode', async () => {
    document.cookie = 'SG_CSRF=csrf-session; path=/'
    let refreshRequests = 0
    server.use(http.post('/api/auth/refresh', ({ request }) => {
      refreshRequests += 1
      expect(request.headers.get('X-CSRF-Token')).toBe('csrf-session')
      return HttpResponse.json(validReply({ sub: 'restored@example.test' }))
    }))
    render(<StrictMode><AuthProvider><AuthProbe /></AuthProvider></StrictMode>)

    await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent('authenticated'))
    expect(screen.getByTestId('auth-initializing')).toHaveTextContent('ready')
    expect(screen.getByTestId('user-email')).toHaveTextContent('restored@example.test')
    expect(refreshRequests).toBe(1)
  })

  it('ends startup initialization if the initial refresh request never responds', async () => {
    vi.useFakeTimers()
    document.cookie = 'SG_CSRF=csrf-session; path=/'
    server.use(http.post('/api/auth/refresh', () => new Promise<Response>(() => undefined)))
    render(<StrictMode><AuthProvider><AuthProbe /></AuthProvider></StrictMode>)

    await act(async () => { await vi.advanceTimersByTimeAsync(8_000) })

    expect(screen.getByTestId('auth-initializing')).toHaveTextContent('ready')
    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
  })

  it('starts a session and exposes basic identity and a live token getter', async () => {
    renderProvider()
    await authenticate()

    expect(screen.getByTestId('auth-state')).toHaveTextContent('authenticated')
    expect(screen.getByTestId('user-email')).toHaveTextContent('empleado@example.test')
    expect(screen.getByTestId('roles')).toHaveTextContent('ENTRENADOR')
    expect(screen.getByTestId('token-type')).toHaveTextContent('Bearer')
    expect(screen.getByTestId('access-token')).not.toBeEmptyDOMElement()
    expect(auth.getAccessToken()).toBeTruthy()
  })

  it('normalizes the four supported backend authorities in one consistent form', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    renderProvider()
    await authenticate(validReply({
      roles: [
        'ROLE_ADMINISTRADOR',
        'ROLE_RECEPCIONISTA',
        'ROLE_ENTRENADOR',
        'ROLE_NUTRICIONISTA',
      ],
    }))

    expect(auth.user?.roles).toEqual([
      'ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA',
    ])
  })

  it('rejects a malformed JWT without exposing its value', async () => {
    renderProvider()
    const malformed = { token: 'not-a-jwt-secret-value', tipo: 'Bearer', expiraEnMs: 900_000 }
    mockLogin(malformed)

    await act(async () => {
      await expect(auth.login(credentials)).rejects.toBeInstanceOf(InvalidSessionError)
    })

    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    expect(screen.queryByText('not-a-jwt-secret-value')).not.toBeInTheDocument()
  })

  it.each([
    ['unknown role', { roles: ['ROLE_SOCIO'] }],
    ['empty subject', { sub: '' }],
    ['inconsistent lifetime', { expiraEnMs: 600_000 }],
  ])('rejects invalid JWT claims (%s)', async (_case, overrides) => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    renderProvider()
    mockLogin(validReply(overrides))

    await act(async () => {
      await expect(auth.login(credentials)).rejects.toBeInstanceOf(InvalidSessionError)
    })
    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    expect(auth.getAccessToken()).toBeNull()
  })

  it('rejects an already expired access token', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    const nowSeconds = BASE_NOW / 1000
    renderProvider()
    mockLogin(validReply({ iat: nowSeconds - 60, exp: nowSeconds - 1, expiraEnMs: 60_000 }))

    await act(async () => {
      await expect(auth.login(credentials)).rejects.toBeInstanceOf(InvalidSessionError)
    })
    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
  })

  it('uses the earlier deadline from the response lifetime and JWT exp claim', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    const nowSeconds = BASE_NOW / 1000
    renderProvider()
    await authenticate(validReply({
      iat: nowSeconds,
      exp: nowSeconds + 60,
      expiraEnMs: 61_000,
    }))

    expect(auth.expiresAt).toBe(BASE_NOW + 60_000)
  })

  it('clears the session automatically when its timer reaches expiry', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    const nowSeconds = BASE_NOW / 1000
    renderProvider()
    await authenticate(validReply({ iat: nowSeconds, exp: nowSeconds + 60 }))
    expect(auth.getAccessToken()).toBeTruthy()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000)
    })

    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('user-email')).toBeEmptyDOMElement()
    expect(auth.getAccessToken()).toBeNull()
  })

  it('renews before the original 15-minute expiry and retains the active session', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    let refreshRequests = 0
    server.use(http.post('/api/auth/refresh', ({ request }) => {
      refreshRequests += 1
      expect(request.headers.get('X-CSRF-Token')).toBe('csrf-session')
      const nowSeconds = Math.floor(Date.now() / 1000)
      return HttpResponse.json(validReply({ iat: nowSeconds, exp: nowSeconds + 900 }))
    }))
    renderProvider()
    await authenticate()
    const originalToken = auth.getAccessToken()
    document.cookie = 'SG_CSRF=csrf-session; path=/'

    await act(async () => { await vi.advanceTimersByTimeAsync(840_000) })
    await act(async () => { await vi.advanceTimersByTimeAsync(120_000) })

    expect(refreshRequests).toBe(1)
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.getAccessToken()).not.toBe(originalToken)
    expect(auth.user?.correo).toBe('empleado@example.test')
  })

  it('restores a valid cookie-backed session after provider remount without browser storage', async () => {
    document.cookie = 'SG_CSRF=csrf-session; path=/'
    let refreshRequests = 0
    server.use(http.post('/api/auth/refresh', ({ request }) => {
      refreshRequests += 1
      expect(request.headers.get('X-CSRF-Token')).toBe('csrf-session')
      return HttpResponse.json(validReply({ sub: 'reload@example.test' }))
    }))

    renderProvider()

    await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent('authenticated'))
    expect(auth.user?.correo).toBe('reload@example.test')
    expect(refreshRequests).toBe(1)
  })

  it('logout immediately removes identity, token, and expiry from memory', async () => {
    renderProvider()
    await authenticate()
    expect(auth.getAccessToken()).toBeTruthy()

    act(() => auth.logout())

    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('user-email')).toBeEmptyDOMElement()
    expect(screen.getByTestId('token-type')).toBeEmptyDOMElement()
    expect(screen.getByTestId('expiry')).toBeEmptyDOMElement()
    expect(auth.getAccessToken()).toBeNull()
  })

  it('only invalidates the session version associated with the failed request', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    renderProvider()
    const previousVersion = auth.getSessionVersion()
    await authenticate()
    const currentVersion = auth.getSessionVersion()

    expect(currentVersion).not.toBe(previousVersion)
    expect(auth.logoutIfCurrentSession(previousVersion)).toBe(false)
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.getAccessToken()).toBeTruthy()

    let invalidated = false
    act(() => {
      invalidated = auth.logoutIfCurrentSession(currentVersion)
    })
    expect(invalidated).toBe(true)
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.getAccessToken()).toBeNull()
  })

  it('does not restore a session after unmounting and mounting a new provider', async () => {
    const mounted = renderProvider()
    await authenticate()
    expect(auth.getAccessToken()).toBeTruthy()
    mounted.unmount()

    renderProvider()

    expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    expect(auth.user).toBeNull()
    expect(auth.getAccessToken()).toBeNull()
  })

  it('lets only the most recently started simultaneous login attempt set the session', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(BASE_NOW)
    let markFirstStarted!: () => void
    let releaseFirst!: () => void
    const firstStarted = new Promise<void>((resolve) => { markFirstStarted = resolve })
    const firstGate = new Promise<void>((resolve) => { releaseFirst = resolve })

    server.use(http.post(API_PATH, async ({ request }) => {
      const body = await request.json() as LoginRequest
      if (body.correo === 'primero@example.test') {
        markFirstStarted()
        await firstGate
        return HttpResponse.json(validReply({ sub: body.correo }))
      }
      return HttpResponse.json(validReply({ sub: body.correo }))
    }))

    renderProvider()
    const firstLogin = auth.login({ correo: 'primero@example.test', contrasena: 'solo-prueba' })
    await firstStarted
    let secondResult = false
    await act(async () => {
      secondResult = await auth.login({ correo: 'segundo@example.test', contrasena: 'solo-prueba' })
    })
    releaseFirst()
    let firstResult = true
    await act(async () => {
      firstResult = await firstLogin
    })

    expect(secondResult).toBe(true)
    expect(firstResult).toBe(false)
    expect(screen.getByTestId('user-email')).toHaveTextContent('segundo@example.test')
    expect(auth.getAccessToken()).toBeTruthy()
  })
})
