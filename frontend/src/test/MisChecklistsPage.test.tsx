import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { MisChecklistsPage } from '../app/MisChecklistsPage'
import { server } from './server'

let auth: AuthContextValue
const latest = {
  idChecklist: 42, idEntrenador: 23, correoEntrenador: 'trainer@example.test',
  fechaHora: '2026-11-06T07:00:00-05:00', observaciones: 'Revisión de apertura',
  detalles: [
    { idDetalle: 1, idElemento: 8, nombreElemento: 'Caminadora 1', tipoElemento: 'MAQUINA', ubicacionElemento: 'Sala principal', resultado: 'CORRECTO', observaciones: 'Sin ruido', fechaRevision: '2026-11-06T07:00:00-05:00', incidencia: null },
    { idDetalle: 2, idElemento: 9, nombreElemento: 'Zona de pesas', tipoElemento: 'AREA', ubicacionElemento: null, resultado: 'CON_PROBLEMA', observaciones: null, fechaRevision: '2026-11-06T07:00:00-05:00', incidencia: { idReporte: 77, descripcion: 'Piso húmedo', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: '2026-11-06T07:05:00-05:00' } },
  ],
}
const older = {
  idChecklist: 41, idEntrenador: 23, correoEntrenador: 'trainer@example.test',
  fechaHora: '2026-11-05T08:00:00-05:00', observaciones: null,
  detalles: [{ idDetalle: 3, idElemento: 10, nombreElemento: 'Bicicleta 2', tipoElemento: 'MAQUINA', ubicacionElemento: null, resultado: 'CORRECTO', observaciones: null, fechaRevision: '2026-11-05T08:00:00-05:00', incidencia: null }],
}

function sessionResponse(role: AuthRole = 'ENTRENADOR') {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'trainer@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <MisChecklistsPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn(role: AuthRole = 'ENTRENADOR') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(sessionResponse(role))))
  await act(async () => { await auth.login({ correo: 'trainer@example.test', contrasena: 'test-password' }) })
}
function mockList(response: unknown = [latest, older]) {
  server.use(http.get('/api/checklists/mios', ({ request }) => {
    expect(request.headers.get('Authorization')).toContain('Bearer ')
    return HttpResponse.json(response)
  }))
}

describe('MisChecklistsPage', () => {
  it('lists backend order and derives per-checklist totals and Lima dates without fake pagination', async () => {
    mockList()
    renderPage()
    await signIn()
    expect(await screen.findByText('INSPECCIÓN · #42')).toBeInTheDocument()
    expect(screen.getByText('INSPECCIÓN · #41')).toBeInTheDocument()
    const cards = Array.from(document.querySelectorAll('.checklist-record'))
    expect(cards).toHaveLength(2)
    expect(cards[0]).toHaveTextContent('INSPECCIÓN · #42')
    expect(cards[1]).toHaveTextContent('INSPECCIÓN · #41')
    expect(cards[0]).toHaveTextContent('Revisión de apertura')
    const counts = within(cards[0] as HTMLElement).getAllByText('1')
    expect(counts).toHaveLength(2)
    expect(within(cards[0] as HTMLElement).getByText('2')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Registrar revisión' })).toHaveAttribute('href', '/entrenamiento')
    expect(screen.queryByRole('button', { name: /Siguiente|Anterior/ })).not.toBeInTheDocument()
    expect(screen.getByText(/6 nov\. 2026, 7:00 a\. m\./)).toBeInTheDocument()
  })

  it('fetches the selected checklist detail and only displays contracted detail and associated incidence fields', async () => {
    const user = userEvent.setup()
    mockList()
    let detailCalls = 0
    server.use(http.get('/api/checklists/42', ({ request }) => {
      detailCalls += 1
      expect(request.headers.get('Authorization')).toContain('Bearer ')
      return HttpResponse.json(latest)
    }))
    renderPage()
    await signIn()
    const latestCard = (await screen.findByText('INSPECCIÓN · #42')).closest('.checklist-record') as HTMLElement
    await user.click(within(latestCard).getByRole('button', { name: 'Ver detalle de la revisión 42' }))

    expect(await screen.findByRole('heading', { name: 'Detalle de revisión' })).toBeInTheDocument()
    expect(screen.getByText('Caminadora 1')).toBeInTheDocument()
    expect(screen.getByText('Máquina · Sala principal')).toBeInTheDocument()
    expect(screen.getByText('Correcto')).toBeInTheDocument()
    expect(screen.getByText('Sin ruido')).toBeInTheDocument()
    expect(screen.getByText('Zona de pesas')).toBeInTheDocument()
    expect(screen.getByText('Área')).toBeInTheDocument()
    expect(screen.getByText('Piso húmedo')).toBeInTheDocument()
    expect(screen.getByText('ALTA')).toBeInTheDocument()
    expect(screen.getByText('PENDIENTE')).toBeInTheDocument()
    expect(screen.getAllByText(/6 nov\. 2026/)).toHaveLength(2)
    expect(screen.queryByText(/historial de cambios/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Editar|Eliminar/ })).not.toBeInTheDocument()
    expect(detailCalls).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Volver a mis revisiones' }))
    expect(await screen.findByText('INSPECCIÓN · #42')).toBeInTheDocument()
  })

  it('opens an existing checklist detail from the dashboard deep link', async () => {
    mockList()
    server.use(http.get('/api/checklists/42', () => HttpResponse.json(latest)))
    render(<AuthProvider><MemoryRouter initialEntries={['/mis-checklists?checklist=42']}><Harness /></MemoryRouter></AuthProvider>)
    await signIn()

    expect(await screen.findByRole('heading', { name: 'Detalle de revisión' })).toBeInTheDocument()
    expect(screen.getByText('Caminadora 1')).toBeInTheDocument()
    expect(window.location.search).toBe('')
  })

  it('shows an empty state and navigation to create a checklist', async () => {
    mockList([])
    renderPage()
    await signIn()
    expect(await screen.findByText('Aún no has registrado inspecciones de equipos.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Registrar una revisión' })).toHaveAttribute('href', '/entrenamiento')
  })

  it('shows list errors and supports an explicit retry, with no automatic repeat', async () => {
    const user = userEvent.setup()
    let calls = 0
    server.use(http.get('/api/checklists/mios', () => {
      calls += 1
      return calls === 1 ? HttpResponse.json({}, { status: 403 }) : HttpResponse.json([])
    }))
    renderPage()
    await signIn()
    expect(await screen.findByRole('alert')).toHaveTextContent('permiso')
    expect(calls).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Aún no has registrado inspecciones de equipos.')).toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA', 'NUTRICIONISTA'] as const)('does not load checklist data for %s', async (role) => {
    let calls = 0
    server.use(http.get('/api/checklists/mios', () => { calls += 1; return HttpResponse.json([latest]) }))
    renderPage()
    await signIn(role)
    expect(await screen.findByText('Esta funcionalidad está disponible únicamente para entrenadores.')).toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
