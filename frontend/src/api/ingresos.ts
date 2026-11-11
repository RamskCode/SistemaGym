import { apiGet, apiPost, type AuthenticatedRequestContext } from './http'

export type IngresoRequest = {
  tipoDocumento: 'DNI' | 'CE'
  numeroDocumento: string
  idSocio: number
  nombre: string
  apellidos: string
  celular: string | null
  correo: string | null
  metodoPago: 'EFECTIVO' | 'YAPE' | 'PLIN' | 'TRANSFERENCIA' | 'TARJETA' | 'OTRO'
  referencia: string | null
  claveIdempotencia: string
}

export type IngresoResult = {
  idIngreso: number | null
  tipoIngreso: 'SOCIO' | 'VISITANTE'
  idSocio: number | null
  idVisitante: number | null
  tipoDocumento: 'DNI' | 'CE'
  numeroDocumento: string
  fechaHora: string
  paseDiarioCobrado: boolean
  montoCobrado: number | null
  idPago: number | null
  resultado: string
}

export type PaseDiarioHistorial = {
  idIngreso: number
  fechaHora: string
  cobradoEnEsteAcceso: boolean
  idPaseDiario: number | null
  idPago: number | null
  montoPase: number | null
  metodoPago: string | null
}

export type EstadoIngresoMembresia = {
  idSocio: number
  codigoSocio: string
  tipoDocumento: 'DNI' | 'CE' | null
  numeroDocumento: string | null
  nombres: string
  apellidos: string
  estadoSocio: string
  autorizado: boolean
  motivo: string
  membresia: {
    idMembresia: number
    plan: string
    estado: string
    fechaInicio: string
    fechaVencimiento: string
  } | null
}

export type HistorialIngresoSocio = {
  idIngreso: number
  fechaHora: string
  modalidad: 'SOCIO' | 'VISITANTE'
}

export type PaginaHistorialIngresoSocio = {
  contenido: HistorialIngresoSocio[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export type PaginaSociosHabilitados = {
  contenido: EstadoIngresoMembresia[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export function paseDiarioV2Habilitado(auth: AuthenticatedRequestContext) {
  return apiGet<boolean>('/api/ingresos/pase-diario/habilitado', auth)
}

export function registrarIngreso(auth: AuthenticatedRequestContext, request: IngresoRequest) {
  return apiPost<IngresoResult>('/api/ingresos', auth, request, undefined, true)
}

export function listarPasesDiariosPorSocio(auth: AuthenticatedRequestContext, idSocio: number) {
  return apiGet<PaseDiarioHistorial[]>(`/api/ingresos/pase-diario/socio/${idSocio}`, auth)
}

export function evaluarIngresoMembresia(auth: AuthenticatedRequestContext, idSocio: number) {
  return apiGet<EstadoIngresoMembresia>(`/api/ingresos/socios/${idSocio}/evaluacion`, auth)
}

export function listarSociosHabilitados(
  auth: AuthenticatedRequestContext,
  filters: { nombre?: string; tipoDocumento?: 'DNI' | 'CE' | ''; numeroDocumento?: string } = {},
  page = 0,
  size = 20,
  sort = 'nombres,asc',
) {
  const params = new URLSearchParams({
    nombre: filters.nombre ?? '',
    tipoDocumento: filters.tipoDocumento ?? '',
    numeroDocumento: filters.numeroDocumento ?? '',
    page: String(page),
    size: String(size),
    sort,
  })
  return apiGet<PaginaSociosHabilitados>(`/api/ingresos/socios/habilitados?${params}`, auth)
}

export function listarHistorialIngresoSocio(
  auth: AuthenticatedRequestContext, idSocio: number, page = 0, size = 10,
) {
  return apiGet<PaginaHistorialIngresoSocio>(`/api/ingresos/socios/${idSocio}/historial?page=${page}&size=${size}`, auth)
}

export function registrarIngresoMembresia(
  auth: AuthenticatedRequestContext, idSocio: number, claveIdempotencia: string,
) {
  return apiPost<IngresoResult>('/api/ingresos/membresia', auth, { idSocio, claveIdempotencia }, undefined, true)
}
