import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from './server'
import { login, LoginError, type LoginRequest } from '../api/auth'

const credentials: LoginRequest = {
  correo: 'usuario@ejemplo.com',
  contrasena: 'contraseña-de-ejemplo',
}

const validResponse = {
  token: 'test.jwt.token',
  tipo: 'Bearer',
  expiraEnMs: 900_000,
}

describe('login API client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('posts the contract to the relative API path and returns a valid response', async () => {
    let requestUrl = ''
    let requestMethod = ''
    let requestBody: unknown
    let authorizationHeader: string | null = null

    server.use(http.post('/api/auth/login', async ({ request }) => {
      requestUrl = new URL(request.url).pathname
      requestMethod = request.method
      requestBody = await request.json()
      authorizationHeader = request.headers.get('Authorization')
      return HttpResponse.json(validResponse)
    }))

    await expect(login(credentials)).resolves.toEqual(validResponse)
    expect(requestUrl).toBe('/api/auth/login')
    expect(requestMethod).toBe('POST')
    expect(requestBody).toEqual(credentials)
    expect(authorizationHeader).toBeNull()
  })

  it('maps invalid credentials to a safe 401 error', async () => {
    server.use(http.post('/api/auth/login', () => new HttpResponse(null, { status: 401 })))

    await expect(login(credentials)).rejects.toMatchObject({
      name: 'LoginError',
      code: 'unauthorized',
      status: 401,
      message: 'Correo o contraseña incorrectos.',
    })
  })

  it('maps request validation errors to a safe 400 error', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json({
      status: 400,
      errores: ['El correo no tiene un formato válido.'],
    }, { status: 400 })))

    await expect(login(credentials)).rejects.toMatchObject({
      code: 'validation',
      status: 400,
      message: 'Revisa el correo y la contraseña e inténtalo de nuevo.',
    })
  })

  it('does not expose server response details for a 500 error', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json({
      message: 'sensitive internal diagnostic',
    }, { status: 500 })))

    const result = login(credentials)
    await expect(result).rejects.toMatchObject({ code: 'server', status: 500 })
    await expect(result).rejects.not.toThrow('sensitive internal diagnostic')
  })

  it('maps a network failure without exposing the underlying exception', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.error()))

    await expect(login(credentials)).rejects.toMatchObject({
      name: 'LoginError',
      code: 'network',
      message: 'No se pudo conectar con el servidor. Comprueba tu conexión.',
    })
  })

  it('rejects a successful response that does not match the login contract', async () => {
    server.use(http.post('/api/auth/login', () => HttpResponse.json({
      token: '',
      tipo: 'Bearer',
      expiraEnMs: '900000',
    })))

    await expect(login(credentials)).rejects.toBeInstanceOf(LoginError)
    await expect(login(credentials)).rejects.toMatchObject({ code: 'invalid-response' })
  })
})
