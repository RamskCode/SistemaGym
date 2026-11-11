import { apiGet, apiPost, type AuthenticatedRequestContext } from './http'

export type TipoElementoRevision = 'MAQUINA' | 'AREA'
export type ResultadoChecklist = 'CORRECTO' | 'CON_PROBLEMA'
export type PrioridadIncidencia = 'BAJA' | 'MEDIA' | 'ALTA'

export type ElementoRevision = {
  idElemento: number
  nombre: string
  tipo: TipoElementoRevision
  descripcion: string | null
  ubicacion: string | null
  activo: boolean
  fechaCreacion: string
  fechaActualizacion: string
}

export type DetalleChecklistRequest = {
  idElemento: number
  resultado: ResultadoChecklist
  observaciones?: string
  descripcionIncidencia?: string
  prioridadIncidencia?: PrioridadIncidencia
}

export type CrearChecklistRequest = {
  detalles: DetalleChecklistRequest[]
  observaciones?: string
}

export type ReporteIncidenciaResumen = {
  idReporte: number
  descripcion: string
  prioridad: PrioridadIncidencia
  estado: 'PENDIENTE' | 'EN_REVISION' | 'SOLUCIONADO'
  fechaReporte: string
}

export type DetalleChecklistRespuesta = {
  idDetalle: number
  idElemento: number
  nombreElemento: string
  tipoElemento: TipoElementoRevision
  ubicacionElemento: string | null
  resultado: ResultadoChecklist
  observaciones: string | null
  fechaRevision: string
  incidencia: ReporteIncidenciaResumen | null
}

export type ChecklistSalaRespuesta = {
  idChecklist: number
  idEntrenador: number
  correoEntrenador: string
  fechaHora: string
  observaciones: string | null
  detalles: DetalleChecklistRespuesta[]
}

export type EstadoIncidencia = 'PENDIENTE' | 'EN_REVISION' | 'SOLUCIONADO'

export type HistorialIncidencia = {
  idHistorial: number
  idUsuario: number
  correoUsuario: string
  estadoAnterior: EstadoIncidencia | null
  estadoNuevo: EstadoIncidencia
  observacion: string | null
  fechaHora: string
}

export type ReporteIncidenciaRespuesta = {
  idReporte: number
  idEntrenador: number
  correoEntrenador: string
  idElemento: number
  nombreElemento: string
  idChecklist: number | null
  idDetalleChecklist: number | null
  descripcion: string
  prioridad: PrioridadIncidencia
  estado: EstadoIncidencia
  fechaReporte: string
  fechaActualizacion: string
  fechaSolucion: string | null
  historial: HistorialIncidencia[]
}

export type CrearReporteIncidenciaRequest = {
  idElemento: number
  descripcion: string
  prioridad: PrioridadIncidencia
}

export function listarElementosRevisionActivos(
  auth: AuthenticatedRequestContext,
): Promise<ElementoRevision[]> {
  return apiGet<ElementoRevision[]>('/api/elementos-revision/activos', auth)
}

export function registrarChecklist(
  auth: AuthenticatedRequestContext,
  request: CrearChecklistRequest,
): Promise<ChecklistSalaRespuesta> {
  return apiPost<ChecklistSalaRespuesta>(
    '/api/checklists', auth, request, AbortSignal.timeout(30_000),
  )
}

export function listarMisChecklists(
  auth: AuthenticatedRequestContext,
): Promise<ChecklistSalaRespuesta[]> {
  return apiGet<ChecklistSalaRespuesta[]>('/api/checklists/mios', auth)
}

/** Administrative global checklist query. The server returns all rows newest-first, without pagination. */
export function listarTodosChecklists(
  auth: AuthenticatedRequestContext,
): Promise<ChecklistSalaRespuesta[]> {
  return apiGet<ChecklistSalaRespuesta[]>('/api/checklists', auth)
}

export function obtenerChecklist(
  auth: AuthenticatedRequestContext,
  idChecklist: number,
): Promise<ChecklistSalaRespuesta> {
  return apiGet<ChecklistSalaRespuesta>(`/api/checklists/${idChecklist}`, auth)
}

export function listarMisReportesIncidencia(
  auth: AuthenticatedRequestContext,
): Promise<ReporteIncidenciaRespuesta[]> {
  return apiGet<ReporteIncidenciaRespuesta[]>('/api/reportes-incidencias/mios', auth)
}

export function obtenerReporteIncidencia(
  auth: AuthenticatedRequestContext,
  idReporte: number,
): Promise<ReporteIncidenciaRespuesta> {
  return apiGet<ReporteIncidenciaRespuesta>(`/api/reportes-incidencias/${idReporte}`, auth)
}

export function crearReporteIncidencia(
  auth: AuthenticatedRequestContext,
  request: CrearReporteIncidenciaRequest,
): Promise<ReporteIncidenciaRespuesta> {
  return apiPost<ReporteIncidenciaRespuesta>(
    '/api/reportes-incidencias', auth, request, AbortSignal.timeout(30_000),
  )
}
