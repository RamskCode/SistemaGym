import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { actualizarMiCuenta, cambiarMiContrasena, obtenerMiCuenta } from '../api/miCuenta'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = {
  getAccessToken: () => 'session-token',
  getSessionVersion: () => 3,
  logoutIfCurrentSession: () => false,
}
const profile = {
  nombres: 'Ana', apellidos: 'Prueba', correo: 'ana@example.test', rol: 'ENTRENADOR',
  tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
}

describe('API Mi cuenta', () => {
  it('lee el perfil protegido sin exponer datos extra', async () => {
    server.use(http.get('/api/mi-cuenta', ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer session-token')
      return HttpResponse.json(profile)
    }))
    await expect(obtenerMiCuenta(auth)).resolves.toEqual(profile)
  })

  it('envía PATCH parcial de datos personales autenticado', async () => {
    server.use(http.patch('/api/mi-cuenta', async ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer session-token')
      expect(await request.json()).toEqual({ nombres: 'Ana María' })
      return HttpResponse.json({ ...profile, nombres: 'Ana María' })
    }))
    await expect(actualizarMiCuenta(auth, { nombres: 'Ana María' }))
      .resolves.toMatchObject({ nombres: 'Ana María', rol: 'ENTRENADOR' })
  })

  it('envía cambio de contraseña por endpoint separado y nunca devuelve credenciales', async () => {
    server.use(http.patch('/api/mi-cuenta/contrasena', async ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer session-token')
      expect(await request.json()).toEqual({ contrasenaActual: 'old', contrasenaNueva: 'new' })
      return new HttpResponse(null, { status: 204 })
    }))
    await expect(cambiarMiContrasena(auth, {
      contrasenaActual: 'old', contrasenaNueva: 'new',
    })).resolves.toBeUndefined()
  })

  it('rechaza formas de respuesta de perfil incompletas', async () => {
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({ correo: profile.correo })))
    await expect(obtenerMiCuenta(auth)).rejects.toMatchObject({ code: 'invalid-response' })
  })
})
