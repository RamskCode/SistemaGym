import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { ChecklistsAdminPage } from '../app/ChecklistsAdminPage'
import { server } from './server'

let auth: AuthContextValue
const latest = {
  idChecklist: 42, idEntrenador: 23, correoEntrenador: 'trainer@example.test', fechaHora: '2026-11-06T12:00:00Z', observaciones: 'Revisión de apertura',
  detalles: [
    { idDetalle: 1, idElemento: 8, nombreElemento: 'Caminadora 1', tipoElemento: 'MAQUINA', ubicacionElemento: 'Sala principal', resultado: 'CORRECTO', observaciones: 'Sin ruido', fechaRevision: '2026-11-06T12:00:00Z', incidencia: null },
    { idDetalle: 2, idElemento: 9, nombreElemento: 'Zona de pesas', tipoElemento: 'AREA', ubicacionElemento: null, resultado: 'CON_PROBLEMA', observaciones: 'Piso húmedo', fechaRevision: '2026-11-06T12:00:00Z', incidencia: { idReporte: 77, descripcion: 'Piso húmedo', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: '2026-11-06T12:05:00Z' } },
  ],
}
const older = { idChecklist: 41, idEntrenador: 24, correoEntrenador: 'other.trainer@example.test', fechaHora: '2026-11-05T13:00:00Z', observaciones: null, detalles: [{ idDetalle: 3, idElemento: 10, nombreElemento: 'Bicicleta 2', tipoElemento: 'MAQUINA', ubicacionElemento: null, resultado: 'CORRECTO', observaciones: null, fechaRevision: '2026-11-05T13:00:00Z', incidencia: null }] }

function token(role: AuthRole = 'ADMINISTRADOR') {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'admin@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <ChecklistsAdminPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function login(role: AuthRole = 'ADMINISTRADOR') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(token(role))))
  await act(async () => { await auth.login({ correo: 'admin@example.test', contrasena: 'unused' }) })
}
function mockList(data: unknown = [latest, older]) {
  server.use(http.get('/api/checklists', ({ request }) => {
    expect(request.headers.get('Authorization')).toContain('Bearer ')
    return HttpResponse.json(data)
  }))
}

describe('ChecklistsAdminPage', () => {
  beforeEach(() => { window.sessionStorage.clear(); window.localStorage.clear() })

  it('lists the global backend order and derives counts and problem status from actual details', async () => {
    mockList()
    renderPage(); await login()
    expect(await screen.findByRole('heading', { name: 'Checklists del gimnasio' })).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent('Entrenador #23')
    expect(rows[1]).toHaveTextContent('2')
    expect(rows[1]).toHaveTextContent('1 con problema')
    expect(rows[2]).toHaveTextContent('Entrenador #24')
    expect(rows[2]).toHaveTextContent('Sin problemas')
    expect(screen.getByText(/6 nov\. 2026, 7:00 a\. m\./)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Editar|Eliminar|Repetir/ })).not.toBeInTheDocument()
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('filters locally by trainer, Lima date and checklist result', async () => {
    const user = userEvent.setup()
    mockList()
    renderPage(); await login()
    await screen.findByRole('row', { name: /Entrenador #23/ })
    await user.type(screen.getByLabelText('Buscar por entrenador o identificador'), 'other.trainer')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByRole('row', { name: /Entrenador #24/ })).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Buscar por entrenador o identificador'))
    await user.selectOptions(screen.getByLabelText('Filtrar por resultado'), 'CON_PROBLEMA')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByRole('row', { name: /Entrenador #23/ })).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por resultado'), '')
    await user.type(screen.getByLabelText('Filtrar por fecha'), '2026-11-05')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByRole('row', { name: /Entrenador #24/ })).toBeInTheDocument()
  })

  it('loads exact detail, preserves observations and incidence reference, and remains read-only', async () => {
    const user = userEvent.setup()
    mockList()
    server.use(http.get('/api/checklists/42', ({ request }) => {
      expect(request.headers.get('Authorization')).toContain('Bearer ')
      return HttpResponse.json(latest)
    }))
    renderPage(); await login()
    const row = await screen.findByRole('row', { name: /Entrenador #23/ })
    await user.click(within(row).getByRole('button', { name: 'Ver detalle del checklist 42' }))
    expect(await screen.findByRole('heading', { name: 'Detalle del checklist' })).toBeInTheDocument()
    expect(screen.getByText('Entrenador #23')).toBeInTheDocument()
    expect(screen.getByText('Revisión de apertura')).toBeInTheDocument()
    expect(screen.getByText('Caminadora 1')).toBeInTheDocument()
    expect(screen.getByText('Sin ruido')).toBeInTheDocument()
    expect(screen.getByText('Correcto')).toBeInTheDocument()
    expect(screen.getByText('Zona de pesas')).toBeInTheDocument()
    expect(screen.getAllByText('Piso húmedo')).toHaveLength(2)
    expect(screen.getByText('Con problema')).toBeInTheDocument()
    expect(screen.getByText('Incidencia asociada #77')).toBeInTheDocument()
    expect(screen.getByText('ALTA')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /incidencia/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Editar|Eliminar|Cambiar estado|Repetir/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Volver a todos los checklists' }))
    expect(await screen.findByRole('heading', { name: 'Checklists del gimnasio' })).toBeInTheDocument()
  })

  it('shows recoverable list and detail errors without automatic retry', async () => {
    const user = userEvent.setup()
    let listCalls = 0
    server.use(http.get('/api/checklists', () => { listCalls += 1; return listCalls === 1 ? HttpResponse.json({}, { status: 403 }) : HttpResponse.json([latest]) }))
    renderPage(); await login()
    expect(await screen.findByRole('alert')).toHaveTextContent(/permiso/i)
    expect(listCalls).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    await screen.findByRole('row', { name: /Entrenador #23/ })
    expect(listCalls).toBe(2)

    mockList([latest])
    server.use(http.get('/api/checklists/42', () => HttpResponse.json({}, { status: 404 })))
    await user.click(screen.getByRole('button', { name: 'Ver detalle del checklist 42' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/No se pudo abrir el detalle/)
    expect(screen.getByRole('button', { name: 'Reintentar detalle' })).toBeInTheDocument()
  })

  it.each([401, 403, 404])('shows a recoverable detail error for HTTP %i', async (status) => {
    mockList([latest])
    server.use(http.get('/api/checklists/42', () => HttpResponse.json({}, { status })))
    renderPage(); await login()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Ver detalle del checklist 42' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(status === 401 ? /sesión|iniciar sesión/i : /No se pudo abrir el detalle/)
  })

  it('ignores a late checklist detail response after the session ends', async () => {
    const user = userEvent.setup()
    mockList([latest])
    let release!: (response: Response) => void
    server.use(http.get('/api/checklists/42', () => new Promise<Response>((resolve) => { release = resolve })))
    renderPage(); await login()
    await user.click(await screen.findByRole('button', { name: 'Ver detalle del checklist 42' }))
    await waitFor(() => expect(release).toBeTypeOf('function'))
    await act(async () => { auth.logout() })
    release(HttpResponse.json(latest))
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    expect(screen.queryByRole('heading', { name: 'Detalle del checklist' })).not.toBeInTheDocument()
  })
})
