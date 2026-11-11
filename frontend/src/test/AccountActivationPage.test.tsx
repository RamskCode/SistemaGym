import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AccountActivationPage } from '../auth/AccountActivationPage'
import { server } from './server'

function page() {
  return render(<MemoryRouter initialEntries={['/activar-cuenta?token=opaque-token']}><AccountActivationPage /></MemoryRouter>)
}

describe('AccountActivationPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('validates invite, applies password rules and completes activation', async () => {
    let body: unknown
    server.use(http.post('/api/auth/activation/validate', async ({ request }) => {
      expect(await request.json()).toEqual({ token: 'opaque-token' })
      return new HttpResponse(null, { status: 204 })
    }))
    server.use(http.post('/api/auth/activation/complete', async ({ request }) => {
      body = await request.json()
      return new HttpResponse(null, { status: 204 })
    }))
    const user = userEvent.setup()
    page()
    expect(await screen.findByRole('heading', { name: 'Crea tu contraseña' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Requisitos de contraseña' })).toBeInTheDocument()
    expect(screen.getByText('Al menos 8 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Al menos una letra')).toBeInTheDocument()
    expect(screen.getByText('Al menos un número')).toBeInTheDocument()
    expect(screen.getByText('Al menos un símbolo')).toBeInTheDocument()
    const requirements = within(screen.getByRole('list', { name: 'Requisitos de contraseña' }))
    expect(requirements.getAllByRole('listitem').every((item) => !item.classList.contains('is-valid'))).toBe(true)
    expect(screen.getByLabelText('Nueva contraseña')).toHaveClass('password-field__input')
    expect(screen.getByLabelText('Nueva contraseña').closest('.password-field__control')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Nueva contraseña'), 'StrongNewPassword!42')
    expect(requirements.getAllByRole('listitem')).toHaveLength(4)
    expect(requirements.getAllByRole('listitem').every((item) => item.classList.contains('is-valid'))).toBe(true)
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'StrongNewPassword!42')
    await user.click(screen.getByRole('button', { name: 'Mostrar nueva contraseña' }))
    expect(screen.getByLabelText('Nueva contraseña')).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar nueva contraseña' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Activar cuenta' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Activar cuenta' }))
    expect(await screen.findByRole('heading', { name: 'Cuenta activada' })).toBeInTheDocument()
    expect(body).toEqual({ token: 'opaque-token', contrasena: 'StrongNewPassword!42' })
  })

  it('does not present the form for an expired invitation', async () => {
    server.use(http.post('/api/auth/activation/validate', () => new HttpResponse(null, { status: 410 })))
    page()
    expect(await screen.findByRole('heading', { name: 'Invitación no disponible' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Nueva contraseña')).not.toBeInTheDocument()
  })

  it('preserves password-policy and confirmation validation without submitting invalid values', async () => {
    let completionCalls = 0
    server.use(http.post('/api/auth/activation/validate', () => new HttpResponse(null, { status: 204 })))
    server.use(http.post('/api/auth/activation/complete', () => {
      completionCalls += 1
      return new HttpResponse(null, { status: 204 })
    }))
    const user = userEvent.setup()
    page()
    expect(await screen.findByRole('heading', { name: 'Crea tu contraseña' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Nueva contraseña'), 'StrongNewPassword!42')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'OtherStrongPassword!42')
    expect(screen.getByRole('status')).toHaveTextContent('Las contraseñas aún no coinciden.')
    expect(screen.getByRole('button', { name: 'Activar cuenta' })).toBeDisabled()
    expect(completionCalls).toBe(0)
  })

  it('keeps activation disabled until the password rules and matching confirmation are satisfied', async () => {
    server.use(http.post('/api/auth/activation/validate', () => new HttpResponse(null, { status: 204 })))
    const user = userEvent.setup()
    page()
    expect(await screen.findByRole('heading', { name: 'Crea tu contraseña' })).toBeInTheDocument()
    const submit = screen.getByRole('button', { name: 'Activar cuenta' })
    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('Nueva contraseña'), 'StrongNewPassword!42')
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'otra')
    expect(screen.getByRole('status')).toHaveTextContent('Las contraseñas aún no coinciden.')
    expect(submit).toBeDisabled()
    await user.clear(screen.getByLabelText('Confirmar contraseña'))
    await user.type(screen.getByLabelText('Confirmar contraseña'), 'StrongNewPassword!42')
    expect(submit).toBeEnabled()
  })
})
