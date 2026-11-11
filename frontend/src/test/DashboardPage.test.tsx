import { act, render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { DashboardDemo } from '../app/DashboardDemo'
import { server } from './server'

let auth: AuthContextValue
function Harness() {
  auth = useAuth()
  return auth.isAuthenticated ? <DashboardDemo /> : null
}
function tokenPart(value: unknown) {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}
async function renderDashboard() {
  const now = Math.floor(Date.now() / 1000)
  server.use(http.post('/api/auth/login', () => HttpResponse.json({
    token: `${tokenPart({ alg: 'HS256', typ: 'JWT' })}.${tokenPart({ sub: 'admin@example.test', roles: ['ROLE_ADMINISTRADOR'], iat: now, exp: now + 600 })}.c2lnbmF0dXJl`,
    tipo: 'Bearer', expiraEnMs: 600_000,
  })))
  render(<AuthProvider><Harness /></AuthProvider>)
  await act(async () => auth.login({ correo: 'admin@example.test', contrasena: 'test' }))
}

afterEach(() => vi.restoreAllMocks())

describe('Dashboard de administración', () => {
  it('presenta ceros y estados vacíos cuando las consultas reales no devuelven registros', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json({
      sociosActivos: 0, ingresosMes: 0, ingresosCobradosHoy: 0, ingresosHoy: 0,
      ingresosMembresiaHoy: 0, ingresosPaseDiarioHoy: 0, sociosNuevosMes: 0,
      ingresosSemana: [], actividadReciente: [],
      membresias: { activas: 0, porPagar: 0, vencidas: 0, canceladas: 0 },
    })))
    await renderDashboard()
    expect(await screen.findByText('Todavía no hay actividad registrada.')).toBeInTheDocument()
    expect(screen.getByText('Aún no hay entradas en este periodo.')).toBeInTheDocument()
    expect(screen.getByText('Todavía no hay membresías registradas.')).toBeInTheDocument()
    expect(screen.getAllByText('0', { selector: 'strong' })).toHaveLength(3)
    expect(screen.queryByText(/fictici|muestra|vista previa/i)).not.toBeInTheDocument()
  })

  it('refleja las cifras y la actividad entregadas por el servicio de dashboard', async () => {
    server.use(http.get('/api/dashboard/resumen', () => HttpResponse.json({
      sociosActivos: 17, ingresosMes: 1450.5, ingresosCobradosHoy: 500.25, ingresosHoy: 4,
      ingresosMembresiaHoy: 3, ingresosPaseDiarioHoy: 1, sociosNuevosMes: 3,
      ingresosSemana: [
        { fecha: '2026-11-02', cantidad: 0 }, { fecha: '2026-11-03', cantidad: 4 },
        { fecha: '2026-11-04', cantidad: 3 }, { fecha: '2026-11-05', cantidad: 0 },
        { fecha: '2026-11-06', cantidad: 2 }, { fecha: '2026-11-07', cantidad: 1 },
        { fecha: '2026-11-08', cantidad: 4 },
      ],
      actividadReciente: [{ idIngreso: 9, nombre: 'jHoNaTaN OSORIO', tipo: 'SOCIO', fechaHora: '2026-11-08T12:00:00-05:00' }],
      membresias: { activas: 6, porPagar: 2, vencidas: 1, canceladas: 1 },
    })))
    await renderDashboard()
    expect(await screen.findByText('Jhonatan Osorio')).toBeInTheDocument()
    expect(screen.getByText('17')).toBeInTheDocument()
    expect(screen.getByText('4', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Entradas recientes registradas' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /Entradas por día: lun 2, 0; mar 3, 4/ })).toBeInTheDocument()
    expect(screen.getByText('Vencidas')).toBeInTheDocument()
    expect(screen.getByText('Canceladas')).toBeInTheDocument()
    expect(screen.queryByText(/modalidad/i)).not.toBeInTheDocument()
  })

  it('distingue un error de API de un resultado vacío y ofrece reintento', async () => {
    server.use(http.get('/api/dashboard/resumen', () => new HttpResponse(null, { status: 503 })))
    await renderDashboard()
    expect(await screen.findByRole('alert')).toHaveTextContent('No fue posible cargar los datos del panel.')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
    expect(screen.queryByText('Todavía no hay actividad registrada.')).not.toBeInTheDocument()
  })
})
