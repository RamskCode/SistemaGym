import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { AuthContextValue } from '../auth/AuthContext'
import { IngresosClientesAdminView } from '../app/IngresosClientesAdminView'
import { server } from './server'

const auth = {
  user: { correo: 'admin@example.test', roles: ['ADMINISTRADOR'], issuedAt: Date.now() },
  getAccessToken: () => 'test-token', getSessionVersion: () => 1,
  logoutIfCurrentSession: () => false,
} as unknown as AuthContextValue

const row = {
  id: 14, tipo: 'VISITANTE', persona: 'María Torres', detalle: 'Pase diario', estado: 'REGISTRADO',
  monto: null, fecha: '2026-11-10T10:30:00', documentoProtegido: 'DNI · ••••1234',
  modalidad: 'PASE_DIARIO', operacion: 'REINGRESO', responsable: 'Ana Recepción',
}
const summary = {
  ingresosConfirmados: 0, pagosConfirmados: 0, sociosNuevos: 0, membresiasActivas: 0,
  membresiasVencidas: 0, membresiasProximasAVencer: 0, ingresosSocios: 2, ingresosVisitantes: 3,
  personasUnicas: 4, personasUnicasSocios: 2, personasUnicasVisitantes: 2,
  responsables: [{ id: 77, nombre: 'Ana Recepción' }],
  pagosPorFecha: [], sociosPorFecha: [], ingresosPorFecha: [], membresiasPorPlan: [],
}

describe('IngresosClientesAdminView', () => {
  it('muestra conteos reales, filtra la consulta y presenta un detalle de solo lectura', async () => {
    const user = userEvent.setup()
    let query = new URLSearchParams()
    server.use(
      http.get('/api/reportes/resumen', () => HttpResponse.json(summary)),
      http.get('/api/reportes/detalle', ({ request }) => {
        query = new URL(request.url).searchParams
        return HttpResponse.json([row])
      }),
    )
    render(<IngresosClientesAdminView auth={auth} />)

    expect(await screen.findByText('María Torres')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText(/4 personas únicas/)).toBeInTheDocument()
    expect(screen.getByText('DNI · ••••1234')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('Ana Recepción')).toBeInTheDocument()

    await user.type(screen.getByRole('searchbox', { name: 'Buscar por nombre o documento' }), 'María')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Modalidad' }), 'PASE_DIARIO')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Recepcionista' }), '77')
    await user.click(screen.getByRole('button', { name: /Aplicar/ }))
    await waitFor(() => expect(query.get('buscar')).toBe('María'))
    expect(query.get('modalidad')).toBe('PASE_DIARIO')
    expect(query.get('responsableId')).toBe('77')

    await user.click(screen.getByRole('button', { name: 'Ver detalle del acceso de María Torres' }))
    const dialog = screen.getByRole('dialog', { name: 'María Torres' })
    expect(within(dialog).getByText('Reingreso')).toBeInTheDocument()
    expect(within(dialog).getByText('Ana Recepción')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /registrar|cobrar/i })).not.toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
