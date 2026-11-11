import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { PlanesPage } from '../app/PlanesPage'
import { server } from './server'

let auth: AuthContextValue
const initial = { idPlan: 7, nombre: 'Plan Base', descripcion: 'Rutina mensual', precio: 100, duracionDias: 30, estado: 'ACTIVO', fechaCreacion: '2026-05-01T10:00:00' }
const response = (role = 'ADMINISTRADOR') => {
  const iat = Math.floor(Date.now() / 1000)
  const enc = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc({ sub: 'admin@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <PlanesPage /> }
function renderPage(mockPrice = true) {
  if (mockPrice) server.use(http.get('/api/configuraciones/:key', ({ params }) => HttpResponse.json({
    idConfiguracion: 1, clave: params.key, valor: '8.00', descripcion: 'Tarifa de pase diario', fechaActualizacion: '2026-11-10T12:00:00',
  })))
  return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>)
}
async function signIn(role = 'ADMINISTRADOR') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(response(role))))
  await act(async () => { await auth.login({ correo: 'admin@example.test', contrasena: 'test-password' }) })
}
function getPlans() {
  server.use(http.get('/api/planes', () => HttpResponse.json([initial])))
  server.use(http.get('/api/planes/buscar', () => HttpResponse.json([initial])))
  server.use(http.get('/api/planes/estado/:state', () => HttpResponse.json([initial])))
}

afterEach(() => window.sessionStorage.removeItem('planes:escritura-incierta'))

describe('PlanesPage', () => {
  it('allows only an administrator to initialize a missing daily-pass price with an explicitly confirmed amount', async () => {
    const user = userEvent.setup()
    let configured = false
    getPlans(); renderPage(false)
    server.use(
      http.get('/api/configuraciones/:key', ({ params }) => configured
        ? HttpResponse.json({ idConfiguracion: 8, clave: params.key, valor: '17.50', descripcion: 'Tarifa de pase diario', fechaActualizacion: '2026-11-10T12:00:00' })
        : HttpResponse.json({ mensaje: 'No se encontro la configuración.' }, { status: 404 })),
      http.put('/api/configuraciones/:key', async ({ params, request }) => {
        expect(params.key).toBe('PRECIO_PASE_DIARIO')
        expect(await request.json()).toEqual({ valor: '17.50' })
        configured = true
        return HttpResponse.json({ idConfiguracion: 8, clave: params.key, valor: '17.50', descripcion: 'Tarifa de pase diario', fechaActualizacion: '2026-11-10T12:00:00' })
      }),
    )
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Pase diario' }))
    await user.click(await screen.findByRole('button', { name: 'Configurar tarifa' }))
    await user.type(screen.getByLabelText('Tarifa inicial (S/)'), '17.50')
    await user.click(screen.getByLabelText('Confirmo establecer esta tarifa para el pase diario.'))
    await user.click(screen.getByRole('button', { name: 'Guardar tarifa' }))
    expect(await screen.findByRole('status')).toHaveTextContent(/actualizada y verificada/)
    expect(screen.getByText('S/ 17.50')).toBeInTheDocument()
  })

  it('does not expose daily price creation or editing to receptionists', async () => {
    const user = userEvent.setup()
    getPlans(); renderPage(); await signIn('RECEPCIONISTA')
    await screen.findByText('Plan Base')
    await user.click(screen.getByRole('tab', { name: 'Pase diario' }))
    expect(screen.queryByRole('button', { name: /configurar tarifa|editar tarifa/i })).not.toBeInTheDocument()
  })

  it('switches between accessible tabs with click and arrow-key navigation', async () => {
    const user = userEvent.setup()
    getPlans(); renderPage(); await signIn()
    const memberships = screen.getByRole('tab', { name: 'Planes de membresía' })
    const daily = screen.getByRole('tab', { name: 'Pase diario' })
    expect(memberships).toHaveAttribute('aria-selected', 'true')
    await user.click(daily)
    expect(daily).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('S/ 8.00')).toBeInTheDocument()
    expect(screen.getByText('Vigencia · 1 día calendario')).toBeInTheDocument()
    daily.focus()
    await user.keyboard('{ArrowLeft}')
    expect(memberships).toHaveFocus()
    expect(memberships).toHaveAttribute('aria-selected', 'true')
  })

  it('lists plans and sends search and status filters to backend endpoints', async () => {
    const user = userEvent.setup()
    const calls: string[] = []
    server.use(
      http.get('/api/planes', () => { calls.push('all'); return HttpResponse.json([initial]) }),
      http.get('/api/planes/buscar', ({ request }) => { calls.push(`name:${new URL(request.url).searchParams.get('nombre')}`); return HttpResponse.json([initial]) }),
      http.get('/api/planes/estado/:state', ({ params }) => { calls.push(`state:${params.state}`); return HttpResponse.json([initial]) }),
      http.get('/api/planes/7', () => HttpResponse.json(initial)),
    )
    renderPage(); await signIn()
    expect(await screen.findByText('Plan Base')).toBeInTheDocument()
    expect(screen.getByText(/100\.00/)).toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('Buscar por nombre del plan'), 'Plan Base')
    await user.selectOptions(screen.getByLabelText('Filtrar por estado'), 'ACTIVO')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    await waitFor(() => expect(calls).toContain('name:Plan Base'))
    expect(calls).toContain('state:ACTIVO')
    expect(screen.getByText('30 días')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver detalle de Plan Base' }))
    expect(await screen.findByRole('dialog', { name: 'Detalle del plan' })).toHaveTextContent('Rutina mensual')
  })

  it('requires review and confirmation before posting one creation; blocks duplicate submission while pending', async () => {
    const user = userEvent.setup()
    let submissions = 0
    let release!: (response: Response) => void
    getPlans()
    server.use(http.post('/api/planes', async ({ request }) => {
      submissions += 1
      expect(await request.json()).toEqual({ nombre: 'Plan Premium', descripcion: 'Acceso total', precio: 149.5, duracionDias: 45 })
      return await new Promise<Response>((resolve) => { release = resolve })
    }))
    renderPage(); await signIn()
    await screen.findByText('Plan Base')
    await user.click(screen.getByRole('button', { name: 'Nuevo plan' }))
    await user.type(screen.getByLabelText('Nombre del plan'), 'Plan Premium')
    await user.type(screen.getByLabelText('Descripción del plan'), 'Acceso total')
    await user.type(screen.getByLabelText('Precio'), '149.50')
    await user.type(screen.getByLabelText('Duración en días'), '45')
    await user.click(screen.getByRole('button', { name: 'Revisar plan' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar creación' })).toBeInTheDocument()
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Volver a editar' })).toHaveFocus()
    expect(submissions).toBe(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByRole('button', { name: 'Enviando…' })).toBeDisabled()
    expect(submissions).toBe(1)
    release(HttpResponse.json({ ...initial, idPlan: 8, nombre: 'Plan Premium', precio: 149.5, duracionDias: 45 }, { status: 201 }))
    expect(await screen.findByRole('status')).toHaveTextContent(/Plan “Plan Premium” creado/)
    expect(submissions).toBe(1)
  })

  it('loads actual detail before editing, confirms exact PUT and communicates membership price distinction', async () => {
    const user = userEvent.setup()
    let body: unknown
    getPlans()
    server.use(http.get('/api/planes/7', () => HttpResponse.json(initial)))
    server.use(http.put('/api/planes/7', async ({ request }) => { body = await request.json(); return HttpResponse.json({ ...initial, nombre: 'Plan Actualizado', precio: 120 }) }))
    renderPage(); await signIn()
    await screen.findByText('Plan Base')
    const row = screen.getByRole('row', { name: /Plan Base/ })
    await user.click(within(row).getByRole('button', { name: 'Editar Plan Base' }))
    expect(await screen.findByDisplayValue('Plan Base')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Nombre del plan'))
    await user.type(screen.getByLabelText('Nombre del plan'), 'Plan Actualizado')
    await user.clear(screen.getByLabelText('Precio'))
    await user.type(screen.getByLabelText('Precio'), '120')
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }))
    expect(await screen.findByText(/precio acordado de membresías existentes no se modifica/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByRole('status')).toHaveTextContent(/precio acordado de membresías existentes no cambia/)
    expect(body).toEqual({ nombre: 'Plan Actualizado', descripcion: 'Rutina mensual', precio: 120, duracionDias: 30 })
  })

  it('confirms deactivation and does not expose reactivation or deletion', async () => {
    const user = userEvent.setup()
    getPlans()
    server.use(http.patch('/api/planes/7/desactivar', () => HttpResponse.json({ ...initial, estado: 'INACTIVO' })))
    renderPage(); await signIn()
    const row = await screen.findByRole('row', { name: /Plan Base/ })
    await user.click(within(row).getByRole('button', { name: 'Desactivar Plan Base' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar desactivación' })).toHaveTextContent(/no estará disponible para nuevas membresías/)
    await user.click(screen.getByRole('button', { name: 'Confirmar desactivación' }))
    expect(await screen.findByRole('status')).toHaveTextContent(/quedó inactivo/)
    expect(screen.queryByRole('button', { name: /reactivar|eliminar/i })).not.toBeInTheDocument()
  })

  it('locks mutations after uncertain network response until explicit refresh and verification', async () => {
    const user = userEvent.setup()
    window.sessionStorage.removeItem('planes:escritura-incierta')
    let submissions = 0
    getPlans()
    server.use(http.post('/api/planes', () => { submissions += 1; return HttpResponse.error() }))
    renderPage(); await signIn()
    await screen.findByText('Plan Base')
    await user.click(screen.getByRole('button', { name: 'Nuevo plan' }))
    await user.type(screen.getByLabelText('Nombre del plan'), 'Plan Incierto')
    await user.type(screen.getByLabelText('Precio'), '80')
    await user.type(screen.getByLabelText('Duración en días'), '30')
    await user.click(screen.getByRole('button', { name: 'Revisar plan' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }))
    expect(await screen.findByText(/No pudimos confirmar el cambio\. Revisa los planes/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nuevo plan' })).toBeDisabled()
    expect(submissions).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Actualizar planes' }))
    expect(screen.getByRole('button', { name: 'Nuevo plan' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Confirmar revisión' }))
    await waitFor(() => expect(screen.queryByText(/No se confirmó el último cambio/)).not.toBeInTheDocument())
    expect(window.sessionStorage.getItem('planes:escritura-incierta')).toBeNull()
  })

  it('shows API errors and recovers by reloading the plan list', async () => {
    const user = userEvent.setup()
    let attempt = 0
    server.use(http.get('/api/planes', () => ++attempt === 1
      ? HttpResponse.json({ mensaje: 'invalid' }, { status: 400 })
      : HttpResponse.json([])))
    renderPage(); await signIn()
    expect(await screen.findByText(/revisa los datos ingresados/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar consulta' }))
    expect(await screen.findByText('Aún no hay planes registrados.')).toBeInTheDocument()
  })

  it('traps keyboard focus in the detail dialog, closes with Escape, and restores focus to its row action', async () => {
    const user = userEvent.setup()
    getPlans()
    server.use(http.get('/api/planes/7', () => HttpResponse.json(initial)))
    renderPage(); await signIn()
    await screen.findByRole('button', { name: 'Ver detalle de Plan Base' })
    const triggerSelector = '[data-plan-detail-trigger="7"]'
    const detailTrigger = document.querySelector<HTMLButtonElement>(triggerSelector)!
    await user.click(detailTrigger)
    const dialog = await screen.findByRole('dialog', { name: 'Detalle del plan' })
    expect(screen.getByRole('button', { name: 'Cerrar detalle' })).toHaveFocus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(document.querySelector(triggerSelector)).toHaveFocus())
  })
})
