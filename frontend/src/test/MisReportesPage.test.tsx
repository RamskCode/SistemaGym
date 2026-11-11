import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { MisReportesPage } from '../app/MisReportesPage'
import { server } from './server'

let auth: AuthContextValue
const elemento = { idElemento: 8, nombre: 'Caminadora 1', tipo: 'MAQUINA', descripcion: 'Equipo', ubicacion: 'Sala principal', activo: true, fechaCreacion: '2026-01-01T10:00:00', fechaActualizacion: '2026-01-01T10:00:00' }
const reporte = {
  idReporte: 91, idEntrenador: 19, correoEntrenador: 'trainer@example.test', idElemento: 8,
  nombreElemento: 'Caminadora 1', idChecklist: null, idDetalleChecklist: null,
  descripcion: 'Banda desgastada', prioridad: 'ALTA', estado: 'EN_REVISION',
  fechaReporte: '2026-11-06T07:00:00-05:00', fechaActualizacion: '2026-11-06T08:00:00-05:00',
  fechaSolucion: null, historial: [
    { idHistorial: 1, idUsuario: 19, correoUsuario: 'trainer@example.test', estadoAnterior: null, estadoNuevo: 'PENDIENTE', observacion: null, fechaHora: '2026-11-06T07:00:00-05:00' },
    { idHistorial: 2, idUsuario: 2, correoUsuario: 'admin@example.test', estadoAnterior: 'PENDIENTE', estadoNuevo: 'EN_REVISION', observacion: 'Se evaluará el equipo', fechaHora: '2026-11-06T08:00:00-05:00' },
  ],
}
function sessionResponse(role: AuthRole = 'ENTRENADOR') {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'trainer@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <MisReportesPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn(role: AuthRole = 'ENTRENADOR') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(sessionResponse(role))))
  await act(async () => { await auth.login({ correo: 'trainer@example.test', contrasena: 'test-password' }) })
}
function mockInitial(list: unknown = [reporte]) {
  server.use(http.get('/api/reportes-incidencias/mios', ({ request }) => {
    expect(request.headers.get('Authorization')).toContain('Bearer ')
    return HttpResponse.json(list)
  }))
  server.use(http.get('/api/elementos-revision/activos', ({ request }) => {
    expect(request.headers.get('Authorization')).toContain('Bearer ')
    return HttpResponse.json([elemento])
  }))
}

describe('MisReportesPage', () => {
  it('shows the own report list and backend status, priority, description and Lima date', async () => {
    mockInitial()
    renderPage()
    await signIn()
    expect(await screen.findByText('Caminadora 1')).toBeInTheDocument()
    expect(screen.getByText('Banda desgastada')).toBeInTheDocument()
    expect(screen.getByText('ALTA')).toBeInTheDocument()
    expect(screen.getByText('En revisión')).toBeInTheDocument()
    expect(screen.getByText(/6 nov\. 2026, 7:00 a\. m\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mis checklists' })).toHaveAttribute('href', '/mis-checklists')
    expect(screen.getByRole('link', { name: 'Nueva revisión' })).toHaveAttribute('href', '/entrenamiento')
    expect(screen.queryByRole('button', { name: /Editar|Eliminar|Cambiar estado/ })).not.toBeInTheDocument()
  })

  it('loads detail by ID and renders contracted element and status history read-only', async () => {
    const user = userEvent.setup()
    mockInitial()
    let calls = 0
    server.use(http.get('/api/reportes-incidencias/91', ({ request }) => {
      calls += 1
      expect(request.headers.get('Authorization')).toContain('Bearer ')
      return HttpResponse.json(reporte)
    }))
    renderPage()
    await signIn()
    await user.click(await screen.findByRole('button', { name: 'Ver detalle e historial' }))
    expect(await screen.findByRole('heading', { name: 'Detalle del reporte' })).toBeInTheDocument()
    expect(screen.getByText('Elemento afectado')).toBeInTheDocument()
    expect(screen.getByText('Caminadora 1')).toBeInTheDocument()
    expect(screen.getByText(/Pendiente → En revisión/)).toBeInTheDocument()
    expect(screen.getByText('Se evaluará el equipo')).toBeInTheDocument()
    expect(screen.getByText(/admin@example\.test/)).toBeInTheDocument()
    expect(screen.getByText('El historial es de solo lectura.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Editar|Eliminar|Cambiar estado/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Cambiar estado/ })).not.toBeInTheDocument()
    expect(calls).toBe(1)
  })

  it('shows empty state and available direct report action', async () => {
    const user = userEvent.setup()
    mockInitial([])
    renderPage()
    await signIn()
    expect(await screen.findByText('Aún no has registrado reportes de incidencias.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Nueva incidencia' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText(/Elemento afectado/)).toBeInTheDocument()
  })

  it('validates required values and 4000-character description limit before confirmation', async () => {
    const user = userEvent.setup()
    let posts = 0
    mockInitial([])
    server.use(http.post('/api/reportes-incidencias', () => { posts += 1; return HttpResponse.json(reporte, { status: 201 }) }))
    renderPage()
    await signIn()
    await user.click((await screen.findAllByRole('button', { name: 'Nueva incidencia' }))[0])
    const description = screen.getByRole('textbox', { name: /Descripción/ })
    expect(description).toHaveAttribute('maxLength', '4000')
    await user.selectOptions(screen.getByLabelText(/Elemento afectado/), '8')
    await user.type(description, '   ')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'ALTA')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('obligatoria')
    expect(posts).toBe(0)
    await user.clear(description)
    await user.type(description, 'Falla de equipo')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar incidencia' })).toHaveTextContent('Falla de equipo')
    expect(screen.getByRole('dialog', { name: 'Confirmar incidencia' })).toHaveTextContent('ALTA')
    expect(posts).toBe(0)
  })

  it('submits exactly one direct report after confirmation and displays server result', async () => {
    const user = userEvent.setup()
    let calls = 0
    let payload: unknown
    mockInitial([])
    server.use(http.post('/api/reportes-incidencias', async ({ request }) => {
      calls += 1
      payload = await request.json()
      return HttpResponse.json({ ...reporte, descripcion: 'Piso húmedo', prioridad: 'MEDIA', estado: 'PENDIENTE' }, { status: 201 })
    }))
    renderPage()
    await signIn()
    await user.click((await screen.findAllByRole('button', { name: 'Nueva incidencia' }))[0])
    await user.selectOptions(screen.getByLabelText(/Elemento afectado/), '8')
    await user.type(screen.getByRole('textbox', { name: /Descripción/ }), 'Piso húmedo')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'MEDIA')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByRole('dialog', { name: 'Confirmar incidencia' })).toHaveTextContent('Caminadora 1')
    expect(screen.getByRole('dialog', { name: 'Confirmar incidencia' })).toHaveTextContent('Piso húmedo')
    expect(calls).toBe(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Incidencia #91 registrada')
    expect(payload).toEqual({ idElemento: 8, descripcion: 'Piso húmedo', prioridad: 'MEDIA' })
    expect(calls).toBe(1)
  })

  it('disables duplicate submission while POST is in flight', async () => {
    const user = userEvent.setup()
    let calls = 0
    let release: (response: Response) => void = () => undefined
    mockInitial([])
    server.use(http.post('/api/reportes-incidencias', async () => {
      calls += 1
      return await new Promise<Response>((resolve) => { release = resolve })
    }))
    renderPage()
    await signIn()
    await user.click((await screen.findAllByRole('button', { name: 'Nueva incidencia' }))[0])
    await user.selectOptions(screen.getByLabelText(/Elemento afectado/), '8')
    await user.type(screen.getByRole('textbox', { name: /Descripción/ }), 'Ruido')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'BAJA')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(screen.getByRole('button', { name: 'Registrando…' })).toBeDisabled()
    expect(calls).toBe(1)
    release(HttpResponse.json(reporte, { status: 201 }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Incidencia #91 registrada'))
  })

  it('reports uncertain network outcome and blocks another POST', async () => {
    const user = userEvent.setup()
    let calls = 0
    mockInitial([])
    server.use(http.post('/api/reportes-incidencias', () => { calls += 1; return HttpResponse.error() }))
    renderPage()
    await signIn()
    await user.click((await screen.findAllByRole('button', { name: 'Nueva incidencia' }))[0])
    await user.selectOptions(screen.getByLabelText(/Elemento afectado/), '8')
    await user.type(screen.getByRole('textbox', { name: /Descripción/ }), 'Falla')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'ALTA')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Pudo registrarse; hay riesgo de duplicación')
    expect(screen.getByRole('button', { name: 'Nueva incidencia' })).toBeDisabled()
    expect(calls).toBe(1)
  })

  it('persists a server-error lock after remount and prevents a second report POST', async () => {
    const user = userEvent.setup()
    mockInitial([])
    let calls = 0
    server.use(http.post('/api/reportes-incidencias', () => { calls += 1; return HttpResponse.json({}, { status: 503 }) }))
    const page = renderPage()
    await signIn()
    await user.click((await screen.findAllByRole('button', { name: 'Nueva incidencia' }))[0])
    await user.selectOptions(screen.getByLabelText(/Elemento afectado/), '8')
    await user.type(screen.getByRole('textbox', { name: /Descripción/ }), 'Ruido')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'ALTA')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('riesgo de duplicación')
    expect(calls).toBe(1)
    page.unmount()
    renderPage()
    await signIn()
    expect(await screen.findByRole('alert')).toHaveTextContent('bloqueada por resultado incierto')
    expect(screen.getByRole('button', { name: 'Nueva incidencia' })).toBeDisabled()
    expect(calls).toBe(1)
  })

  it('does not announce success and keeps the lock for a malformed created-report response', async () => {
    const user = userEvent.setup()
    mockInitial([])
    server.use(http.post('/api/reportes-incidencias', () => HttpResponse.json({}, { status: 201 })))
    renderPage()
    await signIn()
    await user.click((await screen.findAllByRole('button', { name: 'Nueva incidencia' }))[0])
    await user.selectOptions(screen.getByLabelText(/Elemento afectado/), '8')
    await user.type(screen.getByRole('textbox', { name: /Descripción/ }), 'Ruido')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'ALTA')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('bloqueada por resultado incierto')
    expect(screen.queryByText(/registrada\. Estado/)).not.toBeInTheDocument()
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA', 'NUTRICIONISTA'] as const)('does not request reports or show controls to %s', async (role) => {
    let calls = 0
    server.use(http.get('/api/reportes-incidencias/mios', () => { calls += 1; return HttpResponse.json([reporte]) }))
    renderPage()
    await signIn(role)
    expect(await screen.findByText('Esta funcionalidad está disponible únicamente para entrenadores.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nueva incidencia' })).not.toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
