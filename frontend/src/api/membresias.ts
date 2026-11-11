import { apiGet, apiPost, type AuthenticatedRequestContext } from './http'
import type { Socio } from './socios'
import { formatPersonName } from '../utils/personName'

function normalizeSocio(socio: MembresiaSocio): MembresiaSocio {
  return { ...socio, nombres: formatPersonName(socio.nombres), apellidos: formatPersonName(socio.apellidos) }
}

function normalizeMembresia(membresia: Membresia): Membresia {
  return { ...membresia, socio: membresia.socio ? normalizeSocio(membresia.socio) : null }
}

export type MembresiaSocio = {
  idSocio: number
  codigoSocio: string
  dni: string | null
  tipoDocumento?: 'DNI' | 'CE' | null
  numeroDocumento?: string | null
  nombres: string
  apellidos: string
  fechaNacimiento: string | null
  celular: string | null
  correo: string | null
  estado: string
  fechaRegistro: string
}

export type Plan = {
  idPlan: number
  nombre: string
  descripcion: string | null
  precio: number
  duracionDias: number
  estado: string
  fechaCreacion: string
}

export type Membresia = {
  idMembresia: number
  socio: MembresiaSocio | null
  plan: Plan | null
  precioAcordado: number
  fechaInicio: string
  fechaVencimiento: string
  fechaLimitePago: string | null
  estado: string
  motivoCancelacion: string | null
  fechaCreacion: string
}

export type EstadoMembresia = {
  idMembresia: number
  estado: string
  situacion: string
  precioAcordado: number
  saldoPendiente: number
  pagada: boolean
  fechaInicio: string
  fechaVencimiento: string
  fechaLimitePago: string | null
  motivoCancelacion: string | null
  fechaCreacion: string
}

export type RegistrarMembresiaRequest = {
  idSocio: number
  idPlan: number
  montoPrimerPago: number
  metodoPago: string
  referencia: string | null
  observaciones: string | null
}

export type RegistroMembresiaRespuesta = {
  idMembresia: number
  idSocio: number
  idPlan: number
  precioAcordado: number
  fechaInicio: string
  fechaVencimiento: string
  fechaLimitePago: string | null
  estado: string
  idPago: number
  montoPrimerPago: number
  metodoPago: string
  idAplicacion: number
}

export type RegistrarPagoPosteriorRequest = {
  monto: number
  metodoPago: string
  referencia: string | null
  observaciones: string | null
}

export type PagoPosteriorMembresiaRespuesta = {
  idPago: number
  idAplicacion: number
  idMembresia: number
  montoPago: number
  montoAplicado: number
  metodoPago: string
  referencia: string | null
  observaciones: string | null
  fechaPago: string
  saldoPendiente: number
  estadoMembresia: string
}

export type OperacionPagoRespuesta = {
  idOperacion: string
  tipoOperacion: 'MEMBRESIA_INICIAL' | 'PAGO_POSTERIOR'
  estado: 'PENDING' | 'CONFIRMED' | 'REJECTED'
  idMembresia: number | null
  idPago: number | null
  idAplicacion: number | null
  rechazoTipo: string | null
  rechazoMensaje: string | null
  resultado: RegistroMembresiaRespuesta | PagoPosteriorMembresiaRespuesta | null
}

export function listarMembresias(auth: AuthenticatedRequestContext): Promise<Membresia[]> {
  return apiGet<Membresia[]>('/api/membresias', auth).then((items) => items.map(normalizeMembresia))
}

export function buscarMembresiaPorId(
  auth: AuthenticatedRequestContext,
  id: number,
): Promise<Membresia> {
  return apiGet<Membresia>(`/api/membresias/${id}`, auth).then(normalizeMembresia)
}

export function listarMembresiasPorSocio(
  auth: AuthenticatedRequestContext,
  idSocio: number,
): Promise<Membresia[]> {
  return apiGet<Membresia[]>(`/api/membresias/socio/${idSocio}`, auth).then((items) => items.map(normalizeMembresia))
}

export function obtenerEstadoMembresia(
  auth: AuthenticatedRequestContext,
  idMembresia: number,
): Promise<EstadoMembresia> {
  return apiGet<EstadoMembresia>(`/api/membresias/${idMembresia}/estado`, auth)
}

export function listarPlanesActivos(auth: AuthenticatedRequestContext): Promise<Plan[]> {
  return apiGet<Plan[]>('/api/planes/estado/ACTIVO', auth)
}

export function listarSociosActivos(auth: AuthenticatedRequestContext): Promise<Socio[]> {
  return apiGet<Socio[]>('/api/socios/estado/ACTIVO', auth).then((items) => items.map((socio) => ({
    ...socio, nombres: formatPersonName(socio.nombres), apellidos: formatPersonName(socio.apellidos),
  })))
}

export function registrarMembresia(
  auth: AuthenticatedRequestContext,
  body: RegistrarMembresiaRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<RegistroMembresiaRespuesta> {
  return apiPost<RegistroMembresiaRespuesta>('/api/membresias', auth, body, signal, false, { 'Idempotency-Key': idempotencyKey })
}

export function registrarPagoPosterior(
  auth: AuthenticatedRequestContext,
  idMembresia: number,
  body: RegistrarPagoPosteriorRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<PagoPosteriorMembresiaRespuesta> {
  return apiPost<PagoPosteriorMembresiaRespuesta>(`/api/membresias/${idMembresia}/pagos`, auth, body, signal, false, { 'Idempotency-Key': idempotencyKey })
}

export function consultarOperacionPorClave(auth: AuthenticatedRequestContext, key: string): Promise<OperacionPagoRespuesta> {
  return apiGet<OperacionPagoRespuesta>(`/api/operaciones-pago/por-clave/${encodeURIComponent(key)}`, auth)
}
