import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { cambiarEstadoIncidencia, listarReportesIncidencia, obtenerReporteIncidenciaAdmin } from '../api/reportesIncidencias'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'admin-token', getSessionVersion: () => 5, logoutIfCurrentSession: () => false }
const report = { idReporte: 91, idEntrenador: 19, correoEntrenador: 'trainer@example.test', idElemento: 8, nombreElemento: 'Caminadora', idChecklist: 42, idDetalleChecklist: 7, descripcion: 'Cable flojo', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: '2026-11-06T07:00:00-05:00', fechaActualizacion: '2026-11-06T07:00:00-05:00', fechaSolucion: null, historial: [{ idHistorial: 11, idUsuario: 19, correoUsuario: 'trainer@example.test', estadoAnterior: null, estadoNuevo: 'PENDIENTE', observacion: null, fechaHora: '2026-11-06T07:00:00-05:00' }] }

describe('API administrativa de incidencias', () => {
  it('consulta listado global y detalle usando autenticación', async () => {
    const paths: string[] = []
    server.use(http.get('/api/reportes-incidencias', ({ request }) => {
      paths.push(new URL(request.url).pathname)
      expect(request.headers.get('Authorization')).toBe('Bearer admin-token')
      return HttpResponse.json([report])
    }))
    server.use(http.get('/api/reportes-incidencias/91', ({ request }) => {
      paths.push(new URL(request.url).pathname)
      expect(request.headers.get('Authorization')).toBe('Bearer admin-token')
      return HttpResponse.json(report)
    }))
    await expect(listarReportesIncidencia(auth)).resolves.toEqual([report])
    await expect(obtenerReporteIncidenciaAdmin(auth, 91)).resolves.toEqual(report)
    expect(paths).toEqual(['/api/reportes-incidencias', '/api/reportes-incidencias/91'])
  })

  it('envía PATCH exacto con estado y observación, sin reintento', async () => {
    let calls = 0
    server.use(http.patch('/api/reportes-incidencias/91/estado', async ({ request }) => {
      calls += 1
      expect(request.headers.get('Authorization')).toBe('Bearer admin-token')
      expect(request.headers.get('Content-Type')).toContain('application/json')
      expect(await request.json()).toEqual({ estado: 'EN_REVISION', observacion: 'Se deriva a mantenimiento.' })
      return HttpResponse.json({ ...report, estado: 'EN_REVISION', historial: [...report.historial, { idHistorial: 12, idUsuario: 1, correoUsuario: 'admin@example.test', estadoAnterior: 'PENDIENTE', estadoNuevo: 'EN_REVISION', observacion: 'Se deriva a mantenimiento.', fechaHora: '2026-11-06T08:00:00-05:00' }] })
    }))
    await expect(cambiarEstadoIncidencia(auth, 91, { estado: 'EN_REVISION', observacion: 'Se deriva a mantenimiento.' })).resolves.toMatchObject({ estado: 'EN_REVISION', historial: expect.arrayContaining([expect.objectContaining({ idHistorial: 12 })]) })
    expect(calls).toBe(1)
  })

  it.each([[400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict']] as const)(
    'mapea PATCH %i como %s sin reintentar', async (status, code) => {
      let calls = 0
      server.use(http.patch('/api/reportes-incidencias/91/estado', () => { calls += 1; return HttpResponse.json({}, { status }) }))
      await expect(cambiarEstadoIncidencia(auth, 91, { estado: 'EN_REVISION', observacion: 'Verificado' })).rejects.toMatchObject({ status, code })
      expect(calls).toBe(1)
    },
  )

  it('rechaza contrato local incompleto e invalida respuestas malformadas', async () => {
    await expect(cambiarEstadoIncidencia(auth, 91, { estado: 'EN_REVISION', observacion: '  ' })).rejects.toMatchObject({ code: 'validation' })
    server.use(http.get('/api/reportes-incidencias', () => HttpResponse.json({ data: [report] })))
    await expect(listarReportesIncidencia(auth)).rejects.toMatchObject({ code: 'invalid-response' })
  })

  it('clasifica fallo de red como incierto en cliente y rechaza respuesta de detalle inválida', async () => {
    server.use(http.patch('/api/reportes-incidencias/91/estado', () => HttpResponse.error()))
    await expect(cambiarEstadoIncidencia(auth, 91, { estado: 'EN_REVISION', observacion: 'Verificado' })).rejects.toMatchObject({ code: 'network' })
    server.use(http.get('/api/reportes-incidencias/91', () => HttpResponse.json({ idReporte: 91 })))
    await expect(obtenerReporteIncidenciaAdmin(auth, 91)).rejects.toMatchObject({ code: 'invalid-response' })
  })
})
