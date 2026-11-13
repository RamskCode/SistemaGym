import { apiGet, apiPost, authenticatedApiRequest, ApiError, type AuthenticatedRequestContext } from './http'
import { formatPersonName } from '../utils/personName'

function normalizePerson(person: PersonaAsistencia): PersonaAsistencia {
  return { ...person, nombres: formatPersonName(person.nombres), apellidos: formatPersonName(person.apellidos) }
}

export type PersonalSeleccionable = {
  idEmpleado: number
  nombres: string
  apellidos: string
  rol: 'ENTRENADOR' | 'NUTRICIONISTA'
}

export type AsistenciaLaboral = {
  idAsistencia: number
  idEmpleado: number
  nombresEmpleado?: string | null
  apellidosEmpleado?: string | null
  rolEmpleado?: string | null
  fechaEntradaUtc: string
  fechaSalidaUtc: string | null
  fechaLaboral: string
  estado: 'ACTIVA' | 'ANULADA'
}

export type AsistenciaLaboralPagina = {
  contenido: AsistenciaLaboral[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export type ConsultaAsistenciasPropias = {
  page: number
  size: 10 | 20 | 50 | 100
  fechaDesde?: string
  fechaHasta?: string
}

export type ConsultaAsistenciasAdmin = ConsultaAsistenciasPropias & {
  estado?: 'ACTIVA' | 'ANULADA'
}

export type PersonaAsistencia = {
  idUsuario: number
  nombres: string
  apellidos: string
  correo: string
}

export type HistorialAsistenciaLaboral = AsistenciaLaboral & {
  empleado: PersonaAsistencia
  registradorEntrada: PersonaAsistencia | null
  registradorSalida: PersonaAsistencia | null
  eventos: { idEvento: number; tipoEvento: string; fechaHoraUtc: string; registrador: PersonaAsistencia }[]
  auditorias: {
    idAuditoria: number
    tipoOperacion: string
    motivo: string
    fechaHoraUtc: string
    actor: PersonaAsistencia
    estadoAnterior: 'ACTIVA' | 'ANULADA' | null
    estadoNuevo: 'ACTIVA' | 'ANULADA' | null
    fechaEntradaAnteriorUtc: string | null
    fechaSalidaAnteriorUtc: string | null
    fechaEntradaNuevaUtc: string | null
    fechaSalidaNuevaUtc: string | null
  }[]
}

export type CorreccionAsistenciaRequest = {
  fechaEntradaUtc?: string
  fechaSalidaUtc?: string
  motivo: string
}

export type AnulacionAsistenciaRequest = { motivo: string }

function validarMotivo(motivo: string) {
  if (!motivo.trim() || Array.from(motivo.trim()).length > 500) {
    throw new Error('El motivo es obligatorio y no puede superar los 500 caracteres.')
  }
}

function validarRespuestaAsistencia(value: unknown): AsistenciaLaboral {
  if (typeof value !== 'object' || value === null) throw new ApiError('invalid-response')
  const item = value as Partial<AsistenciaLaboral>
  if (!Number.isInteger(item.idAsistencia) || !Number.isInteger(item.idEmpleado)
      || typeof item.fechaEntradaUtc !== 'string' || Number.isNaN(Date.parse(item.fechaEntradaUtc))
      || !(item.fechaSalidaUtc === null || (typeof item.fechaSalidaUtc === 'string' && !Number.isNaN(Date.parse(item.fechaSalidaUtc))))
      || typeof item.fechaLaboral !== 'string' || !['ACTIVA', 'ANULADA'].includes(item.estado ?? '')) {
    throw new ApiError('invalid-response')
  }
  return item as AsistenciaLaboral
}

export async function corregirAsistenciaLaboral(
  auth: AuthenticatedRequestContext,
  idAsistencia: number,
  request: CorreccionAsistenciaRequest,
): Promise<AsistenciaLaboral> {
  if (!Number.isInteger(idAsistencia) || idAsistencia <= 0) throw new Error('La asistencia seleccionada no es válida.')
  validarMotivo(request.motivo)
  if (!request.fechaEntradaUtc && !request.fechaSalidaUtc) throw new Error('Indica al menos un horario para corregir.')
  for (const time of [request.fechaEntradaUtc, request.fechaSalidaUtc]) {
    if (time !== undefined && Number.isNaN(Date.parse(time))) throw new Error('Indica horarios válidos.')
  }
  const response = await authenticatedApiRequest<unknown>(`/api/asistencias-laborales/${idAsistencia}/correccion`, auth, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, true)
  return validarRespuestaAsistencia(response)
}

export async function anularAsistenciaLaboral(
  auth: AuthenticatedRequestContext,
  idAsistencia: number,
  request: AnulacionAsistenciaRequest,
): Promise<AsistenciaLaboral> {
  if (!Number.isInteger(idAsistencia) || idAsistencia <= 0) throw new Error('La asistencia seleccionada no es válida.')
  validarMotivo(request.motivo)
  const response = await authenticatedApiRequest<unknown>(`/api/asistencias-laborales/${idAsistencia}/anulacion`, auth, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, true)
  return validarRespuestaAsistencia(response)
}

export type ReporteAsistenciaEmpleado = {
  idEmpleado: number
  fechaDesde: string
  fechaHasta: string
  cantidadActivas: number
  cantidadAnuladas: number
  jornadasCerradas: number
  jornadasAbiertas: number
  totalHorasRegistradas: number
}

function parametrosConsulta(query: ConsultaAsistenciasAdmin) {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.fechaDesde) params.set('fechaDesde', query.fechaDesde)
  if (query.fechaHasta) params.set('fechaHasta', query.fechaHasta)
  if (query.estado) params.set('estado', query.estado)
  return params
}

export function consultarAsistenciasAdmin(
  auth: AuthenticatedRequestContext,
  query: ConsultaAsistenciasAdmin,
  idEmpleado?: number,
): Promise<AsistenciaLaboralPagina> {
  if (!Number.isInteger(query.page) || query.page < 0 || ![10, 20, 50, 100].includes(query.size)) {
    return Promise.reject(new Error('Parámetros de paginación inválidos.'))
  }
  if (idEmpleado !== undefined && (!Number.isInteger(idEmpleado) || idEmpleado <= 0)) {
    return Promise.reject(new Error('El empleado seleccionado no es válido.'))
  }
  const path = idEmpleado === undefined
    ? '/api/asistencias-laborales'
    : `/api/asistencias-laborales/empleado/${idEmpleado}`
  const requestPath = `${path}?${parametrosConsulta(query)}` as `/api/${string}`
  return apiGet<AsistenciaLaboralPagina>(requestPath, auth).then((result) => ({
    ...result,
    contenido: result.contenido.map((item) => ({
      ...item,
      nombresEmpleado: item.nombresEmpleado ? formatPersonName(item.nombresEmpleado) : item.nombresEmpleado,
      apellidosEmpleado: item.apellidosEmpleado ? formatPersonName(item.apellidosEmpleado) : item.apellidosEmpleado,
    })),
  }))
}

export function consultarHistorialAsistenciaRecepcionista(
  auth: AuthenticatedRequestContext,
  query: ConsultaAsistenciasAdmin,
  idEmpleado?: number,
): Promise<AsistenciaLaboralPagina> {
  if (!Number.isInteger(query.page) || query.page < 0 || ![10, 20, 50, 100].includes(query.size)) {
    return Promise.reject(new Error('Parámetros de paginación inválidos.'))
  }
  const params = parametrosConsulta(query)
  if (idEmpleado !== undefined) params.set('idEmpleado', String(idEmpleado))
  return apiGet<AsistenciaLaboralPagina>(`/api/asistencias-laborales/consulta-recepcionista?${params}`, auth).then((result) => ({
    ...result,
    contenido: result.contenido.map((item) => ({
      ...item,
      nombresEmpleado: item.nombresEmpleado ? formatPersonName(item.nombresEmpleado) : item.nombresEmpleado,
      apellidosEmpleado: item.apellidosEmpleado ? formatPersonName(item.apellidosEmpleado) : item.apellidosEmpleado,
    })),
  }))
}

export type EmpleadoConHistorial = {
  idEmpleado: number
  nombres: string
  apellidos: string
  rol: string
}

export function consultarHistorialAsistencia(
  auth: AuthenticatedRequestContext,
  idAsistencia: number,
): Promise<HistorialAsistenciaLaboral> {
  return apiGet<HistorialAsistenciaLaboral>(`/api/asistencias-laborales/${idAsistencia}/historial`, auth).then((history) => ({
    ...history,
    empleado: normalizePerson(history.empleado),
    registradorEntrada: history.registradorEntrada ? normalizePerson(history.registradorEntrada) : null,
    registradorSalida: history.registradorSalida ? normalizePerson(history.registradorSalida) : null,
    eventos: history.eventos.map((event) => ({ ...event, registrador: normalizePerson(event.registrador) })),
    auditorias: history.auditorias.map((audit) => ({ ...audit, actor: normalizePerson(audit.actor) })),
  }))
}

export function consultarReporteAsistenciaEmpleado(
  auth: AuthenticatedRequestContext,
  idEmpleado: number,
  fechaDesde: string,
  fechaHasta: string,
): Promise<ReporteAsistenciaEmpleado> {
  if (!Number.isInteger(idEmpleado) || idEmpleado <= 0 || !fechaDesde || !fechaHasta || fechaDesde > fechaHasta) {
    return Promise.reject(new Error('Selecciona un empleado y un rango de fechas válido.'))
  }
  const params = new URLSearchParams({ fechaDesde, fechaHasta })
  return apiGet<ReporteAsistenciaEmpleado>(`/api/asistencias-laborales/reportes/empleados/${idEmpleado}?${params}`, auth)
}

export function listarPersonalSeleccionable(
  auth: AuthenticatedRequestContext,
): Promise<PersonalSeleccionable[]> {
  return apiGet<PersonalSeleccionable[]>('/api/asistencias-laborales/personal-seleccionable', auth).then((people) => people.map((person) => ({
    ...person, nombres: formatPersonName(person.nombres), apellidos: formatPersonName(person.apellidos),
  })))
}

export function listarEmpleadosConHistorial(
  auth: AuthenticatedRequestContext,
): Promise<EmpleadoConHistorial[]> {
  return apiGet<EmpleadoConHistorial[]>('/api/asistencias-laborales/empleados-con-historial', auth).then((people) => people.map((person) => ({
    ...person,
    nombres: formatPersonName(person.nombres),
    apellidos: formatPersonName(person.apellidos),
  })))
}

export function consultarMisAsistencias(
  auth: AuthenticatedRequestContext,
  query: ConsultaAsistenciasPropias,
): Promise<AsistenciaLaboralPagina> {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) })
  if (query.fechaDesde) params.set('fechaDesde', query.fechaDesde)
  if (query.fechaHasta) params.set('fechaHasta', query.fechaHasta)
  return apiGet<AsistenciaLaboralPagina>(`/api/asistencias-laborales/mis-asistencias?${params}`, auth)
}

export function registrarEntrada(
  auth: AuthenticatedRequestContext,
  idEmpleado: number,
): Promise<AsistenciaLaboral> {
  return apiPost<unknown>('/api/asistencias-laborales/entrada', auth, { idEmpleado }, AbortSignal.timeout(30_000)).then(validarRespuestaAsistencia)
}

export function registrarSalida(
  auth: AuthenticatedRequestContext,
  idEmpleado: number,
): Promise<AsistenciaLaboral> {
  return apiPost<unknown>('/api/asistencias-laborales/salida', auth, { idEmpleado }, AbortSignal.timeout(30_000)).then(validarRespuestaAsistencia)
}

export function registrarMiEntrada(
  auth: AuthenticatedRequestContext,
): Promise<AsistenciaLaboral> {
  return authenticatedApiRequest<unknown>(
    '/api/asistencias-laborales/mi-entrada', auth, { method: 'POST', signal: AbortSignal.timeout(30_000) },
  ).then(validarRespuestaAsistencia)
}

export function registrarMiSalida(
  auth: AuthenticatedRequestContext,
): Promise<AsistenciaLaboral> {
  return authenticatedApiRequest<unknown>(
    '/api/asistencias-laborales/mi-salida', auth, { method: 'POST', signal: AbortSignal.timeout(30_000) },
  ).then(validarRespuestaAsistencia)
}
