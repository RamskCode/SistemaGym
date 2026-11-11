import { apiGet, apiPatch, apiPost, apiPut, type AuthenticatedRequestContext } from './http'
import { formatPersonName } from '../utils/personName'

export type Socio = {
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
  estado: 'ACTIVO' | 'INACTIVO'
  fechaRegistro: string
}

export type SocioResumenNutricion = Pick<
  Socio,
  'idSocio' | 'codigoSocio' | 'nombres' | 'apellidos' | 'estado'
>

export type SocioRequest = {
  dni: string | null
  tipoDocumento: 'DNI' | 'CE' | null
  numeroDocumento: string | null
  nombres: string
  apellidos: string
  fechaNacimiento: string | null
  celular: string | null
  correo: string | null
}

function normalizeSocio(socio: Socio): Socio {
  return { ...socio, nombres: formatPersonName(socio.nombres), apellidos: formatPersonName(socio.apellidos) }
}

function normalizeNameRequest(data: SocioRequest): SocioRequest {
  return { ...data, nombres: formatPersonName(data.nombres), apellidos: formatPersonName(data.apellidos) }
}

export async function listarSocios(
  auth: AuthenticatedRequestContext,
  { query = '', estado = 'TODOS' }: { query?: string; estado?: 'TODOS' | Socio['estado'] } = {},
): Promise<Socio[]> {
  const termino = query.trim()
  let socios: Socio[]

  if (termino) {
    socios = await apiGet<Socio[]>(`/api/socios/buscar?q=${encodeURIComponent(termino)}`, auth)
  } else if (estado !== 'TODOS') {
    socios = await apiGet<Socio[]>(`/api/socios/estado/${estado}`, auth)
    return socios.map(normalizeSocio)
  } else {
    socios = await apiGet<Socio[]>('/api/socios', auth)
  }

  return (estado === 'TODOS' ? socios : socios.filter((socio) => socio.estado === estado)).map(normalizeSocio)
}

/** Socio DTO reducido entregado al rol NUTRICIONISTA. */
export async function listarSociosNutricion(
  auth: AuthenticatedRequestContext,
  { query = '', estado = 'TODOS' }: {
    query?: string
    estado?: 'TODOS' | SocioResumenNutricion['estado']
  } = {},
): Promise<SocioResumenNutricion[]> {
  const termino = query.trim()
  if (termino) {
    const socios = await apiGet<SocioResumenNutricion[]>(
      `/api/socios/buscar?q=${encodeURIComponent(termino)}`, auth,
    )
    return (estado === 'TODOS' ? socios : socios.filter((socio) => socio.estado === estado)).map((socio) => ({
      ...socio, nombres: formatPersonName(socio.nombres), apellidos: formatPersonName(socio.apellidos),
    }))
  }

  const path: `/api/${string}` = estado === 'TODOS' ? '/api/socios' : `/api/socios/estado/${estado}`
  return apiGet<SocioResumenNutricion[]>(path, auth).then((socios) => socios.map((socio) => ({
    ...socio, nombres: formatPersonName(socio.nombres), apellidos: formatPersonName(socio.apellidos),
  })))
}

export function buscarResumenSocioNutricion(
  auth: AuthenticatedRequestContext,
  id: number,
): Promise<SocioResumenNutricion> {
  return apiGet<SocioResumenNutricion>(`/api/socios/${id}`, auth).then((socio) => ({
    ...socio, nombres: formatPersonName(socio.nombres), apellidos: formatPersonName(socio.apellidos),
  }))
}

export function buscarSocioPorId(auth: AuthenticatedRequestContext, id: number): Promise<Socio> {
  return apiGet<Socio>(`/api/socios/${id}`, auth).then(normalizeSocio)
}

export function registrarSocio(auth: AuthenticatedRequestContext, datos: SocioRequest): Promise<Socio> {
  return apiPost<Socio>('/api/socios', auth, normalizeNameRequest(datos)).then(normalizeSocio)
}

export function actualizarSocio(
  auth: AuthenticatedRequestContext,
  id: number,
  datos: SocioRequest,
): Promise<Socio> {
  return apiPut<Socio>(`/api/socios/${id}`, auth, normalizeNameRequest(datos)).then(normalizeSocio)
}

export function cambiarEstadoSocio(
  auth: AuthenticatedRequestContext,
  id: number,
  estado: Socio['estado'],
  motivo: string,
): Promise<Socio> {
  const accion = estado === 'ACTIVO' ? 'activar' : 'desactivar'
  return apiPatch<Socio>(`/api/socios/${id}/${accion}`, auth, { motivo }).then(normalizeSocio)
}
