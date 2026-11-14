import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, useNavigate, type NavigateFunction } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { AppRoutes } from '../app/App'
import { server } from './server'

const API_PATH = '/api/auth/login'
const credentials = { correo: 'personal@example.test', contrasena: 'clave-de-prueba' }

let auth: AuthContextValue
let navigate: NavigateFunction

function base64Url(value: unknown) {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}

function sessionResponse(role: AuthRole, lifetimeSeconds = 900) {
  const iat = Math.floor(Date.now() / 1000)
  const payload = {
    sub: credentials.correo,
    roles: [`ROLE_${role}`],
    iat,
    exp: iat + lifetimeSeconds,
  }
  return {
    token: `${base64Url({ alg: 'HS256', typ: 'JWT' })}.${base64Url(payload)}.c2lnbmF0dXJl`,
    tipo: 'Bearer',
    expiraEnMs: lifetimeSeconds * 1000,
  }
}

function RouterHarness() {
  auth = useAuth()
  navigate = useNavigate()
  return <AppRoutes />
}

function renderAt(path = '/') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <RouterHarness />
      </MemoryRouter>
    </AuthProvider>,
  )
}

function mockLogin(role: AuthRole, lifetimeSeconds = 900) {
  server.use(http.post(API_PATH, () => HttpResponse.json(sessionResponse(role, lifetimeSeconds))))
}

async function signIn(role: AuthRole, lifetimeSeconds = 900) {
  mockLogin(role, lifetimeSeconds)
  await act(async () => {
    await auth.login(credentials)
  })
}

async function navigateTo(path: string) {
  act(() => navigate(path))
}

afterEach(() => {
  vi.useRealTimers()
  document.cookie = 'SG_CSRF=; Max-Age=0; path=/'
})

describe('role-aware protected navigation', () => {
  it('loads real nutrition indicators and lets the nutritionist choose a member before evaluating', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('/api/nutricion/resumen', () => HttpResponse.json({
        sociosDisponibles: 14, evaluacionesEsteMes: 9, sociosEvaluadosEsteMes: 7,
        evaluacionesUltimosSieteDias: 3,
        evaluacionesRecientes: [{ idEvaluacion: 71, idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', fechaEvaluacion: '2026-11-10T15:00:00Z' }],
      })),
      http.get('/api/socios', () => HttpResponse.json([
        { idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO' },
      ])),
    )
    renderAt()
    await signIn('NUTRICIONISTA')

    expect(await screen.findByRole('heading', { name: 'Panel de nutrición' })).toBeInTheDocument()
    expect(await screen.findByText('14', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('9', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('7', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('3', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText(/SOC-0023 ·/)).toHaveTextContent('10 nov. 2026, 10:00 a. m.')
    expect(screen.getByRole('link', { name: 'Ver historial de Ana Prueba' })).toHaveAttribute('href', '/nutricion?vista=historial&socio=23')

    await user.click(screen.getByRole('link', { name: 'Nueva evaluación' }))
    expect(await screen.findByRole('searchbox', { name: 'Buscar socio por nombre, apellido o código' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Nueva evaluación' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Seleccionar socio Ana Prueba' }))
    expect(await screen.findByRole('heading', { name: 'Nueva evaluación' })).toBeInTheDocument()
  })

  it('restores the current protected route after reloading a valid session', async () => {
    document.cookie = 'SG_CSRF=csrf-session; path=/'
    server.use(
      http.post('/api/auth/refresh', () => HttpResponse.json(sessionResponse('ADMINISTRADOR'))),
      http.get('/api/mi-cuenta', () => HttpResponse.json({
        nombres: 'Admin', apellidos: 'Prueba', correo: credentials.correo,
        rol: 'ADMINISTRADOR', tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
      })),
    )
    renderAt('/mi-cuenta')

    expect(await screen.findByRole('heading', { name: 'Mi perfil' })).toBeInTheDocument()
    expect(auth.isAuthenticated).toBe(true)
    expect(screen.queryByRole('heading', { name: 'Resumen general' })).not.toBeInTheDocument()
  })

  it('shows reduced socios, selects an active member and keeps future actions disabled', async () => {
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    }])))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')

    expect(await screen.findByText('Ana Prueba')).toBeInTheDocument()
    expect(screen.getByText(/SOC-0023/)).toBeInTheDocument()
    expect(screen.queryByText(/12345678|999111222/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Abrir opciones de cuenta/ })).toHaveTextContent('Usuario Prueba')
    await userEvent.setup().click(screen.getByRole('button', { name: /Ver ficha de/ }))
    expect(screen.getByRole('heading', { name: 'Ficha nutricional' })).toBeInTheDocument()
    expect(await screen.findByText('Este socio todavía no tiene evaluaciones nutricionales.')).toBeInTheDocument()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nueva evaluación' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Ver historial' })).toBeEnabled()
  })

  it('allows selecting an inactive member only for the future history action', async () => {
    server.use(http.get('/api/socios/estado/INACTIVO', () => HttpResponse.json([{
      idSocio: 24, codigoSocio: 'SOC-0024', nombres: 'Luis', apellidos: 'Inactivo', estado: 'INACTIVO',
    }])))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await userEvent.setup().selectOptions(screen.getByLabelText('Estado'), 'INACTIVO')
    expect(await screen.findByText('Luis Inactivo')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: /Ver ficha de/ }))

    expect(screen.queryByRole('button', { name: 'Nueva evaluación' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver historial' })).toBeEnabled()
  })

  it('shows ordered history and read-only evaluation detail for an active member', async () => {
    const user = userEvent.setup()
    const older = {
      idEvaluacion: 51, idSocio: 23, idNutricionista: 8, fechaEvaluacion: '2026-11-05T18:00:00Z',
      pesoKg: 80, tallaM: 1.8, porcentajeGrasa: 22, masaMuscularKg: 31,
      habitosAlimenticios: 'Registro anterior', objetivosNutricionales: 'Objetivo anterior',
      recomendacionesNutricionales: 'Recomendación anterior', observacionesSeguimiento: null,
    }
    const latest = {
      ...older, idEvaluacion: 52, idNutricionista: 9, fechaEvaluacion: '2026-11-07T18:00:00Z',
      pesoKg: 78.4, porcentajeGrasa: 20, habitosAlimenticios: 'Registro reciente',
      objetivosNutricionales: 'Objetivo reciente', recomendacionesNutricionales: 'Recomendación reciente',
      observacionesSeguimiento: 'Observación de detalle',
    }
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    }])))
    server.use(http.get('/api/socios/23/evaluaciones-nutricionales', () => HttpResponse.json([older, latest])))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await screen.findByText('Ana Prueba')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    expect(await screen.findByText('78.4 kg')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver historial' }))

    const details = await screen.findAllByRole('button', { name: 'Ver detalle' })
    expect(details).toHaveLength(2)
    const rows = screen.getByRole('list', { name: 'Evaluaciones nutricionales, más recientes primero' })
      .querySelectorAll(':scope > li')
    expect(rows[0]).toHaveTextContent('Nutricionista #9')
    expect(rows[1]).toHaveTextContent('Nutricionista #8')
    expect(screen.getByText((_, element) => element?.tagName === 'STRONG'
      && /7 nov\. 2026, 1:00 p\. m\./.test(element.textContent ?? ''))).toBeInTheDocument()
    await user.click(details[0])
    expect(screen.getByRole('heading', { name: 'Detalle de evaluación' })).toBeInTheDocument()
    expect(screen.getByText('Registro reciente')).toBeInTheDocument()
    expect(screen.getByText('Objetivo reciente')).toBeInTheDocument()
    expect(screen.getByText('Recomendación reciente')).toBeInTheDocument()
    expect(screen.getByText('Observación de detalle')).toBeInTheDocument()
    expect(screen.getByText('Nutricionista #9')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /editar|eliminar/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /volver al historial/i }))
    await user.click(screen.getByRole('button', { name: /volver a ficha/i }))
    await user.click(screen.getByRole('button', { name: /volver al listado/i }))
    expect(screen.getByRole('heading', { name: 'Socios' })).toBeInTheDocument()
  })

  it('shows empty inactive history and never enables new evaluation', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/socios/estado/INACTIVO', () => HttpResponse.json([{
      idSocio: 24, codigoSocio: 'SOC-0024', nombres: 'Luis', apellidos: 'Inactivo', estado: 'INACTIVO',
    }])))
    server.use(http.get('/api/socios/24/evaluaciones-nutricionales', () => HttpResponse.json([])))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await user.selectOptions(screen.getByLabelText('Estado'), 'INACTIVO')
    await screen.findByText('Luis Inactivo')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    await user.click(screen.getByRole('button', { name: 'Ver historial' }))
    expect(await screen.findByText('Este socio todavía no tiene evaluaciones nutricionales.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nueva evaluación' })).not.toBeInTheDocument()
  })

  it('does not treat a similar history row as confirmation of an uncertain POST', async () => {
    const user = userEvent.setup()
    window.sessionStorage.setItem('nutricion:envios-inciertos', JSON.stringify([25]))
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 25, codigoSocio: 'SOC-0025', nombres: 'Eva', apellidos: 'Pendiente', estado: 'ACTIVO',
    }])))
    server.use(http.get('/api/socios/25/evaluaciones-nutricionales', () => HttpResponse.json([{
      idEvaluacion: 80, idSocio: 25, idNutricionista: 7, fechaEvaluacion: '2026-11-07T18:00:00Z',
      pesoKg: 80, tallaM: 1.8, porcentajeGrasa: 20, masaMuscularKg: 30,
      habitosAlimenticios: 'Hábitos coincidentes', objetivosNutricionales: 'Objetivos',
      recomendacionesNutricionales: 'Recomendaciones', observacionesSeguimiento: null,
    }])))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await screen.findByText('Eva Pendiente')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    expect(screen.getByRole('button', { name: /envío bloqueado/i })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Ver historial' }))
    expect(await screen.findByText(/no permite asociar inequívocamente/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver detalle' }))
    expect(screen.getByText('Hábitos coincidentes')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /envío bloqueado/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nueva evaluación' })).not.toBeInTheDocument()
    window.sessionStorage.removeItem('nutricion:envios-inciertos')
  })

  it('shows empty and error states in the nutrition socios list', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/socios', () => HttpResponse.json([])))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    expect(await screen.findByText('No se encontraron socios con esos criterios.')).toBeInTheDocument()

    server.use(http.get('/api/socios', () => HttpResponse.json({}, { status: 500 })))
    await user.type(screen.getByLabelText('Buscar socio por nombre, apellido o código'), 'fallo')
    expect(await screen.findByRole('alert')).toHaveTextContent(/servidor/i)
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('validates fields, requires confirmation and sends one evaluation for an active member', async () => {
    const user = userEvent.setup()
    const payloads: unknown[] = []
    let historyReads = 0
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    }])))
    server.use(http.post('/api/socios/23/evaluaciones-nutricionales', async ({ request }) => {
      payloads.push(await request.json())
      return HttpResponse.json({
        idEvaluacion: 44, idSocio: 23, idNutricionista: 7, fechaEvaluacion: '2026-11-07T18:00:00Z',
        pesoKg: 78.4, tallaM: 1.72, porcentajeGrasa: 24.5, masaMuscularKg: 32.1,
        habitosAlimenticios: 'Tres comidas', objetivosNutricionales: 'Mejorar composición',
        recomendacionesNutricionales: 'Hidratarse', observacionesSeguimiento: null,
      }, { status: 201 })
    }))
    server.use(http.get('/api/socios/23/evaluaciones-nutricionales', () => {
      historyReads += 1
      return HttpResponse.json([{
        idEvaluacion: 44, idSocio: 23, idNutricionista: 7, fechaEvaluacion: '2026-11-07T18:00:00Z',
        pesoKg: 78.4, tallaM: 1.72, porcentajeGrasa: 24.5, masaMuscularKg: 32.1,
        habitosAlimenticios: 'Tres comidas', objetivosNutricionales: 'Mejorar composición',
        recomendacionesNutricionales: 'Hidratarse', observacionesSeguimiento: null,
      }])
    }))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await screen.findByText('Ana Prueba')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    await user.click(screen.getByRole('button', { name: 'Nueva evaluación' }))

    await user.type(screen.getByLabelText(/Peso \(kg\)/i), '78.4')
    await user.type(screen.getByLabelText(/Talla \(m\)/i), '1.72')
    await user.type(screen.getByLabelText(/Porcentaje de grasa/i), '100.01')
    await user.type(screen.getByLabelText(/Masa muscular/i), '32.1')
    await user.type(screen.getByLabelText(/Hábitos alimenticios/), 'Tres comidas')
    await user.type(screen.getByLabelText(/Objetivos nutricionales/), 'Mejorar composición')
    await user.type(screen.getByLabelText(/Recomendaciones nutricionales/), 'Hidratarse')
    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    expect(await screen.findByText(/no puede superar 100/i)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    const grasa = screen.getByLabelText(/Porcentaje de grasa/i)
    await user.clear(grasa)
    await user.type(grasa, '24.5')

    const peso = screen.getByLabelText(/Peso \(kg\)/i)
    await user.clear(peso)
    await user.type(peso, '78.401')
    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    expect(await screen.findByText(/hasta 4 dígitos enteros y 2 decimales/i)).toBeInTheDocument()
    await user.clear(peso)
    await user.type(peso, '78.4')

    const habitos = screen.getByLabelText(/Hábitos alimenticios/)
    await user.clear(habitos)
    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    expect(await screen.findByText('Este campo es obligatorio.')).toBeInTheDocument()
    await user.type(habitos, 'Tres comidas')

    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    expect(await screen.findByRole('dialog', { name: 'Confirmar nueva evaluación' })).toBeInTheDocument()
    expect(payloads).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))

    expect(await screen.findByText(/Registro confirmado por el servidor/)).toBeInTheDocument()
    expect(screen.getByText((_, element) => element?.tagName === 'DD' && element.textContent?.includes('2026') === true))
      .toBeInTheDocument()
    expect(payloads).toHaveLength(1)
    expect(payloads[0]).toEqual({
      pesoKg: 78.4, tallaM: 1.72, porcentajeGrasa: 24.5, masaMuscularKg: 32.1,
      habitosAlimenticios: 'Tres comidas', objetivosNutricionales: 'Mejorar composición',
      recomendacionesNutricionales: 'Hidratarse', observacionesSeguimiento: null,
    })
    await user.click(screen.getByRole('button', { name: 'Ver historial actualizado' }))
    expect(await screen.findByText(/Historial · Ana Prueba/)).toBeInTheDocument()
    expect(await screen.findByText('Nutricionista #7')).toBeInTheDocument()
    expect(historyReads).toBe(2)
  })

  it('preserves form and uses refreshed member status after 409 instead of assuming inactive', async () => {
    const user = userEvent.setup()
    let submissions = 0
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    }])))
    server.use(http.get('/api/socios/23', () => HttpResponse.json({
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    })))
    server.use(http.post('/api/socios/23/evaluaciones-nutricionales', () => {
      submissions += 1
      return HttpResponse.json({ status: 409, error: 'Conflicto', mensaje: 'El estado actual impide registrar esta evaluación.' }, { status: 409 })
    }))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await screen.findByText('Ana Prueba')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    await user.click(screen.getByRole('button', { name: 'Nueva evaluación' }))
    await user.type(screen.getByLabelText(/Peso \(kg\)/i), '80')
    await user.type(screen.getByLabelText(/Talla \(m\)/i), '1.8')
    await user.type(screen.getByLabelText(/Porcentaje de grasa/i), '20')
    await user.type(screen.getByLabelText(/Masa muscular/i), '30')
    await user.type(screen.getByLabelText(/Hábitos alimenticios/), 'Hábitos guardados')
    await user.type(screen.getByLabelText(/Objetivos nutricionales/), 'Objetivos')
    await user.type(screen.getByLabelText(/Recomendaciones nutricionales/), 'Recomendaciones')
    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El estado actual impide registrar esta evaluación.')
    expect(screen.getByLabelText(/Peso \(kg\)/i)).toHaveValue(80)
    expect(screen.getByRole('button', { name: 'Guardar evaluación' })).toBeEnabled()
    expect(submissions).toBe(1)

    // A conflict alone is not evidence of inactivity; the authorized lookup confirmed ACTIVO.
    await user.click(screen.getByRole('button', { name: 'Volver a editar' }))
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.getByRole('button', { name: 'Nueva evaluación' })).toBeEnabled()
    expect(submissions).toBe(1)
  })

  it('locks an ambiguous network result against accidental resubmission', async () => {
    const user = userEvent.setup()
    window.sessionStorage.removeItem('nutricion:envios-inciertos')
    let submissions = 0
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    }])))
    server.use(http.post('/api/socios/23/evaluaciones-nutricionales', () => {
      submissions += 1
      return HttpResponse.error()
    }))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await screen.findByText('Ana Prueba')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    await user.click(screen.getByRole('button', { name: 'Nueva evaluación' }))
    await user.type(screen.getByLabelText(/Peso \(kg\)/i), '80')
    await user.type(screen.getByLabelText(/Talla \(m\)/i), '1.8')
    await user.type(screen.getByLabelText(/Porcentaje de grasa/i), '20')
    await user.type(screen.getByLabelText(/Masa muscular/i), '30')
    await user.type(screen.getByLabelText(/Hábitos alimenticios/), 'Hábitos')
    await user.type(screen.getByLabelText(/Objetivos nutricionales/), 'Objetivos')
    await user.type(screen.getByLabelText(/Recomendaciones nutricionales/), 'Recomendaciones')
    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/pudo haberse registrado/i)
    expect(screen.getByText('80 kg')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /confirmar y registrar/i })).not.toBeInTheDocument()
    expect(submissions).toBe(1)
  })

  it('does not let a late failed registration mutate the UI or uncertain lock after logout', async () => {
    const user = userEvent.setup()
    window.sessionStorage.removeItem('nutricion:envios-inciertos')
    let releaseResponse!: (response: Response) => void
    let requestStarted!: () => void
    const started = new Promise<void>((resolve) => { requestStarted = resolve })
    server.use(http.get('/api/socios', () => HttpResponse.json([{
      idSocio: 23, codigoSocio: 'SOC-0023', nombres: 'Ana', apellidos: 'Prueba', estado: 'ACTIVO',
    }])))
    server.use(http.post('/api/socios/23/evaluaciones-nutricionales', () => {
      requestStarted()
      return new Promise<Response>((resolve) => { releaseResponse = resolve })
    }))
    renderAt('/nutricion')
    await signIn('NUTRICIONISTA')
    await screen.findByText('Ana Prueba')
    await user.click(screen.getByRole('button', { name: /Ver ficha de/ }))
    await user.click(screen.getByRole('button', { name: 'Nueva evaluación' }))
    await user.type(screen.getByLabelText(/Peso \(kg\)/i), '80')
    await user.type(screen.getByLabelText(/Talla \(m\)/i), '1.8')
    await user.type(screen.getByLabelText(/Porcentaje de grasa/i), '20')
    await user.type(screen.getByLabelText(/Masa muscular/i), '30')
    await user.type(screen.getByLabelText(/Hábitos alimenticios/), 'Hábitos')
    await user.type(screen.getByLabelText(/Objetivos nutricionales/), 'Objetivos')
    await user.type(screen.getByLabelText(/Recomendaciones nutricionales/), 'Recomendaciones')
    await user.click(screen.getByRole('button', { name: 'Guardar evaluación' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar y registrar' }))
    await started

    act(() => auth.logout())
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    releaseResponse(HttpResponse.json({ mensaje: 'Respuesta incompleta' }, { status: 201 }))
    await act(async () => { await new Promise((resolve) => window.setTimeout(resolve, 0)) })

    expect(window.sessionStorage.getItem('nutricion:envios-inciertos')).toBeNull()
    expect(screen.queryByText(/pudo haberse registrado/i)).not.toBeInTheDocument()
  })

  it('redirects a visitor from an internal URL to the public login', async () => {
    renderAt('/reportes?periodo=mes')

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Reportes' })).not.toBeInTheDocument()
  })

  it('authenticates through the login form and always opens Inicio, ignoring the previous route', async () => {
    const user = userEvent.setup()
    mockLogin('ADMINISTRADOR')
    renderAt('/reportes?periodo=mes')

    await user.type(await screen.findByLabelText('Correo electrónico'), credentials.correo)
    await user.type(screen.getByLabelText('Contraseña'), credentials.contrasena)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('heading', { name: 'Inicio' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Reportes' })).not.toBeInTheDocument()
  })

  it.each([
    ['ADMINISTRADOR', 'Inicio'],
    ['RECEPCIONISTA', 'Resumen del día'],
    ['ENTRENADOR', 'Mi jornada'],
    ['NUTRICIONISTA', 'Panel de nutrición'],
  ] as const)('sends an authenticated %s visitor from /login to Inicio', async (role, heading) => {
    renderAt('/login')
    await signIn(role)
    await navigateTo('/login')

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Iniciar sesión' })).not.toBeInTheDocument()
  })

  it.each([
    ['ADMINISTRADOR', 'Inicio'],
    ['RECEPCIONISTA', 'Resumen del día'],
    ['ENTRENADOR', 'Mi jornada'],
    ['NUTRICIONISTA', 'Panel de nutrición'],
  ] as const)('%s logout from Mi cuenta followed by a new login opens Inicio', async (role, homeHeading) => {
    const user = userEvent.setup()
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({
      nombres: 'Personal', apellidos: 'Prueba', correo: credentials.correo,
      rol: role, tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
    })))
    renderAt('/')
    await signIn(role)
    await navigateTo('/mi-cuenta')
    expect(await screen.findByRole('heading', { name: 'Mi perfil' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Abrir opciones de cuenta/ }))
    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()

    mockLogin(role)
    await user.type(screen.getByLabelText('Correo electrónico'), credentials.correo)
    await user.type(screen.getByLabelText('Contraseña'), credentials.contrasena)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('heading', { name: homeHeading })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Mi perfil' })).not.toBeInTheDocument()
  })

  it('allows a recepcionista to open the real socios route inside the protected shell', async () => {
    server.use(http.get('/api/socios', () => HttpResponse.json([])))
    renderAt('/socios')
    await signIn('RECEPCIONISTA')

    expect(await screen.findByRole('heading', { name: 'Socios' })).toBeInTheDocument()
    expect(screen.getByText('Aún no hay socios registrados.')).toBeInTheDocument()
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA'] as const)('allows %s to open protected memberships', async (role) => {
    server.use(http.get('/api/membresias', () => HttpResponse.json([])))
    server.use(http.get('/api/planes/estado/ACTIVO', () => HttpResponse.json([])))
    server.use(http.get('/api/socios/estado/ACTIVO', () => HttpResponse.json([])))
    renderAt('/membresias')
    await signIn(role)

    expect(await screen.findByRole('heading', { name: 'Membresías' })).toBeInTheDocument()
    expect(screen.getByText(/Aún no hay membresías registradas/)).toBeInTheDocument()
  })

  it('allows only the administrator to open plan management', async () => {
    server.use(
      http.get('/api/planes', () => HttpResponse.json([])),
      http.get('/api/configuraciones/:clave', ({ params }) => HttpResponse.json({ idConfiguracion: 1, clave: params.clave, valor: '8.00', descripcion: 'Tarifa', fechaActualizacion: '2026-11-10T12:00:00' })),
    )
    renderAt('/planes')
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Planes y tarifas' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent('Planes y tarifas')
  })

  it('allows only the administrator to manage review catalog while trainer routes remain separate', async () => {
    server.use(http.get('/api/elementos-revision', () => HttpResponse.json([])))
    renderAt('/revisiones')
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Equipos y áreas de revisión' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent('Equipos y áreas')
  })

  it('allows only the administrator to open user management', async () => {
    server.use(http.get('/api/usuarios', () => HttpResponse.json([])))
    renderAt('/usuarios')
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Usuarios y empleados' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent('Usuarios y empleados')
  })

  it('allows only the administrator to open global incident management', async () => {
    server.use(http.get('/api/reportes-incidencias', () => HttpResponse.json([])))
    renderAt('/incidencias')
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Incidencias' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent('Incidencias')
  })

  it('allows only the administrator to inspect all checklists', async () => {
    server.use(http.get('/api/checklists', () => HttpResponse.json([])))
    renderAt('/checklists')
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Checklists del gimnasio' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent('Checklists del gimnasio')
  })

  it('does not expose the retired configuration route or navigation item', async () => {
    renderAt('/configuracion')
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
    expect(screen.queryByText('Parámetros del gimnasio')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Configuración' })).not.toBeInTheDocument()
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA'] as const)(
    '%s can open their own account from its protected route', async (role) => {
      server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({
        nombres: 'Cuenta', apellidos: 'Personal', correo: credentials.correo,
        rol: role, tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
      })))
      renderAt('/mi-cuenta')
      await signIn(role)
      expect(await screen.findByRole('heading', { name: 'Mi perfil' })).toBeInTheDocument()
      expect(screen.getAllByText(role, { exact: true })).toHaveLength(3)
      expect(screen.queryByDisplayValue(role)).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Configuración' })).not.toBeInTheDocument()
    },
  )

  it('provides an accessible account dropdown with email, account and logout actions', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({
      nombres: 'Cuenta', apellidos: 'Personal', correo: credentials.correo,
      rol: 'ADMINISTRADOR', tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
    })))
    renderAt()
    await signIn('ADMINISTRADOR')
    const trigger = screen.getByRole('button', { name: /Abrir opciones de cuenta/ })
    await user.click(trigger)
    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    const menu = screen.getByLabelText('Opciones de cuenta')
    expect(within(menu).getAllByText(credentials.correo)).toHaveLength(1)
    expect(within(menu).getByRole('link', { name: 'Mi perfil' })).toHaveAttribute('href', '/mi-cuenta')
    expect(within(menu).getByRole('button', { name: 'Cambiar de cuenta' })).toBeInTheDocument()
    expect(within(menu).queryByRole('link', { name: 'Seguridad' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect(trigger).toHaveFocus()
    await user.click(trigger)
    await user.click(screen.getByRole('button', { name: 'Cambiar de cuenta' }))
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })

  it('shows the non-blocking initial-password reminder only to the authenticated account', async () => {
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({
      nombres: 'Cuenta', apellidos: 'Inicial', correo: credentials.correo,
      rol: 'RECEPCIONISTA', tipoDocumento: null, numeroDocumento: null, contrasenaInicial: true,
    })))
    renderAt('/')
    await signIn('RECEPCIONISTA')
    const notice = await screen.findByText(/Estás utilizando tu contraseña inicial/)
    expect(notice).toHaveTextContent('Estás utilizando tu contraseña inicial.')
    expect(within(notice).getByRole('link', { name: 'Mi perfil → Seguridad' })).toHaveAttribute('href', '/mi-cuenta#seguridad')
  })

  it.each(['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA'] as const)(
    'shows the authenticated account and %s role in the header', async (role) => {
      server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({
        nombres: 'Ada', apellidos: 'Lovelace', correo: credentials.correo,
        rol: role, tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
      })))
      renderAt()
      await signIn(role)

      const identity = screen.getByRole('button', { name: /Abrir opciones de cuenta/ })
      expect(await screen.findByRole('button', { name: 'Abrir opciones de cuenta: Ada Lovelace' })).toBeInTheDocument()
      expect(identity).toHaveTextContent('Ada Lovelace')
      expect(identity).toHaveTextContent(role)
      expect(identity.querySelector('.avatar')).toHaveTextContent('AL')
      await userEvent.setup().click(identity)
      const menu = screen.getByLabelText('Opciones de cuenta')
      expect(within(menu).getAllByText(credentials.correo)).toHaveLength(1)
      expect(within(menu).queryByText('Ada Lovelace')).not.toBeInTheDocument()
      expect(within(menu).queryByText(role)).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()
      expect(screen.queryByText('Gestión de registros')).not.toBeInTheDocument()
    },
  )

  it('shows an explicit non-email error identity when the personal profile cannot be loaded', async () => {
    server.use(http.get('/api/mi-cuenta', () => HttpResponse.json({}, { status: 503 })))
    renderAt()
    await signIn('RECEPCIONISTA')

    const trigger = await screen.findByRole('button', { name: 'Abrir opciones de cuenta: —' })
    expect(trigger).not.toHaveTextContent('Perfil no disponible')
    expect(trigger).not.toHaveTextContent(credentials.correo)
    expect(trigger.querySelector('.topbar__user-meta')).not.toHaveTextContent('Cargando perfil')
    await userEvent.setup().click(trigger)
    const menu = screen.getByLabelText('Opciones de cuenta')
    expect(within(menu).getAllByText(credentials.correo)).toHaveLength(1)
    expect(within(menu).getByRole('status')).toHaveTextContent('Perfil temporalmente no disponible')
    expect(within(menu).queryByText('RECEPCIONISTA')).not.toBeInTheDocument()
    expect(within(menu).queryByText(/Cargando/)).not.toBeInTheDocument()
  })

  it.each(['ENTRENADOR', 'NUTRICIONISTA'] as const)('%s can open their own-attendance route', async (role) => {
    server.use(http.get('/api/asistencias-laborales/mis-asistencias', () => HttpResponse.json({
      contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0,
    })))
    renderAt('/mi-asistencia')
    await signIn(role)

    expect(await screen.findByRole('heading', { name: 'Mis asistencias', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('No hay asistencias propias para el período seleccionado.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Registrar/ })).not.toBeInTheDocument()
  })

  it('allows the trainer-only checklist feature inside the protected training route', async () => {
    server.use(http.get('/api/elementos-revision/activos', () => HttpResponse.json([])))
    renderAt('/entrenamiento')
    await signIn('ENTRENADOR')

    expect(await screen.findByRole('heading', { name: 'Revisión de equipos' })).toBeInTheDocument()
    expect(await screen.findByText('No hay elementos activos para revisar.')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent('Revisión de equipos')
  })

  it('allows the trainer-only own-checklists route inside the protected shell', async () => {
    server.use(http.get('/api/checklists/mios', () => HttpResponse.json([])))
    renderAt('/mis-checklists')
    await signIn('ENTRENADOR')

    expect(await screen.findByRole('heading', { name: 'Mis revisiones' })).toBeInTheDocument()
    expect(screen.getByText('Aún no has registrado inspecciones de equipos.')).toBeInTheDocument()
  })

  it('allows the trainer-only own-reports route inside the protected shell', async () => {
    server.use(http.get('/api/reportes-incidencias/mios', () => HttpResponse.json([])))
    server.use(http.get('/api/elementos-revision/activos', () => HttpResponse.json([])))
    renderAt('/mis-reportes')
    await signIn('ENTRENADOR')

    expect(await screen.findByRole('heading', { name: 'Mis incidencias' })).toBeInTheDocument()
    expect(await screen.findByText('Aún no has registrado incidencias.')).toBeInTheDocument()
  })

  it.each([
    ['ADMINISTRADOR', ['Inicio', 'Socios', 'Membresías', 'Planes y tarifas', 'Usuarios y empleados', 'Asistencia', 'Actividad', 'Reportes', 'Equipos y áreas', 'Incidencias', 'Checklists del gimnasio'], 'Inicio'],
    ['RECEPCIONISTA', ['Inicio', 'Socios', 'Membresías', 'Asistencia'], 'Resumen del día'],
    ['ENTRENADOR', ['Inicio', 'Mis asistencias', 'Revisión de equipos', 'Mis revisiones', 'Mis incidencias'], 'Mi jornada'],
    ['NUTRICIONISTA', ['Inicio', 'Mis asistencias', 'Nutrición'], 'Panel de nutrición'],
  ] as const)('shows only the %s navigation', async (role, expectedItems, homeTitle) => {
    renderAt()
    await signIn(role)

    const navigationElement = screen.getByRole('navigation', { name: 'Navegación principal' })
    const actualItems = within(navigationElement).getAllByRole('link').map((link) =>
      link.querySelector(':scope > span:not(.nav-item__count)')?.textContent?.trim())
    expect(actualItems).toEqual(expectedItems)
    expect(screen.queryByLabelText('2 novedades')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: homeTitle })).toBeInTheDocument()
    if (role === 'NUTRICIONISTA') {
      expect(screen.getByRole('link', { name: 'Buscar socio' })).toHaveAttribute('href', '/nutricion')
      expect(screen.getByRole('link', { name: 'Nueva evaluación' })).toHaveAttribute('href', '/nutricion?accion=nueva')
      expect(screen.queryByText(/vista provisional|próximas funciones/i)).not.toBeInTheDocument()
    }
    expect(screen.queryByText(/portal para socios/i)).not.toBeInTheDocument()
  })

  it.each([
    ['RECEPCIONISTA', '/reportes', 'Reportes'],
    ['RECEPCIONISTA', '/planes', 'Planes y tarifas'],
    ['RECEPCIONISTA', '/revisiones', 'Equipos y áreas de revisión'],
    ['RECEPCIONISTA', '/usuarios', 'Usuarios y empleados'],
    ['RECEPCIONISTA', '/incidencias', 'Incidencias'],
    ['ENTRENADOR', '/incidencias', 'Incidencias'],
    ['NUTRICIONISTA', '/incidencias', 'Incidencias'],
    ['RECEPCIONISTA', '/checklists', 'Checklists del gimnasio'],
    ['ENTRENADOR', '/checklists', 'Checklists del gimnasio'],
    ['NUTRICIONISTA', '/checklists', 'Checklists del gimnasio'],
    ['ENTRENADOR', '/usuarios', 'Usuarios y empleados'],
    ['NUTRICIONISTA', '/usuarios', 'Usuarios y empleados'],
    ['ENTRENADOR', '/revisiones', 'Equipos y áreas de revisión'],
    ['NUTRICIONISTA', '/revisiones', 'Equipos y áreas de revisión'],
    ['ENTRENADOR', '/membresias', 'Membresías'],
    ['ENTRENADOR', '/socios', 'Socios'],
    ['NUTRICIONISTA', '/entrenamiento', 'Revisión de equipos'],
    ['NUTRICIONISTA', '/mis-checklists', 'Mis checklists'],
    ['RECEPCIONISTA', '/mis-checklists', 'Mis checklists'],
    ['ADMINISTRADOR', '/mis-checklists', 'Mis checklists'],
    ['NUTRICIONISTA', '/mis-reportes', 'Mis reportes'],
    ['RECEPCIONISTA', '/mis-reportes', 'Mis reportes'],
    ['ADMINISTRADOR', '/mis-reportes', 'Mis reportes'],
    ['ADMINISTRADOR', '/nutricion', 'Nutrición'],
  ] as const)('denies a %s manually entering %s by URL', async (role, path, forbiddenHeading) => {
    renderAt()
    await signIn(role)
    await navigateTo(path)

    expect(await screen.findByRole('heading', { name: 'Acceso no autorizado' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: forbiddenHeading })).not.toBeInTheDocument()
  })

  it('does not restore an unauthorized requested route after login', async () => {
    const user = userEvent.setup()
    mockLogin('ADMINISTRADOR')
    renderAt('/nutricion')

    await user.type(await screen.findByLabelText('Correo electrónico'), credentials.correo)
    await user.type(screen.getByLabelText('Contraseña'), credentials.contrasena)
    await user.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('heading', { name: 'Inicio' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Nutrición' })).not.toBeInTheDocument()
  })

  it.each([
    ['ADMINISTRADOR', 'Inicio'],
    ['RECEPCIONISTA', 'Resumen del día'],
    ['ENTRENADOR', 'Mi jornada'],
    ['NUTRICIONISTA', 'Panel de nutrición'],
  ] as const)('%s manual reauthentication after session expiry opens Inicio', async (role, homeHeading) => {
    vi.useFakeTimers()
    vi.setSystemTime(Date.UTC(2026, 0, 1, 12))
    renderAt()
    await signIn(role, 60)
    expect(screen.getByRole('heading', { name: homeHeading })).toBeInTheDocument()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000)
    })

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    mockLogin(role)
    await act(async () => { await auth.login(credentials) })
    expect(screen.getByRole('heading', { name: homeHeading })).toBeInTheDocument()
  })

  it('exposes an explicit logout control and ends the current session', async () => {
    const user = userEvent.setup()
    renderAt()
    await signIn('ADMINISTRADOR')
    await user.click(screen.getByRole('button', { name: /Abrir opciones de cuenta/ }))
    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(auth.isAuthenticated).toBe(false)
  })

  it('keeps an authenticated unknown URL within the protected not-found page', async () => {
    renderAt()
    await signIn('ADMINISTRADOR')
    await navigateTo('/ruta-inexistente')

    expect(await screen.findByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
    expect(screen.getByText('/ruta-inexistente')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al resumen' })).toHaveAttribute('href', '/')
  })

  it('keeps existing admin sections inside AppShell and preserves its mobile sidebar behavior', async () => {
    const user = userEvent.setup()
    server.use(http.get('/api/socios', () => HttpResponse.json([])))
    renderAt()
    await signIn('ADMINISTRADOR')

    const navigationElement = screen.getByRole('navigation', { name: 'Navegación principal' })
    await user.click(within(navigationElement).getByRole('link', { name: 'Socios' }))
    expect(screen.getByRole('heading', { name: 'Socios' })).toBeInTheDocument()
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Abrir navegación' }))
    expect(screen.getByRole('button', { name: 'Cerrar navegación' })).toHaveAttribute('aria-expanded', 'true')
    await user.click(screen.getByRole('button', { name: 'Cerrar menú' }))
    expect(screen.getByRole('button', { name: 'Abrir navegación' })).toHaveAttribute('aria-expanded', 'false')

    await user.click(screen.getByRole('button', { name: 'Abrir navegación' }))
    await user.keyboard('{Escape}')
    expect(screen.getByRole('button', { name: 'Abrir navegación' })).toHaveAttribute('aria-expanded', 'false')

    await user.click(screen.getByRole('button', { name: 'Abrir navegación' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar menú lateral' }))
    expect(screen.getByRole('button', { name: 'Abrir navegación' })).toHaveAttribute('aria-expanded', 'false')
  })
})
