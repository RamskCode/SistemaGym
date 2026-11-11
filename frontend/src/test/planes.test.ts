import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { actualizarPlan, buscarPlanPorId, buscarPlanesPorNombre, crearPlan, desactivarPlan, listarPlanes, listarPlanesPorEstado } from '../api/planes'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'plans-token', getSessionVersion: () => 2, logoutIfCurrentSession: () => false }
const plan = { idPlan: 12, nombre: 'Plan mensual', descripcion: 'Acceso por mes', precio: 89.9, duracionDias: 30, estado: 'ACTIVO', fechaCreacion: '2026-01-02T12:00:00' }
const body = { nombre: 'Plan mensual', descripcion: 'Acceso por mes', precio: 89.9, duracionDias: 30 }

afterEach(() => vi.restoreAllMocks())

describe('Planes API', () => {
  it('usa los cinco contratos GET reales y envia la sesion existente', async () => {
    const paths: string[] = []
    for (const path of ['/api/planes', '/api/planes/12', '/api/planes/buscar?nombre=Plan+mensual', '/api/planes/estado/ACTIVO']) {
      server.use(http.get(path.split('?')[0], ({ request }) => {
        paths.push(`${new URL(request.url).pathname}${new URL(request.url).search}`)
        expect(request.headers.get('Authorization')).toBe('Bearer plans-token')
        return HttpResponse.json(path === '/api/planes/12' ? plan : [plan])
      }))
    }
    await expect(listarPlanes(auth)).resolves.toEqual([plan])
    await expect(buscarPlanPorId(auth, 12)).resolves.toEqual(plan)
    await expect(buscarPlanesPorNombre(auth, 'Plan mensual')).resolves.toEqual([plan])
    await expect(listarPlanesPorEstado(auth, 'ACTIVO')).resolves.toEqual([plan])
    expect(paths).toEqual(['/api/planes', '/api/planes/12', '/api/planes/buscar?nombre=Plan+mensual', '/api/planes/estado/ACTIVO'])
  })

  it('envia los DTO exactos para crear, actualizar y desactivar', async () => {
    server.use(http.post('/api/planes', async ({ request }) => {
      expect(await request.json()).toEqual(body)
      expect(request.headers.get('Authorization')).toBe('Bearer plans-token')
      return HttpResponse.json(plan, { status: 201 })
    }))
    server.use(http.put('/api/planes/12', async ({ request }) => {
      expect(await request.json()).toEqual(body)
      return HttpResponse.json(plan)
    }))
    server.use(http.patch('/api/planes/12/desactivar', async ({ request }) => {
      expect(request.headers.get('Content-Type')).toBeNull()
      return HttpResponse.json({ ...plan, estado: 'INACTIVO' })
    }))
    await expect(crearPlan(auth, body)).resolves.toMatchObject({ idPlan: 12 })
    await expect(actualizarPlan(auth, 12, body)).resolves.toMatchObject({ nombre: body.nombre })
    await expect(desactivarPlan(auth, 12)).resolves.toMatchObject({ estado: 'INACTIVO' })
  })

  it('rechaza IDs inválidos localmente y clasifica respuestas HTTP sin alterar el contrato', async () => {
    expect(() => buscarPlanPorId(auth, 0)).toThrow(/entero positivo/)
    server.use(http.get('/api/planes/999', () => HttpResponse.json({ mensaje: 'missing' }, { status: 404 })))
    await expect(buscarPlanPorId(auth, 999)).rejects.toMatchObject({ code: 'not-found', status: 404 })
    server.use(http.post('/api/planes', () => HttpResponse.json({ mensaje: 'duplicate' }, { status: 409 })))
    await expect(crearPlan(auth, body)).rejects.toMatchObject({ code: 'conflict', status: 409 })
    server.use(http.put('/api/planes/12', () => HttpResponse.json({}, { status: 403 })))
    await expect(actualizarPlan(auth, 12, body)).rejects.toMatchObject({ code: 'forbidden', status: 403 })
  })

  it.each([
    [400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict'],
  ] as const)('clasifica un error HTTP %s', async (status, code) => {
    server.use(http.post('/api/planes', () => HttpResponse.json({ mensaje: 'error' }, { status })))
    await expect(crearPlan(auth, body)).rejects.toMatchObject({ code, status })
  })

  it('trata red, timeout y respuesta JSON inválida como resultados que la UI debe resolver con cautela', async () => {
    server.use(http.post('/api/planes', () => HttpResponse.error()))
    await expect(crearPlan(auth, body)).rejects.toMatchObject({ code: 'network' })
    vi.spyOn(AbortSignal, 'timeout').mockReturnValue(AbortSignal.abort())
    await expect(crearPlan(auth, body)).rejects.toMatchObject({ code: 'network' })
    vi.restoreAllMocks()
    server.use(http.post('/api/planes', () => HttpResponse.text('not-json', { status: 201, headers: { 'Content-Type': 'application/json' } })))
    await expect(crearPlan(auth, body)).rejects.toMatchObject({ code: 'invalid-response' })
  })
})
