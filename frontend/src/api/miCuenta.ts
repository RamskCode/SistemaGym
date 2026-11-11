import { ApiError, apiGet, authenticatedApiRequest, type AuthenticatedRequestContext } from './http'

export type TipoDocumentoCuenta = 'DNI' | 'CE'
export type MiCuenta = {
  nombres: string
  apellidos: string
  correo: string
  rol: string
  tipoDocumento: TipoDocumentoCuenta | null
  numeroDocumento: string | null
}
export type ActualizarMiCuenta = Partial<Pick<MiCuenta,
  'nombres' | 'apellidos' | 'tipoDocumento' | 'numeroDocumento'>>
export type CambiarMiContrasena = { contrasenaActual: string; contrasenaNueva: string }

function parseProfile(value: unknown): MiCuenta {
  if (typeof value !== 'object' || value === null) throw new ApiError('invalid-response')
  const profile = value as Partial<MiCuenta>
  if (typeof profile.nombres !== 'string' || typeof profile.apellidos !== 'string'
      || typeof profile.correo !== 'string' || typeof profile.rol !== 'string'
      || !(profile.tipoDocumento === null || profile.tipoDocumento === 'DNI' || profile.tipoDocumento === 'CE')
      || !(profile.numeroDocumento === null || typeof profile.numeroDocumento === 'string')) {
    throw new ApiError('invalid-response')
  }
  return profile as MiCuenta
}

export async function obtenerMiCuenta(auth: AuthenticatedRequestContext): Promise<MiCuenta> {
  return parseProfile(await apiGet<unknown>('/api/mi-cuenta', auth))
}

export async function actualizarMiCuenta(
  auth: AuthenticatedRequestContext, changes: ActualizarMiCuenta,
): Promise<MiCuenta> {
  return parseProfile(await authenticatedApiRequest<unknown>('/api/mi-cuenta', auth, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(changes),
  }, true))
}

export async function cambiarMiContrasena(
  auth: AuthenticatedRequestContext, change: CambiarMiContrasena,
): Promise<void> {
  await authenticatedApiRequest<void>('/api/mi-cuenta/contrasena', auth, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(change),
  })
}
