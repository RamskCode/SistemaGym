import { afterEach, describe, expect, it, vi } from 'vitest'
import { obtenerDetalleReporte, obtenerResumenReportes } from '../api/reportes'
import type { AuthenticatedRequestContext } from '../api/http'

const auth: AuthenticatedRequestContext = {
  getAccessToken: () => 'token', getSessionVersion: () => 1,
  logoutIfCurrentSession: () => false,
}
afterEach(() => vi.unstubAllGlobals())

describe('API de reportes', () => {
  it('envía el período y el plan al resumen protegido', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      ingresosConfirmados: 0, pagosConfirmados: 0, sociosNuevos: 0,
      membresiasActivas: 0, membresiasVencidas: 0, membresiasProximasAVencer: 0,
      ingresosSocios: 0, ingresosVisitantes: 0, personasUnicas: 0,
      personasUnicasSocios: 0, personasUnicasVisitantes: 0,
      pagosPorFecha: [], sociosPorFecha: [], ingresosPorFecha: [], membresiasPorPlan: [],
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await obtenerResumenReportes(auth, '2026-01-01', '2026-01-31', 7)
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe('/api/reportes/resumen?desde=2026-01-01&hasta=2026-01-31&planId=7')
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe('Bearer token')
  })

  it('solicita detalle con tipo y paginación', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await obtenerDetalleReporte(auth, 'pagos', '2026-02-01', '2026-02-28', 2)
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/reportes/detalle?tipo=pagos')
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('pagina=2&tamano=25')
  })
})
