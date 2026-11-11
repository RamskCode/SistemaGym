import { apiGet, type AuthenticatedRequestContext } from './http'
import { ApiError } from './http'
import { formatPersonName } from '../utils/personName'

export type DashboardSummary = {
  sociosActivos: number
  ingresosMes: number
  ingresosCobradosHoy: number
  ingresosHoy: number
  ingresosMembresiaHoy: number
  ingresosPaseDiarioHoy: number
  sociosNuevosMes: number
  ingresosSemana: { fecha: string; cantidad: number }[]
  actividadReciente: { idIngreso: number; nombre: string; tipo: 'SOCIO' | 'VISITANTE'; fechaHora: string }[]
  membresias: { activas: number; porPagar: number; vencidas: number; canceladas: number }
}

export function getDashboardSummary(auth: AuthenticatedRequestContext) {
  return apiGet<unknown>('/api/dashboard/resumen', auth).then((value) => {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new ApiError('invalid-response')
    const data = value as Record<string, unknown>
    const validCounts = ['sociosActivos', 'ingresosHoy', 'ingresosMembresiaHoy', 'ingresosPaseDiarioHoy', 'sociosNuevosMes'].every((key) => Number.isSafeInteger(data[key]) && Number(data[key]) >= 0)
    if (!validCounts || typeof data.ingresosMes !== 'number' || !Number.isFinite(data.ingresosMes) || data.ingresosMes < 0
      || typeof data.ingresosCobradosHoy !== 'number' || !Number.isFinite(data.ingresosCobradosHoy) || data.ingresosCobradosHoy < 0
      || !Array.isArray(data.ingresosSemana) || data.ingresosSemana.some((item) =>
        typeof item !== 'object' || item === null || typeof item.fecha !== 'string'
        || !/^\d{4}-\d{2}-\d{2}$/.test(item.fecha)
        || !Number.isSafeInteger(item.cantidad) || Number(item.cantidad) < 0)
      || !Array.isArray(data.actividadReciente)
      || typeof data.membresias !== 'object' || data.membresias === null) throw new ApiError('invalid-response')
    const membership = data.membresias as Record<string, unknown>
    if (!['activas', 'porPagar', 'vencidas', 'canceladas'].every((key) => Number.isSafeInteger(membership[key]) && Number(membership[key]) >= 0)) throw new ApiError('invalid-response')
    return {
      ...(data as unknown as DashboardSummary),
      actividadReciente: (data.actividadReciente as DashboardSummary['actividadReciente']).map((entry) => {
        if (!entry || !Number.isSafeInteger(entry.idIngreso) || typeof entry.nombre !== 'string'
          || (entry.tipo !== 'SOCIO' && entry.tipo !== 'VISITANTE') || typeof entry.fechaHora !== 'string') throw new ApiError('invalid-response')
        return { ...entry, nombre: formatPersonName(entry.nombre) }
      }),
    }
  })
}
