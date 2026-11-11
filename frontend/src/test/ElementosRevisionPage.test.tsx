import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { ElementosRevisionPage } from '../app/ElementosRevisionPage'
import { server } from './server'

let auth: AuthContextValue
const machine = { idElemento: 4, nombre: 'Bicicleta fija', tipo: 'MAQUINA', descripcion: 'Cardio', ubicacion: 'Sala principal', activo: true, fechaCreacion: '2026-01-01T10:00:00', fechaActualizacion: '2026-01-01T10:00:00' }
const area = { ...machine, idElemento: 5, nombre: 'Vestidores', tipo: 'AREA', descripcion: 'Zona común', ubicacion: 'Primer piso', activo: false }
const session = () => {
  const iat = Math.floor(Date.now() / 1000)
  const enc = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc({ sub: 'admin@example.test', roles: ['ROLE_ADMINISTRADOR'], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <ElementosRevisionPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn() {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(session())))
  await act(async () => { await auth.login({ correo: 'admin@example.test', contrasena: 'password' }) })
}
function setupList(response: unknown[] = [machine, area]) {
  server.use(http.get('/api/elementos-revision', () => HttpResponse.json(response)))
}
afterEach(() => window.sessionStorage.removeItem('elementos-revision:escritura-incierta'))

describe('ElementosRevisionPage', () => {
  it('lista catálogo completo y filtra localmente por texto, tipo y estado', async () => {
    const user = userEvent.setup()
    let listCalls = 0
    server.use(http.get('/api/elementos-revision', () => { listCalls += 1; return HttpResponse.json([machine, area]) }))
    renderPage(); await signIn()
    expect(await screen.findByText('Bicicleta fija')).toBeInTheDocument()
    expect(screen.getByText('Vestidores')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por tipo'), 'AREA')
    expect(screen.queryByText('Bicicleta fija')).not.toBeInTheDocument()
    expect(screen.getByText('Vestidores')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por estado'), 'false')
    expect(screen.getByText('Vestidores')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Buscar elementos'), 'piso')
    expect(screen.getByText('Vestidores')).toBeInTheDocument()
    expect(listCalls).toBe(1)
  })

  it('loads detail from API and creates only after review and explicit confirmation', async () => {
    const user = userEvent.setup()
    let createCount = 0
    setupList([machine])
    server.use(http.get('/api/elementos-revision/4', () => HttpResponse.json(machine)))
    server.use(http.post('/api/elementos-revision', async ({ request }) => {
      createCount += 1
      expect(await request.json()).toEqual({ nombre: 'Remo', tipo: 'MAQUINA', descripcion: null, ubicacion: 'Sala cardio' })
      return HttpResponse.json({ ...machine, idElemento: 8, nombre: 'Remo', descripcion: null, ubicacion: 'Sala cardio' }, { status: 201 })
    }))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Bicicleta fija/ })
    await user.click(within(row).getByRole('button', { name: 'Detalle de Bicicleta fija' }))
    expect(await screen.findByRole('dialog', { name: 'Detalle del elemento' })).toHaveTextContent('Sala principal')
    await user.click(screen.getByRole('button', { name: 'Cerrar detalle' }))
    await user.click(screen.getByRole('button', { name: 'Nuevo elemento' }))
    await user.type(screen.getByLabelText('Nombre del elemento'), 'Remo')
    await user.selectOptions(screen.getByLabelText('Tipo de elemento'), 'MAQUINA')
    await user.type(screen.getByLabelText('Ubicación del elemento'), 'Sala cardio')
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar creación' })).toBeInTheDocument()
    expect(createCount).toBe(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByRole('status')).toHaveTextContent(/creado según la respuesta del servidor/)
    expect(createCount).toBe(1)
  })

  it('validates fields and loads server data before PUT editing', async () => {
    const user = userEvent.setup()
    let updateBody: unknown
    setupList([machine])
    server.use(http.get('/api/elementos-revision/4', () => HttpResponse.json(machine)))
    server.use(http.put('/api/elementos-revision/4', async ({ request }) => { updateBody = await request.json(); return HttpResponse.json({ ...machine, nombre: 'Bicicleta vertical' }) }))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Bicicleta fija/ })
    await user.click(within(row).getByRole('button', { name: 'Editar Bicicleta fija' }))
    expect(await screen.findByDisplayValue('Bicicleta fija')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Nombre del elemento'))
    await user.type(screen.getByLabelText('Nombre del elemento'), '   ')
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El nombre es obligatorio')
    await user.type(screen.getByLabelText('Nombre del elemento'), 'Bicicleta vertical')
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar edición' })).toHaveTextContent(/los registros históricos se conservan/i)
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByRole('status')).toHaveTextContent(/checklists e incidencias existentes no se modifican/i)
    expect(updateBody).toEqual({ nombre: 'Bicicleta vertical', tipo: 'MAQUINA', descripcion: 'Cardio', ubicacion: 'Sala principal' })
  })

  it.each([[true, 'activar', 'activación'], [false, 'desactivar', 'desactivación']] as const)('confirma operación de estado %s y actualiza con respuesta real', async (active, endpoint, action) => {
    const user = userEvent.setup()
    setupList([active ? { ...machine, activo: false } : machine])
    server.use(http.patch(`/api/elementos-revision/4/${endpoint}`, () => HttpResponse.json({ ...machine, activo: active })))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Bicicleta fija/ })
    await user.click(within(row).getByRole('button', { name: `${active ? 'Activar' : 'Desactivar'} Bicicleta fija` }))
    const dialog = await screen.findByRole('dialog', { name: `Confirmar ${action}` })
    expect(dialog).toHaveTextContent(active ? /volverá a estar disponible/ : /retirará de futuras selecciones del entrenador/)
    expect(dialog).toHaveTextContent(/no se modifican/)
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByRole('status')).toHaveTextContent(active ? /activado según el servidor/ : /desactivado según el servidor/)
  })

  it('prevents double submit and blocks writes after uncertain network result until refresh plus acknowledgement', async () => {
    const user = userEvent.setup()
    let calls = 0
    window.sessionStorage.removeItem('elementos-revision:escritura-incierta')
    setupList([machine])
    server.use(http.post('/api/elementos-revision', () => { calls += 1; return HttpResponse.error() }))
    renderPage(); await signIn()
    await screen.findByText('Bicicleta fija')
    await user.click(screen.getByRole('button', { name: 'Nuevo elemento' }))
    await user.type(screen.getByLabelText('Nombre del elemento'), 'Prensa')
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByText(/El cambio podría haberse aplicado/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nuevo elemento' })).toBeDisabled()
    expect(calls).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Actualizar catálogo' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Ya verifiqué; habilitar escritura' })).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Nuevo elemento' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Ya verifiqué; habilitar escritura' }))
    expect(screen.getByRole('button', { name: 'Nuevo elemento' })).toBeEnabled()
    expect(calls).toBe(1)
  })

  it('recovers from HTTP errors and handles empty catalog', async () => {
    const user = userEvent.setup()
    let count = 0
    server.use(http.get('/api/elementos-revision', () => ++count === 1 ? HttpResponse.json({}, { status: 400 }) : HttpResponse.json([])))
    renderPage(); await signIn()
    expect(await screen.findByRole('alert')).toHaveTextContent(/servidor rechazó/)
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Todavía no hay elementos de revisión registrados.')).toBeInTheDocument()
  })
})
