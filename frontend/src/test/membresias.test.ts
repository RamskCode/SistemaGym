import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { buscarMembresiaPorId, consultarOperacionPorClave, listarMembresias, listarMembresiasPorSocio, listarPlanesActivos, listarSociosActivos, obtenerEstadoMembresia, registrarMembresia, registrarPagoPosterior, type Membresia } from '../api/membresias'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const member: Membresia = {
  idMembresia: 8,
  socio: { idSocio: 3, codigoSocio: 'SOC-0003', dni: '12345678', nombres: 'Ana', apellidos: 'Pérez', fechaNacimiento: null, celular: null, correo: null, estado: 'ACTIVO', fechaRegistro: '2026-01-01T10:00:00' },
  plan: { idPlan: 2, nombre: 'Mensual', descripcion: null, precio: 100, duracionDias: 30, estado: 'ACTIVO', fechaCreacion: '2025-12-01T10:00:00' },
  precioAcordado: 100, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-31', fechaLimitePago: '2026-01-10', estado: 'ACTIVA', motivoCancelacion: null, fechaCreacion: '2026-01-01T10:00:00',
}

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'memory-token', getSessionVersion: () => 1, logoutIfCurrentSession: () => false }

describe('Membresías API', () => {
  it('llama los cinco contratos GET existentes y envía autorización', async () => {
    const calls: string[] = []
    server.use(http.get('/api/membresias', ({ request }) => { calls.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer memory-token'); return HttpResponse.json([member]) }))
    server.use(http.get('/api/membresias/8', ({ request }) => { calls.push(new URL(request.url).pathname); return HttpResponse.json(member) }))
    server.use(http.get('/api/membresias/socio/3', ({ request }) => { calls.push(new URL(request.url).pathname); return HttpResponse.json([member]) }))
    server.use(http.get('/api/membresias/8/estado', ({ request }) => { calls.push(new URL(request.url).pathname); return HttpResponse.json({ idMembresia: 8, estado: 'ACTIVA', situacion: 'ACTIVA_PAGADA', precioAcordado: 100, saldoPendiente: 0, pagada: true, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-31', fechaLimitePago: null, motivoCancelacion: null, fechaCreacion: '2026-01-01T10:00:00' }) }))
    server.use(http.get('/api/planes/estado/ACTIVO', ({ request }) => { calls.push(new URL(request.url).pathname); return HttpResponse.json([member.plan]) }))

    await expect(listarMembresias(auth)).resolves.toEqual([member])
    await expect(buscarMembresiaPorId(auth, 8)).resolves.toEqual(member)
    await expect(listarMembresiasPorSocio(auth, 3)).resolves.toEqual([member])
    await expect(obtenerEstadoMembresia(auth, 8)).resolves.toMatchObject({ saldoPendiente: 0 })
    await expect(listarPlanesActivos(auth)).resolves.toEqual([member.plan])
    expect(calls).toEqual(['/api/membresias', '/api/membresias/8', '/api/membresias/socio/3', '/api/membresias/8/estado', '/api/planes/estado/ACTIVO'])
  })

  it('envía los DTO exactos para el alta con pago inicial y abono posterior', async () => {
    const initial = { idSocio: 3, idPlan: 2, montoPrimerPago: 40, metodoPago: 'YAPE', referencia: 'OP-01', observaciones: null }
    const following = { monto: 60, metodoPago: 'EFECTIVO', referencia: null, observaciones: 'Abono final' }
    server.use(http.get('/api/socios/estado/ACTIVO', () => HttpResponse.json([member.socio])))
    server.use(http.post('/api/membresias', async ({ request }) => {
      expect(await request.json()).toEqual(initial)
      expect(request.headers.get('Authorization')).toBe('Bearer memory-token')
      expect(request.headers.get('Idempotency-Key')).toBe('00000000-0000-4000-8000-000000000001')
      return HttpResponse.json({ idMembresia: 8, idSocio: 3, idPlan: 2, precioAcordado: 100, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-31', fechaLimitePago: '2026-01-10', estado: 'ACTIVA', idPago: 21, montoPrimerPago: 40, metodoPago: 'YAPE', idAplicacion: 7 }, { status: 201 })
    }))
    server.use(http.post('/api/membresias/8/pagos', async ({ request }) => {
      expect(await request.json()).toEqual(following)
      expect(request.headers.get('Idempotency-Key')).toBe('00000000-0000-4000-8000-000000000002')
      return HttpResponse.json({ idPago: 22, idAplicacion: 8, idMembresia: 8, montoPago: 60, montoAplicado: 60, metodoPago: 'EFECTIVO', referencia: null, observaciones: 'Abono final', fechaPago: '2026-01-02T10:00:00', saldoPendiente: 0, estadoMembresia: 'ACTIVA' }, { status: 201 })
    }))
    server.use(http.get('/api/operaciones-pago/por-clave/:key', ({ params, request }) => {
      expect(params.key).toBe('00000000-0000-4000-8000-000000000003')
      expect(request.headers.get('Authorization')).toBe('Bearer memory-token')
      return HttpResponse.json({ idOperacion: 'operation', tipoOperacion: 'MEMBRESIA_INICIAL', estado: 'PENDING', idMembresia: null, idPago: null, idAplicacion: null, rechazoTipo: null, rechazoMensaje: null, resultado: null })
    }))

    await expect(listarSociosActivos(auth)).resolves.toHaveLength(1)
    await expect(registrarMembresia(auth, initial, '00000000-0000-4000-8000-000000000001')).resolves.toMatchObject({ idMembresia: 8, montoPrimerPago: 40 })
    await expect(registrarPagoPosterior(auth, 8, following, '00000000-0000-4000-8000-000000000002')).resolves.toMatchObject({ idMembresia: 8, saldoPendiente: 0 })
    await expect(consultarOperacionPorClave(auth, '00000000-0000-4000-8000-000000000003')).resolves.toMatchObject({ estado: 'PENDING' })
  })
})
