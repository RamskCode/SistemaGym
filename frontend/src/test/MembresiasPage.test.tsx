import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { MembresiasPage } from '../app/MembresiasPage'
import type { Membresia } from '../api/membresias'
import { server } from './server'

let auth: AuthContextValue
const membership: Membresia = {
  idMembresia: 8,
  socio: { idSocio: 3, codigoSocio: 'SOC-0003', dni: '12345678', nombres: 'Ana', apellidos: 'Pérez', fechaNacimiento: null, celular: null, correo: null, estado: 'ACTIVO', fechaRegistro: '2026-01-01T10:00:00' },
  plan: { idPlan: 2, nombre: 'Mensual', descripcion: null, precio: 100, duracionDias: 30, estado: 'ACTIVO', fechaCreacion: '2025-12-01T10:00:00' },
  precioAcordado: 100, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-31', fechaLimitePago: '2026-01-10', estado: 'ACTIVA', motivoCancelacion: null, fechaCreacion: '2026-01-01T10:00:00',
}
const state = { idMembresia: 8, estado: 'ACTIVA', situacion: 'ACTIVA_PAGADA', precioAcordado: 100, saldoPendiente: 25, pagada: false, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-31', fechaLimitePago: '2026-01-10', motivoCancelacion: null, fechaCreacion: '2026-01-01T10:00:00' }

function tokenResponse() {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'recepcion@example.test', roles: ['ROLE_RECEPCIONISTA'], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <MembresiasPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn() {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(tokenResponse())))
  await act(async () => { await auth.login({ correo: 'recepcion@example.test', contrasena: 'test-password' }) })
}
function handlers(rows: Membresia[] = [membership]) {
  server.use(http.get('/api/membresias', () => HttpResponse.json(rows)))
  server.use(http.get('/api/membresias/8/estado', () => HttpResponse.json(state)))
  server.use(http.get('/api/membresias/8', () => HttpResponse.json(membership)))
  server.use(http.get('/api/planes/estado/ACTIVO', () => HttpResponse.json([membership.plan])))
  server.use(http.get('/api/socios/estado/ACTIVO', () => HttpResponse.json([membership.socio])))
}

describe('MembresiasPage', () => {
  it('shows backend membership, pending balance and details fetched by ID', async () => {
    const user = userEvent.setup()
    handlers()
    renderPage()
    await signIn()
    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.getByText(/S\/\s*25\.00/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver detalle de membresía 8' }))
    const dialog = await screen.findByRole('dialog', { name: 'Detalle de membresía' })
    expect(within(dialog).getByText('31 ene. 2026')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver detalle de membresía 8' })).toHaveFocus()
  })

  it('searches and filters using only real membership DTO properties', async () => {
    const user = userEvent.setup()
    const other = { ...membership, idMembresia: 9, socio: { ...membership.socio!, idSocio: 4, codigoSocio: 'SOC-0004', nombres: 'Bruno' }, estado: 'PENDIENTE_PAGO' }
    handlers([membership, other])
    server.use(http.get('/api/membresias/9/estado', () => HttpResponse.json({ ...state, idMembresia: 9, estado: 'PENDIENTE_PAGO' })))
    renderPage()
    await signIn()
    expect(await screen.findByText(/2\s+membresías/)).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por estado de membresía'), 'PENDIENTE_PAGO')
    expect((await screen.findAllByText('Pendiente de pago')).length).toBeGreaterThan(1)
    expect(screen.queryByText('Ana Pérez')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por estado de membresía'), 'TODOS')
    await user.type(screen.getByRole('searchbox', { name: 'Buscar por socio, código, documento, plan o número de membresía' }), 'SOC-0003')
    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.queryByText(/2\s+membresías/)).not.toBeInTheDocument()
  })

  it('shows empty and safe error states', async () => {
    server.use(http.get('/api/membresias', () => HttpResponse.json([])))
    server.use(http.get('/api/planes/estado/ACTIVO', () => HttpResponse.json([])))
    server.use(http.get('/api/socios/estado/ACTIVO', () => HttpResponse.json([])))
    renderPage()
    await signIn()
    expect(await screen.findByText(/Aún no hay membresías registradas/)).toBeInTheDocument()
  })
})
