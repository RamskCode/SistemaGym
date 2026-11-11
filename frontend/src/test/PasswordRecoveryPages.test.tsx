import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { RequestPasswordRecoveryPage, ResetPasswordPage } from '../auth/PasswordRecoveryPages'
import { passwordPolicyError } from '../utils/passwordPolicy'
import { server } from './server'

function renderPage(element: ReactNode) {
  return render(<MemoryRouter>{element}</MemoryRouter>)
}

afterEach(() => {
  window.history.replaceState({}, '', '/')
  vi.restoreAllMocks()
})

describe('recuperación de contraseña', () => {
  it('presenta la misma respuesta genérica del servidor al solicitar instrucciones', async () => {
    const user = userEvent.setup()
    let received: unknown
    server.use(http.post('/api/auth/password-recovery/solicitar', async ({ request }) => {
      received = await request.json()
      return HttpResponse.json({ mensaje: 'Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.' }, { status: 202 })
    }))
    renderPage(<RequestPasswordRecoveryPage />)
    await user.type(screen.getByLabelText('Correo electrónico'), 'persona@example.test')
    await user.click(screen.getByRole('button', { name: 'Enviar instrucciones' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Si el correo está registrado')
    expect(received).toEqual({ correo: 'persona@example.test' })
  })

  it('valida token por POST y envía la contraseña nueva en body, nunca en URL', async () => {
    const user = userEvent.setup()
    const token = 'x'.repeat(43)
    window.history.replaceState({}, '', `/restablecer-contrasena?token=${token}`)
    let posted: unknown
    server.use(
      http.post('/api/auth/password-recovery/validar', async ({ request }) => {
        expect(await request.json()).toEqual({ token })
        return new HttpResponse(null, { status: 204 })
      }),
      http.post('/api/auth/password-recovery/restablecer', async ({ request }) => {
        posted = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderPage(<ResetPasswordPage />)
    await screen.findByRole('heading', { name: 'Nueva contraseña' })
    expect(window.location.search).toBe('')
    const newPassword = document.getElementById('new-password')
    expect(newPassword).toHaveClass('password-field__input')
    expect(newPassword?.closest('.password-field__control')).toBeInTheDocument()
    await user.type(document.getElementById('new-password') as HTMLInputElement, 'Verde Lima! 2026')
    await user.type(document.getElementById('confirm-password') as HTMLInputElement, 'Verde Lima! 2026')
    await user.click(screen.getByRole('button', { name: 'Guardar nueva contraseña' }))
    expect(await screen.findByRole('heading', { name: 'Contraseña actualizada' })).toBeInTheDocument()
    expect(posted).toEqual({ token, contrasena: 'Verde Lima! 2026' })
  })

  it('muestra error seguro para token vencido o incorrecto', async () => {
    window.history.replaceState({}, '', `/restablecer-contrasena?token=${'z'.repeat(43)}`)
    server.use(http.post('/api/auth/password-recovery/validar', () => new HttpResponse(null, { status: 410 })))
    renderPage(<ResetPasswordPage />)
    expect(await screen.findByRole('heading', { name: 'Enlace vencido' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Solicitar otro enlace/ })).toHaveAttribute('href', '/recuperar-contrasena')
  })

  it('aplica las cuatro reglas, admite frases largas y conserva el bloqueo de contraseñas comunes', () => {
    expect(passwordPolicyError('corta7!')).toMatch(/8 caracteres/)
    expect(passwordPolicyError('1234567!')).toMatch(/letra/)
    expect(passwordPolicyError('Abcdefg!')).toMatch(/número/)
    expect(passwordPolicyError('Abcdefg1')).toMatch(/símbolo/)
    expect(passwordPolicyError('Verde Lima! 2026')).toBe('')
    expect(passwordPolicyError('Password123!')).toMatch(/común o predecible/)
    expect(passwordPolicyError('Long passphrase with symbols 123!')).toBe('')
  })
})
