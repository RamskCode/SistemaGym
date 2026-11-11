import { apiGet, authenticatedApiRequest, type AuthenticatedRequestContext } from './http'
import type { ElementoRevision, TipoElementoRevision } from './checklists'

export type { ElementoRevision, TipoElementoRevision }
export type ElementoRevisionRequest = {
  nombre: string
  tipo: TipoElementoRevision
  descripcion: string | null
  ubicacion: string | null
}

const WRITE_TIMEOUT_MS = 20_000

function validarId(id: number) {
  if (!Number.isInteger(id) || id <= 0) throw new Error('El ID del elemento debe ser un entero positivo.')
}

export function listarElementosRevision(auth: AuthenticatedRequestContext): Promise<ElementoRevision[]> {
  return apiGet('/api/elementos-revision', auth)
}

export function listarElementosRevisionActivos(auth: AuthenticatedRequestContext): Promise<ElementoRevision[]> {
  return apiGet('/api/elementos-revision/activos', auth)
}

export function buscarElementoRevisionPorId(auth: AuthenticatedRequestContext, id: number): Promise<ElementoRevision> {
  validarId(id)
  return apiGet(`/api/elementos-revision/${id}`, auth)
}

function write(path: `/api/${string}`, auth: AuthenticatedRequestContext, method: 'POST' | 'PUT' | 'PATCH', body?: unknown) {
  return authenticatedApiRequest<ElementoRevision>(path, auth, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  })
}

export function crearElementoRevision(auth: AuthenticatedRequestContext, body: ElementoRevisionRequest) {
  return write('/api/elementos-revision', auth, 'POST', body)
}

export function actualizarElementoRevision(auth: AuthenticatedRequestContext, id: number, body: ElementoRevisionRequest) {
  validarId(id)
  return write(`/api/elementos-revision/${id}`, auth, 'PUT', body)
}

export function cambiarEstadoElementoRevision(auth: AuthenticatedRequestContext, id: number, activo: boolean) {
  validarId(id)
  return write(`/api/elementos-revision/${id}/${activo ? 'activar' : 'desactivar'}`, auth, 'PATCH')
}
