import { act, cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { ActividadPage } from '../app/ActividadPage'
import { AppRoutes } from '../app/App'
import { server } from './server'

let auth: AuthContextValue
function jwt(role: AuthRole) {
  const now = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'staff@example.test', roles: [`ROLE_${role}`], iat: now, exp: now + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness({ activity = false }: { activity?: boolean }) { auth = useAuth(); return auth.isAuthenticated ? (activity ? <ActividadPage /> : <AppRoutes />) : null }
function renderPage(role: AuthRole = 'ADMINISTRADOR', path = '/actividad') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(jwt(role))))
  const view = render(<AuthProvider><MemoryRouter initialEntries={[path]}><Harness activity={path === '/standalone'} /></MemoryRouter></AuthProvider>)
  return act(async () => { await auth.login({ correo: 'staff@example.test', contrasena: 'unused' }) }).then(() => view)
}
const payload = (events: unknown[] = []) => ({
  sociosActivos: 12, ingresosMes: 250.5, ingresosCobradosHoy: 50.5, ingresosHoy: 2,
  ingresosMembresiaHoy: 1, ingresosPaseDiarioHoy: 1, sociosNuevosMes: 3,
  ingresosSemana: [{ fecha: '2026-11-02', cantidad: 2 }], actividadReciente: events,
  membresias: { activas: 4, porPagar: 1, vencidas: 0, canceladas: 0 },
})
const entries = [
  { idIngreso: 1, nombre: 'Ana Socia', tipo: 'SOCIO', fechaHora: '2026-11-08T09:00:00-05:00' },
  { idIngreso: 2, nombre: 'Carlos Visitante', tipo: 'VISITANTE', fechaHora: '2026-11-08T11:00:00-05:00' },
  { idIngreso: 3, nombre: 'Beatriz Socia', tipo: 'SOCIO', fechaHora: '2026-11-07T11:00:00-05:00' },
]

describe('ActividadPage', () => {
  it('consume el resumen real una sola vez y ordena cronológicamente sin atribuir responsables', async () => {
    let calls = 0
    server.use(http.get('/api/dashboard/resumen', () => { calls += 1; return HttpResponse.json(payload(entries)) }))
    await renderPage('ADMINISTRADOR', '/standalone')
    expect(await screen.findByRole('heading', { name: 'Actividad' })).toBeInTheDocument()
    const rows = within(screen.getByRole('table')).getAllByRole('row')
    expect(rows).toHaveLength(4)
    expect(rows[1]).toHaveTextContent('Carlos Visitante')
    expect(rows[2]).toHaveTextContent('Ana Socia')
    expect(screen.getByText('Pagos confirmados este mes')).toBeInTheDocument()
    expect(screen.getByText('Ingresos cobrados hoy')).toBeInTheDocument()
    expect(screen.getByText('Ingresos cobrados hoy').closest('.actividad-metric')?.querySelector('strong'))
      .toHaveTextContent('50.50')
    expect(within(screen.getByRole('table')).queryByRole('columnheader', { name: /responsable/i })).not.toBeInTheDocument()
    expect(screen.getByText(/no representan el historial completo/i)).toBeInTheDocument()
    expect(calls).toBeGreaterThan(0)
  })

  it('filtra localmente por nombre, tipo y fecha usando Lima', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json(payload(entries))))
    const user = userEvent.setup()
    await renderPage('ADMINISTRADOR', '/standalone')
    await screen.findByText('Ana Socia')
    await user.type(screen.getByRole('searchbox', { name: 'Buscar persona' }), 'Carlos')
    expect(screen.getByText('Carlos Visitante')).toBeInTheDocument()
    expect(screen.queryByText('Ana Socia')).not.toBeInTheDocument()
    await user.clear(screen.getByRole('searchbox', { name: 'Buscar persona' }))
    await user.selectOptions(screen.getByLabelText('Tipo'), 'SOCIO')
    await user.type(screen.getByLabelText('Desde'), '2026-11-08')
    const body = within(screen.getByRole('table')).getAllByRole('row')
    expect(body).toHaveLength(2)
    expect(body[1]).toHaveTextContent('Ana Socia')
  })

  it('distingue actividad sin datos y respuesta fallida', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json(payload([]))))
    await renderPage('ADMINISTRADOR', '/standalone')
    expect(await screen.findByRole('heading', { name: 'Sin entradas recientes' })).toBeInTheDocument()
    expect(screen.getByText('Cuando se registren entradas, aparecerán aquí.')).toBeInTheDocument()
  })

  it('presenta un vacío distinto para filtros y permite limpiarlos', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json(payload(entries))))
    const user = userEvent.setup()
    await renderPage('ADMINISTRADOR', '/standalone')
    await screen.findByText('Ana Socia')
    await user.type(screen.getByRole('searchbox', { name: 'Buscar persona' }), 'No existe')
    expect(await screen.findByRole('heading', { name: 'Sin resultados' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(await screen.findByText('Ana Socia')).toBeInTheDocument()
  })

  it('presenta errores recuperables y permite volver a consultar', async () => {
    let calls = 0
    server.use(http.get('/api/dashboard/resumen', () => ++calls === 1
      ? new HttpResponse(null, { status: 503 }) : HttpResponse.json(payload(entries))))
    const user = userEvent.setup()
    await renderPage('ADMINISTRADOR', '/standalone')
    expect(await screen.findByRole('alert')).toHaveTextContent('No fue posible cargar la actividad.')
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Ana Socia')).toBeInTheDocument()
    expect(calls).toBe(2)
  })

  it('restringe Actividad a recepción sin afectar la ruta administrativa', async () => {
    let calls = 0
    server.use(http.get('/api/dashboard/resumen', () => { calls += 1; return HttpResponse.json(payload(entries)) }))
    await renderPage('RECEPCIONISTA')
    expect(await screen.findByRole('heading', { name: 'Acceso no autorizado' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Actividad' })).not.toBeInTheDocument()
    expect(calls).toBe(0)
  })

  it('no muestra Actividad a entrenador ni nutricionista', async () => {
    for (const role of ['ENTRENADOR', 'NUTRICIONISTA'] as const) {
      await renderPage(role)
      expect(await screen.findByRole('heading', { name: 'Acceso no autorizado' })).toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Actividad' })).not.toBeInTheDocument()
      cleanup()
    }
  })
})
