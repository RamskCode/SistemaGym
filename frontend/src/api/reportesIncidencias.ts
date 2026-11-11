import { ApiError, apiGet, apiPatch, type AuthenticatedRequestContext } from './http'
import type { EstadoIncidencia, ReporteIncidenciaRespuesta } from './checklists'

export type CambiarEstadoIncidenciaRequest = {
  estado: EstadoIncidencia
  observacion: string
}

const ESTADOS: readonly EstadoIncidencia[] = ['PENDIENTE', 'EN_REVISION', 'SOLUCIONADO']

function validarId(id: number) {
  if (!Number.isSafeInteger(id) || id <= 0) throw new ApiError('validation')
}

function validarReporte(value: unknown): ReporteIncidenciaRespuesta {
  if (typeof value !== 'object' || value === null) throw new ApiError('invalid-response')
  const item = value as Partial<ReporteIncidenciaRespuesta>
  const fechaValida = (date: unknown) => typeof date === 'string' && !Number.isNaN(Date.parse(date))
  const historialValido = Array.isArray(item.historial) && item.historial.every((event) =>
    typeof event.idHistorial === 'number' && typeof event.idUsuario === 'number'
      && typeof event.correoUsuario === 'string' && ESTADOS.includes(event.estadoNuevo)
      && (event.estadoAnterior === null || ESTADOS.includes(event.estadoAnterior))
      && fechaValida(event.fechaHora)
      && (event.observacion === null || typeof event.observacion === 'string'))
  if (!Number.isSafeInteger(item.idReporte) || (item.idReporte ?? 0) <= 0
      || !Number.isSafeInteger(item.idEntrenador) || !Number.isSafeInteger(item.idElemento)
      || typeof item.correoEntrenador !== 'string' || typeof item.nombreElemento !== 'string'
      || !(item.idChecklist === null || typeof item.idChecklist === 'number')
      || !(item.idDetalleChecklist === null || typeof item.idDetalleChecklist === 'number')
      || typeof item.descripcion !== 'string' || !['BAJA', 'MEDIA', 'ALTA'].includes(item.prioridad ?? '')
      || !ESTADOS.includes(item.estado as EstadoIncidencia)
      || !fechaValida(item.fechaReporte) || !fechaValida(item.fechaActualizacion)
      || !(item.fechaSolucion === null || fechaValida(item.fechaSolucion)) || !historialValido) {
    throw new ApiError('invalid-response')
  }
  return item as ReporteIncidenciaRespuesta
}

export async function listarReportesIncidencia(
  auth: AuthenticatedRequestContext,
): Promise<ReporteIncidenciaRespuesta[]> {
  const result: unknown = await apiGet('/api/reportes-incidencias', auth)
  if (!Array.isArray(result)) throw new ApiError('invalid-response')
  return result.map(validarReporte)
}

export async function obtenerReporteIncidenciaAdmin(
  auth: AuthenticatedRequestContext,
  idReporte: number,
): Promise<ReporteIncidenciaRespuesta> {
  validarId(idReporte)
  return validarReporte(await apiGet<unknown>(`/api/reportes-incidencias/${idReporte}`, auth))
}

export async function cambiarEstadoIncidencia(
  auth: AuthenticatedRequestContext,
  idReporte: number,
  request: CambiarEstadoIncidenciaRequest,
): Promise<ReporteIncidenciaRespuesta> {
  validarId(idReporte)
  if (!ESTADOS.includes(request.estado) || !request.observacion.trim() || request.observacion.length > 2000) {
    throw new ApiError('validation')
  }
  return validarReporte(await apiPatch<unknown>(`/api/reportes-incidencias/${idReporte}/estado`, auth, request))
}
