import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  apiGet,
  apiPatch,
  apiPost,
  apiPut,
  type AuthenticatedRequestContext,
} from '../api/http'
import { server } from './server'

const API_PATH = '/api/protected-resource'

function createAuth(initialToken: string | null = 'memory-token') {
  let token = initialToken
  let version = initialToken ? 1 : 0
  let logoutCount = 0
  let sessionPresent = initialToken !== null
  const auth: AuthenticatedRequestContext = {
    getAccessToken: () => token,
    getSessionVersion: () => version,
    logoutIfCurrentSession: (expectedVersion) => {
      if (expectedVersion !== version || !sessionPresent) return false
      version += 1
      token = null
      sessionPresent = false
      logoutCount += 1
      return true
    },
  }

  return {
    auth,
    setToken(nextToken: string | null) {
      token = nextToken
      version += 1
      sessionPresent = nextToken !== null
    },
    expireToken() {
      token = null
    },
    getVersion: () => version,
    getLogoutCount: () => logoutCount,
  }
}

afterEach(() => vi.restoreAllMocks())

describe('authenticated HTTP API', () => {
  it('sends a protected GET with only the current bearer token and JSON accept header', async () => {
    let authorization: string | null = null
    let accept: string | null = null
    let method = ''
    let cache: RequestCache | undefined
    const realFetch = globalThis.fetch
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if (String(input).includes(API_PATH)) cache = init?.cache
      return realFetch(input, init)
    })
    server.use(http.get(API_PATH, ({ request }) => {
      authorization = request.headers.get('Authorization')
      accept = request.headers.get('Accept')
      method = request.method
      return HttpResponse.json({ ok: true })
    }))

    await expect(apiGet(API_PATH, createAuth().auth)).resolves.toEqual({ ok: true })
    expect(authorization).toBe('Bearer memory-token')
    expect(accept).toBe('application/json')
    expect(method).toBe('GET')
    expect(cache).toBe('no-store')
  })

  it.each([
    ['POST', apiPost],
    ['PUT', apiPut],
    ['PATCH', apiPatch],
  ] as const)('sends %s JSON with content type and bearer authorization', async (expectedMethod, send) => {
    let authorization: string | null = null
    let contentType: string | null = null
    let method = ''
    let body: unknown
    server.use(http.all(API_PATH, async ({ request }) => {
      authorization = request.headers.get('Authorization')
      contentType = request.headers.get('Content-Type')
      method = request.method
      body = await request.json()
      return HttpResponse.json({ saved: true })
    }))

    const payload = { nombre: 'Socio de prueba', activo: true }
    await expect(send(API_PATH, createAuth().auth, payload)).resolves.toEqual({ saved: true })
    expect(method).toBe(expectedMethod)
    expect(contentType).toContain('application/json')
    expect(authorization).toBe('Bearer memory-token')
    expect(body).toEqual(payload)
  })

  it('does not send a request without a session', async () => {
    const request = vi.fn()
    server.use(http.get(API_PATH, () => {
      request()
      return HttpResponse.json({ ok: true })
    }))
    const anonymous = createAuth(null)

    await expect(apiGet(API_PATH, anonymous.auth)).rejects.toMatchObject({
      name: 'ApiError', code: 'unauthenticated', status: 401,
    })
    expect(request).not.toHaveBeenCalled()
    expect(anonymous.getLogoutCount()).toBe(0)
  })

  it('invalidates an expired current session without sending the request', async () => {
    const request = vi.fn()
    server.use(http.get(API_PATH, () => {
      request()
      return HttpResponse.json({ ok: true })
    }))
    const expired = createAuth()
    expired.expireToken()

    await expect(apiGet(API_PATH, expired.auth)).rejects.toMatchObject({
      name: 'ApiError', code: 'unauthenticated', status: 401,
    })
    expect(request).not.toHaveBeenCalled()
    expect(expired.getLogoutCount()).toBe(1)
  })

  it.each([
    [400, 'validation'],
    [401, 'unauthenticated'],
    [403, 'forbidden'],
    [404, 'not-found'],
    [409, 'conflict'],
    [500, 'server'],
  ] as const)('maps HTTP %s to a safe user-facing error', async (status, code) => {
    server.use(http.get(API_PATH, () => HttpResponse.json({
      mensaje: 'Sensitive backend detail with private data',
      errores: ['Internal exception details'],
    }, { status })))
    const session = createAuth()

    const error = await apiGet(API_PATH, session.auth).then(() => null, (reason: unknown) => reason)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ name: 'ApiError', code, status })
    expect((error as Error).message).not.toContain('Sensitive backend detail')
    expect((error as Error).message).not.toContain('Internal exception details')
    expect(session.getLogoutCount()).toBe(status === 401 ? 1 : 0)
  })

  it('does not let a late 401 from an old session invalidate a newer session', async () => {
    let releaseResponse!: () => void
    let markStarted!: () => void
    const gate = new Promise<void>((resolve) => { releaseResponse = resolve })
    const started = new Promise<void>((resolve) => { markStarted = resolve })
    const session = createAuth('old-token')
    server.use(http.get(API_PATH, async () => {
      markStarted()
      await gate
      return new HttpResponse(null, { status: 401 })
    }))

    const oldRequest = apiGet(API_PATH, session.auth)
    await started
    session.setToken('new-token')
    releaseResponse()

    await expect(oldRequest).rejects.toMatchObject({ code: 'unauthenticated', status: 401 })
    expect(session.getLogoutCount()).toBe(0)
    expect(session.auth.getAccessToken()).toBe('new-token')
  })

  it('renews after a recoverable 401 and retries the original request only once', async () => {
    let requests = 0
    server.use(http.get(API_PATH, ({ request }) => {
      requests += 1
      if (request.headers.get('Authorization') === 'Bearer old-token') {
        return new HttpResponse(null, { status: 401 })
      }
      return HttpResponse.json({ ok: true })
    }))
    const refresh = vi.fn(async () => 'new-token')
    const auth: AuthenticatedRequestContext = {
      ...createAuth('old-token').auth,
      refreshAccessToken: refresh,
    }

    await expect(apiGet(API_PATH, auth)).resolves.toEqual({ ok: true })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(requests).toBe(2)
  })

  it('shares one in-flight refresh across concurrent expired-token requests', async () => {
    let accessToken = 'old-token'
    let refreshCount = 0
    let releaseRefresh!: () => void
    let markRefreshStarted!: () => void
    const refreshGate = new Promise<void>((resolve) => { releaseRefresh = resolve })
    const refreshStarted = new Promise<void>((resolve) => { markRefreshStarted = resolve })
    let refreshPromise: Promise<string | null> | null = null
    const base = createAuth('old-token').auth
    const auth: AuthenticatedRequestContext = {
      ...base,
      getAccessToken: () => accessToken,
      refreshAccessToken: () => {
        if (!refreshPromise) {
          refreshCount += 1
          refreshPromise = (async () => {
            markRefreshStarted()
            await refreshGate
            accessToken = 'new-token'
            return accessToken
          })()
        }
        return refreshPromise
      },
    }
    server.use(http.get(API_PATH, ({ request }) => request.headers.get('Authorization') === 'Bearer old-token'
      ? new HttpResponse(null, { status: 401 })
      : HttpResponse.json({ ok: true })))

    const first = apiGet(API_PATH, auth)
    const second = apiGet(API_PATH, auth)
    await refreshStarted
    releaseRefresh()

    await expect(Promise.all([first, second])).resolves.toEqual([{ ok: true }, { ok: true }])
    expect(refreshCount).toBe(1)
  })

  it('uses one safe network error for fetch failures', async () => {
    server.use(http.get(API_PATH, () => HttpResponse.error()))

    await expect(apiGet(API_PATH, createAuth().auth)).rejects.toMatchObject({
      code: 'network', status: 0,
      message: 'No se pudo conectar con el servidor. Comprueba tu conexión.',
    })
  })
})
