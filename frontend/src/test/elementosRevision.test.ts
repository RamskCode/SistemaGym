import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { actualizarElementoRevision, buscarElementoRevisionPorId, cambiarEstadoElementoRevision, crearElementoRevision, listarElementosRevision, listarElementosRevisionActivos } from '../api/elementosRevision'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'catalog-token', getSessionVersion: () => 1, logoutIfCurrentSession: () => false }
const element = { idElemento: 4, nombre: 'Bicicleta fija', tipo: 'MAQUINA', descripcion: 'Cardio', ubicacion: 'Sala principal', activo: true, fechaCreacion: '2026-01-01T10:00:00', fechaActualizacion: '2026-01-01T10:00:00' }
const body = { nombre: element.nombre, tipo: 'MAQUINA' as const, descripcion: 'Cardio', ubicacion: 'Sala principal' }
afterEach(() => vi.restoreAllMocks())

describe('API de elementos de revisión', () => {
  it('consume listados y detalle usando autenticación existente', async () => {
    const paths: string[] = []
    server.use(http.get('/api/elementos-revision', ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer catalog-token'); return HttpResponse.json([element]) }))
    server.use(http.get('/api/elementos-revision/activos', ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer catalog-token'); return HttpResponse.json([element]) }))
    server.use(http.get('/api/elementos-revision/4', ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer catalog-token'); return HttpResponse.json(element) }))
    await expect(listarElementosRevision(auth)).resolves.toEqual([element])
    await expect(listarElementosRevisionActivos(auth)).resolves.toEqual([element])
    await expect(buscarElementoRevisionPorId(auth, 4)).resolves.toEqual(element)
    expect(paths).toEqual(['/api/elementos-revision', '/api/elementos-revision/activos', '/api/elementos-revision/4'])
  })

  it('envía POST, PUT y PATCH reales con DTO/paths correctos', async () => {
    server.use(http.post('/api/elementos-revision', async ({ request }) => { expect(await request.json()).toEqual(body); return HttpResponse.json(element, { status: 201 }) }))
    server.use(http.put('/api/elementos-revision/4', async ({ request }) => { expect(await request.json()).toEqual(body); return HttpResponse.json(element) }))
    server.use(http.patch('/api/elementos-revision/4/desactivar', ({ request }) => { expect(request.headers.get('Content-Type')).toBeNull(); return HttpResponse.json({ ...element, activo: false }) }))
    server.use(http.patch('/api/elementos-revision/4/activar', () => HttpResponse.json(element)))
    await expect(crearElementoRevision(auth, body)).resolves.toMatchObject({ idElemento: 4 })
    await expect(actualizarElementoRevision(auth, 4, body)).resolves.toMatchObject({ nombre: element.nombre })
    await expect(cambiarEstadoElementoRevision(auth, 4, false)).resolves.toMatchObject({ activo: false })
    await expect(cambiarEstadoElementoRevision(auth, 4, true)).resolves.toMatchObject({ activo: true })
  })

  it('rechaza IDs y clasifica HTTP 400/401/403/404/409', async () => {
    expect(() => buscarElementoRevisionPorId(auth, -1)).toThrow(/entero positivo/)
    for (const [status, code] of [[400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict']] as const) {
      server.use(http.post('/api/elementos-revision', () => HttpResponse.json({ mensaje: 'error' }, { status })))
      await expect(crearElementoRevision(auth, body)).rejects.toMatchObject({ status, code })
    }
  })

  it('clasifica red, timeout y respuestas inválidas para tratamiento cauteloso de escritura', async () => {
    server.use(http.post('/api/elementos-revision', () => HttpResponse.error()))
    await expect(crearElementoRevision(auth, body)).rejects.toMatchObject({ code: 'network' })
    vi.spyOn(AbortSignal, 'timeout').mockReturnValue(AbortSignal.abort())
    await expect(crearElementoRevision(auth, body)).rejects.toMatchObject({ code: 'network' })
    vi.restoreAllMocks()
    server.use(http.post('/api/elementos-revision', () => HttpResponse.text('bad-json', { status: 201, headers: { 'Content-Type': 'application/json' } })))
    await expect(crearElementoRevision(auth, body)).rejects.toMatchObject({ code: 'invalid-response' })
  })
})
