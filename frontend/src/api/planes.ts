import { apiGet, authenticatedApiRequest, type AuthenticatedRequestContext } from './http'
import type { Plan } from './membresias'

export type { Plan }

export type PlanRequest = {
  nombre: string
  descripcion: string | null
  precio: number
  duracionDias: number
}

const WRITE_TIMEOUT_MS = 20_000

function validarId(id: number) {
  if (!Number.isInteger(id) || id <= 0) throw new Error('El ID del plan debe ser un entero positivo.')
}

export function listarPlanes(auth: AuthenticatedRequestContext): Promise<Plan[]> {
  return apiGet<Plan[]>('/api/planes', auth)
}

export function buscarPlanPorId(auth: AuthenticatedRequestContext, id: number): Promise<Plan> {
  validarId(id)
  return apiGet<Plan>(`/api/planes/${id}`, auth)
}

export function buscarPlanesPorNombre(auth: AuthenticatedRequestContext, nombre: string): Promise<Plan[]> {
  const params = new URLSearchParams({ nombre })
  return apiGet<Plan[]>(`/api/planes/buscar?${params}`, auth)
}

export function listarPlanesPorEstado(
  auth: AuthenticatedRequestContext,
  estado: 'ACTIVO' | 'INACTIVO',
): Promise<Plan[]> {
  return apiGet<Plan[]>(`/api/planes/estado/${estado}`, auth)
}

function write<T>(path: `/api/${string}`, auth: AuthenticatedRequestContext, method: 'POST' | 'PUT' | 'PATCH', body?: unknown): Promise<T> {
  return authenticatedApiRequest<T>(path, auth, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  })
}

export function crearPlan(auth: AuthenticatedRequestContext, body: PlanRequest): Promise<Plan> {
  return write<Plan>('/api/planes', auth, 'POST', body)
}

export function actualizarPlan(auth: AuthenticatedRequestContext, id: number, body: PlanRequest): Promise<Plan> {
  validarId(id)
  return write<Plan>(`/api/planes/${id}`, auth, 'PUT', body)
}

export function desactivarPlan(auth: AuthenticatedRequestContext, id: number): Promise<Plan> {
  validarId(id)
  return write<Plan>(`/api/planes/${id}/desactivar`, auth, 'PATCH')
}
