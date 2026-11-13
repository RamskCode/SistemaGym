import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { SociosPage } from '../app/SociosPage'
import type { Socio } from '../api/socios'
import { server } from './server'

const API_LOGIN = '/api/auth/login'
let auth: AuthContextValue

const ana: Socio = {
  idSocio: 12,
  codigoSocio: 'SOC-0012',
  dni: '12345678',
  nombres: 'Ana',
  apellidos: 'Pérez Soto',
  fechaNacimiento: '1995-04-02',
  celular: '999111222',
  correo: 'ana@example.test',
  estado: 'ACTIVO',
  fechaRegistro: '2026-01-01T10:00:00',
}

function b64url(value: unknown) {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}

function loginResponse() {
  const iat = Math.floor(Date.now() / 1000)
  const payload = { sub: 'recepcion@example.test', roles: ['ROLE_RECEPCIONISTA'], iat, exp: iat + 900 }
  return {
    token: `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url(payload)}.c2lnbmF0dXJl`,
    tipo: 'Bearer',
    expiraEnMs: 900_000,
  }
}

function PageHarness() {
  auth = useAuth()
  return <SociosPage />
}

function renderPage() {
  return render(
    <AuthProvider>
      <MemoryRouter><PageHarness /></MemoryRouter>
    </AuthProvider>,
  )
}

async function signIn() {
  server.use(http.post(API_LOGIN, () => HttpResponse.json(loginResponse())))
  await act(async () => { await auth.login({ correo: 'recepcion@example.test', contrasena: 'clave-prueba' }) })
}

function serveList(rows: Socio[] = [ana]) {
  server.use(http.get('/api/socios', () => HttpResponse.json(rows)))
}

describe('SociosPage', () => {
  it('loads and displays backend records without fabricating data', async () => {
    let authorization: string | null = null
    server.use(http.get('/api/socios', ({ request }) => {
      authorization = request.headers.get('Authorization')
      return HttpResponse.json([ana])
    }))
    renderPage()
    await signIn()

    expect(await screen.findByText('SOC-0012')).toBeInTheDocument()
    expect(screen.getByText('Ana')).toBeInTheDocument()
    expect(screen.getByText('Pérez Soto')).toBeInTheDocument()
    expect(screen.getByText('DNI ·••••••78')).toBeInTheDocument()
    expect(screen.queryByText('12345678')).not.toBeInTheDocument()
    expect(screen.getByText('Activo')).toBeInTheDocument()
    expect(authorization).toBe('Bearer ' + auth.getAccessToken())
  })

  it('uses the real search endpoint and combines its results with the selected state filter', async () => {
    const user = userEvent.setup()
    const inactive = { ...ana, idSocio: 13, codigoSocio: 'SOC-0013', estado: 'INACTIVO' as const }
    let searchPath = ''
    serveList([ana])
    server.use(http.get('/api/socios/estado/INACTIVO', () => HttpResponse.json([inactive])))
    server.use(http.get('/api/socios/buscar', ({ request }) => {
      searchPath = `${new URL(request.url).pathname}${new URL(request.url).search}`
      return HttpResponse.json([ana, inactive])
    }))
    renderPage()
    await signIn()
    await screen.findByText('SOC-0012')

    await user.selectOptions(screen.getByLabelText('Filtrar por estado'), 'INACTIVO')
    expect(await screen.findByText('SOC-0013')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar por nombre, apellidos, documento o código' }), { target: { value: '12345678' } })

    await waitFor(() => expect(searchPath).not.toBe(''))
    expect(await screen.findByText('SOC-0013')).toBeInTheDocument()
    expect(screen.queryByText('SOC-0012')).not.toBeInTheDocument()
    expect(searchPath).toBe('/api/socios/buscar?q=12345678')
  })

  it('shows a real empty state and a safe API error', async () => {
    server.use(http.get('/api/socios', () => HttpResponse.json([])))
    renderPage()
    await signIn()
    expect(await screen.findByText('Aún no hay socios registrados.')).toBeInTheDocument()

    // Trigger a server-side status query; backend error detail must not be exposed.
    server.use(http.get('/api/socios/estado/ACTIVO', () => HttpResponse.json({ mensaje: 'Internal detail' }, { status: 403 })))
    fireEvent.change(screen.getByLabelText('Filtrar por estado'), { target: { value: 'ACTIVO' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso para realizar esta acción.')
    expect(screen.queryByText('Internal detail')).not.toBeInTheDocument()
  })

  it('validates required fields and creates a socio using only DTO keys', async () => {
    const user = userEvent.setup()
    serveList([])
    let submitted: unknown
    server.use(http.post('/api/socios', async ({ request }) => {
      submitted = await request.json()
      return HttpResponse.json({ ...ana, codigoSocio: 'SOC-0099' }, { status: 201 })
    }))
    renderPage()
    await signIn()
    await screen.findByText('Aún no hay socios registrados.')
    await user.click(screen.getByRole('button', { name: 'Nuevo socio' }))
    expect(screen.getByLabelText('Tipo de documento')).toHaveValue('DNI')
    await user.click(screen.getByRole('button', { name: 'Registrar socio' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Los nombres y apellidos son obligatorios.')
    expect(submitted).toBeUndefined()

    await user.type(screen.getByRole('textbox', { name: /Nombres/ }), 'Ana')
    await user.type(screen.getByRole('textbox', { name: /Apellidos/ }), 'Pérez Soto')
    await user.type(screen.getByLabelText('Número de documento'), '12345678')
    await user.type(screen.getByLabelText('Correo electrónico'), 'ana@example.test')
    await user.click(screen.getByRole('button', { name: 'Registrar socio' }))

    expect(await screen.findByRole('status')).toHaveTextContent('SOC-0099')
    expect(submitted).toEqual({
      dni: '12345678', tipoDocumento: 'DNI', numeroDocumento: '12345678', nombres: 'Ana', apellidos: 'Pérez Soto',
      fechaNacimiento: null, celular: null, correo: 'ana@example.test',
    })
  })

  it('creates a CE member using digits only and strips pasted letters', async () => {
    const user = userEvent.setup()
    let submitted: Record<string, unknown> | undefined
    server.use(http.get('/api/socios', () => HttpResponse.json([])))
    server.use(http.post('/api/socios', async ({ request }) => {
      submitted = await request.json() as Record<string, unknown>
      return HttpResponse.json({ ...ana, dni: null, tipoDocumento: 'CE', numeroDocumento: '12345' }, { status: 201 })
    }))
    renderPage(); await signIn()
    await screen.findByText('Aún no hay socios registrados.')
    await user.click(screen.getByRole('button', { name: 'Nuevo socio' }))
    await user.type(screen.getByRole('textbox', { name: /Nombres/ }), 'Eva')
    await user.type(screen.getByRole('textbox', { name: /Apellidos/ }), 'Ramos')
    await user.selectOptions(screen.getByLabelText('Tipo de documento'), 'CE')
    await user.type(screen.getByLabelText('Número de documento'), '12x34567890123')
    expect(screen.getByLabelText('Número de documento')).toHaveValue('123456789012')
    await user.click(screen.getByRole('button', { name: 'Registrar socio' }))
    expect(await screen.findByRole('status')).toHaveTextContent('registrado correctamente')
    expect(submitted).toMatchObject({ dni: null, tipoDocumento: 'CE', numeroDocumento: '123456789012' })
  })

  it('loads a selected record then updates it with PUT', async () => {
    const user = userEvent.setup()
    let updatedPayload: unknown
    serveList([ana])
    server.use(http.get('/api/socios/12', () => HttpResponse.json(ana)))
    server.use(http.put('/api/socios/12', async ({ request }) => {
      updatedPayload = await request.json()
      return HttpResponse.json({ ...ana, nombres: 'Ana María' })
    }))
    renderPage()
    await signIn()
    await user.click(await screen.findByRole('button', { name: 'Editar SOC-0012' }))
    expect(await screen.findByRole('dialog', { name: 'Editar socio' })).toBeInTheDocument()
    expect(screen.getByText('Ana Pérez Soto')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /SOC-0012/ })).toHaveClass('socios-row-selected')
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
    expect(await screen.findByDisplayValue('Ana')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Nombres *'), { target: { value: 'Ana María' } })
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Se actualizaron los datos')
    expect(updatedPayload).toEqual({
      dni: '12345678', tipoDocumento: 'DNI', numeroDocumento: '12345678', nombres: 'Ana María', apellidos: 'Pérez Soto',
      fechaNacimiento: '1995-04-02', celular: '999111222', correo: 'ana@example.test',
    })
    expect(screen.getByRole('row', { name: /SOC-0012/ })).not.toHaveClass('socios-row-selected')
  })

  it('closes edit with Escape, restores focus, and clears the temporary row highlight', async () => {
    const user = userEvent.setup()
    serveList([ana])
    server.use(http.get('/api/socios/12', () => HttpResponse.json(ana)))
    renderPage()
    await signIn()
    const edit = await screen.findByRole('button', { name: 'Editar SOC-0012' })
    await user.click(edit)
    const dialog = await screen.findByRole('dialog', { name: 'Editar socio' })
    expect(screen.getByRole('row', { name: /SOC-0012/ })).toHaveClass('socios-row-selected')
    expect(screen.getByRole('button', { name: 'Cerrar formulario de socio' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Editar socio' })).not.toBeInTheDocument()
    expect(screen.getByRole('row', { name: /SOC-0012/ })).not.toHaveClass('socios-row-selected')
    expect(edit).toHaveFocus()
    expect(dialog).not.toBeInTheDocument()
  })

  it('requires a reason and explicit confirmation before changing status', async () => {
    const user = userEvent.setup()
    let patchBody: unknown
    let listAfterPatch: Socio[] = [ana]
    server.use(http.get('/api/socios', () => HttpResponse.json(listAfterPatch)))
    server.use(http.patch('/api/socios/12/desactivar', async ({ request }) => {
      patchBody = await request.json()
      listAfterPatch = [{ ...ana, estado: 'INACTIVO' }]
      return HttpResponse.json(listAfterPatch[0])
    }))
    renderPage()
    await signIn()
    await user.click(await screen.findByRole('button', { name: 'Desactivar SOC-0012' }))

    const confirm = screen.getByRole('button', { name: 'Confirmar operación' })
    expect(confirm).toBeDisabled()
    expect(patchBody).toBeUndefined()
    await user.type(screen.getByRole('textbox', { name: 'Motivo del cambio de estado' }), 'Solicitud del socio')
    await user.click(confirm)

    expect(await screen.findByRole('status')).toHaveTextContent('ahora está inactivo')
    expect(patchBody).toEqual({ motivo: 'Solicitud del socio' })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Activar SOC-0012' })).toBeInTheDocument())
  })

  it('sorts and paginates locally, resets to page one when sorting changes, and clears filters', async () => {
    const user = userEvent.setup()
    const records = Array.from({ length: 12 }, (_, index) => ({
      ...ana,
      idSocio: index + 1,
      codigoSocio: `SOC-${String(index + 1).padStart(4, '0')}`,
      nombres: `Nombre ${String(12 - index).padStart(2, '0')}`,
    }))
    let listCalls = 0
    server.use(http.get('/api/socios', () => {
      listCalls += 1
      return HttpResponse.json(records)
    }))
    renderPage()
    await signIn()
    expect(await screen.findByText(/12\s+resultados/)).toBeInTheDocument()
    expect(screen.getByText('Nombre 01')).toBeInTheDocument()
    expect(screen.queryByText('Nombre 12')).not.toBeInTheDocument()
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(screen.getByText('Nombre 12')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Ordenar socios por'), 'codigoSocio')
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
    expect(screen.getByText('SOC-0001')).toBeInTheDocument()
    expect(listCalls).toBe(1)

    await user.selectOptions(screen.getByLabelText('Registros por página'), '20')
    expect(screen.getByText('Página 1 de 1')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Filtrar por estado'), 'ACTIVO')
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    expect(screen.getByRole('searchbox', { name: 'Buscar por nombre, apellidos, documento o código' })).toHaveValue('')
    expect(screen.getByLabelText('Filtrar por estado')).toHaveValue('TODOS')
    expect(screen.getByLabelText('Ordenar socios por')).toHaveValue('nombres')
  })

  it('renders one accessible search prompt and keeps filter controls available together', async () => {
    serveList([ana])
    renderPage()
    await signIn()
    const search = await screen.findByRole('searchbox', { name: 'Buscar por nombre, apellidos, documento o código' })
    expect(search).toHaveAttribute('placeholder', 'Buscar por nombre, apellidos, documento o código')
    await userEvent.setup().click(search)
    expect(search).toHaveFocus()
    expect(search.closest('.search-field')).toHaveClass('socios-search')
    expect(screen.getAllByRole('searchbox')).toHaveLength(1)
    expect(screen.getByLabelText('Filtrar por estado')).toBeInTheDocument()
    expect(screen.getByLabelText('Ordenar socios por')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Orden ascendente, cambiar a descendente' })).toBeInTheDocument()
    expect(screen.getByLabelText('Registros por página')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Limpiar filtros' })).toBeInTheDocument()
  })

  it('debounces search requests and shows detail using optional DTO values', async () => {
    const user = userEvent.setup()
    const optionalSocio = { ...ana, dni: null, celular: null, correo: null, fechaNacimiento: null }
    let searches = 0
    serveList([optionalSocio])
    server.use(http.get('/api/socios/buscar', () => {
      searches += 1
      return HttpResponse.json([optionalSocio])
    }), http.get('/api/membresias/socio/12', () => HttpResponse.json([{
      idMembresia: 9, socio: optionalSocio, plan: { idPlan: 3, nombre: 'Mensual', descripcion: null, precio: 90,
        duracionDias: 30, estado: 'ACTIVO', fechaCreacion: '2026-01-01T10:00:00' },
      precioAcordado: 85, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-30',
      fechaLimitePago: null, estado: 'VENCIDA', motivoCancelacion: null, fechaCreacion: '2026-01-01T10:00:00',
    }])), http.get('/api/ingresos/pase-diario/socio/12', () => HttpResponse.json([
      { idIngreso: 25, fechaHora: '2026-11-10T09:00:00', cobradoEnEsteAcceso: false,
        idPaseDiario: 24, idPago: 31, montoPase: 15, metodoPago: 'YAPE' },
      { idIngreso: 24, fechaHora: '2026-11-10T08:00:00', cobradoEnEsteAcceso: true,
        idPaseDiario: 24, idPago: 31, montoPase: 15, metodoPago: 'YAPE' },
    ])))
    renderPage()
    await signIn()
    await user.click(await screen.findByRole('button', { name: 'Ver detalle de SOC-0012' }))
    expect(screen.getByRole('dialog', { name: 'Ana Pérez Soto' })).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /SOC-0012/ })).toHaveClass('socios-row-selected')
    expect(screen.getByRole('heading', { name: 'Ana Pérez Soto' })).toBeInTheDocument()
    expect(screen.getAllByText('No registrado')).toHaveLength(3)
    expect(screen.getByText('No registrada')).toBeInTheDocument()
    expect(await screen.findByText('Historial de membresías')).toBeInTheDocument()
    expect(screen.getByText('Mensual')).toBeInTheDocument()
    expect(await screen.findByText('Historial de pases diarios')).toBeInTheDocument()
    expect(screen.getByText('Reingreso · pase #24')).toBeInTheDocument()
    expect(screen.getByText('Venta de pase diario')).toBeInTheDocument()
    expect(screen.getAllByText(/Pago #31/)).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: 'Cerrar detalle' }))

    const searchInput = screen.getByRole('searchbox', { name: 'Buscar por nombre, apellidos, documento o código' })
    fireEvent.change(searchInput, { target: { value: 'A' } })
    fireEvent.change(searchInput, { target: { value: 'An' } })
    fireEvent.change(searchInput, { target: { value: 'Ana' } })
    expect(searches).toBe(0)
    await waitFor(() => expect(searches).toBe(1), { timeout: 1500 })
  })

  it('filters by real membership records without labeling people without memberships as visitors', async () => {
    const user = userEvent.setup()
    const another = { ...ana, idSocio: 13, codigoSocio: 'SOC-0013', dni: '23456789',
      numeroDocumento: '23456789', nombres: 'Luis', apellidos: 'Ramos' }
    serveList([ana, another])
    server.use(http.get('/api/membresias', () => HttpResponse.json([{
      idMembresia: 22, socio: ana, plan: null, precioAcordado: 80,
      fechaInicio: '2026-02-01', fechaVencimiento: '2026-02-28', fechaLimitePago: null,
      estado: 'VENCIDA', motivoCancelacion: null, fechaCreacion: '2026-02-01T10:00:00',
    }])))
    renderPage()
    await signIn()
    const filter = await screen.findByLabelText('Filtrar por historial de membresía')
    await user.selectOptions(filter, 'CON_MEMBRESIA')
    expect(await screen.findByRole('row', { name: /SOC-0012/ })).toBeInTheDocument()
    expect(screen.queryByRole('row', { name: /SOC-0013/ })).not.toBeInTheDocument()
    expect(screen.getByText(/no clasifica a la persona como visitante/i)).toBeInTheDocument()
  })
})
