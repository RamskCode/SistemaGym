import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { BriefcaseBusiness, CalendarDays, Search, SearchX } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  consultarAsistenciasAdmin,
  consultarHistorialAsistencia,
  consultarReporteAsistenciaEmpleado,
  anularAsistenciaLaboral,
  corregirAsistenciaLaboral,
  listarPersonalSeleccionable,
  type AsistenciaLaboralPagina,
  type HistorialAsistenciaLaboral,
  type PersonalSeleccionable,
  type ReporteAsistenciaEmpleado,
} from '../api/asistenciasLaborales'
import type { AuthContextValue } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

const PAGE_SIZE = 20 as const
type AppliedFilters = { desde: string; hasta: string; estado: string; idEmpleado: string }
// Keep uncertain write guards for the lifetime of the frontend runtime, including logout/login remounts.
const uncertainAttendanceWrites = new Set<number>()

function horaLima(value: string | null) {
  if (!value) return 'Pendiente'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Hora no disponible'
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'medium', timeZone: 'America/Lima',
  }).format(date)
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : 'No se pudo completar la consulta.'
}

function rangoHastaDoceMeses(desde: string, hasta: string) {
  if (!desde || !hasta || desde > hasta) return false
  const [year, month, day] = desde.split('-').map(Number)
  const targetYear = year + 1
  const targetMonth = month - 1
  const lastTargetDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate()
  const limite = `${targetYear}-${String(month).padStart(2, '0')}-${String(Math.min(day, lastTargetDay)).padStart(2, '0')}`
  return hasta <= limite
}

function fechaLimaInput(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date)
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}:${part('second')}`
}

function fechaInputAUtc(value: string) {
  // America/Lima is UTC-05:00 year-round; datetime-local carries no zone.
  const date = new Date(`${value}-05:00`)
  if (!value || Number.isNaN(date.getTime())) return null
  return date.toISOString()
}

export function AsistenciaAdminView({ auth }: { auth: AuthContextValue }) {
  const [personal, setPersonal] = useState<PersonalSeleccionable[]>([])
  const [idEmpleado, setIdEmpleado] = useState('')
  const [buscarEmpleado, setBuscarEmpleado] = useState('')
  const [page, setPage] = useState<AsistenciaLaboralPagina | null>(null)
  const [pageNumber, setPageNumber] = useState(0)
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [estado, setEstado] = useState('')
  const [filters, setFilters] = useState<AppliedFilters>({ desde: '', hasta: '', estado: '', idEmpleado: '' })
  const [reporte, setReporte] = useState<ReporteAsistenciaEmpleado | null>(null)
  const [historial, setHistorial] = useState<HistorialAsistenciaLaboral | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [loadingReport, setLoadingReport] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [reportError, setReportError] = useState('')
  const [personalError, setPersonalError] = useState('')
  const [writeMode, setWriteMode] = useState<'correccion' | 'anulacion' | null>(null)
  const [writeReason, setWriteReason] = useState('')
  const [writeEntry, setWriteEntry] = useState('')
  const [writeExit, setWriteExit] = useState('')
  const [writeConfirmed, setWriteConfirmed] = useState(false)
  const [writeBusy, setWriteBusy] = useState(false)
  const [writeError, setWriteError] = useState('')
  const [writeSuccess, setWriteSuccess] = useState('')
  const [verifying, setVerifying] = useState(false)
  const [uncertainIds, setUncertainIds] = useState<Set<number>>(() => new Set(uncertainAttendanceWrites))
  const sequence = useRef(0)
  const writeLock = useRef(false)
  const historyCloseRef = useRef<HTMLButtonElement>(null)
  const historyTriggerRef = useRef<HTMLButtonElement | null>(null)
  const historyWasOpen = useRef(false)
  const historyDialogOpen = loadingDetail || Boolean(detailError) || Boolean(historial)

  useEffect(() => {
    if (historyDialogOpen) {
      historyWasOpen.current = true
      historyCloseRef.current?.focus()
      return
    }
    if (historyWasOpen.current) {
      historyWasOpen.current = false
      historyTriggerRef.current?.focus()
      historyTriggerRef.current = null
    }
  }, [historyDialogOpen])

  const load = useCallback(async (number: number) => {
    const requestId = ++sequence.current
    const sessionVersion = auth.getSessionVersion()
    setLoading(true)
    setError('')
    try {
      const result = await consultarAsistenciasAdmin(auth, {
        page: number, size: PAGE_SIZE,
        ...(filters.desde ? { fechaDesde: filters.desde } : {}),
        ...(filters.hasta ? { fechaHasta: filters.hasta } : {}),
        ...(filters.estado ? { estado: filters.estado as 'ACTIVA' | 'ANULADA' } : {}),
      }, filters.idEmpleado ? Number(filters.idEmpleado) : undefined)
      if (requestId === sequence.current && auth.getSessionVersion() === sessionVersion) {
        setPage(result)
        setPageNumber(result.pagina)
      }
    } catch (reason) {
      if (requestId === sequence.current && auth.getSessionVersion() === sessionVersion) setError(errorMessage(reason))
    } finally {
      if (requestId === sequence.current && auth.getSessionVersion() === sessionVersion) setLoading(false)
    }
  }, [auth, filters])

  useEffect(() => { void load(0) }, [load])
  useEffect(() => {
    let active = true
    void listarPersonalSeleccionable(auth).then((result) => {
    if (active) { setPersonal(result); setPersonalError('') }
    }).catch((reason: unknown) => {
      if (active) setPersonalError(errorMessage(reason))
    })
    return () => { active = false }
  }, [auth])

  async function openHistory(id: number, trigger?: HTMLButtonElement) {
    if (trigger) historyTriggerRef.current = trigger
    const sessionVersion = auth.getSessionVersion()
    setLoadingDetail(true)
    setDetailError('')
    setHistorial(null)
    try {
      const fresh = await consultarHistorialAsistencia(auth, id)
      if (auth.getSessionVersion() === sessionVersion) setHistorial(fresh)
    } catch (reason) {
      if (auth.getSessionVersion() === sessionVersion) setDetailError(errorMessage(reason))
    } finally { if (auth.getSessionVersion() === sessionVersion) setLoadingDetail(false) }
  }

  function startWrite(mode: 'correccion' | 'anulacion') {
    if (!historial || historial.estado !== 'ACTIVA' || uncertainIds.has(historial.idAsistencia)) return
    setWriteMode(mode); setWriteReason(''); setWriteError(''); setWriteSuccess(''); setWriteConfirmed(false)
    setWriteEntry(fechaLimaInput(historial.fechaEntradaUtc))
    setWriteExit(fechaLimaInput(historial.fechaSalidaUtc))
  }

  function closeHistory() {
    if (writeBusy || verifying) return
    setHistorial(null); setDetailError(''); setWriteMode(null); setWriteError(''); setWriteSuccess('')
  }

  function handleHistoryKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape' && !writeBusy && !verifying) {
      event.preventDefault()
      closeHistory()
      return
    }
    if (event.key !== 'Tab') return
    const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )).filter((item) => !item.hidden)
    if (!focusable.length) { event.preventDefault(); return }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && (document.activeElement === first || !event.currentTarget.contains(document.activeElement))) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && (document.activeElement === last || !event.currentTarget.contains(document.activeElement))) {
      event.preventDefault(); first.focus()
    }
  }

  async function verifyUncertain(id: number) {
    if (verifying) return
    const sessionVersion = auth.getSessionVersion()
    setVerifying(true); setWriteError('')
    try {
      const [freshHistory, freshPage] = await Promise.all([
        consultarHistorialAsistencia(auth, id),
        consultarAsistenciasAdmin(auth, {
          page: pageNumber, size: PAGE_SIZE,
          ...(filters.desde ? { fechaDesde: filters.desde } : {}),
          ...(filters.hasta ? { fechaHasta: filters.hasta } : {}),
          ...(filters.estado ? { estado: filters.estado as 'ACTIVA' | 'ANULADA' } : {}),
        }, filters.idEmpleado ? Number(filters.idEmpleado) : undefined),
      ])
      if (auth.getSessionVersion() !== sessionVersion) return
      if (freshHistory.idAsistencia !== id || !freshHistory.empleado || !Array.isArray(freshHistory.auditorias)
          || !freshPage.contenido.every((item) => Number.isInteger(item.idAsistencia))) {
        throw new Error('La consulta no devolvió el registro e historial esperados.')
      }
      setHistorial(freshHistory); setPage(freshPage); setPageNumber(freshPage.pagina)
      uncertainAttendanceWrites.delete(id)
      setUncertainIds((current) => { const next = new Set(current); next.delete(id); return next })
      setWriteMode(null); setWriteReason(''); setWriteConfirmed(false)
      setWriteSuccess('Registro e historial verificados. Ya puedes iniciar una nueva operación si corresponde.')
    } catch (reason) {
      if (auth.getSessionVersion() === sessionVersion) setWriteError(`No se pudo verificar el estado. Las operaciones siguen bloqueadas: ${errorMessage(reason)}`)
    } finally { if (auth.getSessionVersion() === sessionVersion) setVerifying(false) }
  }

  async function submitWrite(event: FormEvent) {
    event.preventDefault()
    const current = historial
    if (!current || !writeMode || writeLock.current || writeBusy || uncertainIds.has(current.idAsistencia)) return
    const reason = writeReason.trim()
    if (!reason || Array.from(reason).length > 500) { setWriteError('El motivo es obligatorio y admite hasta 500 caracteres.'); return }
    if (!writeConfirmed) { setWriteError('Confirma explícitamente la operación antes de continuar.'); return }
    writeLock.current = true; setWriteBusy(true); setWriteError(''); setWriteSuccess('')
    const sessionVersion = auth.getSessionVersion()
    let writeMayHaveApplied = false
    let writeStarted = false
    let priorAuditIds = new Set<number>()
    try {
      // Re-read both current state and audit trail before any write.
      const fresh = await consultarHistorialAsistencia(auth, current.idAsistencia)
      if (auth.getSessionVersion() !== sessionVersion) return
      setHistorial(fresh)
      if (fresh.estado !== 'ACTIVA') throw new Error('La asistencia ya no está activa. El estado se actualizó; no se envió ningún cambio.')
      priorAuditIds = new Set(fresh.auditorias.map((item) => item.idAuditoria))

      let updated
      if (writeMode === 'correccion') {
        const entryUtc = fechaInputAUtc(writeEntry)
        const exitUtc = writeExit ? fechaInputAUtc(writeExit) : null
        if (!entryUtc || (writeExit && !exitUtc)) throw new Error('Indica horarios válidos en hora de Lima.')
        if (!fresh.fechaSalidaUtc && exitUtc) throw new Error('No se puede cerrar una asistencia abierta mediante corrección.')
        const entryChanged = Date.parse(entryUtc) !== Date.parse(fresh.fechaEntradaUtc)
        const exitChanged = fresh.fechaSalidaUtc !== null && exitUtc !== null
          ? Date.parse(exitUtc) !== Date.parse(fresh.fechaSalidaUtc)
          : false
        if (fresh.fechaSalidaUtc && !exitUtc) throw new Error('No se permite quitar el horario de salida.')
        if (!entryChanged && !exitChanged) throw new Error('La corrección debe cambiar al menos un horario.')
        if (exitUtc && Date.parse(exitUtc) <= Date.parse(entryUtc)) throw new Error('La salida debe ser posterior a la entrada.')
        writeStarted = true
        updated = await corregirAsistenciaLaboral(auth, current.idAsistencia, {
          ...(entryChanged ? { fechaEntradaUtc: entryUtc } : {}),
          ...(exitChanged && exitUtc ? { fechaSalidaUtc: exitUtc } : {}), motivo: reason,
        })
      } else {
        writeStarted = true
        updated = await anularAsistenciaLaboral(auth, current.idAsistencia, { motivo: reason })
      }
      writeMayHaveApplied = true
      uncertainAttendanceWrites.add(current.idAsistencia)
      if (auth.getSessionVersion() === sessionVersion) {
        setUncertainIds((ids) => new Set(ids).add(current.idAsistencia))
      }
      if (auth.getSessionVersion() !== sessionVersion) return
      if (updated.idAsistencia !== current.idAsistencia) throw new Error('La respuesta no coincide con la asistencia modificada.')
      if ((writeMode === 'correccion' && updated.estado !== 'ACTIVA')
          || (writeMode === 'anulacion' && updated.estado !== 'ANULADA')) {
        throw new Error('La respuesta no confirma el estado esperado de la operación.')
      }
      // Verify the saved record and real audit history before enabling further writes.
      const [freshHistory, freshPage] = await Promise.all([
        consultarHistorialAsistencia(auth, current.idAsistencia),
        consultarAsistenciasAdmin(auth, {
          page: pageNumber, size: PAGE_SIZE,
          ...(filters.desde ? { fechaDesde: filters.desde } : {}),
          ...(filters.hasta ? { fechaHasta: filters.hasta } : {}),
          ...(filters.estado ? { estado: filters.estado as 'ACTIVA' | 'ANULADA' } : {}),
        }, filters.idEmpleado ? Number(filters.idEmpleado) : undefined),
      ])
      if (auth.getSessionVersion() !== sessionVersion) return
      if (freshHistory.idAsistencia !== current.idAsistencia || !Array.isArray(freshHistory.auditorias)) {
        throw new Error('El servidor no confirmó el registro y su historial.')
      }
      const expectedOperation = writeMode === 'correccion' ? 'CORRECCION' : 'ANULACION'
      const hasNewAudit = freshHistory.auditorias.some((item) => !priorAuditIds.has(item.idAuditoria)
        && item.tipoOperacion === expectedOperation && item.motivo === reason)
      const expectedState = writeMode === 'correccion' ? 'ACTIVA' : 'ANULADA'
      if (!hasNewAudit || freshHistory.estado !== expectedState
          || !freshPage.contenido.every((item) => Number.isInteger(item.idAsistencia))) {
        throw new Error('La respuesta no pudo verificarse contra el nuevo registro y su auditoría.')
      }
      setHistorial(freshHistory); setPage(freshPage); setPageNumber(freshPage.pagina)
      uncertainAttendanceWrites.delete(current.idAsistencia)
      setUncertainIds((ids) => { const next = new Set(ids); next.delete(current.idAsistencia); return next })
      setWriteMode(null); setWriteReason(''); setWriteConfirmed(false)
      setWriteSuccess(writeMode === 'correccion' ? 'Corrección guardada y auditoría actualizada.' : 'Asistencia anulada y auditoría actualizada.')
    } catch (reason) {
      const uncertain = writeMayHaveApplied || (writeStarted && reason instanceof ApiError
        && ['network', 'invalid-response', 'server'].includes(reason.code))
      if (uncertain) uncertainAttendanceWrites.add(current.idAsistencia)
      if (auth.getSessionVersion() !== sessionVersion) return
      if (uncertain) setUncertainIds((currentIds) => new Set(currentIds).add(current.idAsistencia))
      setWriteError(uncertain
        ? `Resultado incierto. No repitas la operación; consulta registro e historial para verificar. ${errorMessage(reason)}`
        : errorMessage(reason))
      if (reason instanceof ApiError && reason.code === 'conflict') {
        // A conflict is definitive, but refresh current state before another attempt.
        try {
          const refreshed = await consultarHistorialAsistencia(auth, current.idAsistencia)
          if (auth.getSessionVersion() === sessionVersion) setHistorial(refreshed)
        } catch { /* Keep the conflict visible; explicit history reload remains available. */ }
      }
    } finally {
      writeLock.current = false
      if (auth.getSessionVersion() === sessionVersion) setWriteBusy(false)
    }
  }

  async function runReport() {
    setReportError('')
    setReporte(null)
    if (!filters.idEmpleado) { setReportError('Aplica un filtro de empleado para consultar su reporte.'); return }
    if (!rangoHastaDoceMeses(filters.desde, filters.hasta)) {
      setReportError('Selecciona un período válido de hasta 12 meses en los filtros del historial.')
      return
    }
    setLoadingReport(true)
    try { setReporte(await consultarReporteAsistenciaEmpleado(auth, Number(filters.idEmpleado), filters.desde, filters.hasta)) }
    catch (reason) { setReportError(errorMessage(reason)) }
    finally { setLoadingReport(false) }
  }

  return <div className="dashboard asistencia-page">
    <header className="laboral-heading"><div><p className="ingresos-eyebrow">SUPERVISIÓN · PERSONAL</p><h2>Asistencia laboral</h2><p>Consulta y revisa los registros de jornada del personal.</p></div></header>
    <Card className="asistencia-card laboral-history-card" title="Historial de asistencias">
      <form className="laboral-toolbar" onSubmit={(event) => { event.preventDefault(); setPageNumber(0); setFilters({ desde, hasta, estado, idEmpleado }) }}>
        <label className="laboral-search"><span className="asistencia-sr-only">Buscar empleado por nombre o apellido</span><Search aria-hidden="true" size={17}/><input aria-label="Buscar empleado" placeholder="Buscar empleado" type="search" value={buscarEmpleado} onChange={(event) => setBuscarEmpleado(event.target.value)}/></label>
        <label className="laboral-filter">Empleado<select aria-label="Empleado" value={idEmpleado} onChange={(event) => setIdEmpleado(event.target.value)}><option value="">Todo el personal</option>{personal.filter(person => `${person.nombres} ${person.apellidos}`.toLocaleLowerCase().includes(buscarEmpleado.trim().toLocaleLowerCase())).map(person => <option key={person.idEmpleado} value={person.idEmpleado}>{person.nombres} {person.apellidos}</option>)}</select></label>
        <label className="laboral-filter">Desde<input aria-label="Fecha desde" type="date" value={desde} onChange={(event) => setDesde(event.target.value)}/></label>
        <label className="laboral-filter">Hasta<input aria-label="Fecha hasta" type="date" value={hasta} onChange={(event) => setHasta(event.target.value)}/></label>
        <label className="laboral-filter">Estado<select aria-label="Estado" value={estado} onChange={(event) => setEstado(event.target.value)}><option value="">Todos</option><option value="ACTIVA">Activa</option><option value="ANULADA">Anulada</option></select></label>
        <div className="laboral-actions"><Button type="submit" variant="secondary" disabled={loading}><Search aria-hidden="true" size={15}/>Aplicar</Button><Button type="button" variant="ghost" disabled={loading} onClick={() => { setDesde(''); setHasta(''); setEstado(''); setIdEmpleado(''); setBuscarEmpleado(''); setPageNumber(0); setFilters({ desde: '', hasta: '', estado: '', idEmpleado: '' }) }}>Limpiar</Button></div>
      </form>
      {personalError && <p className="asistencia-feedback asistencia-feedback--error" role="alert">No se pudo cargar la lista de empleados. Puedes consultar los registros disponibles.</p>}
      {error && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{error}<Button type="button" variant="secondary" onClick={() => void load(pageNumber)}>Reintentar</Button></p>}
      {loading ? <p className="laboral-state" aria-live="polite">Cargando asistencias…</p> : error ? null : !page?.contenido.length ? <div className="laboral-empty"><span><SearchX aria-hidden="true" size={20}/></span><strong>No hay asistencias para mostrar</strong><small>Prueba con otras fechas o ajusta los filtros.</small></div> : <>
        <div className="laboral-table-wrap"><table className="laboral-table"><caption className="asistencia-sr-only">Registros de asistencia laboral</caption><thead><tr><th scope="col">EMPLEADO</th><th scope="col">CARGO</th><th scope="col">FECHA</th><th scope="col">ENTRADA</th><th scope="col">SALIDA</th><th scope="col">ESTADO</th><th scope="col">ACCIONES</th></tr></thead><tbody>{page.contenido.map(row => {
          const person = personal.find(item => item.idEmpleado === row.idEmpleado)
          return <tr key={row.idAsistencia}><td data-label="Empleado"><strong>{person ? `${person.nombres} ${person.apellidos}` : 'Empleado no disponible'}</strong></td><td data-label="Cargo">{person ? person.rol === 'ENTRENADOR' ? 'Entrenador' : 'Nutricionista' : 'No disponible'}</td><td data-label="Fecha">{row.fechaLaboral}</td><td data-label="Entrada">{horaLima(row.fechaEntradaUtc)}</td><td data-label="Salida">{horaLima(row.fechaSalidaUtc)}</td><td data-label="Estado"><span className={`asistencia-state asistencia-state--${row.estado.toLowerCase()}`}>{row.estado === 'ACTIVA' ? 'Activa' : 'Anulada'}</span></td><td data-label="Acciones"><Button type="button" variant="secondary" aria-label={`Ver historial de asistencia del ${row.fechaLaboral}`} onClick={(event) => void openHistory(row.idAsistencia, event.currentTarget)}>Ver historial</Button></td></tr>
        })}</tbody></table></div>
        <nav aria-label="Paginación de asistencias laborales" className="laboral-pagination"><Button variant="secondary" type="button" disabled={pageNumber <= 0 || loading} onClick={() => void load(pageNumber - 1)}>Anterior</Button><span>Página {pageNumber + 1} de {Math.max(1, page.totalPaginas)} · {page.totalElementos} registros</span><Button variant="secondary" type="button" disabled={pageNumber + 1 >= page.totalPaginas || loading} onClick={() => void load(pageNumber + 1)}>Siguiente</Button></nav>
      </>}
    </Card>
    <Card className="asistencia-card laboral-report-card" title="Reporte por empleado">
      <div className="laboral-report-heading"><span className="laboral-report-icon"><BriefcaseBusiness aria-hidden="true" size={18}/></span><p>Usa el empleado y el período seleccionados en los filtros del historial.</p></div>
      <div className="laboral-report-controls"><div><span>Empleado</span><strong>{personal.find(person => String(person.idEmpleado) === filters.idEmpleado) ? `${personal.find(person => String(person.idEmpleado) === filters.idEmpleado)?.nombres} ${personal.find(person => String(person.idEmpleado) === filters.idEmpleado)?.apellidos}` : 'Aplica un filtro de empleado'}</strong></div><div><span>Período aplicado</span><strong><CalendarDays aria-hidden="true" size={15}/>{filters.desde || 'Sin límite'} – {filters.hasta || 'Sin límite'}</strong></div><Button type="button" variant="secondary" disabled={loadingReport || !filters.idEmpleado || !filters.desde || !filters.hasta} onClick={() => void runReport()}>{loadingReport ? 'Consultando…' : 'Consultar reporte'}</Button></div>
      {reportError && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{reportError}</p>}
      {reporte && <dl className="asistencia-report"><div><dt>Período</dt><dd>{reporte.fechaDesde} – {reporte.fechaHasta}</dd></div><div><dt>Asistencias activas</dt><dd>{reporte.cantidadActivas}</dd></div><div><dt>Asistencias anuladas</dt><dd>{reporte.cantidadAnuladas}</dd></div><div><dt>Jornadas cerradas</dt><dd>{reporte.jornadasCerradas}</dd></div><div><dt>Jornadas abiertas</dt><dd>{reporte.jornadasAbiertas}</dd></div><div><dt>Horas registradas</dt><dd>{reporte.totalHorasRegistradas}</dd></div></dl>}
    </Card>
    {(loadingDetail || detailError || historial) && <div className="asistencia-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) closeHistory() }}><section className="asistencia-dialog asistencia-history" role="dialog" aria-modal="true" aria-labelledby="asistencia-history-title" onKeyDown={handleHistoryKeyDown} onClick={(event) => event.stopPropagation()}>
      <button ref={historyCloseRef} className="asistencia-close" type="button" aria-label="Cerrar detalle" disabled={writeBusy || verifying} onClick={closeHistory}>×</button><p className="asistencia-dialog-eyebrow">ADMINISTRACIÓN · AUDITORÍA</p><h2 id="asistencia-history-title">Detalle e historial de asistencia</h2>
      {loadingDetail ? <p className="asistencia-message">Cargando historial…</p> : detailError ? <p role="alert" className="asistencia-feedback asistencia-feedback--error">{detailError}<Button type="button" variant="secondary" onClick={() => historial && void openHistory(historial.idAsistencia)}>Consultar de nuevo</Button></p> : historial && <>
        <dl className="asistencia-report"><div><dt>Empleado</dt><dd>{historial.empleado.nombres} {historial.empleado.apellidos}</dd></div><div><dt>Fecha laboral</dt><dd>{historial.fechaLaboral}</dd></div><div><dt>Entrada</dt><dd>{horaLima(historial.fechaEntradaUtc)}</dd></div><div><dt>Salida</dt><dd>{horaLima(historial.fechaSalidaUtc)}</dd></div><div><dt>Estado</dt><dd>{historial.estado}</dd></div></dl>
        <h3>Eventos</h3>{historial.eventos.length ? <ul>{historial.eventos.map((item) => <li key={item.idEvento}>{item.tipoEvento} · {horaLima(item.fechaHoraUtc)} · {item.registrador.nombres} {item.registrador.apellidos}</li>)}</ul> : <p>No hay eventos registrados.</p>}
        <h3>Auditorías de corrección y anulación</h3>{historial.auditorias.length ? <ul>{historial.auditorias.map((item) => <li key={item.idAuditoria}><strong>{item.tipoOperacion}</strong> · {horaLima(item.fechaHoraUtc)} · {item.actor.nombres} {item.actor.apellidos}<p>Motivo: {item.motivo}</p>{item.fechaEntradaAnteriorUtc && <p>Entrada: {horaLima(item.fechaEntradaAnteriorUtc)} → {horaLima(item.fechaEntradaNuevaUtc)}</p>}{item.fechaSalidaAnteriorUtc && <p>Salida: {horaLima(item.fechaSalidaAnteriorUtc)} → {horaLima(item.fechaSalidaNuevaUtc)}</p>}</li>)}</ul> : <p>No hay auditorías de corrección o anulación.</p>}
        {uncertainIds.has(historial.idAsistencia) && <div className="asistencia-feedback asistencia-feedback--error" role="alert"><p>El resultado de una operación anterior es incierto. No envíes otra modificación hasta verificar el registro y su historial.</p><Button type="button" disabled={verifying || writeBusy} onClick={() => void verifyUncertain(historial.idAsistencia)}>{verifying ? 'Verificando…' : 'Consultar registro e historial'}</Button></div>}
        {writeSuccess && <p className="asistencia-feedback" role="status">{writeSuccess}</p>}
        {writeError && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{writeError}</p>}
        {historial.estado === 'ACTIVA' && !uncertainIds.has(historial.idAsistencia) && !writeMode && <div className="asistencia-admin-actions"><Button type="button" variant="secondary" disabled={writeBusy} onClick={() => startWrite('correccion')}>Corregir horarios</Button><Button type="button" variant="secondary" disabled={writeBusy} onClick={() => startWrite('anulacion')}>Anular asistencia</Button></div>}
        {writeMode && <form className="asistencia-admin-write" onSubmit={(event) => void submitWrite(event)}>
          <h3>{writeMode === 'correccion' ? 'Corrección administrativa' : 'Anulación administrativa'}</h3>
          {writeMode === 'correccion' ? <>
            <p className="asistencia-description">Valores vigentes frente a los nuevos horarios (hora de Lima).</p>
            <div className="asistencia-admin-time"><span>Entrada vigente: <strong>{horaLima(historial.fechaEntradaUtc)}</strong></span><label>Nueva entrada<input aria-label="Nueva entrada (hora de Lima)" required type="datetime-local" step="1" value={writeEntry} onChange={(event) => setWriteEntry(event.target.value)} /></label></div>
            <div className="asistencia-admin-time"><span>Salida vigente: <strong>{horaLima(historial.fechaSalidaUtc)}</strong></span>{historial.fechaSalidaUtc ? <label>Nueva salida<input aria-label="Nueva salida (hora de Lima)" required type="datetime-local" step="1" value={writeExit} onChange={(event) => setWriteExit(event.target.value)} /></label> : <p>Asistencia abierta: no se permite añadir una salida mediante corrección.</p>}</div>
          </> : <p>Se conservarán los eventos originales; la asistencia cambiará a ANULADA y quedará registrada en auditoría.</p>}
          <label>Motivo obligatorio<textarea aria-label="Motivo obligatorio" required value={writeReason} onChange={(event) => setWriteReason(event.target.value)} /></label><small>{Array.from(writeReason).length}/500 caracteres</small>
          <label className="asistencia-admin-confirm"><input type="checkbox" checked={writeConfirmed} onChange={(event) => setWriteConfirmed(event.target.checked)} />Confirmo explícitamente esta {writeMode === 'correccion' ? 'corrección' : 'anulación'}.</label>
          <div className="asistencia-admin-actions"><Button type="submit" disabled={writeBusy || verifying || !writeConfirmed || !writeReason.trim() || Array.from(writeReason.trim()).length > 500}>{writeBusy ? 'Procesando…' : `Confirmar ${writeMode === 'correccion' ? 'corrección' : 'anulación'}`}</Button><Button type="button" variant="secondary" disabled={writeBusy} onClick={() => { setWriteMode(null); setWriteError(''); setWriteConfirmed(false) }}>Cancelar</Button></div>
        </form>}
      </>}
    </section></div>}
  </div>
}
