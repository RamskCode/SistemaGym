import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Eye, Search } from 'lucide-react'
import { ApiError } from '../api/http'
import { cambiarEstadoIncidencia, listarReportesIncidencia, obtenerReporteIncidenciaAdmin, type CambiarEstadoIncidenciaRequest } from '../api/reportesIncidencias'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import type { EstadoIncidencia, ReporteIncidenciaRespuesta } from '../api/checklists'
import './incidencias-admin.css'

const ESTADOS: readonly EstadoIncidencia[] = ['PENDIENTE', 'EN_REVISION', 'SOLUCIONADO']
const INCIERTAS_KEY = 'incidencias-admin:estado-incierto'
const ZONA = 'America/Lima'

function fechaLima(value: string | null) {
  if (!value) return 'No disponible'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Fecha no disponible'
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: ZONA }).format(date)
}

function estadoPermitido(current: EstadoIncidencia): EstadoIncidencia | null {
  if (current === 'PENDIENTE') return 'EN_REVISION'
  if (current === 'EN_REVISION') return 'SOLUCIONADO'
  if (current === 'SOLUCIONADO') return 'EN_REVISION'
  return null
}

function mensajeError(reason: unknown) {
  return reason instanceof ApiError ? reason.message : 'No fue posible completar la solicitud.'
}

function resultadoIncierto(reason: unknown) {
  return !(reason instanceof ApiError) || ['network', 'invalid-response', 'server'].includes(reason.code)
}

function leerInciertas(): number[] {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(INCIERTAS_KEY) ?? '[]')
    return Array.isArray(value) ? value.filter((id): id is number => Number.isSafeInteger(id) && id > 0) : []
  } catch { return [] }
}

function guardarInciertas(ids: number[]) {
  try { sessionStorage.setItem(INCIERTAS_KEY, JSON.stringify(ids)) } catch { /* El bloqueo en memoria continúa vigente. */ }
}

export function IncidenciasAdminPage() {
  const auth = useAuth()
  const [reportes, setReportes] = useState<ReporteIncidenciaRespuesta[]>([])
  const [detalle, setDetalle] = useState<ReporteIncidenciaRespuesta | null>(null)
  const [cargando, setCargando] = useState(true)
  const [loadingId, setLoadingId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [query, setQuery] = useState('')
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoIncidencia | ''>('')
  const [inciertas, setInciertas] = useState<number[]>(leerInciertas)
  const [verificacion, setVerificacion] = useState<{ id: number; reporte: ReporteIncidenciaRespuesta } | null>(null)
  const [verificacionConfirmada, setVerificacionConfirmada] = useState(false)
  const [transitionReport, setTransitionReport] = useState<ReporteIncidenciaRespuesta | null>(null)
  const [targetState, setTargetState] = useState<EstadoIncidencia | ''>('')
  const [observacion, setObservacion] = useState('')
  const [confirmacion, setConfirmacion] = useState(false)
  const [transitionError, setTransitionError] = useState('')
  const [preparing, setPreparing] = useState(false)
  const savingLock = useRef(false)
  const detailLock = useRef(false)

  const cargarLista = useCallback(async () => {
    const version = auth.getSessionVersion()
    setCargando(true); setError('')
    try {
      const result = await listarReportesIncidencia(auth)
      if (auth.getSessionVersion() === version) setReportes(result)
    } catch (reason) {
      if (auth.getSessionVersion() === version) setError(mensajeError(reason))
    } finally { if (auth.getSessionVersion() === version) setCargando(false) }
  }, [auth])

  useEffect(() => { void cargarLista() }, [cargarLista])

  const filtrados = useMemo(() => {
    const term = query.trim().toLocaleLowerCase()
    return reportes.filter((item) => (!term || `${item.nombreElemento} ${item.descripcion} ${item.correoEntrenador} ${item.idReporte}`.toLocaleLowerCase().includes(term))
      && (!estadoFiltro || item.estado === estadoFiltro))
  }, [reportes, query, estadoFiltro])

  async function abrirDetalle(id: number) {
    if (detailLock.current) return
    detailLock.current = true
    const version = auth.getSessionVersion()
    setLoadingId(id); setError('')
    try {
      const latest = await obtenerReporteIncidenciaAdmin(auth, id)
      if (auth.getSessionVersion() === version) setDetalle(latest)
    } catch (reason) { if (auth.getSessionVersion() === version) setError(`No se pudo cargar el detalle: ${mensajeError(reason)}`) }
    finally { detailLock.current = false; if (auth.getSessionVersion() === version) setLoadingId(null) }
  }

  async function prepararCambio(id: number) {
    if (inciertas.includes(id) || preparing || savingLock.current) return
    const version = auth.getSessionVersion()
    setPreparing(true); setTransitionError('')
    try {
      const latest = await obtenerReporteIncidenciaAdmin(auth, id)
      if (auth.getSessionVersion() !== version) return
      setDetalle(latest)
      const allowed = estadoPermitido(latest.estado)
      if (!allowed) { setTransitionError('No hay una transición permitida para el estado vigente.'); return }
      setTransitionReport(latest); setTargetState(allowed); setObservacion(''); setConfirmacion(false)
    } catch (reason) { if (auth.getSessionVersion() === version) setTransitionError(`No se pudo confirmar el estado vigente: ${mensajeError(reason)}`) }
    finally { if (auth.getSessionVersion() === version) setPreparing(false) }
  }

  function reviewTransition(event: FormEvent) {
    event.preventDefault()
    if (!transitionReport || !targetState) return
    if (!observacion.trim()) { setTransitionError('La observación es obligatoria.'); return }
    if (observacion.length > 2000) { setTransitionError('La observación no puede superar los 2000 caracteres.'); return }
    if (targetState !== estadoPermitido(transitionReport.estado)) { setTransitionError('La transición ya no corresponde al estado vigente.'); return }
    setTransitionError(''); setConfirmacion(true)
  }

  async function ejecutarCambio() {
    if (!transitionReport || !targetState || savingLock.current || saving || inciertas.includes(transitionReport.idReporte)) return
    const operation = transitionReport
    const body: CambiarEstadoIncidenciaRequest = { estado: targetState, observacion: observacion.trim() }
    const version = auth.getSessionVersion()
    savingLock.current = true; setSaving(true); setTransitionError(''); setError('')
    try {
      const updated = await cambiarEstadoIncidencia(auth, operation.idReporte, body)
      if (auth.getSessionVersion() !== version) return
      setDetalle(updated)
      setReportes((current) => current.map((item) => item.idReporte === updated.idReporte ? updated : item))
      setMensaje(`El servidor confirmó el estado ${updated.estado} y devolvió el historial registrado.`)
      setTransitionReport(null); setConfirmacion(false); setObservacion('')
      try {
        const fresh = await obtenerReporteIncidenciaAdmin(auth, operation.idReporte)
        if (auth.getSessionVersion() === version) {
          setDetalle(fresh)
          setReportes((current) => current.map((item) => item.idReporte === fresh.idReporte ? fresh : item))
          setMensaje('Estado e historial actualizados desde el servidor.')
        }
      } catch (reason) {
        if (auth.getSessionVersion() === version) setError(`El cambio fue confirmado por el servidor, pero no se pudo recargar el historial: ${mensajeError(reason)}. Usa “Actualizar historial”.`)
      }
    } catch (reason) {
      if (auth.getSessionVersion() !== version) return
      if (resultadoIncierto(reason)) {
        const next = [...new Set([...inciertas, operation.idReporte])]
        setInciertas(next); guardarInciertas(next)
        setTransitionReport(null); setConfirmacion(false); setObservacion('')
        setError(`Resultado incierto para la incidencia #${operation.idReporte}. No se reintentará. Consulta estado e historial y confirma la verificación antes de permitir otro cambio.`)
      } else setTransitionError(mensajeError(reason))
    } finally {
      savingLock.current = false
      if (auth.getSessionVersion() === version) setSaving(false)
    }
  }

  async function consultarIncierta(id: number) {
    const version = auth.getSessionVersion()
    setLoadingId(id); setError(''); setVerificacionConfirmada(false)
    try {
      const latest = await obtenerReporteIncidenciaAdmin(auth, id)
      if (auth.getSessionVersion() !== version) return
      setDetalle(latest); setVerificacion({ id, reporte: latest })
      setReportes((current) => current.map((item) => item.idReporte === id ? latest : item))
    } catch (reason) { if (auth.getSessionVersion() === version) setError(`No se pudo consultar estado e historial: ${mensajeError(reason)}`) }
    finally { if (auth.getSessionVersion() === version) setLoadingId(null) }
  }

  function liberarIncierta() {
    if (!verificacion || !verificacionConfirmada) return
    const next = inciertas.filter((id) => id !== verificacion.id)
    setInciertas(next); guardarInciertas(next); setVerificacion(null); setVerificacionConfirmada(false)
    setMensaje(`Verificación explícita registrada para la incidencia #${verificacion.id}. Revisa el estado vigente antes de cualquier transición.`)
  }

  const estadosNombre: Record<EstadoIncidencia, string> = { PENDIENTE: 'Pendiente', EN_REVISION: 'En revisión', SOLUCIONADO: 'Solucionado' }

  return <div className="dashboard incidencias-admin-page">
    <PageTitle description="Consulta incidencias y gestiona únicamente las transiciones administrativas permitidas." eyebrow="ADMINISTRACIÓN · GIMNASIO" title="Incidencias" />
    <p className="inc-admin-security-note">Solo administradores pueden consultar este listado global y cambiar estados. Las incidencias y checklists del Entrenador no se editan ni eliminan aquí.</p>
    {mensaje && <p className="inc-admin-feedback inc-admin-feedback--success" role="status">{mensaje}</p>}
    {error && <div className="inc-admin-feedback inc-admin-feedback--error" role="alert"><span>{error}</span><Button disabled={cargando} onClick={() => void cargarLista()} type="button" variant="secondary">Reintentar consulta</Button></div>}
    {inciertas.length > 0 && <section className="inc-admin-uncertain" role="alert"><div><strong>Cambios pendientes de verificación</strong><p>La operación pudo haberse aplicado. No vuelvas a enviarla ni te bases solo en el estado: consulta el historial del servidor y verifica el evento antes de desbloquear esa incidencia.</p></div><div className="inc-admin-uncertain-list">{inciertas.map((id) => <Button key={id} disabled={loadingId !== null} onClick={() => void consultarIncierta(id)} type="button" variant="secondary">{loadingId === id ? 'Consultando…' : `Verificar #${id}`}</Button>)}</div></section>}
    <Card className="inc-admin-card" title="Todas las incidencias">
      <p className="inc-admin-hint">Listado global sin paginación del servidor. Los filtros se aplican localmente; las fechas se muestran en America/Lima.</p>
      <div className="inc-admin-toolbar"><label className="inc-admin-search"><Search aria-hidden="true" size={16}/><input aria-label="Buscar incidencias" onChange={(event) => setQuery(event.target.value)} placeholder="Elemento, descripción o entrenador" value={query}/></label><label className="inc-admin-filter">Estado<select aria-label="Filtrar por estado" onChange={(event) => setEstadoFiltro(event.target.value as EstadoIncidencia | '')} value={estadoFiltro}><option value="">Todos</option>{ESTADOS.map((state) => <option key={state} value={state}>{estadosNombre[state]}</option>)}</select></label></div>
      {cargando ? <p aria-live="polite" className="inc-admin-message">Cargando incidencias…</p> : error && reportes.length === 0 ? null : filtrados.length === 0 ? <p className="inc-admin-message">{reportes.length ? 'No hay incidencias que coincidan con los filtros.' : 'No hay incidencias registradas.'}</p> : <div className="table-wrap inc-admin-table-wrap"><table className="inc-admin-table"><caption className="inc-admin-sr-only">Listado global de incidencias</caption><thead><tr><th scope="col">ELEMENTO</th><th scope="col">PRIORIDAD</th><th scope="col">ESTADO</th><th scope="col">FECHA</th><th scope="col">ACCIONES</th></tr></thead><tbody>{filtrados.map((item) => <tr key={item.idReporte}><td data-label="Elemento"><strong>{item.nombreElemento}</strong><small>#{item.idReporte} · {item.descripcion}</small></td><td data-label="Prioridad">{item.prioridad}</td><td data-label="Estado"><span className={`inc-admin-state inc-admin-state--${item.estado.toLowerCase()}`}>{estadosNombre[item.estado]}</span></td><td data-label="Fecha">{fechaLima(item.fechaReporte)}</td><td data-label="Acciones"><div className="inc-admin-actions"><Button aria-label={`Detalle de incidencia ${item.idReporte}: ${item.nombreElemento}`} disabled={loadingId !== null} onClick={() => void abrirDetalle(item.idReporte)} type="button" variant="secondary"><Eye aria-hidden="true" size={14}/>{loadingId === item.idReporte ? 'Cargando…' : 'Detalle'}</Button><Button aria-label={`Cambiar estado de incidencia ${item.idReporte}`} disabled={loadingId !== null || saving || inciertas.includes(item.idReporte)} onClick={() => void prepararCambio(item.idReporte)} type="button">{preparing && detalle?.idReporte === item.idReporte ? 'Consultando…' : 'Cambiar estado'}</Button></div></td></tr>)}</tbody></table></div>}
    </Card>

    {detalle && <div className="inc-admin-backdrop" onClick={() => setDetalle(null)}><section aria-labelledby="inc-admin-detail-title" aria-modal="true" className="inc-admin-dialog" onClick={(event) => event.stopPropagation()} role="dialog"><button aria-label="Cerrar detalle" className="inc-admin-close" onClick={() => setDetalle(null)} type="button">×</button><h2 id="inc-admin-detail-title">Detalle de incidencia</h2><dl className="inc-admin-detail"><div><dt>Elemento afectado</dt><dd>{detalle.nombreElemento}</dd></div><div><dt>Prioridad</dt><dd>{detalle.prioridad}</dd></div><div><dt>Estado actual</dt><dd>{estadosNombre[detalle.estado]}</dd></div><div><dt>Entrenador responsable</dt><dd>{detalle.correoEntrenador}</dd></div><div><dt>Fecha de creación</dt><dd>{fechaLima(detalle.fechaReporte)}</dd></div><div><dt>Última actualización</dt><dd>{fechaLima(detalle.fechaActualizacion)}</dd></div>{detalle.fechaSolucion && <div><dt>Fecha de solución</dt><dd>{fechaLima(detalle.fechaSolucion)}</dd></div>}{detalle.idChecklist !== null && <div><dt>Checklist relacionado</dt><dd>#{detalle.idChecklist}</dd></div>}</dl><section className="inc-admin-description"><h3>Descripción</h3><p>{detalle.descripcion}</p></section><section className="inc-admin-history"><div className="inc-admin-history-heading"><h3>Historial de cambios</h3><Button disabled={loadingId !== null} onClick={() => void abrirDetalle(detalle.idReporte)} type="button" variant="secondary">{loadingId === detalle.idReporte ? 'Actualizando…' : 'Actualizar historial'}</Button></div>{detalle.historial.length === 0 ? <p className="inc-admin-message">No hay eventos de historial disponibles.</p> : <ol>{detalle.historial.map((event) => <li key={event.idHistorial}><strong>{event.estadoAnterior ? `${estadosNombre[event.estadoAnterior]} → ` : ''}{estadosNombre[event.estadoNuevo]}</strong><time>{fechaLima(event.fechaHora)}</time><span>Actor: {event.correoUsuario}</span>{event.observacion && <p>{event.observacion}</p>}</li>)}</ol>}<p className="inc-admin-hint">Historial de solo lectura; no se editan ni eliminan eventos.</p></section><Button onClick={() => setDetalle(null)} type="button" variant="secondary">Cerrar</Button></section></div>}

    {transitionReport && <div className="inc-admin-backdrop" onClick={() => { if (!saving) { setTransitionReport(null); setConfirmacion(false) } }}><section aria-labelledby="inc-admin-transition-title" aria-modal="true" className="inc-admin-dialog" onClick={(event) => event.stopPropagation()} role="dialog"><button aria-label="Cancelar cambio de estado" className="inc-admin-close" disabled={saving} onClick={() => { setTransitionReport(null); setConfirmacion(false) }} type="button">×</button><h2 id="inc-admin-transition-title">{confirmacion ? 'Confirmar cambio de estado' : 'Preparar cambio de estado'}</h2><p><strong>{transitionReport.nombreElemento}</strong> · incidencia #{transitionReport.idReporte}</p><p>Estado vigente consultado: <strong>{estadosNombre[transitionReport.estado]}</strong></p>{!confirmacion ? <form className="inc-admin-form" onSubmit={reviewTransition}><label>Nuevo estado<select aria-label="Nuevo estado" onChange={(event) => setTargetState(event.target.value as EstadoIncidencia)} value={targetState}>{ESTADOS.filter((state) => state === estadoPermitido(transitionReport.estado)).map((state) => <option key={state} value={state}>{estadosNombre[state]}</option>)}</select></label><label>Observación obligatoria<textarea aria-label="Observación del cambio" maxLength={2000} onChange={(event) => setObservacion(event.target.value)} required value={observacion}/><small>{observacion.length}/2000</small></label>{transitionError && <p role="alert">{transitionError}</p>}<div className="inc-admin-actions"><Button disabled={saving} onClick={() => setTransitionReport(null)} type="button" variant="secondary">Cancelar</Button><Button disabled={saving} type="submit">Revisar cambio</Button></div></form> : <><dl className="inc-admin-detail"><div><dt>Elemento</dt><dd>{transitionReport.nombreElemento}</dd></div><div><dt>Estado actual</dt><dd>{estadosNombre[transitionReport.estado]}</dd></div><div><dt>Estado nuevo</dt><dd>{estadosNombre[targetState as EstadoIncidencia]}</dd></div><div><dt>Observación</dt><dd>{observacion.trim()}</dd></div></dl><p>Al confirmar, el servidor actualizará el estado y registrará el evento. La respuesta y el historial se volverán a consultar.</p>{transitionError && <p role="alert">{transitionError}</p>}<div className="inc-admin-actions"><Button disabled={saving} onClick={() => setConfirmacion(false)} type="button" variant="secondary">Volver</Button><Button disabled={saving} onClick={() => void ejecutarCambio()} type="button">{saving ? 'Guardando…' : 'Confirmar y actualizar'}</Button></div></>}</section></div>}

    {verificacion && <div className="inc-admin-backdrop"><section aria-labelledby="inc-admin-verify-title" aria-modal="true" className="inc-admin-dialog" role="dialog"><h2 id="inc-admin-verify-title">Verificar incidencia #{verificacion.id}</h2><p>Estado observado: <strong>{estadosNombre[verificacion.reporte.estado]}</strong>. Revisa el historial completo cargado del servidor y confirma si el evento correspondiente quedó registrado. Coincidir el estado esperado no basta por sí solo.</p><ol className="inc-admin-verify-history">{verificacion.reporte.historial.map((event) => <li key={event.idHistorial}>{event.estadoAnterior ? `${estadosNombre[event.estadoAnterior]} → ` : ''}{estadosNombre[event.estadoNuevo]} · {fechaLima(event.fechaHora)} · {event.correoUsuario}{event.observacion ? ` · ${event.observacion}` : ''}</li>)}</ol><label className="inc-admin-check"><input checked={verificacionConfirmada} onChange={(event) => setVerificacionConfirmada(event.target.checked)} type="checkbox"/>Confirmo que revisé el historial real y verifiqué el resultado antes de desbloquear esta incidencia.</label><div className="inc-admin-actions"><Button onClick={() => { setVerificacion(null); setVerificacionConfirmada(false) }} type="button" variant="secondary">Cancelar</Button><Button disabled={!verificacionConfirmada} onClick={liberarIncierta} type="button">Verificado; desbloquear</Button></div></section></div>}
  </div>
}
