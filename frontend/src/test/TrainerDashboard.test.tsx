import { act, render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { AppRoutes } from '../app/App'
import { server } from './server'

let auth: AuthContextValue
function Harness() { auth = useAuth(); return <AppRoutes /> }
function currentLimaDay() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
}
function loginResponse() {
  const now = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'trainer@example.test', roles: ['ROLE_ENTRENADOR'], iat: now, exp: now + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}
async function renderTrainer(path = '/') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(loginResponse())))
  render(<AuthProvider><MemoryRouter initialEntries={[path]}><Harness /></MemoryRouter></AuthProvider>)
  await act(async () => { await auth.login({ correo: 'trainer@example.test', contrasena: 'test' }) })
}

describe('TrainerDashboard', () => {
  it('loads only the trainer own attendance, checklists and reports and derives daily indicators', async () => {
    const day = currentLimaDay()
    const checklist = {
      idChecklist: 48, idEntrenador: 5, correoEntrenador: 'trainer@example.test', fechaHora: `${day}T09:00:00-05:00`, observaciones: null,
      detalles: [{ idDetalle: 1, idElemento: 4, nombreElemento: 'Caminadora', tipoElemento: 'MAQUINA', ubicacionElemento: 'Sala', resultado: 'CON_PROBLEMA', observaciones: null, fechaRevision: `${day}T09:00:00-05:00`, incidencia: null }],
    }
    const seen: string[] = []
    server.use(
      http.get('/api/asistencias-laborales/mis-asistencias', ({ request }) => {
        seen.push(new URL(request.url).pathname)
        return HttpResponse.json({ contenido: [{ idAsistencia: 1, idEmpleado: 5, fechaEntradaUtc: `${day}T13:00:00Z`, fechaSalidaUtc: null, fechaLaboral: day, estado: 'ACTIVA' }], pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 })
      }),
      http.get('/api/checklists/mios', () => { seen.push('/api/checklists/mios'); return HttpResponse.json([checklist]) }),
      http.get('/api/reportes-incidencias/mios', () => { seen.push('/api/reportes-incidencias/mios'); return HttpResponse.json([
        { idReporte: 7, idEntrenador: 5, correoEntrenador: 'trainer@example.test', idElemento: 4, nombreElemento: 'Caminadora', idChecklist: 48, idDetalleChecklist: 1, descripcion: 'Banda desgastada', prioridad: 'MEDIA', estado: 'PENDIENTE', fechaReporte: `${day}T09:00:00-05:00`, fechaActualizacion: `${day}T09:00:00-05:00`, fechaSolucion: null, historial: [] },
      ]) }),
    )
    await renderTrainer()

    expect(await screen.findByRole('heading', { name: 'Mi jornada' })).toBeInTheDocument()
    expect(screen.getByText('Consulta tus actividades y revisiones del gimnasio')).toBeInTheDocument()
    const metrics = within(screen.getByRole('region', { name: 'Indicadores de mi jornada' }))
    expect(metrics.getByText('Mi asistencia de hoy').closest('.trainer-dashboard__metric')).toHaveTextContent('Entrada registrada')
    expect(metrics.getByText('Revisiones realizadas hoy').closest('.trainer-dashboard__metric')).toHaveTextContent('1')
    expect(metrics.getByText('Incidencias sin resolver').closest('.trainer-dashboard__metric')).toHaveTextContent('1')
    expect(metrics.getByText('Equipos con problema hoy').closest('.trainer-dashboard__metric')).toHaveTextContent('1')
    expect(screen.getByText('Caminadora')).toBeInTheDocument()
    expect(screen.getByText('Con hallazgos')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver detalle del checklist 48' })).toHaveAttribute('href', '/mis-checklists?checklist=48')
    expect(screen.getByRole('link', { name: 'Registrar mi asistencia' })).toHaveAttribute('href', '/mi-asistencia')
    expect(screen.getByRole('link', { name: 'Revisar equipos' })).toHaveAttribute('href', '/entrenamiento')
    expect(screen.getAllByRole('link', { name: 'Mis checklists' }).some((link) => link.classList.contains('trainer-dashboard__quick-link'))).toBe(true)
    expect(screen.getAllByRole('link', { name: 'Mis reportes' }).some((link) => link.classList.contains('trainer-dashboard__quick-link'))).toBe(true)
    expect(seen).toEqual(expect.arrayContaining(['/api/checklists/mios', '/api/reportes-incidencias/mios', '/api/asistencias-laborales/mis-asistencias']))
    expect(screen.queryByText(/funcionalidad.*fase posterior/i)).not.toBeInTheDocument()
  })

  it('shows the empty activity state and does not claim attendance or reports that do not exist', async () => {
    await renderTrainer()
    expect(await screen.findByText('Todavía no has registrado revisiones.')).toBeInTheDocument()
    const metrics = within(screen.getByRole('region', { name: 'Indicadores de mi jornada' }))
    expect(metrics.getByText('Mi asistencia de hoy').closest('.trainer-dashboard__metric')).toHaveTextContent('Sin registro')
    expect(metrics.getByText('Revisiones realizadas hoy').closest('.trainer-dashboard__metric')).toHaveTextContent('0')
    expect(metrics.getByText('Incidencias sin resolver').closest('.trainer-dashboard__metric')).toHaveTextContent('0')
  })
})
