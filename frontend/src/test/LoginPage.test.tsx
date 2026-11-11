import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from '../auth/LoginPage'
import { server } from './server'

const API_PATH = '/api/auth/login'

function b64url(value: unknown) {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}

function successfulReply(role = 'RECEPCIONISTA') {
  const issuedAt = Math.floor(Date.now() / 1000)
  const payload = { sub: 'personal@sistemagym.test', roles: [`ROLE_${role}`], iat: issuedAt, exp: issuedAt + 900 }
  return {
    token: `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url(payload)}.c2lnbmF0dXJl`,
    tipo: 'Bearer',
    expiraEnMs: 900_000,
  }
}

function renderLogin() {
  return render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/nutricion' } } }]}>
      <AuthProvider><Routes>
        <Route element={<LoginPage />} path="/login" />
        <Route element={<p>Inicio</p>} path="/" />
        <Route element={<p>Ruta anterior</p>} path="/nutricion" />
      </Routes></AuthProvider>
    </MemoryRouter>,
  )
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Correo electrónico'), 'personal@sistemagym.test')
  await user.type(screen.getByLabelText('Contraseña'), 'clave-de-prueba')
}

afterEach(() => vi.restoreAllMocks())

describe('LoginPage', () => {
  it('renders the Spanish employee login form and SistemaGym identity', () => {
    renderLogin()

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(document.querySelector('.login-brand__name')).toHaveTextContent('SistemaGym')
    expect(screen.getByText('Gestión integral del gimnasio')).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled()
    expect(screen.queryByText(/recordarme|recuperar|crear cuenta/i)).not.toBeInTheDocument()
  })

  it('muestra la configuración de primer administrador cuando el backend la solicita', async () => {
    server.use(http.get('/api/setup/status', () => HttpResponse.json({ required: true })))
    renderLogin()

    expect(await screen.findByRole('heading', { name: 'Configura tu gimnasio' })).toBeInTheDocument()
    expect(screen.getByLabelText('Token de instalación')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear administrador' })).toBeDisabled()
  })

  it('valida requisitos de contraseña progresivamente y permite mostrar ambas contraseñas', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/setup/status', () => HttpResponse.json({ required: true })))
    renderLogin()
    await screen.findByRole('heading', { name: 'Configura tu gimnasio' })
    const password = await screen.findByLabelText('Contraseña')
    const confirmation = screen.getByLabelText('Confirmar contraseña')

    expect(screen.getByRole('list', { name: 'Requisitos de contraseña' })).toBeInTheDocument()
    await user.click(password)
    expect(screen.getByText('Al menos 8 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Al menos una letra')).toBeInTheDocument()
    expect(screen.getByText('Al menos un número')).toBeInTheDocument()
    expect(screen.getByText('Al menos un símbolo')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Requisitos de contraseña' }).querySelectorAll('.is-valid')).toHaveLength(0)
    await user.type(password, 'Verde Lima! 2026')
    expect(screen.getByText('Al menos 8 caracteres').parentElement).toHaveClass('is-valid')
    expect(screen.getByText('Al menos una letra').parentElement).toHaveClass('is-valid')
    expect(screen.getByText('Al menos un número').parentElement).toHaveClass('is-valid')
    expect(screen.getByText('Al menos un símbolo').parentElement).toHaveClass('is-valid')

    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(password).toHaveValue('Verde Lima! 2026')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' })).toHaveAttribute('aria-pressed', 'true')
    await user.type(confirmation, 'Verde Lima! 2026')
    await user.click(screen.getByRole('button', { name: 'Mostrar confirmación de contraseña' }))
    expect(confirmation).toHaveAttribute('type', 'text')
    expect(confirmation).toHaveValue('Verde Lima! 2026')
    expect(screen.getByRole('button', { name: 'Ocultar confirmación de contraseña' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('status')).toHaveTextContent('Las contraseñas coinciden.')
    expect(screen.getByRole('button', { name: 'Crear administrador' })).toBeEnabled()
    await user.clear(password)
    expect(screen.getByRole('button', { name: 'Crear administrador' })).toBeDisabled()
    expect(screen.getByRole('list', { name: 'Requisitos de contraseña' }).querySelectorAll('.is-valid')).toHaveLength(0)
  })

  it('validates required fields and email format without calling the API', async () => {
    const user = userEvent.setup()
    const request = vi.fn()
    server.use(http.post(API_PATH, () => {
      request()
      return HttpResponse.json(successfulReply())
    }))
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Ingresar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Completa tu correo electrónico y contraseña.')

    await user.type(screen.getByLabelText('Correo electrónico'), 'correo-no-valido')
    await user.type(screen.getByLabelText('Contraseña'), 'clave')
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Ingresa un correo electrónico válido.')
    expect(request).not.toHaveBeenCalled()
  })

  it('logs in through AuthContext and shows a safe success message', async () => {
    const user = userEvent.setup()
    let submittedBody: unknown
    server.use(http.post(API_PATH, async ({ request }) => {
      submittedBody = await request.json()
      return HttpResponse.json(successfulReply())
    }))
    renderLogin()
    await fillForm(user)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Inicio')).toBeInTheDocument()
    expect(screen.queryByText('Ruta anterior')).not.toBeInTheDocument()
    expect(submittedBody).toEqual({ correo: 'personal@sistemagym.test', contrasena: 'clave-de-prueba' })
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA'])(
    'redirects %s to Inicio after successful login instead of restoring the prior route', async (role) => {
      const user = userEvent.setup()
      server.use(http.post(API_PATH, () => HttpResponse.json(successfulReply(role))))
      renderLogin()
      await fillForm(user)
      await user.click(screen.getByRole('button', { name: 'Ingresar' }))

      expect(await screen.findByText('Inicio')).toBeInTheDocument()
      expect(screen.queryByText('Ruta anterior')).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Iniciar sesión' })).not.toBeInTheDocument()
    },
  )

  it('shows a safe message for invalid credentials', async () => {
    const user = userEvent.setup()
    server.use(http.post(API_PATH, () => new HttpResponse(null, { status: 401 })))
    renderLogin()
    await fillForm(user)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.')
    expect(screen.queryByText(/usuario inactivo|no existe/i)).not.toBeInTheDocument()
  })

  it('shows a safe network failure message', async () => {
    const user = userEvent.setup()
    server.use(http.post(API_PATH, () => HttpResponse.error()))
    renderLogin()
    await fillForm(user)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor. Comprueba tu conexión.')
  })

  it('displays loading status and prevents duplicate submissions until the request resolves', async () => {
    const user = userEvent.setup()
    let resolveRequest!: (response: Response) => void
    let requests = 0
    server.use(http.post(API_PATH, () => {
      requests += 1
      return new Promise<Response>((resolve) => { resolveRequest = resolve })
    }))
    renderLogin()
    await fillForm(user)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    const loadingButton = await screen.findByRole('button', { name: /Validando acceso/ })
    expect(loadingButton).toBeDisabled()
    await user.click(loadingButton)
    expect(requests).toBe(1)

    await act(async () => {
      resolveRequest(HttpResponse.json(successfulReply()))
    })
    expect(await screen.findByText('Inicio')).toBeInTheDocument()
    expect(requests).toBe(1)
  })

  it('shows and hides the password with an accessible toggle', async () => {
    const user = userEvent.setup()
    renderLogin()
    const password = screen.getByLabelText('Contraseña')

    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Ocultar contraseña' }))
    expect(password).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Mostrar contraseña' })).toHaveAttribute('aria-pressed', 'false')
  })
})
