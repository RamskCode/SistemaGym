import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  actualizarConfiguracion,
  listarConfiguraciones,
  obtenerConfiguracion,
  validarPrecioPaseDiario,
} from '../api/administracion'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'admin-token', getSessionVersion: () => 7, logoutIfCurrentSession: () => false }
const configuration = { idConfiguracion: 4, clave: 'PRECIO_PASE_DIARIO', valor: '8.00', descripcion: 'Precio vigente de pase diario', fechaActualizacion: '2026-11-08T12:00:00' }

describe('API de configuración', () => {
  it('usa GET listado/detalle y PUT con el DTO { valor } y Bearer autenticado', async () => {
    const paths: string[] = []
    server.use(
      http.get('/api/configuraciones', ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer admin-token'); return HttpResponse.json([configuration]) }),
      http.get('/api/configuraciones/:clave', ({ request }) => { paths.push(new URL(request.url).pathname); return HttpResponse.json(configuration) }),
      http.put('/api/configuraciones/:clave', async ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer admin-token'); expect(await request.json()).toEqual({ valor: '9.25' }); return HttpResponse.json({ ...configuration, valor: '9.25' }) }),
    )
    await expect(listarConfiguraciones(auth)).resolves.toEqual([configuration])
    await expect(obtenerConfiguracion(auth, 'precio_pase_diario')).resolves.toEqual(configuration)
    await expect(actualizarConfiguracion(auth, 'PRECIO_PASE_DIARIO', '9.25')).resolves.toMatchObject({ valor: '9.25' })
    expect(paths).toEqual(['/api/configuraciones', '/api/configuraciones/PRECIO_PASE_DIARIO', '/api/configuraciones/PRECIO_PASE_DIARIO'])
  })

  it('valida PRECIO_PASE_DIARIO igual al backend y rechaza valores genéricos vacíos o mayores a 255', async () => {
    expect(validarPrecioPaseDiario('0')).toBe(false)
    expect(validarPrecioPaseDiario('-1')).toBe(false)
    expect(validarPrecioPaseDiario('1.001')).toBe(false)
    expect(validarPrecioPaseDiario('1e3')).toBe(true)
    expect(validarPrecioPaseDiario('1e-3')).toBe(false)
    expect(validarPrecioPaseDiario('0.01')).toBe(true)
    expect(validarPrecioPaseDiario('999999999999999999999999999999.99')).toBe(true)
    await expect(actualizarConfiguracion(auth, 'PRECIO_PASE_DIARIO', '0')).rejects.toThrow(/positivo/i)
    await expect(actualizarConfiguracion(auth, 'PRECIO_PASE_DIARIO', '3.555')).rejects.toThrow(/dos decimales/i)
    await expect(actualizarConfiguracion(auth, 'OTRA_CLAVE', ' ')).rejects.toThrow(/obligatorio/i)
    await expect(actualizarConfiguracion(auth, 'OTRA_CLAVE', 'x'.repeat(256))).rejects.toThrow(/255/)
  })

  it.each([400, 401, 403, 404, 409, 500])('mapea errores HTTP %i de actualización', async (status) => {
    server.use(http.put('/api/configuraciones/:clave', () => HttpResponse.json({ mensaje: 'falló' }, { status })))
    const code = status === 400 ? 'validation' : status === 401 ? 'unauthenticated' : status === 403 ? 'forbidden' : status === 404 ? 'not-found' : status === 409 ? 'conflict' : 'server'
    await expect(actualizarConfiguracion(auth, 'PRECIO_PASE_DIARIO', '9.00')).rejects.toMatchObject({ status, code })
  })
})
