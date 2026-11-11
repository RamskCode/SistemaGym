import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { ReportesPage } from '../app/ReportesPage'
import { server } from './server'

let auth: AuthContextValue
function jwt() {
  const now = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'admin@example.test', roles: ['ROLE_ADMINISTRADOR'], iat: now, exp: now + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return auth.isAuthenticated ? <ReportesPage /> : null }
async function renderPage() {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(jwt())))
  const view = render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>)
  await act(async () => { await auth.login({ correo: 'admin@example.test', contrasena: 'unused' }) })
  return view
}
const summary = {
  ingresosConfirmados: 125.5, pagosConfirmados: 2, sociosNuevos: 3,
  membresiasActivas: 4, membresiasVencidas: 1, membresiasProximasAVencer: 2,
  ingresosSocios: 8, ingresosVisitantes: 5, personasUnicas: 11,
  personasUnicasSocios: 8, personasUnicasVisitantes: 5,
  pagosPorFecha: [{ etiqueta: '2026-11', cantidad: 2, monto: 125.5, referenciaId: null }],
  sociosPorFecha: [{ etiqueta: '2026-11', cantidad: 3, monto: 0, referenciaId: null }],
  ingresosPorFecha: [{ etiqueta: '2026-11', cantidad: 13, monto: 0, referenciaId: null }],
  membresiasPorPlan: [{ etiqueta: 'Mensual', cantidad: 2, monto: 0, referenciaId: 5 }],
}

describe('ReportesPage', () => {
  it('consume datos reales del API y presenta indicadores y detalle', async () => {
    server.use(
      http.get('/api/reportes/resumen', () => HttpResponse.json(summary)),
      http.get('/api/reportes/detalle', () => HttpResponse.json([{ id: 18, tipo: 'PAGO', persona: 'Pago #18', detalle: 'EFECTIVO', estado: 'CONFIRMADO', monto: 125.5, fecha: '2026-11-09T10:00:00' }])),
      http.get('/api/reportes/planes', () => HttpResponse.json([{ id: 5, nombre: 'Mensual' }])),
    )
    await renderPage()
    expect(await within(screen.getByLabelText('Indicadores del período')).findByText(/125\.50/)).toBeInTheDocument()
    expect(screen.getByText('Membresías activas')).toBeInTheDocument()
    expect(screen.getAllByText('Estado actual')).toHaveLength(2)
    expect(await screen.findByText('Pago #18')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Mensual' })).toHaveValue('5')
    expect(within(screen.getByRole('table')).getByText('CONFIRMADO')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Ingresos económicos por mes' })).toBeInTheDocument()
    expect(screen.getByRole('table').querySelector('caption')).toHaveTextContent('Pagos confirmados')
    expect(document.querySelector('.reportes-plan-list__track > span')).toHaveStyle({ width: '100%' })
  })

  it('presenta estados vacíos de gráficos, planes y detalle sin inventar períodos', async () => {
    server.use(
      http.get('/api/reportes/resumen', () => HttpResponse.json({ ...summary, pagosPorFecha: [], sociosPorFecha: [], ingresosPorFecha: [], membresiasPorPlan: [] })),
      http.get('/api/reportes/detalle', () => HttpResponse.json([])),
      http.get('/api/reportes/planes', () => HttpResponse.json([])),
    )
    await renderPage()
    expect(await screen.findAllByText('Sin datos en este período')).toHaveLength(4)
    expect(screen.getByText('Sin membresías en este período')).toBeInTheDocument()
    expect(screen.getByText('No hay registros en esta página')).toBeInTheDocument()
    expect(document.querySelectorAll('.reportes-chart__svg[role="img"]')).toHaveLength(0)
  })

  it('permite cambiar tipo de detalle manteniendo la tabla y los controles de exportación', async () => {
    server.use(
      http.get('/api/reportes/resumen', () => HttpResponse.json(summary)),
      http.get('/api/reportes/detalle', ({ request }) => {
        const type = new URL(request.url).searchParams.get('tipo')
        return HttpResponse.json([{ id: 22, tipo: type ?? 'PAGO', persona: 'Socio de prueba', detalle: 'Membresía', estado: 'ACTIVA', monto: null, fecha: '2026-11-09T10:00:00' }])
      }),
      http.get('/api/reportes/planes', () => HttpResponse.json([])),
    )
    const user = userEvent.setup()
    await renderPage()
    expect(await screen.findByText('Socio de prueba')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Tipo de reporte'), 'membresias')
    expect(await screen.findByText('membresias')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'PDF' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Excel' })).toBeInTheDocument()
  })

  it('rechaza rangos invertidos en el cliente sin consultar datos', async () => {
    let calls = 0
    server.use(
      http.get('/api/reportes/resumen', () => { calls++; return HttpResponse.json(summary) }),
      http.get('/api/reportes/detalle', () => HttpResponse.json([])),
      http.get('/api/reportes/planes', () => HttpResponse.json([])),
    )
    const user = userEvent.setup()
    await renderPage()
    await within(screen.getByLabelText('Indicadores del período')).findByText(/125\.50/)
    await user.clear(screen.getByLabelText('Hasta'))
    await user.type(screen.getByLabelText('Hasta'), '2020-01-01')
    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha inicial debe ser anterior o igual')
    expect(calls).toBe(1)
  })

  it('conserva la pantalla y muestra error recuperable si falla el API', async () => {
    server.use(
      http.get('/api/reportes/resumen', () => HttpResponse.json({ mensaje: 'fallo' }, { status: 503 })),
      http.get('/api/reportes/detalle', () => HttpResponse.json([])),
      http.get('/api/reportes/planes', () => HttpResponse.json([])),
    )
    await renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor no pudo completar')
    expect(screen.getByRole('heading', { name: 'Reportes' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('maneja respuesta JSON incompleta del resumen como error, sin romper el render', async () => {
    server.use(
      http.get('/api/reportes/resumen', () => HttpResponse.json({ ingresosConfirmados: 150 })),
      http.get('/api/reportes/detalle', () => HttpResponse.json([])),
      http.get('/api/reportes/planes', () => HttpResponse.json([])),
    )
    await renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('respuesta no válida')
    expect(screen.getByRole('heading', { name: 'Reportes' })).toBeInTheDocument()
  })
})
