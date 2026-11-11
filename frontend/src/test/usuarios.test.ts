import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { actualizarUsuario, buscarUsuarioPorId, cambiarContrasenaUsuario, cambiarEstadoUsuario, cambiarRolUsuario, crearUsuario, listarUsuarios, type CrearUsuarioRequest } from '../api/usuarios'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'users-token', getSessionVersion: () => 4, logoutIfCurrentSession: () => false }
const user = { idUsuario: 16, nombres: 'Lucía', apellidos: 'Soto', correo: 'lucia@example.test', rol: 'ENTRENADOR', estado: 'ACTIVO', fechaCreacion: '2026-04-01T10:00:00', tipoDocumento: 'DNI', numeroDocumento: '12345678' }
const create: CrearUsuarioRequest = { nombres: 'Lucía', apellidos: 'Soto', correo: 'lucia@example.test', rol: 'ENTRENADOR', tipoDocumento: 'DNI', numeroDocumento: '12345678' }
afterEach(() => vi.restoreAllMocks())

describe('API de usuarios', () => {
  it('consume GET list/detail autenticados y solo proyecta campos públicos', async () => {
    const paths: string[] = []
    const responseWithHash = { ...user, contrasenaHash: '$2a$secret', contrasena: 'secret' }
    server.use(http.get('/api/usuarios', ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer users-token'); return HttpResponse.json([responseWithHash]) }))
    server.use(http.get('/api/usuarios/16', ({ request }) => { paths.push(new URL(request.url).pathname); expect(request.headers.get('Authorization')).toBe('Bearer users-token'); return HttpResponse.json(responseWithHash) }))
    const [listed] = await listarUsuarios(auth)
    expect(listed).toEqual(user)
    expect('contrasenaHash' in listed).toBe(false)
    expect(await buscarUsuarioPorId(auth, 16)).toEqual(user)
    expect(paths).toEqual(['/api/usuarios', '/api/usuarios/16'])
  })

  it('envía POST y PUT con DTOs exactos y soporta omitir documento al actualizar', async () => {
    server.use(http.post('/api/usuarios', async ({ request }) => { expect(await request.json()).toEqual(create); return HttpResponse.json({ usuario: user, correoEnviado: true, mensaje: 'invitación enviada' }, { status: 201 }) }))
    server.use(http.put('/api/usuarios/16', async ({ request }) => { expect(await request.json()).toEqual({ nombres: 'Lucía', apellidos: 'Soto', correo: 'lucia@example.test' }); return HttpResponse.json(user) }))
    await expect(crearUsuario(auth, create)).resolves.toEqual({ usuario: user, correoEnviado: true, mensaje: 'invitación enviada' })
    await expect(actualizarUsuario(auth, 16, { nombres: 'Lucía', apellidos: 'Soto', correo: 'lucia@example.test' })).resolves.toEqual(user)
  })

  it('uses the exact role/status/password PATCH contracts and enforces 204 for password changes', async () => {
    server.use(http.patch('/api/usuarios/16/rol', async ({ request }) => {
      expect(request.method).toBe('PATCH')
      expect(await request.json()).toEqual({ rol: 'NUTRICIONISTA' })
      expect(request.headers.get('Authorization')).toBe('Bearer users-token')
      return HttpResponse.json({ ...user, rol: 'NUTRICIONISTA' })
    }))
    server.use(http.patch('/api/usuarios/16/desactivar', ({ request }) => {
      expect(request.method).toBe('PATCH')
      expect(request.headers.get('Content-Type')).toBeNull()
      return HttpResponse.json({ ...user, estado: 'INACTIVO' })
    }))
    server.use(http.patch('/api/usuarios/16/activar', () => HttpResponse.json({ ...user, estado: 'ACTIVO' })))
    server.use(http.patch('/api/usuarios/16/contrasena', async ({ request }) => {
      expect(request.method).toBe('PATCH')
      expect(await request.json()).toEqual({ contrasena: 'NewPassword!123' })
      return new HttpResponse(null, { status: 204 })
    }))
    await expect(cambiarRolUsuario(auth, 16, 'NUTRICIONISTA')).resolves.toMatchObject({ rol: 'NUTRICIONISTA' })
    await expect(cambiarEstadoUsuario(auth, 16, false)).resolves.toMatchObject({ estado: 'INACTIVO' })
    await expect(cambiarEstadoUsuario(auth, 16, true)).resolves.toMatchObject({ estado: 'ACTIVO' })
    await expect(cambiarContrasenaUsuario(auth, 16, 'NewPassword!123')).resolves.toBeUndefined()

    server.use(http.patch('/api/usuarios/16/contrasena', () => HttpResponse.json({ ok: true }, { status: 200 })))
    await expect(cambiarContrasenaUsuario(auth, 16, 'NewPassword!123')).rejects.toMatchObject({ code: 'invalid-response' })
  })

  it('rechaza IDs inválidos y clasifica los errores HTTP administrativos', async () => {
    expect(() => buscarUsuarioPorId(auth, 0)).toThrow(/entero positivo/)
    for (const [status, code] of [[400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict']] as const) {
      server.use(http.post('/api/usuarios', () => HttpResponse.json({ mensaje: 'private details' }, { status })))
      const error = await crearUsuario(auth, create).catch((reason: unknown) => reason)
      expect(error).toMatchObject({ status, code })
      expect((error as Error).message).not.toContain('private details')
    }
  })

  it('marks network, timeout, 5xx and invalid responses as non-confirmed writes', async () => {
    server.use(http.post('/api/usuarios', () => HttpResponse.error()))
    await expect(crearUsuario(auth, create)).rejects.toMatchObject({ code: 'network' })
    vi.spyOn(AbortSignal, 'timeout').mockReturnValue(AbortSignal.abort())
    await expect(crearUsuario(auth, create)).rejects.toMatchObject({ code: 'network' })
    vi.restoreAllMocks()
    server.use(http.post('/api/usuarios', () => HttpResponse.json({ error: 'temporary' }, { status: 500 })))
    await expect(crearUsuario(auth, create)).rejects.toMatchObject({ code: 'server', status: 500 })
    server.use(http.post('/api/usuarios', () => HttpResponse.json({ idUsuario: 16 }, { status: 201 })))
    await expect(crearUsuario(auth, create)).rejects.toMatchObject({ code: 'invalid-response' })
  })

  it.each([[400, 'validation'], [401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [409, 'conflict']] as const)(
    'maps HTTP %s for account PATCH actions', async (status, code) => {
      server.use(http.patch('/api/usuarios/16/rol', () => HttpResponse.json({ mensaje: 'private' }, { status })))
      await expect(cambiarRolUsuario(auth, 16, 'ADMINISTRADOR')).rejects.toMatchObject({ status, code })
    },
  )
})
