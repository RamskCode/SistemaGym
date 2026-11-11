import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { AppRoutes } from '../app/App'
import { server } from './server'

let auth: AuthContextValue
function Harness() { auth = useAuth(); return auth.isAuthenticated ? <AppRoutes /> : null }
function token() {
  const now = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'recepcion@example.test', roles: ['ROLE_RECEPCIONISTA'], iat: now, exp: now + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
async function renderReception(path = '/') {
  server.use(
    http.get('/api/ingresos/socios/habilitados', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })),
    http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([])),
    http.get('/api/asistencias-laborales/mis-asistencias', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 })),
  )
  server.use(http.post('/api/auth/login', () => HttpResponse.json(token())))
  const view = render(<AuthProvider><MemoryRouter initialEntries={[path]}><Harness /></MemoryRouter></AuthProvider>)
  await act(async () => { await auth.login({ correo: 'recepcion@example.test', contrasena: 'unused' }) })
  return view
}

const summary = (overrides: Record<string, unknown> = {}) => ({
  sociosActivos: 9, ingresosMes: 400, ingresosCobradosHoy: 75.5, ingresosHoy: 3,
  ingresosMembresiaHoy: 2, ingresosPaseDiarioHoy: 1, sociosNuevosMes: 1,
  ingresosSemana: [],
  actividadReciente: [
    { idIngreso: 10, nombre: 'Ana Socia', tipo: 'SOCIO', fechaHora: '2026-11-10T11:00:00-05:00' },
    { idIngreso: 9, nombre: 'Luis Visitante', tipo: 'VISITANTE', fechaHora: '2026-11-10T09:00:00-05:00' },
  ],
  membresias: { activas: 2, porPagar: 0, vencidas: 0, canceladas: 0 },
  ...overrides,
})

describe('Inicio de RECEPCIONISTA', () => {
  it('presenta indicadores y últimos accesos del resumen real en el orden más reciente primero', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json(summary())))
    await renderReception()

    expect(await screen.findByRole('heading', { name: 'Resumen del día' })).toBeInTheDocument()
    expect(screen.getByText('Accesos, cobros y actividad del gimnasio')).toBeInTheDocument()
    const indicators = within(screen.getByRole('region', { name: 'Indicadores del día' }))
    expect(indicators.getByText('Entradas hoy').closest('.reception-dashboard__metric')).toHaveTextContent('3')
    expect(indicators.getByText('Cobrado hoy').closest('.reception-dashboard__metric')).toHaveTextContent('75.50')
    expect(indicators.getByText('Con membresía').closest('.reception-dashboard__metric')).toHaveTextContent('2')
    expect(indicators.getByText('Con pase diario').closest('.reception-dashboard__metric')).toHaveTextContent('1')
    const rows = within(screen.getByRole('table')).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('Ana Socia')
    expect(rows[1]).toHaveTextContent('Con membresía')
    expect(rows[2]).toHaveTextContent('Luis Visitante')
    expect(rows[2]).toHaveTextContent('Pase diario')
    expect(screen.queryByRole('link', { name: 'Actividad' })).not.toBeInTheDocument()
  })

  it('abre las subsecciones solicitadas desde los accesos rápidos', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json(summary())))
    const user = userEvent.setup()
    await renderReception()
    await screen.findByRole('heading', { name: 'Resumen del día' })

    await user.click(screen.getByRole('link', { name: 'Ingreso con membresía' }))
    expect(await screen.findByRole('tab', { name: 'Con membresía' })).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByRole('link', { name: 'Inicio' }))
    await screen.findByRole('heading', { name: 'Resumen del día' })
    await user.click(screen.getByRole('link', { name: 'Pase diario' }))
    expect(await screen.findByRole('tab', { name: 'Pase diario' })).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByRole('link', { name: 'Inicio' }))
    await screen.findByRole('heading', { name: 'Resumen del día' })
    await user.click(screen.getByRole('link', { name: 'Asistencia laboral' }))
    expect(await screen.findByRole('tab', { name: 'Asistencia laboral' })).toHaveAttribute('aria-selected', 'true')
  })

  it('actualiza los indicadores al regresar a Inicio sin recargar el navegador', async () => {
    let datosActualizados = false
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json(summary(datosActualizados
      ? { ingresosHoy: 4, ingresosMembresiaHoy: 3, ingresosPaseDiarioHoy: 1, ingresosCobradosHoy: 100 }
      : {}))))
    const user = userEvent.setup()
    await renderReception()
    await screen.findByRole('heading', { name: 'Resumen del día' })
    const entryValue = () => screen.getByText('Entradas hoy').closest('.reception-dashboard__metric')?.querySelector('strong')
    expect(entryValue()).toHaveTextContent('3')

    await user.click(screen.getByRole('link', { name: 'Asistencia' }))
    expect(await screen.findByRole('heading', { name: 'Asistencia' })).toBeInTheDocument()
    datosActualizados = true
    await user.click(screen.getByRole('link', { name: 'Inicio' }))
    await waitFor(() => expect(entryValue()).toHaveTextContent('4'))
    expect(screen.getByText('Cobrado hoy').closest('.reception-dashboard__metric')).toHaveTextContent('100.00')
  })

  it('presenta un vacío amigable y recupera errores con reintento', async () => {
    let allowSuccess = false
    server.use(http.get('/api/dashboard/resumen', () => allowSuccess
      ? HttpResponse.json(summary({ actividadReciente: [] }))
      : new HttpResponse(null, { status: 503 })))
    const user = userEvent.setup()
    await renderReception()
    expect(await screen.findByRole('alert')).toHaveTextContent('No fue posible cargar el resumen del día.')
    allowSuccess = true
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { name: 'Aún no hay ingresos' })).toBeInTheDocument()
  })
})
