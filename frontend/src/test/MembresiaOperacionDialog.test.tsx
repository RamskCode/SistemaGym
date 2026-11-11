import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MembresiaOperacionDialog } from '../app/MembresiaOperacionDialog'
import type { Membresia, Plan } from '../api/membresias'
import type { Socio } from '../api/socios'
import type { AuthenticatedRequestContext } from '../api/http'
import { server } from './server'
import { readMembresiaWriteMarkers } from '../app/membresiasUncertainWrites'

let uuidIndex = 0
Object.defineProperty(globalThis, 'crypto', { configurable: true, value: {
  randomUUID: () => `00000000-0000-4000-8000-${String(++uuidIndex).padStart(12, '0')}`,
  subtle: { digest: async () => new Uint8Array(32).buffer },
} as unknown as Crypto })
beforeEach(() => {
  window.localStorage.clear()
  uuidIndex = 0
  server.use(http.get('/api/operaciones-pago/por-clave/:key', () => HttpResponse.json({}, { status: 404 })))
})

const auth: AuthenticatedRequestContext = { getAccessToken: () => 'cash-token', getSessionVersion: () => 1, logoutIfCurrentSession: () => false }
const socio: Socio = { idSocio: 4, codigoSocio: 'SOC-0004', dni: null, nombres: 'Luis', apellidos: 'Ramos', fechaNacimiento: null, celular: null, correo: null, estado: 'ACTIVO', fechaRegistro: '2026-01-01T10:00:00' }
const plan: Plan = { idPlan: 7, nombre: 'Mensual', descripcion: null, precio: 100, duracionDias: 30, estado: 'ACTIVO', fechaCreacion: '2026-01-01T10:00:00' }
const member: Membresia = { idMembresia: 9, socio, plan, precioAcordado: 100, fechaInicio: '2026-01-01', fechaVencimiento: '2026-01-31', fechaLimitePago: '2099-01-01', estado: 'ACTIVA', motivoCancelacion: null, fechaCreacion: '2026-01-01T10:00:00' }
const paidResponse = { idPago: 90, idAplicacion: 21, idMembresia: 9, montoPago: 20, montoAplicado: 20, metodoPago: 'YAPE', referencia: null, observaciones: null, fechaPago: '2026-01-02T11:00:00', saldoPendiente: 40, estadoMembresia: 'ACTIVA' }

function renderDialog(props: Partial<React.ComponentProps<typeof MembresiaOperacionDialog>> = {}) {
  return render(<MembresiaOperacionDialog account="cashier@example.test" auth={auth} blockedMarkers={[]} onMarkerChange={vi.fn()} operacion="nueva" socios={[socio]} planes={[plan]} onClose={vi.fn()} onCreated={vi.fn()} onPaid={vi.fn()} {...props} />)
}

async function fillNewForm(user: ReturnType<typeof userEvent.setup>, amount: string) {
  await user.selectOptions(screen.getByLabelText('Socio activo'), '4')
  await user.selectOptions(screen.getByLabelText('Plan activo'), '7')
  await user.type(screen.getByLabelText('Monto del primer pago'), amount)
  await user.selectOptions(screen.getByLabelText('Método de pago'), 'YAPE')
}

describe('MembresiaOperacionDialog', () => {
  it('enforces the first-payment minimum and plan price before confirmation', async () => {
    const user = userEvent.setup()
    const submitted = vi.fn()
    renderDialog({ onCreated: submitted })
    await fillNewForm(user, '39.99')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('al menos S/ 40.00')
    expect(screen.queryByRole('button', { name: 'Confirmar cobro' })).not.toBeInTheDocument()
    expect(submitted).not.toHaveBeenCalled()
    await user.clear(screen.getByLabelText('Monto del primer pago'))
    await user.type(screen.getByLabelText('Monto del primer pago'), '100.01')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('no puede superar el precio del plan')
  })

  it('shows a confirmation summary and submits the exact registration DTO once', async () => {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    let body: unknown
    let count = 0
    let key = ''
    server.use(http.post('/api/membresias', async ({ request }) => {
      count += 1
      key = request.headers.get('Idempotency-Key') ?? ''
      body = await request.json()
      return HttpResponse.json({ idMembresia: 33, idSocio: 4, idPlan: 7, precioAcordado: 100, fechaInicio: '2026-02-01', fechaVencimiento: '2026-03-03', fechaLimitePago: '2026-02-06', estado: 'ACTIVA', idPago: 50, montoPrimerPago: 40.1, metodoPago: 'YAPE', idAplicacion: 12 }, { status: 201 })
    }))
    renderDialog({ onCreated })
    await fillNewForm(user, '40.10')
    await user.type(screen.getByLabelText('Referencia (opcional)'), 'YAPE-88')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByRole('heading', { name: 'Confirmar operación' })).toBeInTheDocument()
    expect(screen.getByText('Luis Ramos')).toBeInTheDocument()
    expect(screen.getByText('Mensual')).toBeInTheDocument()
    expect(screen.getByText('YAPE')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ idMembresia: 33 })))
    expect(count).toBe(1)
    expect(key).toBe('00000000-0000-4000-8000-000000000001')
    expect(body).toEqual({ idSocio: 4, idPlan: 7, montoPrimerPago: 40.1, metodoPago: 'YAPE', referencia: 'YAPE-88', observaciones: null })
  })

  it('prevents an amount above the known balance and confirms a posterior payment', async () => {
    const user = userEvent.setup()
    const onPaid = vi.fn()
    let payload: unknown
    let key = ''
    server.use(http.post('/api/membresias/9/pagos', async ({ request }) => {
      key = request.headers.get('Idempotency-Key') ?? ''
      payload = await request.json()
      return HttpResponse.json(paidResponse, { status: 201 })
    }))
    renderDialog({ operacion: 'pago', membresia: member, saldoPendiente: 60, onPaid })
    await user.type(screen.getByLabelText('Monto del abono'), '60.01')
    await user.selectOptions(screen.getByLabelText('Método de pago'), 'YAPE')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('no puede superar el saldo pendiente')
    expect(payload).toBeUndefined()
    await user.clear(screen.getByLabelText('Monto del abono'))
    await user.type(screen.getByLabelText('Monto del abono'), '20.00')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    expect(screen.getByText(/S\/\s*60\.00/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    await waitFor(() => expect(onPaid).toHaveBeenCalledWith(expect.objectContaining({ saldoPendiente: 40 })))
    expect(payload).toEqual({ monto: 20, metodoPago: 'YAPE', referencia: null, observaciones: null })
    expect(key).toBe('00000000-0000-4000-8000-000000000001')
  })

  it('warns that the outcome is uncertain after a network failure and does not retry', async () => {
    const user = userEvent.setup()
    let attempts = 0
    server.use(http.post('/api/membresias', () => { attempts += 1; return HttpResponse.error() }))
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/Todavía no se encuentra el resultado/)).toBeInTheDocument()
    expect(attempts).toBe(1)
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toMatchObject([{ operation: 'nueva', key: expect.any(String), state: 'uncertain' }])
  })

  it('keeps the affected payment blocked with its same key and request context after an HTTP 5xx', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/membresias/9/pagos', () => HttpResponse.json({}, { status: 500 })))
    renderDialog({ operacion: 'pago', membresia: member, saldoPendiente: 60 })
    await user.type(screen.getByLabelText('Monto del abono'), '20')
    await user.selectOptions(screen.getByLabelText('Método de pago'), 'YAPE')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/Todavía no se encuentra el resultado/)).toBeInTheDocument()
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toMatchObject([{ operation: 'pago', idMembresia: 9, key: expect.any(String), state: 'uncertain', body: { monto: 20 } }])
  })

  it('treats an HTTP timeout response as uncertain', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/membresias', () => HttpResponse.json({}, { status: 408 })))
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/Todavía no se encuentra el resultado/)).toBeInTheDocument()
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toMatchObject([{ operation: 'nueva', key: expect.any(String), state: 'uncertain' }])
  })

  it('keeps the lock when a successful response has no operation identifiers', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/membresias', () => HttpResponse.json({}, { status: 201 })))
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/Todavía no se encuentra el resultado/)).toBeInTheDocument()
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toMatchObject([{ operation: 'nueva', key: expect.any(String), state: 'uncertain' }])
  })

  it('does not persist a block for a confirmed validation rejection', async () => {
    const user = userEvent.setup()
    server.use(http.post('/api/membresias', () => HttpResponse.json({ mensaje: 'Invalid' }, { status: 400 })))
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Revisa los datos ingresados')
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toEqual([])
  })

  it('clears the unprocessed attempt on 401 and relies on the authenticated client session handling', async () => {
    const user = userEvent.setup()
    const logoutIfCurrentSession = vi.fn(() => true)
    server.use(http.post('/api/membresias', () => HttpResponse.json({}, { status: 401 })))
    renderDialog({ auth: { ...auth, logoutIfCurrentSession } })
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/sesión no está disponible o ha expirado/i)).toBeInTheDocument()
    expect(logoutIfCurrentSession).toHaveBeenCalledWith(1)
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toEqual([])
  })

  it('shows the backend conflict without retrying the operation', async () => {
    const user = userEvent.setup()
    let attempts = 0
    server.use(http.post('/api/membresias', () => {
      attempts += 1
      return HttpResponse.json({ mensaje: 'Conflict detail' }, { status: 409 })
    }))
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/Todavía no se encuentra el resultado/)).toBeInTheDocument()
    expect(attempts).toBe(1)
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toHaveLength(1)
  })

  it('recovers a confirmed result after the POST response is lost', async () => {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    let postedKey = ''
    server.use(
      http.post('/api/membresias', ({ request }) => { postedKey = request.headers.get('Idempotency-Key') ?? ''; return HttpResponse.error() }),
      http.get('/api/operaciones-pago/por-clave/:key', () => HttpResponse.json({
        idOperacion: 'internal-id', tipoOperacion: 'MEMBRESIA_INICIAL', estado: 'CONFIRMED',
        idMembresia: 33, idPago: 50, idAplicacion: 12, rechazoTipo: null, rechazoMensaje: null,
        resultado: { idMembresia: 33, idSocio: 4, idPlan: 7, precioAcordado: 100, fechaInicio: '2026-02-01', fechaVencimiento: '2026-03-03', fechaLimitePago: null, estado: 'ACTIVA', idPago: 50, montoPrimerPago: 40, metodoPago: 'YAPE', idAplicacion: 12 },
      })),
    )
    renderDialog({ onCreated })
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ idMembresia: 33, idPago: 50 })))
    expect(postedKey).toBe('00000000-0000-4000-8000-000000000001')
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toEqual([])
  })

  it('recovers a persisted REJECTED result and clears only its resolved lock', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('/api/membresias', () => HttpResponse.json({ mensaje: 'Socio inactivo' }, { status: 409 })),
      http.get('/api/operaciones-pago/por-clave/:key', () => HttpResponse.json({
        idOperacion: 'internal-id', tipoOperacion: 'MEMBRESIA_INICIAL', estado: 'REJECTED',
        idMembresia: null, idPago: null, idAplicacion: null, rechazoTipo: 'CONFLICTO', rechazoMensaje: 'Socio inactivo', resultado: null,
      })),
    )
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Socio inactivo')
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toEqual([])
  })

  it('keeps PENDING and retries with the same UUID and exact body', async () => {
    const user = userEvent.setup()
    let posts = 0
    const keys: string[] = []
    let requestBody: unknown
    server.use(
      http.post('/api/membresias', async ({ request }) => {
        posts += 1
        keys.push(request.headers.get('Idempotency-Key') ?? '')
        requestBody = await request.json()
        return posts === 1 ? HttpResponse.error() : HttpResponse.json({ idMembresia: 33, idSocio: 4, idPlan: 7, precioAcordado: 100, fechaInicio: '2026-02-01', fechaVencimiento: '2026-03-03', fechaLimitePago: null, estado: 'ACTIVA', idPago: 50, montoPrimerPago: 40, metodoPago: 'YAPE', idAplicacion: 12 }, { status: 201 })
      }),
      http.get('/api/operaciones-pago/por-clave/:key', () => HttpResponse.json({
        idOperacion: 'internal-id', tipoOperacion: 'MEMBRESIA_INICIAL', estado: 'PENDING',
        idMembresia: null, idPago: null, idAplicacion: null, rechazoTipo: null, rechazoMensaje: null, resultado: null,
      })),
    )
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByRole('button', { name: 'Reintentar envío' })).toBeInTheDocument()
    expect(await readMembresiaWriteMarkers('cashier@example.test')).toMatchObject([{ state: 'pending' }])
    await user.click(screen.getByRole('button', { name: 'Reintentar envío' }))
    await waitFor(() => expect(posts).toBe(2))
    expect(keys).toEqual(['00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001'])
    expect(requestBody).toEqual({ idSocio: 4, idPlan: 7, montoPrimerPago: 40, metodoPago: 'YAPE', referencia: null, observaciones: null })
  })

  it('locks the confirmation button while a charge request is pending', async () => {
    const user = userEvent.setup()
    let release: (response: Response) => void = () => undefined
    let count = 0
    server.use(http.post('/api/membresias', async () => {
      count += 1
      return await new Promise<Response>((resolve) => { release = resolve })
    }))
    renderDialog()
    await fillNewForm(user, '40')
    await user.click(screen.getByRole('button', { name: 'Revisar y confirmar' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(screen.getAllByRole('button', { name: 'Procesando…' })).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: 'Procesando…' }).every((button) => (button as HTMLButtonElement).disabled)).toBe(true)
    expect(count).toBe(1)
    release(HttpResponse.json({ idMembresia: 33, idSocio: 4, idPlan: 7, precioAcordado: 100, fechaInicio: '2026-02-01', fechaVencimiento: '2026-03-03', fechaLimitePago: null, estado: 'ACTIVA', idPago: 50, montoPrimerPago: 40, metodoPago: 'YAPE', idAplicacion: 12 }, { status: 201 }))
  })
})
