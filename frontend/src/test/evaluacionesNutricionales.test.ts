import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import type { AuthenticatedRequestContext } from '../api/http'
import {
  consultarHistorialNutricional,
  registrarEvaluacionNutricional,
} from '../api/evaluacionesNutricionales'
import { server } from './server'

const auth: AuthenticatedRequestContext = {
  getAccessToken: () => 'nutri-token',
  getSessionVersion: () => 1,
  logoutIfCurrentSession: () => false,
}

const request = {
  pesoKg: 78.4,
  tallaM: 1.72,
  porcentajeGrasa: 24.5,
  masaMuscularKg: 32.1,
  habitosAlimenticios: 'Tres comidas',
  objetivosNutricionales: 'Mejorar composición',
  recomendacionesNutricionales: 'Hidratarse',
  observacionesSeguimiento: null,
}

const response = {
  idEvaluacion: 44,
  idSocio: 12,
  idNutricionista: 7,
  fechaEvaluacion: '2026-11-07T18:00:00Z',
  ...request,
}

describe('Evaluaciones nutricionales API', () => {
  it('requests and validates the authenticated history without adding author filters', async () => {
    let path = ''
    let authorization = ''
    server.use(http.get('/api/socios/12/evaluaciones-nutricionales', ({ request: httpRequest }) => {
      path = new URL(httpRequest.url).pathname + new URL(httpRequest.url).search
      authorization = httpRequest.headers.get('Authorization') ?? ''
      return HttpResponse.json([response, { ...response, idEvaluacion: 43 }])
    }))

    await expect(consultarHistorialNutricional(auth, 12)).resolves.toEqual([
      response, { ...response, idEvaluacion: 43 },
    ])
    expect(path).toBe('/api/socios/12/evaluaciones-nutricionales')
    expect(path).not.toContain('idNutricionista')
    expect(authorization).toBe('Bearer nutri-token')
  })

  it.each([401, 403, 404])('preserves history error status %i', async (status) => {
    server.use(http.get('/api/socios/12/evaluaciones-nutricionales', () =>
      HttpResponse.json({}, { status })))
    await expect(consultarHistorialNutricional(auth, 12)).rejects.toMatchObject({ status })
  })

  it('rejects malformed history response data', async () => {
    server.use(http.get('/api/socios/12/evaluaciones-nutricionales', () =>
      HttpResponse.json([{ idEvaluacion: 3 }])))
    await expect(consultarHistorialNutricional(auth, 12))
      .rejects.toMatchObject({ code: 'invalid-response' })
  })

  it('sends only the request DTO to the authenticated endpoint and reads the 201 response', async () => {
    let capturedBody: unknown
    let authorization = ''
    server.use(http.post('/api/socios/12/evaluaciones-nutricionales', async ({ request: httpRequest }) => {
      capturedBody = await httpRequest.json()
      authorization = httpRequest.headers.get('Authorization') ?? ''
      return HttpResponse.json(response, { status: 201 })
    }))

    await expect(registrarEvaluacionNutricional(auth, 12, request)).resolves.toEqual(response)
    expect(capturedBody).toEqual(request)
    expect(capturedBody).not.toHaveProperty('idNutricionista')
    expect(capturedBody).not.toHaveProperty('fechaEvaluacion')
    expect(authorization).toBe('Bearer nutri-token')
  })

  it.each([400, 401, 403, 404, 409])('preserves the backend error status %i', async (status) => {
    server.use(http.post('/api/socios/12/evaluaciones-nutricionales', () =>
      HttpResponse.json({}, { status })))
    await expect(registrarEvaluacionNutricional(auth, 12, request))
      .rejects.toMatchObject({ status })
  })

  it('exposes the business message from a 409 response when available', async () => {
    server.use(http.post('/api/socios/12/evaluaciones-nutricionales', () =>
      HttpResponse.json({ mensaje: 'Conflicto de negocio específico.' }, { status: 409 })))
    await expect(registrarEvaluacionNutricional(auth, 12, request))
      .rejects.toMatchObject({ status: 409, serverMessage: 'Conflicto de negocio específico.' })
  })

  it('treats server failures and invalid successful responses as errors', async () => {
    server.use(http.post('/api/socios/12/evaluaciones-nutricionales', () =>
      HttpResponse.json({}, { status: 500 })))
    await expect(registrarEvaluacionNutricional(auth, 12, request))
      .rejects.toMatchObject({ code: 'server' })

    server.use(http.post('/api/socios/12/evaluaciones-nutricionales', () =>
      HttpResponse.json({ idEvaluacion: 1 }, { status: 201 })))
    await expect(registrarEvaluacionNutricional(auth, 12, request))
      .rejects.toMatchObject({ code: 'invalid-response' })
  })

  it('represents a dropped network response as an ambiguous network error', async () => {
    server.use(http.post('/api/socios/12/evaluaciones-nutricionales', () => HttpResponse.error()))
    await expect(registrarEvaluacionNutricional(auth, 12, request))
      .rejects.toMatchObject({ code: 'network' })
  })

  it('maps an aborted request timeout to the same ambiguous network error', async () => {
    const timeout = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(AbortSignal.abort())
    try {
      await expect(registrarEvaluacionNutricional(auth, 12, request))
        .rejects.toMatchObject({ code: 'network' })
    } finally {
      timeout.mockRestore()
    }
  })
})
