import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { IncidenciasAdminPage } from '../app/IncidenciasAdminPage'
import { server } from './server'

let auth: AuthContextValue
const initial = { idReporte: 91, idEntrenador: 19, correoEntrenador: 'trainer@example.test', idElemento: 8, nombreElemento: 'Caminadora 1', idChecklist: 42, idDetalleChecklist: 7, descripcion: 'Cable desgastado', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: '2026-11-06T12:00:00Z', fechaActualizacion: '2026-11-06T12:00:00Z', fechaSolucion: null, historial: [{ idHistorial: 11, idUsuario: 19, correoUsuario: 'trainer@example.test', estadoAnterior: null, estadoNuevo: 'PENDIENTE', observacion: null, fechaHora: '2026-11-06T12:00:00Z' }] }
function session(role: AuthRole = 'ADMINISTRADOR') {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'admin@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <IncidenciasAdminPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn(role: AuthRole = 'ADMINISTRADOR') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(session(role))))
  await act(async () => { await auth.login({ correo: 'admin@example.test', contrasena: 'unused' }) })
}
function mockReport(initialReport: unknown = initial) {
  let current = initialReport
  server.use(http.get('/api/reportes-incidencias', () => HttpResponse.json([current])))
  server.use(http.get('/api/reportes-incidencias/91', () => HttpResponse.json(current)))
  return { get: () => current, set: (next: unknown) => { current = next } }
}

describe('IncidenciasAdminPage', () => {
  beforeEach(() => { window.sessionStorage.removeItem('incidencias-admin:estado-incierto') })
  it('loads global list, filters locally and renders detail/history and Lima time', async () => {
    const user = userEvent.setup()
    mockReport()
    renderPage(); await signIn()
    expect(await screen.findByRole('heading', { name: 'Incidencias' })).toBeInTheDocument()
    expect(screen.getByText('Caminadora 1')).toBeInTheDocument()
    expect(screen.getByText(/6 nov\. 2026, 7:00 a\. m\./)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Buscar incidencias'), 'no match')
    expect(screen.getByText(/No hay incidencias que coincidan/)).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Buscar incidencias'))
    const row = screen.getByRole('row', { name: /Caminadora 1/ })
    await user.click(within(row).getByRole('button', { name: /Detalle de incidencia 91/ }))
    const detail = await screen.findByRole('dialog', { name: 'Detalle de incidencia' })
    expect(detail).toHaveTextContent('Cable desgastado')
    expect(detail).toHaveTextContent('trainer@example.test')
    expect(detail).toHaveTextContent('Pendiente')
    expect(detail).toHaveTextContent('Checklist relacionado')
  })

  it('confirms only the permitted transition, requires observation, and refreshes server history', async () => {
    const user = userEvent.setup()
    const data = mockReport()
    let patches = 0
    server.use(http.patch('/api/reportes-incidencias/91/estado', async ({ request }) => {
      patches += 1
      expect(await request.json()).toEqual({ estado: 'EN_REVISION', observacion: 'Derivada a mantenimiento' })
      const next = { ...initial, estado: 'EN_REVISION', fechaActualizacion: '2026-11-06T13:00:00Z', historial: [...initial.historial, { idHistorial: 12, idUsuario: 1, correoUsuario: 'admin@example.test', estadoAnterior: 'PENDIENTE', estadoNuevo: 'EN_REVISION', observacion: 'Derivada a mantenimiento', fechaHora: '2026-11-06T13:00:00Z' }] }
      data.set(next)
      return HttpResponse.json(next)
    }))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Caminadora 1/ })
    await user.click(within(row).getByRole('button', { name: 'Cambiar estado de incidencia 91' }))
    const formDialog = await screen.findByRole('dialog', { name: 'Preparar cambio de estado' })
    expect(within(formDialog).getByLabelText('Nuevo estado')).toHaveValue('EN_REVISION')
    expect(within(formDialog).getByRole('button', { name: 'Revisar cambio' })).toBeEnabled()
    await user.click(within(formDialog).getByRole('button', { name: 'Revisar cambio' }))
    expect(within(formDialog).getByLabelText('Observación del cambio')).toBeInvalid()
    await user.type(within(formDialog).getByLabelText('Observación del cambio'), 'Derivada a mantenimiento')
    await user.click(within(formDialog).getByRole('button', { name: 'Revisar cambio' }))
    const confirm = await screen.findByRole('dialog', { name: 'Confirmar cambio de estado' })
    expect(confirm).toHaveTextContent('Pendiente')
    expect(confirm).toHaveTextContent('En revisión')
    expect(confirm).toHaveTextContent('Derivada a mantenimiento')
    expect(patches).toBe(0)
    await user.click(within(confirm).getByRole('button', { name: 'Confirmar y actualizar' }))
    expect(await screen.findByText('Estado e historial actualizados desde el servidor.')).toBeInTheDocument()
    expect(patches).toBe(1)
    expect(data.get()).toMatchObject({ estado: 'EN_REVISION' })
    await user.click(screen.getByRole('button', { name: /Detalle de incidencia 91/ }))
    expect(await screen.findByRole('dialog', { name: 'Detalle de incidencia' })).toHaveTextContent('Derivada a mantenimiento')
  })

  it.each([
    ['EN_REVISION', 'SOLUCIONADO'],
    ['SOLUCIONADO', 'EN_REVISION'],
  ] as const)('offers only %s → %s for the verified current state', async (current, next) => {
    const user = userEvent.setup()
    mockReport({ ...initial, estado: current })
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Caminadora 1/ })
    await user.click(within(row).getByRole('button', { name: 'Cambiar estado de incidencia 91' }))
    const dialog = await screen.findByRole('dialog', { name: 'Preparar cambio de estado' })
    const options = within(dialog).getByLabelText('Nuevo estado').querySelectorAll('option')
    expect(options).toHaveLength(1)
    expect(within(dialog).getByLabelText('Nuevo estado')).toHaveValue(next)
  })

  it('blocks uncertain changes until detail and history are checked and explicitly acknowledged', async () => {
    const user = userEvent.setup()
    window.sessionStorage.removeItem('incidencias-admin:estado-incierto')
    mockReport()
    let patches = 0
    server.use(http.patch('/api/reportes-incidencias/91/estado', () => { patches += 1; return HttpResponse.error() }))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Caminadora 1/ })
    await user.click(within(row).getByRole('button', { name: 'Cambiar estado de incidencia 91' }))
    const formDialog = await screen.findByRole('dialog', { name: 'Preparar cambio de estado' })
    await user.type(within(formDialog).getByLabelText('Observación del cambio'), 'Revisión solicitada')
    await user.click(within(formDialog).getByRole('button', { name: 'Revisar cambio' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Confirmar cambio de estado' })).getByRole('button', { name: 'Confirmar y actualizar' }))
    expect(await screen.findByText(/Resultado incierto para la incidencia #91/)).toBeInTheDocument()
    expect(window.sessionStorage.getItem('incidencias-admin:estado-incierto')).toBe('[91]')
    expect(within(await screen.findByRole('row', { name: /Caminadora 1/ })).getByRole('button', { name: 'Cambiar estado de incidencia 91' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Verificar #91' }))
    const verification = await screen.findByRole('dialog', { name: 'Verificar incidencia #91' })
    expect(verification).toHaveTextContent('Pendiente')
    expect(verification).toHaveTextContent('Pendiente ·')
    expect(within(verification).getByRole('button', { name: 'Verificado; desbloquear' })).toBeDisabled()
    await user.click(within(verification).getByRole('checkbox'))
    await user.click(within(verification).getByRole('button', { name: 'Verificado; desbloquear' }))
    expect(window.sessionStorage.getItem('incidencias-admin:estado-incierto')).toBe('[]')
    expect(patches).toBe(1)
  })

  it('does not update the UI with late authenticated responses after logout', async () => {
    const user = userEvent.setup()
    mockReport()
    let release!: (response: Response) => void
    server.use(http.patch('/api/reportes-incidencias/91/estado', () => new Promise<Response>((resolve) => { release = resolve })))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Caminadora 1/ })
    await user.click(within(row).getByRole('button', { name: 'Cambiar estado de incidencia 91' }))
    const formDialog = await screen.findByRole('dialog', { name: 'Preparar cambio de estado' })
    await user.type(within(formDialog).getByLabelText('Observación del cambio'), 'Cierre')
    await user.click(within(formDialog).getByRole('button', { name: 'Revisar cambio' }))
    await user.click(within(await screen.findByRole('dialog', { name: 'Confirmar cambio de estado' })).getByRole('button', { name: 'Confirmar y actualizar' }))
    await waitFor(() => expect(release).toBeTypeOf('function'))
    await act(async () => { auth.logout() })
    release(HttpResponse.json({ ...initial, estado: 'EN_REVISION' }))
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    expect(screen.queryByText(/Estado e historial actualizados desde el servidor/)).not.toBeInTheDocument()
  })
})
