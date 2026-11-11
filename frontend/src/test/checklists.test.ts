import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { crearReporteIncidencia, listarElementosRevisionActivos, listarMisChecklists, listarMisReportesIncidencia, listarTodosChecklists, obtenerChecklist, obtenerReporteIncidencia, registrarChecklist, type CrearChecklistRequest } from '../api/checklists'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'trainer-token', getSessionVersion: () => 1, logoutIfCurrentSession: () => false }
const elemento = { idElemento: 8, nombre: 'Caminadora 1', tipo: 'MAQUINA', descripcion: 'Equipo cardiovascular', ubicacion: 'Sala principal', activo: true, fechaCreacion: '2026-01-01T10:00:00', fechaActualizacion: '2026-01-01T10:00:00' }
const respuesta = { idChecklist: 42, idEntrenador: 19, correoEntrenador: 'trainer@example.test', fechaHora: '2026-11-06T07:00:00-05:00', observaciones: null, detalles: [{ idDetalle: 71, idElemento: 8, nombreElemento: 'Caminadora 1', tipoElemento: 'MAQUINA', ubicacionElemento: 'Sala principal', resultado: 'CORRECTO', observaciones: null, fechaRevision: '2026-11-06T07:00:00-05:00', incidencia: null }] }
const reporte = { idReporte: 91, idEntrenador: 19, correoEntrenador: 'trainer@example.test', idElemento: 8, nombreElemento: 'Caminadora 1', idChecklist: null, idDetalleChecklist: null, descripcion: 'Banda desgastada', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: '2026-11-06T07:00:00-05:00', fechaActualizacion: '2026-11-06T07:00:00-05:00', fechaSolucion: null, historial: [{ idHistorial: 11, idUsuario: 19, correoUsuario: 'trainer@example.test', estadoAnterior: null, estadoNuevo: 'PENDIENTE', observacion: null, fechaHora: '2026-11-06T07:00:00-05:00' }] }

describe('API de checklist de revisión', () => {
  it('consulta los elementos activos con autenticación', async () => {
    server.use(http.get('/api/elementos-revision/activos', ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      return HttpResponse.json([elemento])
    }))
    await expect(listarElementosRevisionActivos(auth)).resolves.toEqual([elemento])
  })

  it('consulta solo los checklists propios y el detalle por su ID, con Bearer y sin ID de entrenador', async () => {
    const calls: string[] = []
    server.use(http.get('/api/checklists/mios', ({ request }) => {
      calls.push(new URL(request.url).pathname)
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      expect(new URL(request.url).search).toBe('')
      return HttpResponse.json([respuesta])
    }))
    server.use(http.get('/api/checklists/42', ({ request }) => {
      calls.push(new URL(request.url).pathname)
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      expect(new URL(request.url).search).toBe('')
      return HttpResponse.json(respuesta)
    }))

    await expect(listarMisChecklists(auth)).resolves.toEqual([respuesta])
    await expect(obtenerChecklist(auth, 42)).resolves.toEqual(respuesta)
    expect(calls).toEqual(['/api/checklists/mios', '/api/checklists/42'])
  })

  it('consulta el listado administrativo global sin parámetros y respeta el orden del backend', async () => {
    server.use(http.get('/api/checklists', ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      expect(new URL(request.url).search).toBe('')
      return HttpResponse.json([respuesta, { ...respuesta, idChecklist: 41 }])
    }))
    await expect(listarTodosChecklists(auth)).resolves.toMatchObject([
      { idChecklist: 42 }, { idChecklist: 41 },
    ])
  })

  it.each([[401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found']] as const)(
    'mapea el GET global de checklists HTTP %i a %s', async (status, code) => {
      server.use(http.get('/api/checklists', () => HttpResponse.json({}, { status })))
      await expect(listarTodosChecklists(auth)).rejects.toMatchObject({ status, code })
    },
  )

  it('consulta reportes propios y detalle autenticados sin parámetro de entrenador', async () => {
    const paths: string[] = []
    server.use(http.get('/api/reportes-incidencias/mios', ({ request }) => {
      paths.push(new URL(request.url).pathname)
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      expect(new URL(request.url).search).toBe('')
      return HttpResponse.json([reporte])
    }))
    server.use(http.get('/api/reportes-incidencias/91', ({ request }) => {
      paths.push(new URL(request.url).pathname)
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      return HttpResponse.json(reporte)
    }))
    await expect(listarMisReportesIncidencia(auth)).resolves.toEqual([reporte])
    await expect(obtenerReporteIncidencia(auth, 91)).resolves.toEqual(reporte)
    expect(paths).toEqual(['/api/reportes-incidencias/mios', '/api/reportes-incidencias/91'])
  })

  it('crea un reporte directo mediante un único POST con el DTO real y retorna historial/estado del servidor', async () => {
    let calls = 0
    server.use(http.post('/api/reportes-incidencias', async ({ request }) => {
      calls += 1
      expect(request.headers.get('Authorization')).toBe('Bearer trainer-token')
      expect(await request.json()).toEqual({ idElemento: 8, descripcion: 'Banda desgastada', prioridad: 'ALTA' })
      return HttpResponse.json(reporte, { status: 201 })
    }))
    await expect(crearReporteIncidencia(auth, { idElemento: 8, descripcion: 'Banda desgastada', prioridad: 'ALTA' }))
      .resolves.toEqual(reporte)
    expect(calls).toBe(1)
  })

  it.each([[400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict']] as const)(
    'maps direct report POST error %i to %s without retry', async (status, code) => {
      let calls = 0
      server.use(http.post('/api/reportes-incidencias', () => {
        calls += 1
        return HttpResponse.json({}, { status })
      }))
      await expect(crearReporteIncidencia(auth, { idElemento: 8, descripcion: 'Algo', prioridad: 'BAJA' }))
        .rejects.toMatchObject({ code, status })
      expect(calls).toBe(1)
    },
  )

  it.each([[401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found']] as const)(
    'maps own-report GET error %i to %s', async (status, code) => {
      server.use(http.get('/api/reportes-incidencias/mios', () => HttpResponse.json({}, { status })))
      await expect(listarMisReportesIncidencia(auth)).rejects.toMatchObject({ code, status })
    },
  )
  it.each([[401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found']] as const)(
    'maps report detail GET error %i to %s', async (status, code) => {
      server.use(http.get('/api/reportes-incidencias/91', () => HttpResponse.json({}, { status })))
      await expect(obtenerReporteIncidencia(auth, 91)).rejects.toMatchObject({ code, status })
    },
  )

  it.each([[401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found']] as const)(
    'maps checklist GET error %i to %s', async (status, code) => {
      server.use(http.get('/api/checklists/mios', () => HttpResponse.json({}, { status })))
      await expect(listarMisChecklists(auth)).rejects.toMatchObject({ code, status })
    },
  )

  it.each([[401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found']] as const)(
    'maps checklist detail error %i to %s', async (status, code) => {
      server.use(http.get('/api/checklists/42', () => HttpResponse.json({}, { status })))
      await expect(obtenerChecklist(auth, 42)).rejects.toMatchObject({ code, status })
    },
  )

  it('envía una sola solicitud al contrato real y devuelve el resultado real', async () => {
    const request: CrearChecklistRequest = {
      observaciones: 'Revisión de apertura',
      detalles: [
        { idElemento: 8, resultado: 'CORRECTO', observaciones: 'Sin ruido' },
        { idElemento: 12, resultado: 'CON_PROBLEMA', descripcionIncidencia: 'Cable desgastado', prioridadIncidencia: 'ALTA' },
      ],
    }
    let calls = 0
    server.use(http.post('/api/checklists', async ({ request: httpRequest }) => {
      calls += 1
      expect(httpRequest.headers.get('Authorization')).toBe('Bearer trainer-token')
      expect(httpRequest.headers.get('Content-Type')).toContain('application/json')
      expect(await httpRequest.json()).toEqual(request)
      return HttpResponse.json({ ...respuesta, detalles: [
        respuesta.detalles[0],
        { ...respuesta.detalles[0], idDetalle: 72, idElemento: 12, resultado: 'CON_PROBLEMA', incidencia: { idReporte: 91, descripcion: 'Cable desgastado', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: respuesta.fechaHora } },
      ] }, { status: 201 })
    }))
    const result = await registrarChecklist(auth, request)
    expect(calls).toBe(1)
    expect(result.detalles[0].resultado).toBe('CORRECTO')
    expect(result.detalles[0]).not.toHaveProperty('descripcionIncidencia')
    expect(result.detalles[0].incidencia).toBeNull()
    expect(result.detalles[1].incidencia).toMatchObject({ idReporte: 91, estado: 'PENDIENTE' })
  })

  it.each([[400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict']] as const)(
    'mapea respuesta HTTP %i a %s sin reintentar', async (status, code) => {
      let calls = 0
      server.use(http.post('/api/checklists', () => {
        calls += 1
        return HttpResponse.json({ mensaje: 'error' }, { status })
      }))
      await expect(registrarChecklist(auth, { detalles: [{ idElemento: 8, resultado: 'CORRECTO' }] }))
        .rejects.toMatchObject({ code, status })
      expect(calls).toBe(1)
    },
  )

  it('propaga fallos de red como error sin volver a enviar', async () => {
    let calls = 0
    server.use(http.post('/api/checklists', () => {
      calls += 1
      return HttpResponse.error()
    }))
    await expect(registrarChecklist(auth, { detalles: [{ idElemento: 8, resultado: 'CORRECTO' }] }))
      .rejects.toMatchObject({ code: 'network' })
    expect(calls).toBe(1)
  })
})
