import { ApiError, apiGet, type AuthenticatedRequestContext } from './http'

export type PuntoReporte = { etiqueta: string; cantidad: number; monto: number; referenciaId: number | null }
export type ReporteResumen = {
  ingresosConfirmados: number; pagosConfirmados: number; sociosNuevos: number
  membresiasActivas: number; membresiasVencidas: number; membresiasProximasAVencer: number
  ingresosSocios: number; ingresosVisitantes: number
  personasUnicas?: number; personasUnicasSocios?: number; personasUnicasVisitantes?: number
  responsables?: Array<{ id: number; nombre: string }>
  pagosPorFecha: PuntoReporte[]; sociosPorFecha: PuntoReporte[]
  ingresosPorFecha: PuntoReporte[]; membresiasPorPlan: PuntoReporte[]
}
export type ReporteFila = { id: number; tipo: string; persona: string; detalle: string; estado: string; monto: number | null; fecha: string; documentoProtegido?: string | null; modalidad?: string | null; operacion?: string | null; responsable?: string | null }
export type ReporteTipo = 'pagos' | 'socios' | 'membresias' | 'ingresos'
export type PlanReporte = { id: number; nombre: string }

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined) search.set(key, String(value)) })
  return search.toString()
}
function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function count(value: unknown): value is number {
  return Number.isSafeInteger(value) && Number(value) >= 0
}
function points(value: unknown): value is PuntoReporte[] {
  return Array.isArray(value) && value.every(point => record(point)
    && typeof point.etiqueta === 'string' && count(point.cantidad)
    && typeof point.monto === 'number' && Number.isFinite(point.monto)
    && (point.referenciaId === null || count(point.referenciaId)))
}
function resumen(value: unknown): value is ReporteResumen {
  return record(value)
    && typeof value.ingresosConfirmados === 'number' && Number.isFinite(value.ingresosConfirmados) && value.ingresosConfirmados >= 0
    && count(value.pagosConfirmados) && count(value.sociosNuevos)
    && count(value.membresiasActivas) && count(value.membresiasVencidas)
    && count(value.membresiasProximasAVencer) && count(value.ingresosSocios)
    && count(value.ingresosVisitantes) && points(value.pagosPorFecha)
    && (value.personasUnicas === undefined || count(value.personasUnicas))
    && (value.personasUnicasSocios === undefined || count(value.personasUnicasSocios))
    && (value.personasUnicasVisitantes === undefined || count(value.personasUnicasVisitantes))
    && (value.responsables === undefined || (Array.isArray(value.responsables)
      && value.responsables.every(item => record(item) && count(item.id) && typeof item.nombre === 'string')))
    && points(value.sociosPorFecha) && points(value.ingresosPorFecha)
    && points(value.membresiasPorPlan)
}
function detalle(value: unknown): value is ReporteFila[] {
  return Array.isArray(value) && value.every(row => record(row)
    && count(row.id) && typeof row.tipo === 'string' && typeof row.persona === 'string'
    && typeof row.detalle === 'string' && typeof row.estado === 'string'
    && (row.monto === null || (typeof row.monto === 'number' && Number.isFinite(row.monto)))
    && typeof row.fecha === 'string' && Number.isFinite(Date.parse(row.fecha))
    && (row.documentoProtegido === undefined || row.documentoProtegido === null || typeof row.documentoProtegido === 'string')
    && (row.modalidad === undefined || row.modalidad === null || typeof row.modalidad === 'string')
    && (row.operacion === undefined || row.operacion === null || typeof row.operacion === 'string')
    && (row.responsable === undefined || row.responsable === null || typeof row.responsable === 'string'))
}
function planes(value: unknown): value is PlanReporte[] {
  return Array.isArray(value) && value.every(plan => record(plan)
    && count(plan.id) && typeof plan.nombre === 'string')
}

export function obtenerResumenReportes(auth: AuthenticatedRequestContext, desde: string, hasta: string, planId?: number) {
  return apiGet<unknown>(`/api/reportes/resumen?${query({ desde, hasta, planId })}`, auth).then(value => {
    if (!resumen(value)) throw new ApiError('invalid-response')
    return value
  })
}
export function obtenerDetalleReporte(auth: AuthenticatedRequestContext, tipo: ReporteTipo, desde: string, hasta: string, pagina: number, planId?: number, filtros: { buscar?: string; modalidad?: string; responsableId?: number } = {}) {
  return apiGet<unknown>(`/api/reportes/detalle?${query({ tipo, desde, hasta, pagina, tamano: 25, planId, ...filtros })}`, auth).then(value => {
    if (!detalle(value)) throw new ApiError('invalid-response')
    return value
  })
}
export function obtenerPlanesReporte(auth: AuthenticatedRequestContext) {
  return apiGet<unknown>('/api/reportes/planes', auth).then(value => {
    if (!planes(value)) throw new ApiError('invalid-response')
    return value
  })
}
