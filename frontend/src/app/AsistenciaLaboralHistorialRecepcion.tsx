import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Search, SearchX } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  consultarHistorialAsistenciaRecepcionista,
  listarEmpleadosConHistorial,
  type AsistenciaLaboralPagina,
  type EmpleadoConHistorial,
} from '../api/asistenciasLaborales'
import type { AuthContextValue } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

const PAGE_SIZE = 20 as const

function todayInLima() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date())
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function timeInLima(value: string | null) {
  if (!value) return 'Jornada abierta'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? 'Hora no disponible' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(parsed)
}

function message(reason: unknown) {
  return reason instanceof ApiError ? reason.message : 'No se pudo consultar el historial. Intenta nuevamente.'
}

export function AsistenciaLaboralHistorialRecepcion({ auth }: { auth: AuthContextValue }) {
  const today = todayInLima()
  const [employees, setEmployees] = useState<EmpleadoConHistorial[]>([])
  const [employeeError, setEmployeeError] = useState('')
  const [loadingEmployees, setLoadingEmployees] = useState(true)
  const [employeeId, setEmployeeId] = useState('')
  const [from, setFrom] = useState(today)
  const [to, setTo] = useState(today)
  const [state, setState] = useState('')
  const [filters, setFilters] = useState({ from: today, to: today, state: '', employeeId: '' })
  const [pageNumber, setPageNumber] = useState(0)
  const [result, setResult] = useState<AsistenciaLaboralPagina | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    void listarEmpleadosConHistorial(auth).then((list) => {
      if (active) { setEmployees(list); setEmployeeError('') }
    }).catch((reason: unknown) => {
      if (active) setEmployeeError(message(reason))
    }).finally(() => { if (active) setLoadingEmployees(false) })
    return () => { active = false }
  }, [auth])

  const load = useCallback(async (page: number) => {
    setLoading(true); setError('')
    try {
      const data = await consultarHistorialAsistenciaRecepcionista(auth, {
        page, size: PAGE_SIZE,
        ...(filters.from ? { fechaDesde: filters.from } : {}),
        ...(filters.to ? { fechaHasta: filters.to } : {}),
        ...(filters.state ? { estado: filters.state as 'ACTIVA' | 'ANULADA' } : {}),
      }, filters.employeeId ? Number(filters.employeeId) : undefined)
      setResult(data); setPageNumber(data.pagina)
    } catch (reason) { setError(message(reason)) }
    finally { setLoading(false) }
  }, [auth, filters])

  useEffect(() => { void load(0) }, [load])

  function apply(event: FormEvent) {
    event.preventDefault()
    if (from && to && from > to) return
    setPageNumber(0)
    setFilters({ from, to, state, employeeId })
  }

  function clear() {
    setFrom(''); setTo(''); setState(''); setEmployeeId(''); setPageNumber(0)
    setFilters({ from: '', to: '', state: '', employeeId: '' })
  }

  return <div className="ingresos-supervision">
    <header className="laboral-heading"><div><p className="ingresos-eyebrow">CONSULTA · PERSONAL</p><h2>Historial laboral</h2><p>Consulta las marcaciones propias y del personal, sin opciones de edición.</p></div></header>
    <Card className="asistencia-card laboral-history-card" title="Asistencias registradas">
      <form className="laboral-toolbar" onSubmit={apply}>
        <label className="laboral-filter">Empleado<select aria-label="Empleado" value={employeeId} onChange={(event) => setEmployeeId(event.target.value)}><option value="">Todo el personal</option>{employees.map((person) => <option key={person.idEmpleado} value={person.idEmpleado}>{person.nombres} {person.apellidos} · {roleName(person.rol)}</option>)}</select></label>
        <label className="laboral-filter">Desde<input aria-label="Fecha desde" max={to || undefined} type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label className="laboral-filter">Hasta<input aria-label="Fecha hasta" min={from || undefined} type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
        <label className="laboral-filter">Estado<select aria-label="Estado" value={state} onChange={(event) => setState(event.target.value)}><option value="">Todos</option><option value="ACTIVA">Activa</option><option value="ANULADA">Anulada</option></select></label>
        <div className="laboral-actions"><Button disabled={loading || Boolean(from && to && from > to)} type="submit" variant="secondary"><Search aria-hidden="true" size={15} />Aplicar filtros</Button><Button disabled={loading} onClick={clear} type="button" variant="ghost">Limpiar</Button></div>
      </form>
      {from && to && from > to && <p className="asistencia-feedback asistencia-feedback--error" role="alert">La fecha «Desde» no puede ser posterior a «Hasta».</p>}
      {employeeError && <p className="asistencia-feedback asistencia-feedback--error" role="alert">No se pudo cargar la lista de empleados: {employeeError}</p>}
      {loadingEmployees && <p className="laboral-state" role="status">Cargando empleados…</p>}
      {error && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{error}<Button onClick={() => void load(pageNumber)} type="button" variant="secondary">Reintentar</Button></p>}
      {loading ? <p className="laboral-state" role="status">Cargando asistencias laborales…</p> : error ? null : !result?.contenido.length ? <div className="laboral-empty"><span><SearchX aria-hidden="true" size={20} /></span><strong>No hay asistencias para mostrar</strong><small>Prueba con otras fechas o ajusta los filtros.</small></div> : <>
        <div className="laboral-table-wrap"><table className="laboral-table"><caption className="asistencia-sr-only">Historial de entradas y salidas laborales consultable por recepción</caption><thead><tr><th scope="col">EMPLEADO</th><th scope="col">CARGO</th><th scope="col">FECHA LABORAL</th><th scope="col">ENTRADA (LIMA)</th><th scope="col">SALIDA (LIMA)</th><th scope="col">ESTADO</th></tr></thead><tbody>{result.contenido.map((row) => <tr key={row.idAsistencia}><td data-label="Empleado"><strong>{[row.nombresEmpleado, row.apellidosEmpleado].filter(Boolean).join(' ') || `Empleado #${row.idEmpleado}`}</strong></td><td data-label="Cargo">{row.rolEmpleado ? roleName(row.rolEmpleado) : 'No disponible'}</td><td data-label="Fecha laboral">{row.fechaLaboral}</td><td data-label="Entrada (Lima)">{timeInLima(row.fechaEntradaUtc)}</td><td data-label="Salida (Lima)">{timeInLima(row.fechaSalidaUtc)}</td><td data-label="Estado"><span className={`asistencia-state asistencia-state--${row.estado.toLowerCase()}`}>{row.estado === 'ACTIVA' ? row.fechaSalidaUtc ? 'Cerrada' : 'Abierta' : 'Anulada'}</span></td></tr>)}</tbody></table></div>
        <nav aria-label="Paginación del historial laboral" className="laboral-pagination"><Button disabled={pageNumber <= 0 || loading} onClick={() => void load(pageNumber - 1)} type="button" variant="secondary">Anterior</Button><span>Página {pageNumber + 1} de {Math.max(1, result.totalPaginas)} · {result.totalElementos} registros</span><Button disabled={pageNumber + 1 >= result.totalPaginas || loading} onClick={() => void load(pageNumber + 1)} type="button" variant="secondary">Siguiente</Button></nav>
      </>}
    </Card>
  </div>
}

function roleName(role: string) {
  const names: Record<string, string> = { ENTRENADOR: 'Entrenador', NUTRICIONISTA: 'Nutricionista', RECEPCIONISTA: 'Recepcionista', ADMINISTRADOR: 'Administrador' }
  return names[role] ?? role
}
