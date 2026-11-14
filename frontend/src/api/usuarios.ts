import { ApiError, apiGet, authenticatedApiRequest, type AuthenticatedRequestContext } from './http'
import { formatPersonName } from '../utils/personName'

export type RolUsuario = 'ADMINISTRADOR' | 'RECEPCIONISTA' | 'ENTRENADOR' | 'NUTRICIONISTA'
export type TipoDocumentoUsuario = 'DNI' | 'CE'

export type Usuario = {
  idUsuario: number
  nombres: string
  apellidos: string
  correo: string
  rol: RolUsuario
  estado: string
  fechaCreacion: string
  tipoDocumento: TipoDocumentoUsuario | null
  numeroDocumento: string | null
}

export type CrearUsuarioRequest = {
  nombres: string
  apellidos: string
  correo: string
  rol: RolUsuario
  tipoDocumento: TipoDocumentoUsuario
  numeroDocumento: string
}
export type CredencialesIniciales = { usuario: Usuario; contrasenaInicial: string }
export type InvitacionCreada = { usuario: Usuario; correoEnviado: boolean; mensaje: string }

export type ActualizarUsuarioRequest = {
  nombres: string
  apellidos: string
  correo: string
  /** Omit both keys to preserve the existing document; send both null to clear it. */
  tipoDocumento?: TipoDocumentoUsuario | null
  numeroDocumento?: string | null
}

export type CambiarRolUsuarioRequest = { rol: RolUsuario }
export type CambiarContrasenaUsuarioRequest = { contrasena: string }

const WRITE_TIMEOUT_MS = 20_000
const ROLES: readonly RolUsuario[] = ['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA']

function parseUsuario(value: unknown): Usuario {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new ApiError('invalid-response')
  const record = value as Record<string, unknown>
  if (typeof record.idUsuario !== 'number' || !Number.isInteger(record.idUsuario)
    || typeof record.nombres !== 'string' || typeof record.apellidos !== 'string'
    || typeof record.correo !== 'string' || typeof record.rol !== 'string'
    || !ROLES.includes(record.rol as RolUsuario) || typeof record.estado !== 'string'
    || typeof record.fechaCreacion !== 'string'
    || !(record.tipoDocumento === null || record.tipoDocumento === 'DNI' || record.tipoDocumento === 'CE')
    || !(record.numeroDocumento === null || typeof record.numeroDocumento === 'string')) {
    throw new ApiError('invalid-response')
  }
  // Project only contracted public fields: never retain unexpected properties such as password hashes.
  return {
    idUsuario: record.idUsuario,
    nombres: formatPersonName(record.nombres),
    apellidos: formatPersonName(record.apellidos),
    correo: record.correo,
    rol: record.rol as RolUsuario,
    estado: record.estado,
    fechaCreacion: record.fechaCreacion,
    tipoDocumento: record.tipoDocumento as TipoDocumentoUsuario | null,
    numeroDocumento: record.numeroDocumento as string | null,
  }
}

function validarId(id: number) {
  if (!Number.isInteger(id) || id <= 0) throw new Error('El ID del usuario debe ser un entero positivo.')
}

export function listarUsuarios(auth: AuthenticatedRequestContext): Promise<Usuario[]> {
  return apiGet<unknown>('/api/usuarios', auth).then((value) => {
    if (!Array.isArray(value)) throw new ApiError('invalid-response')
    return value.map(parseUsuario)
  })
}

export function buscarUsuarioPorId(auth: AuthenticatedRequestContext, id: number): Promise<Usuario> {
  validarId(id)
  return apiGet<unknown>(`/api/usuarios/${id}`, auth).then(parseUsuario)
}

function write(path: `/api/${string}`, auth: AuthenticatedRequestContext, method: 'POST' | 'PUT', body: CrearUsuarioRequest | ActualizarUsuarioRequest) {
  return authenticatedApiRequest<unknown>(path, auth, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, nombres: formatPersonName(body.nombres), apellidos: formatPersonName(body.apellidos) }),
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then(parseUsuario)
}

export function crearUsuario(auth: AuthenticatedRequestContext, body: CrearUsuarioRequest): Promise<CredencialesIniciales> {
  return authenticatedApiRequest<unknown>('/api/usuarios', auth, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, nombres: formatPersonName(body.nombres), apellidos: formatPersonName(body.apellidos) }),
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then(parseCredencialesIniciales)
}

function parseCredencialesIniciales(value: unknown): CredencialesIniciales {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new ApiError('invalid-response')
  const record = value as Record<string, unknown>
  if (typeof record.contrasenaInicial !== 'string' || record.contrasenaInicial.length < 16) throw new ApiError('invalid-response')
  return { usuario: parseUsuario(record.usuario), contrasenaInicial: record.contrasenaInicial }
}

export function restablecerAcceso(auth: AuthenticatedRequestContext, id: number): Promise<CredencialesIniciales> {
  validarId(id)
  return authenticatedApiRequest<unknown>(`/api/usuarios/${id}/restablecer-acceso`, auth, {
    method: 'POST', signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then(parseCredencialesIniciales)
}

export function reenviarInvitacion(auth: AuthenticatedRequestContext, id: number): Promise<InvitacionCreada> {
  validarId(id)
  return authenticatedApiRequest<unknown>(`/api/usuarios/${id}/invitacion`, auth, {
    method: 'POST', signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then((value) => {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new ApiError('invalid-response')
    const record = value as Record<string, unknown>
    if (typeof record.correoEnviado !== 'boolean' || typeof record.mensaje !== 'string') throw new ApiError('invalid-response')
    return { usuario: parseUsuario(record.usuario), correoEnviado: record.correoEnviado, mensaje: record.mensaje }
  })
}

export function actualizarUsuario(auth: AuthenticatedRequestContext, id: number, body: ActualizarUsuarioRequest): Promise<Usuario> {
  validarId(id)
  return write(`/api/usuarios/${id}`, auth, 'PUT', body)
}

export function cambiarRolUsuario(auth: AuthenticatedRequestContext, id: number, rol: RolUsuario): Promise<Usuario> {
  validarId(id)
  return authenticatedApiRequest<unknown>(`/api/usuarios/${id}/rol`, auth, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rol } satisfies CambiarRolUsuarioRequest),
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then(parseUsuario)
}

export function cambiarEstadoUsuario(auth: AuthenticatedRequestContext, id: number, activo: boolean): Promise<Usuario> {
  validarId(id)
  return authenticatedApiRequest<unknown>(`/api/usuarios/${id}/${activo ? 'activar' : 'desactivar'}`, auth, {
    method: 'PATCH',
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then(parseUsuario)
}

export function cambiarContrasenaUsuario(auth: AuthenticatedRequestContext, id: number, contrasena: string): Promise<void> {
  validarId(id)
  return authenticatedApiRequest<undefined>(`/api/usuarios/${id}/contrasena`, auth, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contrasena } satisfies CambiarContrasenaUsuarioRequest),
    signal: AbortSignal.timeout(WRITE_TIMEOUT_MS),
  }).then((result) => {
    if (result !== undefined) throw new ApiError('invalid-response')
  })
}
