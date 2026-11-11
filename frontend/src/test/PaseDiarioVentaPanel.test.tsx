import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthContextValue } from '../auth/AuthContext'
import { PaseDiarioVentaPanel } from '../app/PaseDiarioVentaPanel'
import { server } from './server'

const auth = {
  user: { correo: 'recepcion@example.test', roles: ['RECEPCIONISTA'], issuedAt: Date.now() },
  getAccessToken: () => 'test-token', getSessionVersion: () => 1,
  logoutIfCurrentSession: () => false,
} as unknown as AuthContextValue
beforeEach(() => vi.stubGlobal('crypto', {
  subtle: globalThis.crypto.subtle,
  randomUUID: () => '765c3b46-ec72-4a99-83fd-51f8d85a63b1',
}))
afterEach(() => {
  vi.unstubAllGlobals()
})

const socio = {
  idSocio: 42, codigoSocio: 'SOC-0042', dni: '12345678', tipoDocumento: 'DNI',
  numeroDocumento: '12345678', nombres: 'Ana', apellidos: 'Quispe',
  fechaNacimiento: null, celular: '999888777', correo: null, estado: 'ACTIVO',
  fechaRegistro: '2026-01-01T00:00:00',
}

function habilitarPanel() {
  server.use(
    http.get('/api/ingresos/pase-diario/habilitado', () => HttpResponse.json(true)),
    http.get('/api/configuraciones/PRECIO_PASE_DIARIO', () => HttpResponse.json({
      idConfiguracion: 1, clave: 'PRECIO_PASE_DIARIO', valor: '12.50',
      descripcion: null, fechaActualizacion: '2026-11-10T00:00:00',
    })),
    http.get('/api/socios/buscar', () => HttpResponse.json([socio])),
  )
}

describe('PaseDiarioVentaPanel', () => {
  it('mantiene desactivada la venta hasta confirmar que el esquema v2 está listo', async () => {
    render(<PaseDiarioVentaPanel auth={auth} />)
    expect(await screen.findByText(/temporalmente deshabilitada/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Buscar persona/ })).not.toBeInTheDocument()
  })

  it('busca la ficha real, confirma, cobra una vez y muestra el resultado del servidor', async () => {
    const user = userEvent.setup()
    habilitarPanel()
    let body: unknown
    let count = 0
    server.use(http.post('/api/ingresos', async ({ request }) => {
      count += 1
      body = await request.json()
      return HttpResponse.json({
        idIngreso: 87, tipoIngreso: 'VISITANTE', idSocio: 42, idVisitante: null,
        tipoDocumento: 'DNI', numeroDocumento: '12345678', fechaHora: '2026-11-10T12:00:00',
        paseDiarioCobrado: true, montoCobrado: 12.5, idPago: 91, resultado: 'ACEPTADO',
      }, { status: 201 })
    }))

    render(<PaseDiarioVentaPanel auth={auth} />)
    await user.type(await screen.findByLabelText('Número de documento'), '12345678')
    await user.click(screen.getByRole('button', { name: 'Buscar persona' }))
    expect(await screen.findByText('Ana Quispe')).toBeInTheDocument()
    await user.click(screen.getByLabelText(/Confirmo los datos/))
    await user.click(screen.getByRole('button', { name: 'Confirmar acceso / pase diario' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Pase diario vendido por S/')
    expect(screen.getByText('Pago registrado')).toBeInTheDocument()
    expect(count).toBe(1)
    expect(body).toMatchObject({ tipoDocumento: 'DNI', numeroDocumento: '12345678', idSocio: 42, metodoPago: 'EFECTIVO' })
    expect(body).toHaveProperty('claveIdempotencia')
  })

  it('mantiene bloqueados los datos de pago y reusa la misma clave tras un resultado incierto', async () => {
    const user = userEvent.setup()
    habilitarPanel()
    const requests: unknown[] = []
    server.use(http.post('/api/ingresos', async ({ request }) => {
      requests.push(await request.json())
      return HttpResponse.json({}, { status: 500 })
    }))
    render(<PaseDiarioVentaPanel auth={auth} />)
    await user.type(await screen.findByLabelText('Número de documento'), '12345678')
    await user.click(screen.getByRole('button', { name: 'Buscar persona' }))
    await screen.findByText('Ana Quispe')
    await user.click(screen.getByLabelText(/Confirmo los datos/))
    await user.click(screen.getByRole('button', { name: 'Confirmar acceso / pase diario' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Resultado incierto')
    expect(screen.getByLabelText('Método de pago')).toBeDisabled()
    expect(screen.getByLabelText('Referencia (si aplica)')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Reintentar misma solicitud' }))
    await waitFor(() => expect(requests).toHaveLength(2))
    expect(requests[1]).toEqual(requests[0])
  })
})
