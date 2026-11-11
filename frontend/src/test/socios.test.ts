import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  actualizarSocio,
  buscarSocioPorId,
  cambiarEstadoSocio,
  listarSocios,
  listarSociosNutricion,
  registrarSocio,
  type Socio,
  type SocioRequest,
} from '../api/socios'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const socio: Socio = {
  idSocio: 12,
  codigoSocio: 'SOC-0012',
  dni: '12345678',
  nombres: 'Ana',
  apellidos: 'Pérez',
  fechaNacimiento: '1995-04-02',
  celular: '999111222',
  correo: 'ana@example.test',
  estado: 'ACTIVO',
  fechaRegistro: '2026-01-01T10:00:00',
}

const payload: SocioRequest = {
  dni: '12345678',
  tipoDocumento: 'DNI',
  numeroDocumento: '12345678',
  nombres: 'Ana',
  apellidos: 'Pérez',
  fechaNacimiento: '1995-04-02',
  celular: '999111222',
  correo: 'ana@example.test',
}

const auth: AuthenticatedRequestContext = {
  getAccessToken: () => 'socio-test-token',
  getSessionVersion: () => 4,
  logoutIfCurrentSession: () => false,
}

describe('Socios API service', () => {
  it('uses the reduced nutrition contract for list, search and status filters', async () => {
    const resumen = {
      idSocio: 12,
      codigoSocio: 'SOC-0012',
      nombres: 'Ana',
      apellidos: 'Pérez',
      estado: 'ACTIVO' as const,
    }
    const paths: string[] = []
    server.use(http.get('/api/socios', ({ request }) => {
      paths.push(new URL(request.url).pathname)
      return HttpResponse.json([resumen])
    }))
    server.use(http.get('/api/socios/buscar', ({ request }) => {
      paths.push(`${new URL(request.url).pathname}${new URL(request.url).search}`)
      return HttpResponse.json([resumen])
    }))
    server.use(http.get('/api/socios/estado/INACTIVO', ({ request }) => {
      paths.push(new URL(request.url).pathname)
      return HttpResponse.json([{ ...resumen, estado: 'INACTIVO' }])
    }))

    await expect(listarSociosNutricion(auth)).resolves.toEqual([resumen])
    await expect(listarSociosNutricion(auth, { query: 'Ana Pérez' })).resolves.toEqual([resumen])
    await expect(listarSociosNutricion(auth, { estado: 'INACTIVO' }))
      .resolves.toEqual([{ ...resumen, estado: 'INACTIVO' }])
    expect(paths).toEqual([
      '/api/socios',
      '/api/socios/buscar?q=Ana%20P%C3%A9rez',
      '/api/socios/estado/INACTIVO',
    ])
    expect(resumen).not.toHaveProperty('dni')
    expect(resumen).not.toHaveProperty('correo')
    expect(resumen).not.toHaveProperty('celular')
    expect(resumen).not.toHaveProperty('fechaNacimiento')
  })

  it('uses the real list, global-search and state-filter endpoints', async () => {
    const calls: string[] = []
    server.use(http.get('/api/socios', ({ request }) => {
      calls.push(new URL(request.url).pathname)
      return HttpResponse.json([socio])
    }))
    server.use(http.get('/api/socios/buscar', ({ request }) => {
      calls.push(`${new URL(request.url).pathname}${new URL(request.url).search}`)
      return HttpResponse.json([socio])
    }))
    server.use(http.get('/api/socios/estado/INACTIVO', ({ request }) => {
      calls.push(new URL(request.url).pathname)
      return HttpResponse.json([])
    }))

    await expect(listarSocios(auth)).resolves.toEqual([socio])
    await expect(listarSocios(auth, { query: 'Ana Pérez/123' })).resolves.toEqual([socio])
    await expect(listarSocios(auth, { estado: 'INACTIVO' })).resolves.toEqual([])
    expect(calls).toEqual([
      '/api/socios',
      '/api/socios/buscar?q=Ana%20P%C3%A9rez%2F123',
      '/api/socios/estado/INACTIVO',
    ])
  })

  it('uses ID lookup, POST creation and PUT replacement with only DTO fields', async () => {
    const calls: Array<{ method: string; path: string; body?: unknown }> = []
    server.use(http.get('/api/socios/12', ({ request }) => {
      calls.push({ method: request.method, path: new URL(request.url).pathname })
      return HttpResponse.json(socio)
    }))
    server.use(http.post('/api/socios', async ({ request }) => {
      calls.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() })
      return HttpResponse.json(socio, { status: 201 })
    }))
    server.use(http.put('/api/socios/12', async ({ request }) => {
      calls.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() })
      return HttpResponse.json(socio)
    }))

    await expect(buscarSocioPorId(auth, 12)).resolves.toEqual(socio)
    await expect(registrarSocio(auth, payload)).resolves.toEqual(socio)
    await expect(actualizarSocio(auth, 12, payload)).resolves.toEqual(socio)
    expect(calls).toEqual([
      { method: 'GET', path: '/api/socios/12' },
      { method: 'POST', path: '/api/socios', body: payload },
      { method: 'PUT', path: '/api/socios/12', body: payload },
    ])
  })

  it('sends only motivo to the real activar/desactivar PATCH endpoints', async () => {
    const calls: Array<{ path: string; body: unknown }> = []
    server.use(http.patch('/api/socios/12/desactivar', async ({ request }) => {
      calls.push({ path: new URL(request.url).pathname, body: await request.json() })
      return HttpResponse.json({ ...socio, estado: 'INACTIVO' })
    }))
    server.use(http.patch('/api/socios/12/activar', async ({ request }) => {
      calls.push({ path: new URL(request.url).pathname, body: await request.json() })
      return HttpResponse.json(socio)
    }))

    await cambiarEstadoSocio(auth, 12, 'INACTIVO', 'Solicitud del socio')
    await cambiarEstadoSocio(auth, 12, 'ACTIVO', 'Regularizó su situación')
    expect(calls).toEqual([
      { path: '/api/socios/12/desactivar', body: { motivo: 'Solicitud del socio' } },
      { path: '/api/socios/12/activar', body: { motivo: 'Regularizó su situación' } },
    ])
  })
})
