import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  consultarMisAsistencias,
  corregirAsistenciaLaboral,
  anularAsistenciaLaboral,
  listarPersonalSeleccionable,
  registrarEntrada,
  registrarMiEntrada,
  registrarMiSalida,
  registrarSalida,
} from '../api/asistenciasLaborales'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'attendance-token', getSessionVersion: () => 4, logoutIfCurrentSession: () => false }
const attendance = { idAsistencia: 18, idEmpleado: 5, fechaEntradaUtc: '2026-11-06T04:59:00Z', fechaSalidaUtc: null, fechaLaboral: '2026-11-05', estado: 'ACTIVA' as const }

describe('API de asistencia laboral', () => {
  it('envía corrección PATCH con campos reales, motivo y horarios UTC; valida el DTO de respuesta', async () => {
    const payload = { fechaEntradaUtc: '2026-11-06T05:00:00.000Z', motivo: 'Ajuste aprobado' }
    server.use(http.patch('/api/asistencias-laborales/18/correccion', async ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer attendance-token')
      expect(await request.json()).toEqual(payload)
      return HttpResponse.json({ ...attendance, fechaEntradaUtc: payload.fechaEntradaUtc })
    }))
    await expect(corregirAsistenciaLaboral(auth, 18, payload)).resolves.toMatchObject({
      idAsistencia: 18, fechaEntradaUtc: payload.fechaEntradaUtc,
    })
  })

  it('envía anulación PATCH con solo motivo y devuelve el estado anulado', async () => {
    server.use(http.patch('/api/asistencias-laborales/18/anulacion', async ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer attendance-token')
      expect(await request.json()).toEqual({ motivo: 'Registro duplicado' })
      return HttpResponse.json({ ...attendance, estado: 'ANULADA' })
    }))
    await expect(anularAsistenciaLaboral(auth, 18, { motivo: 'Registro duplicado' })).resolves.toMatchObject({ estado: 'ANULADA' })
  })

  it('rechaza motivos inválidos, corrección vacía y respuestas de escritura inválidas antes de aceptar el resultado', async () => {
    let writes = 0
    server.use(http.patch('/api/asistencias-laborales/18/correccion', () => { writes += 1; return HttpResponse.json({}) }))
    await expect(corregirAsistenciaLaboral(auth, 18, { motivo: ' ', fechaEntradaUtc: '2026-11-06T05:00:00Z' })).rejects.toThrow(/motivo/i)
    await expect(corregirAsistenciaLaboral(auth, 18, { motivo: 'x'.repeat(501), fechaEntradaUtc: '2026-11-06T05:00:00Z' })).rejects.toThrow(/500/)
    await expect(corregirAsistenciaLaboral(auth, 18, { motivo: 'válido' })).rejects.toThrow(/horario/i)
    await expect(corregirAsistenciaLaboral(auth, 18, { motivo: 'válido', fechaEntradaUtc: 'no-date' })).rejects.toThrow(/horarios/i)
    await expect(corregirAsistenciaLaboral(auth, 18, { motivo: 'válido', fechaEntradaUtc: '2026-11-06T05:00:00Z' })).rejects.toMatchObject({ code: 'invalid-response' })
    expect(writes).toBe(1)
  })

  it.each([400, 401, 403, 404, 409, 500])('preserva errores HTTP %i de corrección sin reintentar', async (status) => {
    let calls = 0
    server.use(http.patch('/api/asistencias-laborales/18/correccion', () => { calls += 1; return HttpResponse.json({ mensaje: 'respuesta' }, { status }) }))
    await expect(corregirAsistenciaLaboral(auth, 18, { motivo: 'Ajuste', fechaEntradaUtc: '2026-11-06T05:00:00Z' })).rejects.toMatchObject({ status })
    expect(calls).toBe(1)
  })

  it('consulta personal limitado y envía token; realiza paginación con filtros reales', async () => {
    server.use(http.get('/api/asistencias-laborales/personal-seleccionable', ({ request }) => {
      expect(request.headers.get('Authorization')).toBe('Bearer attendance-token')
      return HttpResponse.json([{ idEmpleado: 5, nombres: 'Luis', apellidos: 'Ramos', rol: 'ENTRENADOR' }])
    }))
    server.use(http.get('/api/asistencias-laborales/mis-asistencias', ({ request }) => {
      const url = new URL(request.url)
      expect(url.searchParams.get('page')).toBe('1')
      expect(url.searchParams.get('size')).toBe('10')
      expect(url.searchParams.get('fechaDesde')).toBe('2026-11-01')
      expect(url.searchParams.get('fechaHasta')).toBe('2026-11-30')
      return HttpResponse.json({ contenido: [attendance], pagina: 1, tamano: 10, totalElementos: 11, totalPaginas: 2 })
    }))

    await expect(listarPersonalSeleccionable(auth)).resolves.toEqual([
      { idEmpleado: 5, nombres: 'Luis', apellidos: 'Ramos', rol: 'ENTRENADOR' },
    ])
    await expect(consultarMisAsistencias(auth, {
      page: 1, size: 10, fechaDesde: '2026-11-01', fechaHasta: '2026-11-30',
    })).resolves.toMatchObject({ pagina: 1, totalPaginas: 2 })
  })

  it('usa POST con idEmpleado solo para personal y POST sin cuerpo para marcación propia', async () => {
    const calls: string[] = []
    server.use(http.post('/api/asistencias-laborales/entrada', async ({ request }) => {
      calls.push('entrada')
      expect(await request.json()).toEqual({ idEmpleado: 5 })
      return HttpResponse.json(attendance, { status: 201 })
    }))
    server.use(http.post('/api/asistencias-laborales/salida', async ({ request }) => {
      calls.push('salida')
      expect(await request.json()).toEqual({ idEmpleado: 5 })
      return HttpResponse.json({ ...attendance, fechaSalidaUtc: '2026-11-06T05:30:00Z' })
    }))
    server.use(http.post('/api/asistencias-laborales/mi-entrada', ({ request }) => {
      calls.push('mi-entrada')
      expect(request.headers.get('Authorization')).toBe('Bearer attendance-token')
      expect(request.headers.has('Content-Type')).toBe(false)
      return HttpResponse.json(attendance, { status: 201 })
    }))
    server.use(http.post('/api/asistencias-laborales/mi-salida', ({ request }) => {
      calls.push('mi-salida')
      expect(request.headers.has('Content-Type')).toBe(false)
      return HttpResponse.json({ ...attendance, fechaSalidaUtc: '2026-11-06T05:30:00Z' })
    }))

    await expect(registrarEntrada(auth, 5)).resolves.toMatchObject({ idEmpleado: 5 })
    await expect(registrarSalida(auth, 5)).resolves.toMatchObject({ idEmpleado: 5 })
    await expect(registrarMiEntrada(auth)).resolves.toMatchObject({ idEmpleado: 5 })
    await expect(registrarMiSalida(auth)).resolves.toMatchObject({ fechaSalidaUtc: '2026-11-06T05:30:00Z' })
    expect(calls).toEqual(['entrada', 'salida', 'mi-entrada', 'mi-salida'])
  })

  it('rejects malformed successful attendance responses as unconfirmed writes', async () => {
    server.use(http.post('/api/asistencias-laborales/mi-entrada', () => HttpResponse.json({}, { status: 201 })))
    await expect(registrarMiEntrada(auth)).rejects.toMatchObject({ code: 'invalid-response' })
  })
})
