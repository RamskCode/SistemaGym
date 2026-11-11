import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue, type AuthRole } from '../auth/AuthContext'
import { MemoryRouter } from 'react-router-dom'
import { NutricionPage } from '../app/NutricionPage'
import { server } from './server'

let auth: AuthContextValue
const socios = [
  { idSocio: 4, codigoSocio: 'SOC-004', nombres: 'María', apellidos: 'López', estado: 'ACTIVO' as const },
  { idSocio: 5, codigoSocio: 'SOC-005', nombres: 'Carlos', apellidos: 'Pérez', estado: 'INACTIVO' as const },
]
const evaluation = {
  idEvaluacion: 21, idSocio: 4, idNutricionista: 8, fechaEvaluacion: '2026-11-08T12:00:00-05:00',
  pesoKg: 62.5, tallaM: 1.65, porcentajeGrasa: 24, masaMuscularKg: 42,
  habitosAlimenticios: 'Tres comidas', objetivosNutricionales: 'Mejorar energía',
  recomendacionesNutricionales: 'Mantener hidratación', observacionesSeguimiento: null,
}
function token(role: AuthRole = 'NUTRICIONISTA') {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'nutrition@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
function Harness() { auth = useAuth(); return <NutricionPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter initialEntries={['/nutricion']}><Harness /></MemoryRouter></AuthProvider>) }
async function login() {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(token())))
  await act(async () => { await auth.login({ correo: 'nutrition@example.test', contrasena: 'unused' }) })
}

describe('NutricionPage', () => {
  beforeEach(() => { window.sessionStorage.clear(); window.localStorage.clear() })

  it('lista socios desde el servicio, permite seleccionarlos y conserva restricciones de inactivos', async () => {
    server.use(http.get('/api/socios', () => HttpResponse.json(socios)))
    renderPage(); await login()
    expect(await screen.findByText('María López')).toBeInTheDocument()
    expect(screen.getByText('Carlos Pérez')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Ver ficha de Carlos Pérez' }))
    expect(await screen.findByText(/Los socios inactivos no pueden recibir nuevas evaluaciones/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nueva evaluación' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver historial' })).toBeInTheDocument()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
  })

  it('aplica búsqueda y filtro mediante las rutas reales del servicio', async () => {
    const requested: string[] = []
    server.use(
      http.get('/api/socios', ({ request }) => { requested.push(new URL(request.url).pathname); return HttpResponse.json(socios) }),
      http.get('/api/socios/buscar', ({ request }) => { requested.push(new URL(request.url).search); return HttpResponse.json([socios[0]]) }),
      http.get('/api/socios/estado/INACTIVO', () => HttpResponse.json([socios[1]])),
    )
    const user = userEvent.setup()
    renderPage(); await login()
    await screen.findByText('María López')
    await user.type(screen.getByRole('searchbox', { name: 'Buscar socio por nombre, apellido o código' }), 'Maria')
    expect(await screen.findByText('María López')).toBeInTheDocument()
    await waitFor(() => expect(requested).toContain('?q=Maria'))
    await user.clear(screen.getByRole('searchbox', { name: 'Buscar socio por nombre, apellido o código' }))
    await user.selectOptions(screen.getByLabelText('Estado'), 'INACTIVO')
    expect(await screen.findByText('Carlos Pérez')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver ficha de Carlos Pérez' }))
    expect(await screen.findByRole('heading', { name: 'Ficha nutricional' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Volver al listado' }))
    expect(screen.getByLabelText('Estado')).toHaveValue('INACTIVO')
    expect(screen.getByText('Carlos Pérez')).toBeInTheDocument()
  })

  it('abre historial, muestra evaluación real y permite consultar su detalle', async () => {
    server.use(
      http.get('/api/socios', () => HttpResponse.json([socios[0]])),
      http.get('/api/socios/4/evaluaciones-nutricionales', () => HttpResponse.json([evaluation])),
    )
    const user = userEvent.setup()
    renderPage(); await login()
    await screen.findByText('María López')
    await user.click(screen.getByRole('button', { name: 'Ver ficha de María López' }))
    await user.click(screen.getByRole('button', { name: 'Ver historial' }))
    expect(await screen.findByRole('heading', { name: 'Historial · María López' })).toBeInTheDocument()
    expect(screen.getByText('62.5 kg')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver detalle' }))
    expect(screen.getByRole('heading', { name: 'Detalle de evaluación' })).toBeInTheDocument()
    expect(screen.getByText('Mejorar energía')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Volver al historial' }))
    expect(screen.getByRole('heading', { name: 'Historial · María López' })).toBeInTheDocument()
  })

  it('distingue error del directorio de lista vacía y ofrece reintentar', async () => {
    let calls = 0
    server.use(http.get('/api/socios', () => ++calls === 1
      ? new HttpResponse(null, { status: 503 })
      : HttpResponse.json([])))
    const user = userEvent.setup()
    renderPage(); await login()
    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor no pudo completar la operación.')
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('No se encontraron socios con esos criterios.')).toBeInTheDocument()
    expect(calls).toBe(2)
  })
})
