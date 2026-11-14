import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, useAuth, type AuthContextValue } from '../auth/AuthContext'
import { AsistenciaPage } from '../app/AsistenciaPage'
import { server } from './server'

let auth: AuthContextValue
const row = { idAsistencia: 18, idEmpleado: 31, nombresEmpleado: 'Ana', apellidosEmpleado: 'Díaz', rolEmpleado: 'ENTRENADOR', fechaEntradaUtc: '2026-11-06T04:59:00Z', fechaSalidaUtc: '2026-11-06T05:30:00Z', fechaLaboral: '2026-11-05', estado: 'ACTIVA' }
const employee = { idEmpleado: 5, nombres: 'Luis', apellidos: 'Ramos', rol: 'ENTRENADOR' }
const adminHistory = {
  idAsistencia: 18, empleado: { idUsuario: 31, nombres: 'Ana', apellidos: 'Díaz', correo: 'ana@example.test' },
  fechaEntradaUtc: row.fechaEntradaUtc, fechaSalidaUtc: row.fechaSalidaUtc, fechaLaboral: row.fechaLaboral, estado: 'ACTIVA',
  registradorEntrada: null, registradorSalida: null, eventos: [], auditorias: [],
}

function tokenResponse(role = 'RECEPCIONISTA') {
  const iat = Math.floor(Date.now() / 1000)
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return { token: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'recepcion@example.test', roles: [`ROLE_${role}`], iat, exp: iat + 900 })}.c2ln`, tipo: 'Bearer', expiraEnMs: 900_000 }
}

function Harness() { auth = useAuth(); return <AsistenciaPage /> }
function renderPage() { return render(<AuthProvider><MemoryRouter><Harness /></MemoryRouter></AuthProvider>) }
beforeEach(() => {
  server.use(http.get('/api/ingresos/socios/habilitados', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })))
  server.use(http.get('/api/asistencias-laborales/empleados-con-historial', () => HttpResponse.json([employee])))
})
async function signIn(role = 'RECEPCIONISTA') {
  server.use(http.post('/api/auth/login', () => HttpResponse.json(tokenResponse(role))))
  await act(async () => { await auth.login({ correo: 'recepcion@example.test', contrasena: 'test-password' }) })
  if (role === 'ADMINISTRADOR' || role === 'RECEPCIONISTA') {
    await userEvent.setup().click(await screen.findByRole('tab', { name: 'Asistencia laboral' }))
  }
}
function getHandlers(page = 0) {
  server.use(http.get('/api/ingresos/socios/habilitados', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })))
  server.use(http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])))
  server.use(http.get('/api/asistencias-laborales/consulta-recepcionista', ({ request }) => {
    const url = new URL(request.url)
    return HttpResponse.json({ contenido: page === 0 ? [row] : [], pagina: Number(url.searchParams.get('page')), tamano: 20, totalElementos: 11, totalPaginas: 2 })
  }))
  server.use(http.get('/api/asistencias-laborales/mis-asistencias', ({ request }) => {
    const url = new URL(request.url)
    return HttpResponse.json({ contenido: page === 0 ? [row] : [], pagina: Number(url.searchParams.get('page')), tamano: 10, totalElementos: 11, totalPaginas: 2 })
  }))
}

describe('AsistenciaPage', () => {
  it('loads the read-only customer entry history using Lima today and paginates it', async () => {
    const limaParts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit',
    }).formatToParts(new Date())
    const limaToday = `${limaParts.find((part) => part.type === 'year')?.value}-${limaParts.find((part) => part.type === 'month')?.value}-${limaParts.find((part) => part.type === 'day')?.value}`
    const user = userEvent.setup()
    getHandlers()
    let query = new URLSearchParams()
    server.use(http.get('/api/ingresos/historial', ({ request }) => {
      query = new URL(request.url).searchParams
      return HttpResponse.json({
        contenido: [{ id: 77, tipo: 'SOCIO', persona: 'Ana Quispe', detalle: 'Ingreso con membresía',
          estado: 'REGISTRADO', monto: null, fecha: '2026-11-12T09:30:00',
          documentoProtegido: 'DNI · ••••1234', modalidad: 'MEMBRESIA', operacion: 'PRIMER_INGRESO', responsable: 'Luis Ramos' }],
        pagina: Number(query.get('pagina')), tamano: 25, totalElementos: 26, totalPaginas: 2,
      })
    }))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Historial de ingresos' }))
    expect(await screen.findByText('Ana Quispe')).toBeInTheDocument()
    expect(screen.getAllByText('Membresía').length).toBeGreaterThan(1)
    expect(screen.getByText('DNI · ••••1234')).toBeInTheDocument()
    expect(query.get('desde')).toBe(limaToday)
    expect(query.get('hasta')).toBe(limaToday)
    expect(screen.queryByRole('button', { name: /editar|eliminar|corregir/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(query.get('pagina')).toBe('1'))
  })

  it('searches a member, displays membership eligibility and registers a non-chargeable entry', async () => {
    const user = userEvent.setup()
    getHandlers()
    let body: unknown
    server.use(
      http.get('/api/ingresos/socios/habilitados', () => HttpResponse.json({
        contenido: [{ idSocio: 42, codigoSocio: 'SG-42', tipoDocumento: 'DNI', numeroDocumento: '12345678', nombres: 'Ana', apellidos: 'Quispe', estadoSocio: 'ACTIVO', autorizado: true, motivo: 'Acceso autorizado.', membresia: { idMembresia: 8, plan: 'Plan mensual', estado: 'ACTIVA', fechaInicio: '2026-11-01', fechaVencimiento: '2026-11-30' } }],
        pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1,
      })),
      http.get('/api/ingresos/socios/42/evaluacion', () => HttpResponse.json({
        idSocio: 42, codigoSocio: 'SG-42', tipoDocumento: 'DNI', numeroDocumento: '12345678',
        nombres: 'Ana', apellidos: 'Quispe', estadoSocio: 'ACTIVO', autorizado: true,
        motivo: 'Acceso autorizado.', membresia: { idMembresia: 8, plan: 'Plan mensual', estado: 'ACTIVA', fechaInicio: '2026-11-01', fechaVencimiento: '2026-11-30' },
      })),
      http.get('/api/ingresos/socios/42/historial', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 })),
      http.post('/api/ingresos/membresia', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          idIngreso: 72, tipoIngreso: 'SOCIO', idSocio: 42, idVisitante: null,
          tipoDocumento: 'DNI', numeroDocumento: '12345678', fechaHora: '2026-11-10T10:30:00',
          paseDiarioCobrado: false, montoCobrado: null, idPago: null, resultado: 'ACEPTADO',
        }, { status: 201 })
      }),
    )
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Ingresos de clientes' }))
    expect(await screen.findByRole('table', { name: 'Socios con acceso autorizado por membresía' })).toBeInTheDocument()
    await user.type(screen.getByRole('searchbox', { name: 'Nombre o apellido' }), 'Ana')
    await user.click(await screen.findByRole('button', { name: 'Registrar entrada' }))
    expect((await screen.findAllByText('Plan mensual')).length).toBeGreaterThan(0)
    expect(screen.getByText('Acceso autorizado.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirmar entrada' }))
    expect(await screen.findByText(/Entrada registrada/)).toBeInTheDocument()
    expect(body).toEqual({ idSocio: 42, claveIdempotencia: expect.stringMatching(/^[0-9a-f-]{36}$/i) })
    expect(screen.getByText(/Sin cobro de pase diario/)).toBeInTheDocument()
  })

  it('filters automatically to an empty result and preserves real 403 permission errors', async () => {
    const user = userEvent.setup()
    getHandlers()
    server.use(http.get('/api/ingresos/socios/habilitados', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Ingresos de clientes' }))
    await user.type(screen.getByLabelText('Número de documento'), '60770934')
    expect(await screen.findByText('No hay socios habilitados que coincidan con los filtros.')).toBeInTheDocument()

    server.use(http.get('/api/ingresos/socios/habilitados', () => HttpResponse.json({}, { status: 403 })))
    await user.clear(screen.getByLabelText('Número de documento'))
    await user.type(screen.getByLabelText('Número de documento'), '60770934')
    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso para realizar esta acción.')
    expect(screen.queryByText(/No encontramos ningún socio/)).not.toBeInTheDocument()
  })

  it('sends partial text search and selected stable server sort without a submit action', async () => {
    const user = userEvent.setup()
    getHandlers()
    let lastQuery = new URLSearchParams()
    server.use(http.get('/api/ingresos/socios/habilitados', ({ request }) => {
      lastQuery = new URL(request.url).searchParams
      return HttpResponse.json({ contenido: [], pagina: Number(lastQuery.get('page')), tamano: 20, totalElementos: 0, totalPaginas: 0 })
    }))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Ingresos de clientes' }))
    await user.type(screen.getByRole('searchbox', { name: 'Nombre o apellido' }), 'ang')
    await waitFor(() => expect(lastQuery.get('nombre')).toBe('ang'))
    await user.selectOptions(screen.getByLabelText('Ordenar por'), 'apellidos,desc')
    await waitFor(() => expect(lastQuery.get('sort')).toBe('apellidos,desc'))
    expect(lastQuery.get('page')).toBe('0')
  })

  it('combines name, document type, partial numeric document and ordering; changing filters resets pagination', async () => {
    const user = userEvent.setup()
    getHandlers()
    const requests: URLSearchParams[] = []
    server.use(http.get('/api/ingresos/socios/habilitados', ({ request }) => {
      const params = new URL(request.url).searchParams
      requests.push(params)
      return HttpResponse.json({
        contenido: [{ idSocio: 3, codigoSocio: 'SG-3', tipoDocumento: 'CE', numeroDocumento: '000123456789', nombres: 'Cecilia', apellidos: 'Díaz', estadoSocio: 'ACTIVO', autorizado: true, motivo: 'Acceso autorizado.', membresia: { idMembresia: 4, plan: 'Mensual', estado: 'ACTIVA', fechaInicio: '2026-11-01', fechaVencimiento: '2026-11-30' } }],
        pagina: Number(params.get('page')), tamano: 20, totalElementos: 45, totalPaginas: 3,
      })
    }))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Ingresos de clientes' }))
    await screen.findByText('45 socios')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('1'))

    await user.type(screen.getByRole('searchbox', { name: 'Nombre o apellido' }), 'Cec')
    await waitFor(() => expect(requests.at(-1)?.get('nombre')).toBe('Cec'))
    expect(requests.at(-1)?.get('page')).toBe('0')
    await user.selectOptions(screen.getByLabelText('Tipo de documento'), 'DNI')
    await user.type(screen.getByLabelText('Número de documento'), '00a123456789')
    await user.selectOptions(screen.getByLabelText('Ordenar por'), 'nombres,desc')
    await waitFor(() => {
      expect(requests.at(-1)?.get('nombre')).toBe('Cec')
      expect(requests.at(-1)?.get('tipoDocumento')).toBe('DNI')
      expect(requests.at(-1)?.get('numeroDocumento')).toBe('00123456')
      expect(requests.at(-1)?.get('sort')).toBe('nombres,desc')
      expect(requests.at(-1)?.get('page')).toBe('0')
    })
    expect(screen.getByLabelText('Número de documento')).toHaveValue('00123456')
    await user.click(screen.getByRole('button', { name: 'Limpiar filtros' }))
    await waitFor(() => {
      expect(requests.at(-1)?.get('nombre')).toBe('')
      expect(requests.at(-1)?.get('tipoDocumento')).toBe('')
      expect(requests.at(-1)?.get('numeroDocumento')).toBe('')
    })
    expect(requests.at(-1)?.get('sort')).toBe('nombres,desc')
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Tipo de documento'), 'CE')
    await user.type(screen.getByLabelText('Número de documento'), '0001234567890')
    await waitFor(() => expect(requests.at(-1)?.get('numeroDocumento')).toBe('000123456789'))
    expect(screen.getByLabelText('Número de documento')).toHaveValue('000123456789')
  })

  it.each(['ENTRENADOR', 'NUTRICIONISTA'])('%s solo consulta sus propias asistencias, sin selector ni marcaciones', async (role) => {
    const user = userEvent.setup()
    getHandlers()
    let personalRequests = 0
    const queryIds: string[] = []
    const requests: URLSearchParams[] = []
    server.use(http.get('/api/asistencias-laborales/personal-seleccionable', () => {
      personalRequests += 1
      return HttpResponse.json([employee])
    }))
    server.use(http.get('/api/asistencias-laborales/mis-asistencias', ({ request }) => {
      const url = new URL(request.url)
      queryIds.push(...url.searchParams.getAll('idEmpleado'))
      requests.push(new URLSearchParams(url.searchParams))
      return HttpResponse.json({ contenido: [row], pagina: Number(url.searchParams.get('page')), tamano: 10, totalElementos: 11, totalPaginas: 2 })
    }))
    renderPage()
    await signIn(role)
    expect(await screen.findByRole('heading', { name: 'Mis asistencias', level: 1 })).toBeInTheDocument()
    expect(await screen.findByText(/11:59:00 p\. m\./i)).toBeInTheDocument()
    expect(screen.getByText('2026-11-05')).toBeInTheDocument()
    expect(screen.getByText(/12:30:00 a\. m\./i)).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'FECHA LABORAL' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Entrenador o nutricionista activo')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Registrar/ })).not.toBeInTheDocument()
    expect(personalRequests).toBe(0)
    expect(queryIds).toEqual([])
    await user.type(screen.getByLabelText('Fecha desde'), '2026-11-01')
    await user.type(screen.getByLabelText('Fecha hasta'), '2026-11-30')
    await user.click(screen.getByRole('button', { name: /Aplicar/ }))
    await waitFor(() => expect(requests.at(-1)?.get('fechaDesde')).toBe('2026-11-01'))
    expect(requests.at(-1)?.get('fechaHasta')).toBe('2026-11-30')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('1'))
    expect(requests.at(-1)?.get('size')).toBe('10')
    expect(queryIds).toEqual([])
  })

  it('loads the limited employee list and confirms a staff entry before sending the actual DTO', async () => {
    const user = userEvent.setup()
    getHandlers()
    let body: unknown
    let count = 0
    server.use(http.post('/api/asistencias-laborales/entrada', async ({ request }) => {
      count += 1
      body = await request.json()
      return HttpResponse.json({ ...row, idEmpleado: 5, fechaEntradaUtc: '2026-11-06T04:59:00Z', fechaSalidaUtc: null }, { status: 201 })
    }))
    renderPage()
    await signIn()
    await user.selectOptions(await screen.findByLabelText('Entrenador o nutricionista activo'), '5')
    await user.click(screen.getByRole('button', { name: 'Registrar entrada' }))
    expect(screen.getByRole('dialog', { name: 'Confirmar entrada' })).toBeInTheDocument()
    expect(within(screen.getByRole('dialog')).getByText(/Luis Ramos/)).toBeInTheDocument()
    expect(count).toBe(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar marcación' }))
    expect(await screen.findByRole('status')).toHaveTextContent('entrada registrada')
    expect(screen.getByRole('status')).toHaveTextContent('hora de Lima')
    expect(body).toEqual({ idEmpleado: 5 })
    expect(count).toBe(1)
  })

  it('traps focus in the reception confirmation, closes with Escape, and returns focus to its origin', async () => {
    const user = userEvent.setup()
    getHandlers()
    renderPage()
    await signIn()
    await user.selectOptions(await screen.findByLabelText('Entrenador o nutricionista activo'), '5')
    const trigger = screen.getByRole('button', { name: 'Registrar entrada' })
    await user.click(trigger)
    const dialog = screen.getByRole('dialog', { name: 'Confirmar entrada' })
    const cancel = within(dialog).getByRole('button', { name: 'Cancelar' })
    const confirm = within(dialog).getByRole('button', { name: 'Confirmar marcación' })
    expect(cancel).toHaveFocus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(confirm).toHaveFocus()
    await user.keyboard('{Tab}')
    expect(cancel).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()
  })

  it('posts own attendance with no body, guards duplicate submits, and renders queried times in Lima', async () => {
    const user = userEvent.setup()
    getHandlers()
    let attempts = 0
    let release: (response: Response) => void = () => undefined
    server.use(http.post('/api/asistencias-laborales/mi-entrada', async ({ request }) => {
      attempts += 1
      expect(request.headers.has('Content-Type')).toBe(false)
      return await new Promise<Response>((resolve) => { release = resolve })
    }))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Historial laboral' }))
    expect(await screen.findByText(/11:59 p\. m\./i)).toBeInTheDocument()
    expect(screen.getByText('2026-11-05')).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Asistencia laboral' }))
    await user.click(screen.getByRole('button', { name: 'Registrar mi entrada' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar marcación' }))
    expect(screen.getByRole('button', { name: 'Registrando…' })).toBeDisabled()
    expect(attempts).toBe(1)
    release(HttpResponse.json({ ...row, fechaSalidaUtc: null }, { status: 201 }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Tu asistencia: entrada registrada'))
  })

  it('applies date filters and paginates only the own-assistance endpoint', async () => {
    const user = userEvent.setup()
    let lastQuery = new URLSearchParams()
    server.use(http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])))
    server.use(http.get('/api/asistencias-laborales/consulta-recepcionista', ({ request }) => {
      lastQuery = new URL(request.url).searchParams
      return HttpResponse.json({ contenido: [row], pagina: Number(lastQuery.get('page')), tamano: 20, totalElementos: 11, totalPaginas: 2 })
    }))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Historial laboral' }))
    await screen.findByText('2026-11-05')
    fireEvent.change(screen.getByLabelText('Fecha desde'), { target: { value: '2026-11-01' } })
    fireEvent.change(screen.getByLabelText('Fecha hasta'), { target: { value: '2026-11-30' } })
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    await waitFor(() => expect(lastQuery.get('fechaDesde')).toBe('2026-11-01'))
    expect(lastQuery.get('fechaHasta')).toBe('2026-11-30')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(lastQuery.get('page')).toBe('1'))
    expect(lastQuery.get('size')).toBe('20')
  })

  it('explains an uncertain network result and does not retry automatically', async () => {
    const user = userEvent.setup()
    getHandlers()
    let attempts = 0
    server.use(http.post('/api/asistencias-laborales/mi-salida', () => {
      attempts += 1
      return HttpResponse.error()
    }))
    renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Historial laboral' }))
    await screen.findByText('2026-11-05')
    await user.click(screen.getByRole('tab', { name: 'Asistencia laboral' }))
    await user.click(screen.getByRole('button', { name: 'Registrar mi salida' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar marcación' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Marcación bloqueada por resultado incierto')
    expect(screen.getByRole('alert')).toHaveTextContent('Pudo registrarse; no reintentes')
    expect(attempts).toBe(1)
  })

  it('persists a 5xx attendance lock across remount and requires review before retry', async () => {
    const user = userEvent.setup()
    getHandlers()
    let calls = 0
    server.use(http.post('/api/asistencias-laborales/mi-entrada', () => { calls += 1; return HttpResponse.json({}, { status: 500 }) }))
    const page = renderPage()
    await signIn()
    await user.click(screen.getByRole('tab', { name: 'Historial laboral' }))
    await screen.findByText('2026-11-05')
    await user.click(screen.getByRole('tab', { name: 'Asistencia laboral' }))
    await user.click(screen.getByRole('button', { name: 'Registrar mi entrada' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar marcación' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Marcación bloqueada por resultado incierto')
    expect(calls).toBe(1)
    page.unmount()
    renderPage()
    await signIn()
    expect(await screen.findByRole('alert')).toHaveTextContent('Solicita revisión administrativa')
    expect(screen.getByRole('button', { name: 'Registrar mi entrada' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Registrar mi salida' })).toBeDisabled()
    expect(calls).toBe(1)
  })

  it('offers the administrator global labor attendance management without daily-pass sales', async () => {
    getHandlers()
    server.use(http.get('/api/asistencias-laborales', ({ request }) => {
      const url = new URL(request.url)
      return HttpResponse.json({ contenido: [row], pagina: Number(url.searchParams.get('page')), tamano: Number(url.searchParams.get('size')), totalElementos: 21, totalPaginas: 2 })
    }))
    renderPage()
    await signIn('ADMINISTRADOR')
    expect(await screen.findByRole('heading', { name: 'Asistencia', level: 1 })).toBeInTheDocument()
    expect(await screen.findByText('2026-11-05')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'EMPLEADO' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar mi entrada' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar entrada' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /corregir|anular/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Pase diario · acceso' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Buscar persona/ })).not.toBeInTheDocument()
  })

  it('traps focus in the administrator history dialog and restores focus after Escape', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => HttpResponse.json(adminHistory)),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    const trigger = await screen.findByRole('button', { name: /Ver historial/ })
    await user.click(trigger)
    const dialog = await screen.findByRole('dialog', { name: 'Detalle e historial de asistencia' })
    const close = within(dialog).getByRole('button', { name: 'Cerrar detalle' })
    expect(close).toHaveFocus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(within(dialog).getByRole('button', { name: 'Anular asistencia' })).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()
  })

  it('corrects changed Lima time as UTC only after fresh state and explicit confirmation, then refreshes audit and record', async () => {
    const user = userEvent.setup()
    let current = { ...adminHistory }
    let patchCalls = 0
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => HttpResponse.json(current)),
      http.patch('/api/asistencias-laborales/18/correccion', async ({ request }) => {
        patchCalls += 1
        expect(await request.json()).toEqual({ fechaEntradaUtc: '2026-11-06T05:05:00.000Z', motivo: 'Ajuste autorizado' })
        current = { ...current, fechaEntradaUtc: '2026-11-06T05:05:00.000Z', auditorias: [{ idAuditoria: 9, tipoOperacion: 'CORRECCION', motivo: 'Ajuste autorizado', fechaHoraUtc: '2026-11-07T01:00:00Z', actor: { idUsuario: 1, nombres: 'Admin', apellidos: 'Uno', correo: 'admin@example.test' }, estadoAnterior: 'ACTIVA', estadoNuevo: 'ACTIVA', fechaEntradaAnteriorUtc: row.fechaEntradaUtc, fechaSalidaAnteriorUtc: row.fechaSalidaUtc, fechaEntradaNuevaUtc: '2026-11-06T05:05:00.000Z', fechaSalidaNuevaUtc: row.fechaSalidaUtc }] }
        return HttpResponse.json({ ...row, fechaEntradaUtc: current.fechaEntradaUtc })
      }),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    await user.click(await screen.findByRole('button', { name: 'Corregir horarios' }))
    expect(screen.getByText(/Entrada vigente:/)).toBeInTheDocument()
    expect(screen.getByLabelText('Nueva entrada (hora de Lima)')).toHaveValue('2026-11-05T23:59')
    await user.clear(screen.getByLabelText('Nueva entrada (hora de Lima)'))
    await user.type(screen.getByLabelText('Nueva entrada (hora de Lima)'), '2026-11-06T00:05:00')
    await user.type(screen.getByLabelText('Motivo obligatorio'), 'Ajuste autorizado')
    expect(patchCalls).toBe(0)
    await user.click(screen.getByRole('checkbox', { name: /Confirmo explícitamente esta corrección/ }))
    await user.click(screen.getByRole('button', { name: 'Confirmar corrección' }))
    expect(await screen.findByText('Corrección guardada y auditoría actualizada.')).toBeInTheDocument()
    expect(await screen.findByText(/Motivo: Ajuste autorizado/)).toBeInTheDocument()
    expect(patchCalls).toBe(1)
  })

  it('annuls an active attendance only with a reason and explicit confirmation, retaining its audited state', async () => {
    const user = userEvent.setup()
    let current = { ...adminHistory }
    let patchCalls = 0
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => HttpResponse.json(current)),
      http.patch('/api/asistencias-laborales/18/anulacion', async ({ request }) => {
        patchCalls += 1
        expect(await request.json()).toEqual({ motivo: 'Marcación duplicada' })
        current = { ...current, estado: 'ANULADA', auditorias: [{ idAuditoria: 10, tipoOperacion: 'ANULACION', motivo: 'Marcación duplicada', fechaHoraUtc: '2026-11-07T01:00:00Z', actor: { idUsuario: 1, nombres: 'Admin', apellidos: 'Uno', correo: 'admin@example.test' }, estadoAnterior: 'ACTIVA', estadoNuevo: 'ANULADA', fechaEntradaAnteriorUtc: row.fechaEntradaUtc, fechaSalidaAnteriorUtc: row.fechaSalidaUtc, fechaEntradaNuevaUtc: row.fechaEntradaUtc, fechaSalidaNuevaUtc: row.fechaSalidaUtc }] }
        return HttpResponse.json({ ...row, estado: 'ANULADA' })
      }),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    await user.click(await screen.findByRole('button', { name: 'Anular asistencia' }))
    await user.type(screen.getByLabelText('Motivo obligatorio'), 'Marcación duplicada')
    await user.click(screen.getByRole('checkbox', { name: /Confirmo explícitamente esta anulación/ }))
    await user.click(screen.getByRole('button', { name: 'Confirmar anulación' }))
    expect(await screen.findByText('Asistencia anulada y auditoría actualizada.')).toBeInTheDocument()
    expect(screen.getByText('ANULADA')).toBeInTheDocument()
    expect(screen.getByText(/Motivo: Marcación duplicada/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Anular asistencia' })).not.toBeInTheDocument()
    expect(patchCalls).toBe(1)
  })

  it('does not send a correction with no changed time and refreshes state after a 409 conflict', async () => {
    const user = userEvent.setup()
    let patchCalls = 0
    let historyCalls = 0
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => { historyCalls += 1; return HttpResponse.json(adminHistory) }),
      http.patch('/api/asistencias-laborales/18/correccion', () => { patchCalls += 1; return HttpResponse.json({ mensaje: 'La salida debe ser posterior a la entrada.' }, { status: 409 }) }),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    await user.click(await screen.findByRole('button', { name: 'Corregir horarios' }))
    await user.type(screen.getByLabelText('Motivo obligatorio'), 'Motivo de prueba')
    await user.click(screen.getByRole('checkbox', { name: /Confirmo explícitamente esta corrección/ }))
    await user.click(screen.getByRole('button', { name: 'Confirmar corrección' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/debe cambiar al menos un horario/i)
    expect(patchCalls).toBe(0)
    await user.clear(screen.getByLabelText('Nueva entrada (hora de Lima)'))
    await user.type(screen.getByLabelText('Nueva entrada (hora de Lima)'), '2026-11-06T00:05')
    await user.click(screen.getByRole('button', { name: 'Confirmar corrección' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/posterior a la entrada/i)
    expect(patchCalls).toBe(1)
    expect(historyCalls).toBeGreaterThanOrEqual(3)
    expect(screen.queryByText(/Resultado incierto/)).not.toBeInTheDocument()
  })

  it('prevents double submission while a correction PATCH is pending', async () => {
    const user = userEvent.setup()
    let patchCalls = 0
    let release!: (response: Response) => void
    let current = { ...adminHistory }
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => HttpResponse.json(current)),
      http.patch('/api/asistencias-laborales/18/correccion', async ({ request }) => {
        patchCalls += 1
        const body = await request.json() as { fechaEntradaUtc: string; motivo: string }
        current = { ...current, fechaEntradaUtc: body.fechaEntradaUtc, auditorias: [{ idAuditoria: 11, tipoOperacion: 'CORRECCION', motivo: body.motivo, fechaHoraUtc: '2026-11-07T01:00:00Z', actor: { idUsuario: 1, nombres: 'Admin', apellidos: 'Uno', correo: 'admin@example.test' }, estadoAnterior: 'ACTIVA', estadoNuevo: 'ACTIVA', fechaEntradaAnteriorUtc: row.fechaEntradaUtc, fechaSalidaAnteriorUtc: row.fechaSalidaUtc, fechaEntradaNuevaUtc: body.fechaEntradaUtc, fechaSalidaNuevaUtc: row.fechaSalidaUtc }] }
        return new Promise<Response>((resolve) => { release = resolve })
      }),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    await user.click(await screen.findByRole('button', { name: 'Corregir horarios' }))
    await user.clear(screen.getByLabelText('Nueva entrada (hora de Lima)'))
    await user.type(screen.getByLabelText('Nueva entrada (hora de Lima)'), '2026-11-06T00:05')
    await user.type(screen.getByLabelText('Motivo obligatorio'), 'Ajuste')
    await user.click(screen.getByRole('checkbox', { name: /Confirmo explícitamente esta corrección/ }))
    await user.click(screen.getByRole('button', { name: 'Confirmar corrección' }))
    await waitFor(() => expect(release).toBeTypeOf('function'))
    expect(patchCalls).toBe(1)
    expect(screen.getByRole('button', { name: 'Procesando…' })).toBeDisabled()
    release(HttpResponse.json(row))
    expect(await screen.findByText('Corrección guardada y auditoría actualizada.')).toBeInTheDocument()
    expect(patchCalls).toBe(1)
  })

  it('does not let a late correction response update the screen after logout', async () => {
    const user = userEvent.setup()
    let release!: (response: Response) => void
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => HttpResponse.json(adminHistory)),
      http.patch('/api/asistencias-laborales/18/anulacion', () => new Promise<Response>((resolve) => { release = resolve })),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    await user.click(await screen.findByRole('button', { name: 'Anular asistencia' }))
    await user.type(screen.getByLabelText('Motivo obligatorio'), 'Motivo')
    await user.click(screen.getByRole('checkbox', { name: /Confirmo explícitamente esta anulación/ }))
    await user.click(screen.getByRole('button', { name: 'Confirmar anulación' }))
    await waitFor(() => expect(release).toBeTypeOf('function'))
    await act(async () => { auth.logout() })
    release(HttpResponse.json({ ...row, estado: 'ANULADA' }))
    await waitFor(() => expect(auth.isAuthenticated).toBe(false))
    expect(screen.queryByText('Asistencia anulada y auditoría actualizada.')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Detalle e historial de asistencia' })).not.toBeInTheDocument()
    await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    expect(await screen.findByRole('button', { name: 'Consultar registro e historial' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Consultar registro e historial' }))
    expect(await screen.findByText(/Registro e historial verificados/)).toBeInTheDocument()
  })

  it('blocks repeated corrections after uncertain write result until record and history are verified', async () => {
    const user = userEvent.setup()
    let patchCalls = 0
    let historyCalls = 0
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [row], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 })),
      http.get('/api/asistencias-laborales/18/historial', () => { historyCalls += 1; return HttpResponse.json(adminHistory) }),
      http.patch('/api/asistencias-laborales/18/correccion', () => { patchCalls += 1; return HttpResponse.error() }),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    await user.click(await screen.findByRole('button', { name: 'Corregir horarios' }))
    await user.clear(screen.getByLabelText('Nueva entrada (hora de Lima)'))
    await user.type(screen.getByLabelText('Nueva entrada (hora de Lima)'), '2026-11-06T00:05:00')
    await user.type(screen.getByLabelText('Motivo obligatorio'), 'Verificar resultado')
    await user.click(screen.getByRole('checkbox', { name: /Confirmo explícitamente esta corrección/ }))
    await user.click(screen.getByRole('button', { name: 'Confirmar corrección' }))
    expect(await screen.findByText(/Resultado incierto/)).toBeInTheDocument()
    expect(patchCalls).toBe(1)
    expect(screen.queryByRole('button', { name: 'Anular asistencia' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Consultar registro e historial' }))
    expect(await screen.findByText(/Registro e historial verificados/)).toBeInTheDocument()
    expect(historyCalls).toBeGreaterThanOrEqual(3)
    expect(screen.getByRole('button', { name: 'Anular asistencia' })).toBeInTheDocument()
    expect(patchCalls).toBe(1)
  })

  it('uses server filters and pagination and renders read-only history timestamps in Lima', async () => {
    const user = userEvent.setup()
    let lastQuery = new URLSearchParams()
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', ({ request }) => {
        lastQuery = new URL(request.url).searchParams
        return HttpResponse.json({ contenido: [{ ...row, idEmpleado: 31 }], pagina: Number(lastQuery.get('page')), tamano: Number(lastQuery.get('size')), totalElementos: 41, totalPaginas: 3 })
      }),
      http.get('/api/asistencias-laborales/empleado/:id', ({ request }) => {
        lastQuery = new URL(request.url).searchParams
        return HttpResponse.json({ contenido: [row], pagina: Number(lastQuery.get('page')), tamano: Number(lastQuery.get('size')), totalElementos: 41, totalPaginas: 3 })
      }),
      http.get('/api/asistencias-laborales/18/historial', () => HttpResponse.json({
        idAsistencia: 18, empleado: { idUsuario: 31, nombres: 'Ana', apellidos: 'Díaz', correo: 'private@example.test' },
        fechaEntradaUtc: row.fechaEntradaUtc, fechaSalidaUtc: row.fechaSalidaUtc, fechaLaboral: row.fechaLaboral, estado: 'ACTIVA',
        registradorEntrada: null, registradorSalida: null,
        eventos: [{ idEvento: 1, tipoEvento: 'ENTRADA', fechaHoraUtc: row.fechaEntradaUtc, registrador: { idUsuario: 2, nombres: 'Recepción', apellidos: 'Uno', correo: 'r@example.test' } }],
        auditorias: [{ idAuditoria: 3, tipoOperacion: 'CORRECCION', motivo: 'Ajuste validado', fechaHoraUtc: row.fechaEntradaUtc, actor: { idUsuario: 1, nombres: 'Admin', apellidos: 'Uno', correo: 'a@example.test' }, estadoAnterior: 'ACTIVA', estadoNuevo: 'ACTIVA', fechaEntradaAnteriorUtc: row.fechaEntradaUtc, fechaSalidaAnteriorUtc: null, fechaEntradaNuevaUtc: row.fechaEntradaUtc, fechaSalidaNuevaUtc: null }],
      })),
    )
    renderPage()
    await signIn('ADMINISTRADOR')
    await screen.findByText('2026-11-05')
    await user.selectOptions(screen.getByLabelText('Estado'), 'ANULADA')
    await user.selectOptions(screen.getAllByLabelText('Empleado')[0], '5')
    await user.type(screen.getByLabelText('Fecha desde'), '2026-11-01')
    await user.type(screen.getByLabelText('Fecha hasta'), '2026-11-30')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    await waitFor(() => expect(lastQuery.get('estado')).toBe('ANULADA'))
    expect(lastQuery.get('fechaDesde')).toBe('2026-11-01')
    expect(lastQuery.get('fechaHasta')).toBe('2026-11-30')
    expect(lastQuery.get('size')).toBe('20')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    await waitFor(() => expect(lastQuery.get('page')).toBe('1'))
    await user.click(await screen.findByRole('button', { name: /Ver historial/ }))
    expect(await screen.findByRole('dialog', { name: 'Detalle e historial de asistencia' })).toBeInTheDocument()
    expect(screen.getAllByText('Ana Díaz').length).toBeGreaterThan(0)
    expect(screen.getByText(/Motivo: Ajuste validado/)).toBeInTheDocument()
    expect(within(screen.getByRole('dialog')).getAllByText(/11:59:00 p\. m\./i).length).toBeGreaterThan(0)
    expect(screen.getByText(/CORRECCION/)).toBeInTheDocument()
  })

  it('queries report only for selected employee and validates the inclusive calendar range', async () => {
    const user = userEvent.setup()
    let reportUrl = ''
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })),
      http.get('/api/asistencias-laborales/empleado/:id', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })),
      http.get('/api/asistencias-laborales/reportes/empleados/:id', ({ request }) => {
        reportUrl = request.url
        return HttpResponse.json({ idEmpleado: 5, fechaDesde: '2026-01-01', fechaHasta: '2026-12-31', cantidadActivas: 2, cantidadAnuladas: 1, jornadasCerradas: 2, jornadasAbiertas: 0, totalHorasRegistradas: 16.5 })
      }),
    )
    renderPage()
    await signIn('ADMINISTRADOR')
    await user.selectOptions(await screen.findByLabelText('Empleado del reporte'), '5')
    await user.type(screen.getByLabelText('Desde del reporte'), '2026-01-01')
    await user.type(screen.getByLabelText('Hasta del reporte'), '2026-12-31')
    await user.click(screen.getByRole('button', { name: 'Consultar reporte' }))
    expect(await screen.findByText('16.5', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText(/Reporte consultado/)).toBeInTheDocument()
    expect(new URL(reportUrl).pathname).toBe('/api/asistencias-laborales/reportes/empleados/5')
    expect(new URL(reportUrl).searchParams.get('fechaDesde')).toBe('2026-01-01')
  })

  it('filters history and requests report for an inactive employee excluded from attendance entry choices', async () => {
    const user = userEvent.setup()
    const formerEmployee = { idEmpleado: 44, nombres: 'Maricarmen Maira', apellidos: 'Santos Hinostrosa', rol: 'RECEPCIONISTA' }
    const formerEmployeeRow = { ...row, idEmpleado: 44, nombresEmpleado: formerEmployee.nombres, apellidosEmpleado: formerEmployee.apellidos, rolEmpleado: formerEmployee.rol }
    let filteredEmployeeId = ''
    let reportEmployeeId = ''
    server.use(
      http.get('/api/asistencias-laborales/empleados-con-historial', () => HttpResponse.json([employee, formerEmployee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })),
      http.get('/api/asistencias-laborales/empleado/:id', ({ params, request }) => {
        filteredEmployeeId = String(params.id)
        const query = new URL(request.url).searchParams
        return HttpResponse.json({ contenido: [formerEmployeeRow], pagina: Number(query.get('page')), tamano: 20, totalElementos: 2, totalPaginas: 1 })
      }),
      http.get('/api/asistencias-laborales/reportes/empleados/:id', ({ params }) => {
        reportEmployeeId = String(params.id)
        return HttpResponse.json({ idEmpleado: 44, fechaDesde: '2026-11-01', fechaHasta: '2026-11-30', cantidadActivas: 2, cantidadAnuladas: 0, jornadasCerradas: 2, jornadasAbiertas: 0, totalHorasRegistradas: 0.5 })
      }),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await waitFor(() => expect(screen.getAllByRole('option', { name: /Maricarmen Maira Santos Hinostrosa/ })).toHaveLength(2))
    await user.selectOptions(screen.getAllByLabelText('Empleado')[0], '44')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
    expect(await screen.findByRole('row', { name: /Maricarmen Maira Santos Hinostrosa/ })).toBeInTheDocument()
    expect(screen.getByText('Recepcionista')).toBeInTheDocument()
    expect(filteredEmployeeId).toBe('44')

    await user.selectOptions(screen.getByLabelText('Empleado del reporte'), '44')
    await user.type(screen.getByLabelText('Desde del reporte'), '2026-11-01')
    await user.type(screen.getByLabelText('Hasta del reporte'), '2026-11-30')
    await user.click(screen.getByRole('button', { name: 'Consultar reporte' }))
    expect(await screen.findByText('0.5', { selector: 'dd' })).toBeInTheDocument()
    expect(reportEmployeeId).toBe('44')
  })

  it('shows an explanatory empty state when the report has no attendance records', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })),
      http.get('/api/asistencias-laborales/reportes/empleados/:id', () => HttpResponse.json({ idEmpleado: 5, fechaDesde: '2026-01-01', fechaHasta: '2026-01-31', cantidadActivas: 0, cantidadAnuladas: 0, jornadasCerradas: 0, jornadasAbiertas: 0, totalHorasRegistradas: 0 })),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.selectOptions(await screen.findByLabelText('Empleado del reporte'), '5')
    await user.type(screen.getByLabelText('Desde del reporte'), '2026-01-01')
    await user.type(screen.getByLabelText('Hasta del reporte'), '2026-01-31')
    await user.click(screen.getByRole('button', { name: 'Consultar reporte' }))
    expect(await screen.findByText('Sin asistencias en este período')).toBeInTheDocument()
  })

  it('presents a report query failure as an actionable error state', async () => {
    const user = userEvent.setup()
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })),
      http.get('/api/asistencias-laborales/reportes/empleados/:id', () => HttpResponse.json({}, { status: 500 })),
    )
    renderPage(); await signIn('ADMINISTRADOR')
    await user.selectOptions(await screen.findByLabelText('Empleado del reporte'), '5')
    await user.type(screen.getByLabelText('Desde del reporte'), '2026-01-01')
    await user.type(screen.getByLabelText('Hasta del reporte'), '2026-01-31')
    await user.click(screen.getByRole('button', { name: 'Consultar reporte' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor no pudo completar la operación.')
  })

  it.each([400, 401, 403, 404])('shows a safe query error for HTTP %s', async (status) => {
    server.use(
      http.get('/api/asistencias-laborales/personal-seleccionable', () => HttpResponse.json([employee])),
      http.get('/api/asistencias-laborales', () => HttpResponse.json({ mensaje: 'error' }, { status })),
    )
    renderPage()
    await signIn('ADMINISTRADOR')
    if (status === 401) {
      expect(await screen.findByText(/destinada al rol RECEPCIONISTA/)).toBeInTheDocument()
    } else {
      expect(await screen.findByText(status === 400 ? 'Revisa los datos ingresados e inténtalo de nuevo.' : status === 403 ? 'No tienes permiso para realizar esta acción.' : 'No se encontró el recurso solicitado.')).toBeInTheDocument()
    }
  })
})
