import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { RevisionGimnasioPage } from '../app/RevisionGimnasioPage'
import { server } from './server'

let auth: AuthContextValue
const activeElement = { idElemento: 8, nombre: 'Caminadora 1', tipo: 'MAQUINA', descripcion: 'Equipo cardiovascular', ubicacion: 'Sala principal', activo: true, fechaCreacion: '2026-01-01T10:00:00', fechaActualizacion: '2026-01-01T10:00:00' }
const elementArea = { ...activeElement, idElemento: 9, nombre: 'Zona de pesas', tipo: 'AREA', ubicacion: null, descripcion: null }

function sessionResponse(role: AuthRole) {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'trainer@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <RevisionGimnasioPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn(role: AuthRole = 'ENTRENADOR') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(sessionResponse(role))))
  await act(async () => { await auth.login({ correo: 'trainer@example.test', contrasena: 'test-password' }) })
}
function mockElements(items = [activeElement, elementArea]) {
  server.use(http.get('/api/elementos-revision/activos', ({ request }) => {
    expect(request.headers.get('Authorization')).toContain('Bearer ')
    return HttpResponse.json(items)
  }))
}

describe('RevisionGimnasioPage', () => {
  it('loads active element labels, selects unique items, confirms, and submits one payload with conditional problem fields', async () => {
    const user = userEvent.setup()
    mockElements()
    let postCount = 0
    let payload: unknown
    server.use(http.post('/api/checklists', async ({ request }) => {
      postCount += 1
      payload = await request.json()
      return HttpResponse.json({
        idChecklist: 42, idEntrenador: 23, correoEntrenador: 'trainer@example.test',
        fechaHora: '2026-11-06T07:00:00-05:00', observaciones: 'Apertura',
        detalles: [
          { idDetalle: 1, idElemento: 8, nombreElemento: 'Caminadora 1', tipoElemento: 'MAQUINA', ubicacionElemento: 'Sala principal', resultado: 'CORRECTO', observaciones: 'Sin ruido', fechaRevision: '2026-11-06T07:00:00-05:00', incidencia: null },
          { idDetalle: 2, idElemento: 9, nombreElemento: 'Zona de pesas', tipoElemento: 'AREA', ubicacionElemento: null, resultado: 'CON_PROBLEMA', observaciones: null, fechaRevision: '2026-11-06T07:00:00-05:00', incidencia: { idReporte: 77, descripcion: 'Piso húmedo', prioridad: 'ALTA', estado: 'PENDIENTE', fechaReporte: '2026-11-06T07:00:00-05:00' } },
        ],
      }, { status: 201 })
    }))
    renderPage()
    await signIn()
    const treadmill = await screen.findByLabelText('Incluir Caminadora 1 en la revisión')
    expect(screen.getByText(/Máquina · Sala principal/)).toBeInTheDocument()
    expect(screen.getByText('Equipo cardiovascular')).toBeInTheDocument()
    expect(screen.getByLabelText('Incluir Zona de pesas en la revisión')).toBeInTheDocument()
    await user.click(treadmill)
    await user.click(screen.getByLabelText('Incluir Zona de pesas en la revisión'))
    const selects = screen.getAllByLabelText(/Estado del equipo/)
    await user.selectOptions(selects[1], 'CON_PROBLEMA')
    expect(await screen.findByLabelText(/Describe la incidencia/)).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Describe la incidencia/), 'Piso húmedo')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'ALTA')
    await user.type(screen.getAllByRole('textbox', { name: /Observación de este equipo/ })[0], 'Sin ruido')
    await user.type(screen.getByRole('textbox', { name: /Observaciones generales/ }), 'Apertura')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('2 elementos')
    expect(screen.getByRole('dialog')).toHaveTextContent('El servidor creará automáticamente')
    expect(postCount).toBe(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Registro #42')
    expect(screen.getByRole('status')).toHaveTextContent('incidencia #77')
    expect(payload).toEqual({
      observaciones: 'Apertura',
      detalles: [
        { idElemento: 8, resultado: 'CORRECTO', observaciones: 'Sin ruido' },
        { idElemento: 9, resultado: 'CON_PROBLEMA', descripcionIncidencia: 'Piso húmedo', prioridadIncidencia: 'ALTA' },
      ],
    })
    expect(postCount).toBe(1)
  })

  it('removes incidence fields when a selected item returns to CORRECTO', async () => {
    const user = userEvent.setup()
    mockElements()
    let payload: Record<string, unknown> | undefined
    server.use(http.post('/api/checklists', async ({ request }) => {
      payload = await request.json() as Record<string, unknown>
      return HttpResponse.json({ idChecklist: 5, idEntrenador: 23, correoEntrenador: 'trainer@example.test', fechaHora: '2026-11-06T07:00:00-05:00', observaciones: null, detalles: [] }, { status: 201 })
    }))
    renderPage()
    await signIn()
    await user.click(await screen.findByLabelText('Incluir Caminadora 1 en la revisión'))
    await user.selectOptions(screen.getByLabelText(/Estado del equipo/), 'CON_PROBLEMA')
    await user.type(screen.getByLabelText(/Describe la incidencia/), 'Ruido')
    await user.selectOptions(screen.getByLabelText(/Prioridad/), 'MEDIA')
    await user.selectOptions(screen.getByLabelText(/Estado del equipo/), 'CORRECTO')
    expect(screen.queryByLabelText(/Describe la incidencia/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    await waitFor(() => expect(payload).toBeDefined())
    expect(payload).toEqual({ detalles: [{ idElemento: 8, resultado: 'CORRECTO' }] })
  })

  it('blocks empty checklist, validates required problem fields, and enforces actual character and item limits', async () => {
    const user = userEvent.setup()
    const manyElements = Array.from({ length: 101 }, (_, index) => ({ ...activeElement, idElemento: index + 1, nombre: `Equipo ${index + 1}` }))
    mockElements(manyElements)
    renderPage()
    await signIn()
    await screen.findByText('Equipo 1')
    expect(screen.getByRole('button', { name: 'Revisar y confirmar' })).toBeDisabled()
    act(() => {
      for (let index = 1; index <= 100; index += 1) {
        const checkbox = document.getElementById(`elemento-${index}-seleccionar`)
        if (!(checkbox instanceof HTMLInputElement)) throw new Error(`Falta checkbox de elemento ${index}`)
        checkbox.click()
      }
    })
    expect(screen.getByText('100 de 100 incluidos')).toBeInTheDocument()
    expect(document.getElementById('elemento-101-seleccionar')).toBeDisabled()
    const observaciones = screen.getByRole('textbox', { name: /Observaciones generales/ })
    expect(observaciones).toHaveAttribute('maxLength', '1000')
    await user.click(document.getElementById('elemento-1-seleccionar')!)
    await user.click(document.getElementById('elemento-1-seleccionar')!)
    await user.selectOptions(screen.getAllByLabelText(/Estado del equipo/)[0], 'CON_PROBLEMA')
    const description = screen.getByLabelText(/Describe la incidencia/)
    expect(description).toHaveAttribute('maxLength', '4000')
    expect(screen.getAllByRole('textbox', { name: /Observación de este equipo/ })[0]).toHaveAttribute('maxLength', '1000')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Describe la incidencia/)).toBeInvalid()
  }, 15_000)

  it('prevents duplicate submission while one request is in flight', async () => {
    const user = userEvent.setup()
    mockElements()
    let count = 0
    let release: (response: Response) => void = () => undefined
    server.use(http.post('/api/checklists', async () => {
      count += 1
      return await new Promise<Response>((resolve) => { release = resolve })
    }))
    renderPage()
    await signIn()
    await user.click(await screen.findByLabelText(/Caminadora 1/))
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(screen.getByRole('button', { name: 'Registrando…' })).toBeDisabled()
    expect(count).toBe(1)
    release(HttpResponse.json({ idChecklist: 6, idEntrenador: 23, correoEntrenador: 'trainer@example.test', fechaHora: '2026-11-06T07:00:00-05:00', observaciones: null, detalles: [] }, { status: 201 }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Registro #6'))
  })

  it('shows uncertain outcome on network failure and offers no automatic retry', async () => {
    const user = userEvent.setup()
    mockElements()
    let attempts = 0
    server.use(http.post('/api/checklists', () => { attempts += 1; return HttpResponse.error() }))
    renderPage()
    await signIn()
    await user.click(await screen.findByLabelText(/Caminadora 1/))
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Pudo registrarse y generar incidencias automáticas')
    expect(screen.getByRole('alert')).toHaveTextContent('requiere revisión administrativa')
    expect(attempts).toBe(1)
    expect(screen.getByRole('button', { name: 'Checklist bloqueado' })).toBeDisabled()
  })

  it('keeps a checklist 5xx lock after route remount and does not post again', async () => {
    const user = userEvent.setup()
    mockElements()
    let attempts = 0
    server.use(http.post('/api/checklists', () => { attempts += 1; return HttpResponse.json({}, { status: 500 }) }))
    const page = renderPage()
    await signIn()
    await user.click(await screen.findByLabelText(/Caminadora 1/))
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Pudo registrarse')
    expect(attempts).toBe(1)
    page.unmount()
    renderPage()
    await signIn()
    expect(await screen.findByRole('alert')).toHaveTextContent('Checklist bloqueado por resultado incierto')
    expect(screen.getByRole('button', { name: 'Checklist bloqueado' })).toBeDisabled()
    expect(attempts).toBe(1)
  })

  it('keeps the checklist blocked when a 201 response omits its checklist identifier', async () => {
    const user = userEvent.setup()
    mockElements()
    server.use(http.post('/api/checklists', () => HttpResponse.json({}, { status: 201 })))
    renderPage()
    await signIn()
    await user.click(await screen.findByLabelText(/Caminadora 1/))
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Checklist bloqueado por resultado incierto')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('traps focus in checklist confirmation, closes with Escape, and restores focus to submitter', async () => {
    const user = userEvent.setup()
    mockElements()
    renderPage()
    await signIn()
    await user.click(await screen.findByLabelText(/Caminadora 1/))
    const submit = screen.getByRole('button', { name: 'Revisar y confirmar' })
    await user.click(submit)
    const dialog = screen.getByRole('dialog', { name: '3. Revisar y confirmar' })
    const back = within(dialog).getByRole('button', { name: 'Volver a revisar' })
    const confirm = within(dialog).getByRole('button', { name: 'Confirmar y registrar' })
    expect(back).toHaveFocus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(confirm).toHaveFocus()
    await user.keyboard('{Tab}')
    expect(back).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(submit).toHaveFocus()
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA', 'NUTRICIONISTA'] as const)('denies the checklist page to %s without loading protected data', async (role) => {
    let calls = 0
    server.use(http.get('/api/elementos-revision/activos', () => { calls += 1; return HttpResponse.json([activeElement]) }))
    renderPage()
    await signIn(role)
    expect(await screen.findByText('Esta funcionalidad está disponible únicamente para entrenadores.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revisar y confirmar' })).not.toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
