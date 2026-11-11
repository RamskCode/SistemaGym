import { ApiError, apiGet, authenticatedApiRequest, type AuthenticatedRequestContext } from './http'

export type Configuracion = {
  idConfiguracion: number
  clave: string
  valor: string
  descripcion: string | null
  fechaActualizacion: string
}

export const CONFIGURACION_EDITABLE = 'PRECIO_PASE_DIARIO'

function validarConfiguracion(value: unknown): Configuracion {
  if (typeof value !== 'object' || value === null) throw new ApiError('invalid-response')
  const item = value as Partial<Configuracion>
  if (!Number.isInteger(item.idConfiguracion) || typeof item.clave !== 'string'
      || typeof item.valor !== 'string'
      || !(item.descripcion === null || typeof item.descripcion === 'string')
      || typeof item.fechaActualizacion !== 'string' || Number.isNaN(Date.parse(item.fechaActualizacion))) {
    throw new ApiError('invalid-response')
  }
  return item as Configuracion
}

export function validarPrecioPaseDiario(value: string): boolean {
  const normalized = value.trim()
  if (!normalized || normalized.length > 255) return false
  const match = normalized.match(/^\+?(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/)
  if (!match) return false
  const integerPart = match[1] ?? ''
  const decimalPart = match[2] ?? ''
  if (!integerPart && !decimalPart) return false
  const exponent = match[3] === undefined ? 0 : Number(match[3])
  if (!Number.isSafeInteger(exponent)) return false
  const digits = `${integerPart}${decimalPart}`
  if (!/[1-9]/.test(digits)) return false
  const trailingZeros = digits.match(/0+$/)?.[0].length ?? 0
  const strippedScale = decimalPart.length - exponent - trailingZeros
  return strippedScale <= 2 && strippedScale >= -2_147_483_648
}

export async function listarConfiguraciones(auth: AuthenticatedRequestContext): Promise<Configuracion[]> {
  const result = await apiGet<unknown>('/api/configuraciones', auth)
  if (!Array.isArray(result)) throw new ApiError('invalid-response')
  return result.map(validarConfiguracion)
}

export async function obtenerConfiguracion(auth: AuthenticatedRequestContext, clave: string): Promise<Configuracion> {
  const key = clave.trim().toUpperCase()
  if (!key) throw new Error('La clave de configuración es obligatoria.')
  const configuration = validarConfiguracion(await apiGet<unknown>(`/api/configuraciones/${encodeURIComponent(key)}`, auth))
  if (configuration.clave !== key) throw new ApiError('invalid-response')
  return configuration
}

export async function actualizarConfiguracion(
  auth: AuthenticatedRequestContext,
  clave: string,
  valor: string,
): Promise<Configuracion> {
  const key = clave.trim().toUpperCase()
  const normalized = valor.trim()
  if (!key || !normalized || normalized.length > 255) {
    throw new Error('El valor es obligatorio y admite hasta 255 caracteres.')
  }
  if (key === CONFIGURACION_EDITABLE && !validarPrecioPaseDiario(normalized)) {
    throw new Error('El precio debe ser positivo, tener hasta dos decimales y no superar 255 caracteres.')
  }
  const result = await authenticatedApiRequest<unknown>(`/api/configuraciones/${encodeURIComponent(key)}`, auth, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ valor: normalized }),
  }, true)
  const configuration = validarConfiguracion(result)
  if (configuration.clave !== key) throw new ApiError('invalid-response')
  return configuration
}
