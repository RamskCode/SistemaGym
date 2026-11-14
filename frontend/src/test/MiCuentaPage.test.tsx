import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { MiCuentaPage } from '../app/MiCuentaPage'
import { server } from './server'

let auth: AuthContextValue
const baseProfile = {
  nombres: 'Ana', apellidos: 'Prueba', correo: 'ana@example.test', rol: 'RECEPCIONISTA',
  tipoDocumento: 'DNI' as const, numeroDocumento: '12345678',
  contrasenaInicial: false,
}

function token(role: AuthRole = 'RECEPCIONISTA') {
  const now = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: baseProfile.correo, roles: [`ROLE_${role}`], iat: now, exp: now + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}

function Harness() { auth = useAuth(); return <MiCuentaPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
async function signIn(role: AuthRole = 'RECEPCIONISTA') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(token(role))))
  await act(async () => { await auth.login({ correo: baseProfile.correo, contrasena: 'unused' }) })
}

describe('MiCuentaPage', () => {
  beforeEach(() => { window.localStorage.clear(); window.sessionStorage.clear() })

  it('carga y actualiza solo datos personales autorizados del perfil', async () => {
    const user = userEvent.setup()
    let current = { ...baseProfile }
    let requestBody: Record<string, unknown> | undefined
    server.use(
      http.get('/api/mi-cuenta', () => HttpResponse.json(current)),
      http.patch('/api/mi-cuenta', async ({ request }) => {
        requestBody = await request.json() as Record<string, unknown>
        current = { ...current, ...requestBody }
        return HttpResponse.json(current)
      }),
    )
    renderPage()
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    await signIn()

    expect(await screen.findByRole('heading', { name: 'Ana Prueba' })).toBeInTheDocument()
    expect(screen.getAllByText('RECEPCIONISTA', { exact: true })).toHaveLength(2)
    expect(screen.getByText('ana@example.test')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('ana@example.test')).not.toBeInTheDocument()
    expect(screen.getByText('Información de acceso')).toBeInTheDocument()
    expect(screen.getByText('Información laboral')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Editar información personal' }))
    await user.clear(screen.getByLabelText('Nombres'))
    await user.type(screen.getByLabelText('Nombres'), 'Ana María')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tus datos se actualizaron correctamente.')
    expect(requestBody).toEqual({ nombres: 'Ana María', apellidos: 'Prueba', tipoDocumento: 'DNI', numeroDocumento: '12345678' })
    expect(auth.user?.nombres).toBe('Ana María')
    expect(auth.user?.roles).toEqual(['RECEPCIONISTA'])
  })

  it('valida los documentos actuales y la confirmación de contraseña sin persistir credenciales', async () => {
    const user = userEvent.setup()
    let passwordBody: Record<string, unknown> | undefined
    server.use(
      http.get('/api/mi-cuenta', () => HttpResponse.json(baseProfile)),
      http.patch('/api/mi-cuenta/contrasena', async ({ request }) => {
        passwordBody = await request.json() as Record<string, unknown>
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const view = renderPage()
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    await signIn()
    await screen.findByRole('heading', { name: 'Ana Prueba' })
    await user.type(view.container.querySelector<HTMLInputElement>('[autocomplete="current-password"]')!, 'CurrentStrong!42')
    await user.type(view.container.querySelector<HTMLInputElement>('[autocomplete="new-password"]')!, 'DifferentStrong!62')
    await user.type(view.container.querySelectorAll<HTMLInputElement>('[autocomplete="new-password"]')[1], 'DifferentStrong!62')
    await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))

    await waitFor(() => expect(passwordBody).toEqual({
      contrasenaActual: 'CurrentStrong!42', contrasenaNueva: 'DifferentStrong!62',
    }))
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
    expect(auth.isAuthenticated).toBe(false)
  })

  it('presenta los requisitos reales y permite mostrar u ocultar cada contraseña', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json(baseProfile)))
    renderPage()
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    await signIn()
    await screen.findByRole('heading', { name: 'Ana Prueba' })

    const currentPassword = screen.getByLabelText('Contraseña actual')
    expect(currentPassword).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña actual' }))
    expect(currentPassword).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña actual' })).toHaveAttribute('aria-pressed', 'true')

    expect(screen.getByRole('list', { name: 'Requisitos de contraseña' })).toBeInTheDocument()
    const newPassword = screen.getByLabelText('Contraseña nueva')
    await user.type(newPassword, 'DifferentStrong!62')
    expect(screen.getByText('Al menos 8 caracteres').parentElement).toHaveClass('is-valid')
    expect(screen.getByText('Al menos una letra').parentElement).toHaveClass('is-valid')
    expect(screen.getByText('Al menos un número').parentElement).toHaveClass('is-valid')
    expect(screen.getByText('Al menos un símbolo').parentElement).toHaveClass('is-valid')
    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña nueva' }))
    expect(newPassword).toHaveAttribute('type', 'text')

    const confirmation = screen.getByLabelText('Confirmar contraseña nueva')
    await user.click(screen.getByRole('button', { name: 'Mostrar confirmación de contraseña nueva' }))
    expect(confirmation).toHaveAttribute('type', 'text')
  })

  it('rechaza contraseña débil y confirmación distinta antes de enviar', async () => {
    const user = userEvent.setup()
    let requests = 0
    server.use(
      http.get('/api/mi-cuenta', () => HttpResponse.json(baseProfile)),
      http.patch('/api/mi-cuenta/contrasena', () => { requests += 1; return new HttpResponse(null, { status: 204 }) }),
    )
    const view = renderPage()
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    await signIn()
    await screen.findByRole('heading', { name: 'Ana Prueba' })
    await user.type(view.container.querySelector<HTMLInputElement>('[autocomplete="current-password"]')!, 'CurrentStrong!42')
    await user.type(view.container.querySelector<HTMLInputElement>('[autocomplete="new-password"]')!, 'DifferentStrong!62')
    await user.type(view.container.querySelectorAll<HTMLInputElement>('[autocomplete="new-password"]')[1], 'DifferentStrong!63')
    await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))
    expect(screen.getByRole('status')).toHaveTextContent('Las contraseñas aún no coinciden.')
    expect(screen.getByRole('button', { name: 'Actualizar contraseña' })).toBeDisabled()
    expect(requests).toBe(0)
  })

  it('advierte antes de descartar cambios personales y permite conservar la edición', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json(baseProfile)))
    renderPage()
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    await signIn()
    await screen.findByRole('heading', { name: 'Ana Prueba' })
    await user.click(screen.getByRole('button', { name: 'Editar información personal' }))
    await user.clear(screen.getByLabelText('Nombres'))
    await user.type(screen.getByLabelText('Nombres'), 'Ana María')

    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    await user.click(screen.getByRole('button', { name: /Cancelar/ }))
    expect(screen.getByLabelText('Nombres')).toHaveValue('Ana María')
    await user.click(screen.getByRole('button', { name: /Cancelar/ }))
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(screen.queryByLabelText('Nombres')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ana Prueba' })).toBeInTheDocument()
    confirm.mockRestore()
  })

  it('muestra validación y no envía datos personales inválidos', async () => {
    const user = userEvent.setup()
    let requests = 0
    server.use(
      http.get('/api/mi-cuenta', () => HttpResponse.json(baseProfile)),
      http.patch('/api/mi-cuenta', () => { requests += 1; return HttpResponse.json(baseProfile) }),
    )
    renderPage()
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    await signIn()
    await screen.findByRole('heading', { name: 'Ana Prueba' })
    await user.click(screen.getByRole('button', { name: 'Editar información personal' }))
    await user.clear(screen.getByLabelText('Nombres'))
    await user.click(screen.getByRole('button', { name: /Guardar cambios/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Revisa nombres, apellidos y documento')
    expect(screen.getByLabelText('Nombres')).toHaveAttribute('aria-invalid', 'true')
    expect(requests).toBe(0)
  })
})
