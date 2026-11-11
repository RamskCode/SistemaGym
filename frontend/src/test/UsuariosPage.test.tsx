import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { UsuariosPage } from '../app/UsuariosPage'
import { server } from './server'

let auth: AuthContextValue
const employee = { idUsuario: 16, nombres: 'Lucía', apellidos: 'Soto', correo: 'lucia@example.test', rol: 'ENTRENADOR', estado: 'ACTIVO', fechaCreacion: '2026-04-01T10:00:00', tipoDocumento: 'DNI', numeroDocumento: '12345678' }
function session() {
  const iat = Math.floor(Date.now() / 1000)
  const enc = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc({ sub: 'admin@example.test', roles: ['ROLE_ADMINISTRADOR'], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <><div data-testid="session-state">{auth.isAuthenticated ? 'signed-in' : 'signed-out'}</div><UsuariosPage /></> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function login() {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(session())))
  await act(async () => { await auth.login({ correo: 'admin@example.test', contrasena: 'test-password' }) })
}
function users(data: unknown[] = [employee]) { server.use(http.get('/api/usuarios', () => HttpResponse.json(data))) }
afterEach(() => window.sessionStorage.removeItem('usuarios:escritura-incierta'))

describe('UsuariosPage', () => {
  it('lists, searches and filters locally without server pagination; masks documents', async () => {
    const user = userEvent.setup()
    let calls = 0
    server.use(http.get('/api/usuarios', () => { calls += 1; return HttpResponse.json([employee, { ...employee, idUsuario: 17, nombres: 'Marta', correo: 'marta@example.test', rol: 'NUTRICIONISTA', estado: 'INACTIVO', tipoDocumento: null, numeroDocumento: null }]) }))
    renderPage(); await login()
    expect(await screen.findByText('Soto, Lucía')).toBeInTheDocument()
    const search = screen.getByRole('searchbox', { name: 'Buscar por nombre o correo' })
    await user.click(search)
    expect(search).toHaveFocus()
    expect(search.closest('.search-field')).toHaveClass('usuarios-search')
    expect(screen.getByText('DNI · ••••••78')).toBeInTheDocument()
    expect(screen.queryByText('12345678')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por rol'), 'NUTRICIONISTA')
    expect(screen.queryByText('Soto, Lucía')).not.toBeInTheDocument()
    expect(screen.getByText('Soto, Marta')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por estado'), 'INACTIVO')
    await user.type(screen.getByLabelText('Buscar por nombre o correo'), 'marta@')
    expect(screen.getByText('Soto, Marta')).toBeInTheDocument()
    expect(calls).toBe(1)
  })

  it('shows authorized full document in details, masks the directory and loads fresh edit values', async () => {
    const user = userEvent.setup()
    users()
    server.use(http.get('/api/usuarios/16', () => HttpResponse.json({ ...employee, contrasenaHash: 'never-show' })))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Detalle' }))
    const dialog = await screen.findByRole('dialog', { name: 'Información del empleado' })
    expect(within(dialog).getByRole('button', { name: 'Cerrar información del empleado' })).toHaveFocus()
    expect(dialog).toHaveTextContent('Lucía Soto')
    expect(dialog).toHaveTextContent('DNI · 12345678')
    expect(dialog).toHaveTextContent('1 de abril de 2026')
    expect(dialog).not.toHaveTextContent('Detalle de usuario #16')
    expect(dialog).not.toHaveTextContent('never-show')
    expect(screen.getByRole('row', { name: /Lucía/ })).toHaveClass('usuarios-row-selected')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Información del empleado' })).not.toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Lucía/ })).not.toHaveClass('usuarios-row-selected')
    await user.click(within(await screen.findByRole('row', { name: /Lucía/ })).getByRole('button', { name: 'Editar' }))
    expect(await screen.findByDisplayValue('Lucía')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Editar empleado' })).toBeInTheDocument()
    expect(screen.getByText('Lucía Soto')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
    expect(screen.getByRole('row', { name: /Lucía/ })).toHaveClass('usuarios-row-selected')
    expect(screen.queryByLabelText('Rol del usuario')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Contraseña inicial')).not.toBeInTheDocument()
  })

  it('requires a valid document and creates a pending account with an invitation after confirmation', async () => {
    const user = userEvent.setup()
    let postCount = 0
    users([])
    server.use(http.post('/api/usuarios', async ({ request }) => {
      postCount += 1
      expect(await request.json()).toEqual({ nombres: 'Mario', apellidos: 'Rojas', correo: 'mario@example.test', rol: 'RECEPCIONISTA', tipoDocumento: 'DNI', numeroDocumento: '12345678' })
      return HttpResponse.json({ usuario: { ...employee, idUsuario: 21, nombres: 'Mario', apellidos: 'Rojas', correo: 'mario@example.test', rol: 'RECEPCIONISTA', estado: 'PENDIENTE' }, correoEnviado: true, mensaje: 'Invitación enviada correctamente.' }, { status: 201 })
    }))
    renderPage(); await login()
    await screen.findByText('Todavía no hay usuarios registrados.')
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    await user.type(screen.getByLabelText('Nombres'), 'Mario')
    await user.type(screen.getByLabelText('Apellidos'), 'Rojas')
    await user.type(screen.getByLabelText('Correo'), 'mario@example.test')
    await user.click(screen.getByRole('button', { name: 'DNI' }))
    await user.click(screen.getByRole('button', { name: 'Revisar datos' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/deben proporcionarse juntos/)
    await user.type(screen.getByLabelText('Número de documento'), '12345678')
    await user.selectOptions(screen.getByLabelText('Rol del usuario'), 'RECEPCIONISTA')
    await user.click(screen.getByRole('button', { name: 'Revisar datos' }))
    const confirm = await screen.findByRole('dialog', { name: 'Confirmar alta de usuario' })
    expect(confirm).toHaveTextContent('RECEPCIONISTA')
    expect(confirm).not.toHaveTextContent('12345678')
    expect(postCount).toBe(0)
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.getItem('usuarios:escritura-incierta')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByText('Invitación enviada correctamente.')).toBeInTheDocument()
    expect(postCount).toBe(1)
    expect(screen.queryByLabelText('Contraseña inicial')).not.toBeInTheDocument()
  })

  it('edits allowed fields, omits document unless opted in, and does not expose role/password controls', async () => {
    const user = userEvent.setup()
    let body: unknown
    users()
    server.use(http.get('/api/usuarios/16', () => HttpResponse.json(employee)))
    server.use(http.put('/api/usuarios/16', async ({ request }) => { body = await request.json(); return HttpResponse.json({ ...employee, nombres: 'Lucía Elena' }) }))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Editar' }))
    expect(await screen.findByDisplayValue('Lucía')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Nombres'))
    await user.type(screen.getByLabelText('Nombres'), 'Lucía Elena')
    expect(screen.getByRole('heading', { name: 'Editar empleado' })).toBeInTheDocument()
    expect(screen.getByText('Lucía Soto')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar edición de usuario' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByText('Usuario #16 actualizado correctamente.')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Lucía Elena/ })).not.toHaveClass('usuarios-row-selected')
    expect(body).toEqual({ nombres: 'Lucía Elena', apellidos: 'Soto', correo: 'lucia@example.test' })
  })

  it('closes edit by its X or Escape and clears the selected-row highlight', async () => {
    const user = userEvent.setup()
    users()
    server.use(http.get('/api/usuarios/16', () => HttpResponse.json(employee)))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Editar' }))
    const editDialog = await screen.findByRole('dialog', { name: 'Editar empleado' })
    expect(within(editDialog).getByRole('button', { name: 'Cerrar formulario' })).toHaveFocus()
    expect(screen.getByRole('row', { name: /Lucía/ })).toHaveClass('usuarios-row-selected')
    await user.click(within(editDialog).getByRole('button', { name: 'Cerrar formulario' }))
    expect(screen.queryByRole('dialog', { name: 'Editar empleado' })).not.toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Lucía/ })).not.toHaveClass('usuarios-row-selected')

    await user.click(within(await screen.findByRole('row', { name: /Lucía/ })).getByRole('button', { name: 'Editar' }))
    await screen.findByRole('dialog', { name: 'Editar empleado' })
    expect(screen.getByRole('row', { name: /Lucía/ })).toHaveClass('usuarios-row-selected')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Editar empleado' })).not.toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Lucía/ })).not.toHaveClass('usuarios-row-selected')
  })

  it('requires document data and prevents a second confirmation while POST is pending', async () => {
    const user = userEvent.setup()
    let posts = 0
    let finish!: (response: Response) => void
    users([])
    server.use(http.post('/api/usuarios', async ({ request }) => {
      posts += 1
      expect(await request.json()).toEqual({ nombres: 'Alba', apellidos: 'León', correo: 'alba@example.test', rol: 'ENTRENADOR', tipoDocumento: 'DNI', numeroDocumento: '12345678' })
      return await new Promise<Response>((resolve) => { finish = resolve })
    }))
    renderPage(); await login()
    await screen.findByText('Todavía no hay usuarios registrados.')
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    await user.type(screen.getByLabelText('Nombres'), 'Alba')
    await user.type(screen.getByLabelText('Apellidos'), 'León')
    await user.type(screen.getByLabelText('Correo'), 'alba@example.test')
    await user.click(screen.getByRole('button', { name: 'DNI' }))
    await user.type(screen.getByLabelText('Número de documento'), '12345678')
    await user.click(screen.getByRole('button', { name: 'Revisar datos' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByRole('button', { name: 'Enviando…' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Enviando…' }))
    expect(posts).toBe(1)
    finish(HttpResponse.json({ usuario: { ...employee, idUsuario: 31, nombres: 'Alba', apellidos: 'León', correo: 'alba@example.test', estado: 'PENDIENTE' }, correoEnviado: true, mensaje: 'Invitación enviada correctamente.' }, { status: 201 }))
    expect(await screen.findByText('Invitación enviada correctamente.')).toBeInTheDocument()
    expect(posts).toBe(1)
  })

  it('blocks duplicate writes and requires verification after uncertain result without storing password', async () => {
    const user = userEvent.setup()
    let posts = 0
    window.sessionStorage.removeItem('usuarios:escritura-incierta')
    users([])
    server.use(http.post('/api/usuarios', () => { posts += 1; return HttpResponse.error() }))
    renderPage(); await login()
    await screen.findByText('Todavía no hay usuarios registrados.')
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    await user.type(screen.getByLabelText('Nombres'), 'Mario')
    await user.type(screen.getByLabelText('Apellidos'), 'Rojas')
    await user.type(screen.getByLabelText('Correo'), 'mario@example.test')
    await user.click(screen.getByRole('button', { name: 'DNI' }))
    await user.type(screen.getByLabelText('Número de documento'), '12345678')
    await user.click(screen.getByRole('button', { name: 'Revisar datos' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByText(/No se confirmó el resultado/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nuevo usuario' })).toBeDisabled()
    expect(posts).toBe(1)
    expect(window.sessionStorage.getItem('usuarios:escritura-incierta')).not.toContain('contrasena')
    await user.click(screen.getByRole('button', { name: 'Consultar usuario' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Confirmo; habilitar operaciones' })).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Nuevo usuario' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Confirmo; habilitar operaciones' }))
    expect(screen.getByRole('button', { name: 'Nuevo usuario' })).toBeEnabled()
    expect(posts).toBe(1)
  })

  it('reloads after HTTP validation errors and shows empty state', async () => {
    const user = userEvent.setup()
    let reads = 0
    server.use(http.get('/api/usuarios', () => ++reads === 1 ? HttpResponse.json({}, { status: 400 }) : HttpResponse.json([])))
    renderPage(); await login()
    expect(await screen.findByRole('alert')).toHaveTextContent(/servidor rechazó/)
    await user.click(screen.getByRole('button', { name: 'Reintentar consulta' }))
    expect(await screen.findByText('Todavía no hay usuarios registrados.')).toBeInTheDocument()
  })

  it('does not request or retain an initial password in the invitation form', async () => {
    const user = userEvent.setup()
    users([])
    renderPage(); await login()
    await screen.findByText('Todavía no hay usuarios registrados.')
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    expect(screen.queryByLabelText('Contraseña inicial')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    await act(async () => { auth.logout() })
    await waitFor(() => expect(screen.queryByLabelText('Contraseña inicial')).not.toBeInTheDocument())
  })

  it('confirms role changes, shows current/new roles, and prevents redundant role PATCH', async () => {
    const user = userEvent.setup()
    users()
    let patches = 0
    server.use(http.get('/api/usuarios/16', () => HttpResponse.json(employee)))
    server.use(http.patch('/api/usuarios/16/rol', async ({ request }) => {
      patches += 1
      expect(await request.json()).toEqual({ rol: 'RECEPCIONISTA' })
      return HttpResponse.json({ ...employee, rol: 'RECEPCIONISTA' })
    }))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Detalle' }))
    const detail = await screen.findByRole('dialog', { name: 'Información del empleado' })
    await user.selectOptions(within(detail).getByLabelText('Nuevo rol'), 'ENTRENADOR')
    expect(within(detail).getByRole('button', { name: 'Cambiar rol' })).toBeDisabled()
    await user.selectOptions(within(detail).getByLabelText('Nuevo rol'), 'RECEPCIONISTA')
    await user.click(within(detail).getByRole('button', { name: 'Cambiar rol' }))
    const confirmation = await screen.findByRole('dialog', { name: 'Confirmar cambio de rol' })
    expect(confirmation).toHaveTextContent('ENTRENADOR')
    expect(confirmation).toHaveTextContent('RECEPCIONISTA')
    expect(confirmation).toHaveTextContent(/modifica los permisos efectivos/)
    expect(patches).toBe(0)
    await user.click(within(confirmation).getByRole('button', { name: 'Confirmar y ejecutar' }))
    expect(await screen.findByText(/El rol de .* cambió a RECEPCIONISTA/)).toBeInTheDocument()
    expect(patches).toBe(1)
  })

  it('requires reinforced confirmation for deactivation and confirms status activation', async () => {
    const user = userEvent.setup()
    users([{ ...employee, estado: 'ACTIVO' }])
    let deactivations = 0
    let activations = 0
    server.use(http.get('/api/usuarios/16', () => HttpResponse.json(employee)))
    server.use(http.patch('/api/usuarios/16/desactivar', () => { deactivations += 1; return HttpResponse.json({ ...employee, estado: 'INACTIVO' }) }))
    server.use(http.patch('/api/usuarios/16/activar', () => { activations += 1; return HttpResponse.json({ ...employee, estado: 'ACTIVO' }) }))
    renderPage(); await login()
    let row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Desactivar cuenta' }))
    let confirm = await screen.findByRole('dialog', { name: 'Confirmar desactivación de cuenta' })
    expect(confirm).toHaveTextContent('Lucía Soto')
    expect(confirm).toHaveTextContent(/no podrá iniciar nuevas sesiones/)
    expect(within(confirm).getByRole('button', { name: 'Confirmar y ejecutar' })).toBeDisabled()
    await user.click(within(confirm).getByRole('checkbox', { name: /Confirmo que deseo desactivar/ }))
    await user.click(within(confirm).getByRole('button', { name: 'Confirmar y ejecutar' }))
    expect(await screen.findByText(/quedó inactivo/)).toBeInTheDocument()
    expect(deactivations).toBe(1)
    row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Activar cuenta' }))
    confirm = await screen.findByRole('dialog', { name: 'Confirmar activación de cuenta' })
    await user.click(within(confirm).getByRole('button', { name: 'Confirmar y ejecutar' }))
    expect(await screen.findByText(/quedó activo/)).toBeInTheDocument()
    expect(activations).toBe(1)
    row = await screen.findByRole('row', { name: /Lucía/ })
    expect(row).toHaveTextContent('Activo')
  })

  it('shows direct invitation resend for pending employees without exposing status actions', async () => {
    const user = userEvent.setup()
    users([{ ...employee, estado: 'PENDIENTE' }])
    let resends = 0
    server.use(http.post('/api/usuarios/16/invitacion', () => {
      resends += 1
      return HttpResponse.json({ usuario: { ...employee, estado: 'PENDIENTE' }, correoEnviado: true, mensaje: 'Invitación enviada correctamente.' })
    }))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Lucía/ })
    expect(within(row).getByRole('button', { name: 'Reenviar invitación' })).toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: 'Activar cuenta' })).not.toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: 'Desactivar cuenta' })).not.toBeInTheDocument()
    expect(within(row).getAllByRole('button')).toHaveLength(3)
    await user.click(within(row).getByRole('button', { name: 'Reenviar invitación' }))
    expect(await screen.findByText('Invitación enviada correctamente.')).toBeInTheDocument()
    expect(resends).toBe(1)
  })

  it('does not offer direct password setting in the employee details', async () => {
    const user = userEvent.setup()
    users()
    server.use(http.get('/api/usuarios/16', () => HttpResponse.json(employee)))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Lucía/ })
    await user.click(within(row).getByRole('button', { name: 'Detalle' }))
    const detail = await screen.findByRole('dialog', { name: 'Información del empleado' })
    expect(within(detail).queryByRole('button', { name: /Establecer contraseña/i })).not.toBeInTheDocument()
    expect(within(detail).queryByRole('button', { name: /Desactivar cuenta/i })).not.toBeInTheDocument()
    expect(within(detail).queryByRole('button', { name: /^Cerrar$/ })).not.toBeInTheDocument()
  })
  it('logs out locally after changing own role and ignores late PATCH responses after logout', async () => {
    const user = userEvent.setup()
    const self = { ...employee, idUsuario: 1, correo: 'admin@example.test', nombres: 'Admin', rol: 'ADMINISTRADOR' }
    let release!: (response: Response) => void
    server.use(http.get('/api/usuarios', () => HttpResponse.json([self])))
    server.use(http.get('/api/usuarios/1', () => HttpResponse.json(self)))
    server.use(http.patch('/api/usuarios/1/rol', () => new Promise<Response>((resolve) => { release = resolve })))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Admin/ })
    await user.click(within(row).getByRole('button', { name: 'Detalle' }))
    const detail = await screen.findByRole('dialog', { name: 'Información del empleado' })
    await user.selectOptions(within(detail).getByLabelText('Nuevo rol'), 'RECEPCIONISTA')
    await user.click(within(detail).getByRole('button', { name: 'Cambiar rol' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y ejecutar' }))
    await waitFor(() => expect(release).toBeTypeOf('function'))
    await act(async () => { auth.logout() })
    release(HttpResponse.json({ ...self, rol: 'RECEPCIONISTA' }))
    await waitFor(() => expect(screen.getByTestId('session-state')).toHaveTextContent('signed-out'))
    expect(screen.queryByText(/El rol de .* cambió a RECEPCIONISTA/)).not.toBeInTheDocument()
  })
})

