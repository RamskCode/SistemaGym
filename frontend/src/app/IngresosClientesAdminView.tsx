import { useEffect, useRef, useState, type FormEvent } from 'react'
import { CreditCard, DoorOpen, Eye, Search, UsersRound, X } from 'lucide-react'
import { ApiError } from '../api/http'
import { obtenerDetalleIngresoCliente, obtenerDetalleReporte, obtenerResumenReportes, type DetalleIngresoCliente, type ReporteFila, type ReporteResumen } from '../api/reportes'
import type { AuthContextValue } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

function hoyLima() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date())
}

function fechaHoraLima(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? 'Fecha no disponible' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(parsed)
}

function mensajeError(reason: unknown) {
  return reason instanceof ApiError && reason.status === 403
    ? 'No tienes autorización para consultar estos accesos.'
    : 'No se pudo cargar la información. Intenta nuevamente.'
}

export function IngresosClientesAdminView({ auth }: { auth: AuthContextValue }) {
  const hoy = hoyLima()
  const [desde, setDesde] = useState(hoy)
  const [hasta, setHasta] = useState(hoy)
  const [desdeAplicado, setDesdeAplicado] = useState(hoy)
  const [hastaAplicado, setHastaAplicado] = useState(hoy)
  const [buscar, setBuscar] = useState('')
  const [modalidad, setModalidad] = useState('')
  const [responsableId, setResponsableId] = useState('')
  const [buscarAplicado, setBuscarAplicado] = useState('')
  const [modalidadAplicada, setModalidadAplicada] = useState('')
  const [responsableAplicado, setResponsableAplicado] = useState<number | undefined>()
  const [pagina, setPagina] = useState(0)
  const [filas, setFilas] = useState<ReporteFila[]>([])
  const [resumen, setResumen] = useState<ReporteResumen | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [errorResumen, setErrorResumen] = useState('')
  const [detalle, setDetalle] = useState<ReporteFila | null>(null)
  const [detalleModalidad, setDetalleModalidad] = useState<DetalleIngresoCliente | null>(null)
  const [cargandoDetalle, setCargandoDetalle] = useState(false)
  const [errorDetalle, setErrorDetalle] = useState('')
  const closeRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError('')
    Promise.all([
      obtenerDetalleReporte(auth, 'ingresos', desdeAplicado, hastaAplicado, pagina, undefined,
        { buscar: buscarAplicado, modalidad: modalidadAplicada, responsableId: responsableAplicado }),
      obtenerResumenReportes(auth, desdeAplicado, hastaAplicado),
    ]).then(([registros, metricas]) => {
      if (!vigente) return
      setFilas(registros)
      setResumen(metricas)
      setErrorResumen('')
    }).catch((reason: unknown) => {
      if (!vigente) return
      setFilas([])
      setError(mensajeError(reason))
      setErrorResumen(mensajeError(reason))
    }).finally(() => { if (vigente) setCargando(false) })
    return () => { vigente = false }
  }, [auth, desdeAplicado, hastaAplicado, pagina, buscarAplicado, modalidadAplicada, responsableAplicado])

  useEffect(() => {
    if (detalle) closeRef.current?.focus()
  }, [detalle])

  useEffect(() => {
    if (!detalle) { setDetalleModalidad(null); setCargandoDetalle(false); setErrorDetalle(''); return }
    let vigente = true
    setDetalleModalidad(null); setCargandoDetalle(true); setErrorDetalle('')
    void obtenerDetalleIngresoCliente(auth, detalle.id).then((value) => {
      if (vigente) setDetalleModalidad(value)
    }).catch(() => {
      if (vigente) setErrorDetalle('No se pudo cargar el detalle histórico de esta modalidad.')
    }).finally(() => { if (vigente) setCargandoDetalle(false) })
    return () => { vigente = false }
  }, [auth, detalle])

  function cerrarDetalle() {
    setDetalle(null)
    triggerRef.current?.focus()
  }

  function aplicar(event: FormEvent) {
    event.preventDefault()
    if (!desde || !hasta || desde > hasta) {
      setError('Revisa el período seleccionado.')
      return
    }
    setPagina(0)
    setDesdeAplicado(desde)
    setHastaAplicado(hasta)
    setBuscarAplicado(buscar.trim())
    setModalidadAplicada(modalidad)
    setResponsableAplicado(responsableId ? Number(responsableId) : undefined)
  }

  function limpiar() {
    setDesde(hoy)
    setHasta(hoy)
    setBuscar('')
    setModalidad('')
    setResponsableId('')
    setPagina(0)
    setDesdeAplicado(hoy)
    setHastaAplicado(hoy)
    setBuscarAplicado('')
    setModalidadAplicada('')
    setResponsableAplicado(undefined)
  }

  const totalAccesos = resumen ? resumen.ingresosSocios + resumen.ingresosVisitantes : null

  return <div className="ingresos-supervision">
    <header className="ingresos-heading">
      <div><p className="ingresos-eyebrow">SUPERVISIÓN · ACCESOS</p><h2>Ingresos de clientes</h2>
        <p>Consulta los accesos registrados y revisa su modalidad, horario y responsable.</p></div>
    </header>

    <section aria-label="Resumen del período" className="ingresos-kpis">
      <article className="ingresos-kpi"><span className="ingresos-kpi-icon"><DoorOpen aria-hidden="true" size={18} /></span>
        <div><span className="ingresos-kpi-label">Ingresos del día</span><strong>{desdeAplicado === hoy && hastaAplicado === hoy ? (resumen ? totalAccesos : '—') : '—'}</strong>
          <small>{desdeAplicado === hoy && hastaAplicado === hoy ? `${resumen?.personasUnicas ?? '—'} personas únicas · puede incluir reingresos` : 'Selecciona hoy para ver esta cifra'}</small></div></article>
      <article className="ingresos-kpi"><span className="ingresos-kpi-icon"><UsersRound aria-hidden="true" size={18} /></span>
        <div><span className="ingresos-kpi-label">Con membresía</span><strong>{resumen?.ingresosSocios ?? '—'}</strong><small>{resumen?.personasUnicasSocios ?? '—'} personas únicas · accesos del período</small></div></article>
      <article className="ingresos-kpi"><span className="ingresos-kpi-icon"><CreditCard aria-hidden="true" size={18} /></span>
        <div><span className="ingresos-kpi-label">Con pase diario</span><strong>{resumen?.ingresosVisitantes ?? '—'}</strong><small>{resumen?.personasUnicasVisitantes ?? '—'} personas únicas · accesos incluyen reingresos</small></div></article>
    </section>
    {errorResumen && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{errorResumen}</p>}

    <Card className="asistencia-card ingresos-history-card" title="Historial de accesos">
      <form className="ingresos-toolbar" onSubmit={aplicar}>
        <label className="ingresos-search"><span className="asistencia-sr-only">Buscar por nombre o documento</span><Search aria-hidden="true" size={17} />
          <input aria-label="Buscar por nombre o documento" placeholder="Nombre o documento" type="search" value={buscar} onChange={(event) => setBuscar(event.target.value)} /></label>
        <label className="ingresos-filter">Modalidad<select aria-label="Modalidad" value={modalidad} onChange={(event) => setModalidad(event.target.value)}>
          <option value="">Todas</option><option value="MEMBRESIA">Membresía</option><option value="PASE_DIARIO">Pase diario</option>
        </select></label>
        <label className="ingresos-filter">Recepcionista<select aria-label="Recepcionista" value={responsableId} onChange={(event) => setResponsableId(event.target.value)}>
          <option value="">Todas</option>{(resumen?.responsables ?? []).map((persona) => <option key={persona.id} value={persona.id}>{persona.nombre}</option>)}
        </select></label>
        <label className="ingresos-filter">Desde<input aria-label="Ingresos desde" type="date" value={desde} onChange={(event) => setDesde(event.target.value)} /></label>
        <label className="ingresos-filter">Hasta<input aria-label="Ingresos hasta" type="date" value={hasta} onChange={(event) => setHasta(event.target.value)} /></label>
        <div className="ingresos-filter-actions"><Button type="submit" variant="secondary" disabled={cargando}><Search aria-hidden="true" size={15} />Aplicar</Button>
          <Button type="button" variant="ghost" disabled={cargando} onClick={limpiar}>Limpiar</Button></div>
      </form>
      {error && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{error}</p>}
      {cargando ? <p className="ingresos-state" aria-live="polite">Cargando accesos…</p> : error ? null : filas.length === 0 ?
        <div className="ingresos-empty"><span className="ingresos-empty-icon"><DoorOpen aria-hidden="true" size={20} /></span><strong>No hay accesos para mostrar</strong><span>Prueba con otras fechas o ajusta los filtros.</span></div> : <>
        <div className="ingresos-table-wrap"><table className="ingresos-table">
          <caption className="asistencia-sr-only">Historial de accesos de clientes</caption>
          <thead><tr><th scope="col">CLIENTE</th><th scope="col">DOCUMENTO</th><th scope="col">FECHA Y HORA</th><th scope="col">MODALIDAD</th><th scope="col">OPERACIÓN</th><th scope="col">RECEPCIONISTA</th><th scope="col">DETALLE</th></tr></thead>
          <tbody>{filas.map((fila) => <tr key={`${fila.tipo}-${fila.id}`}>
            <td data-label="Cliente"><strong>{fila.persona}</strong></td><td data-label="Documento">{fila.documentoProtegido ?? 'No disponible'}</td>
            <td data-label="Fecha y hora">{fechaHoraLima(fila.fecha)}</td>
            <td data-label="Modalidad"><span className={`ingresos-chip ${fila.modalidad ? `ingresos-chip--${fila.modalidad.toLowerCase()}` : ''}`}>{fila.modalidad === 'MEMBRESIA' ? 'Membresía' : fila.modalidad === 'PASE_DIARIO' ? 'Pase diario' : 'No especificada'}</span></td>
            <td data-label="Operación">{fila.operacion === 'REINGRESO' ? 'Reingreso' : 'Primer ingreso'}</td><td data-label="Recepcionista">{fila.responsable ?? 'No disponible'}</td>
            <td data-label="Detalle"><Button aria-label={`Ver detalle del acceso de ${fila.persona}`} className="ingresos-detail-button" onClick={(event) => { triggerRef.current = event.currentTarget; setDetalle(fila) }} type="button" variant="secondary"><Eye aria-hidden="true" size={15} />Ver</Button></td>
          </tr>)}</tbody>
        </table></div>
        <nav aria-label="Paginación del historial de accesos" className="ingresos-pagination"><Button type="button" variant="secondary" disabled={pagina === 0 || cargando} onClick={() => setPagina((actual) => Math.max(0, actual - 1))}>Anterior</Button>
          <span>Página {pagina + 1}{filas.length < 25 ? '' : ' · hay más resultados'}</span><Button type="button" variant="secondary" disabled={filas.length < 25 || cargando} onClick={() => setPagina((actual) => actual + 1)}>Siguiente</Button></nav>
      </>}
    </Card>

    {detalle && <div className="ingresos-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) cerrarDetalle() }}>
      <section ref={dialogRef} aria-labelledby="ingresos-detail-title" aria-modal="true" className="ingresos-dialog" onKeyDown={(event) => {
        if (event.key === 'Escape') { event.preventDefault(); cerrarDetalle() }
        if (event.key === 'Tab') { event.preventDefault(); closeRef.current?.focus() }
      }} role="dialog" tabIndex={-1}>
        <button ref={closeRef} aria-label="Cerrar detalle" className="ingresos-dialog-close" onClick={cerrarDetalle} type="button"><X aria-hidden="true" size={19} /></button>
        <p className="ingresos-eyebrow">DETALLE DEL ACCESO</p><h2 id="ingresos-detail-title">{detalle.persona}</h2>
        <h3 className="ingresos-detail-section-title">Información general</h3>
        <dl className="ingresos-detail-grid"><div><dt>Documento</dt><dd>{detalle.documentoProtegido ?? 'No disponible'}</dd></div><div><dt>Fecha y hora</dt><dd>{fechaHoraLima(detalle.fecha)}</dd></div>
          <div><dt>Modalidad</dt><dd>{detalle.modalidad === 'MEMBRESIA' ? 'Membresía' : detalle.modalidad === 'PASE_DIARIO' ? 'Pase diario' : 'No especificada'}</dd></div>
          <div><dt>Operación</dt><dd>{detalle.operacion === 'REINGRESO' ? 'Reingreso' : 'Primer ingreso'}</dd></div><div><dt>Recepcionista</dt><dd>{detalle.responsable ?? 'No disponible'}</dd></div><div><dt>Estado</dt><dd>{detalle.estado === 'REGISTRADO' ? 'Registrado' : detalle.estado}</dd></div></dl>
        <section aria-label="Datos de la modalidad" className="ingresos-detail-modality"><h3>Datos de la modalidad</h3>
          {cargandoDetalle ? <p role="status">Consultando datos históricos…</p>
            : errorDetalle ? <p role="alert">{errorDetalle}</p>
              : detalleModalidad?.modalidad === 'MEMBRESIA' ? <dl className="ingresos-detail-grid ingresos-detail-grid--modality"><div><dt>Plan</dt><dd>{detalleModalidad.planNombre ?? detalleModalidad.mensajeSinDato}</dd></div></dl>
                : detalleModalidad?.modalidad === 'PASE_DIARIO' ? <dl className="ingresos-detail-grid ingresos-detail-grid--modality"><div><dt>{detalle.operacion === 'REINGRESO' ? 'Importe de la venta original' : 'Importe pagado'}</dt><dd>{detalleModalidad.importePagado === null ? detalleModalidad.mensajeSinDato : new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(detalleModalidad.importePagado)}</dd></div>{detalle.operacion === 'REINGRESO' && <div><dt>Reingreso</dt><dd>Usa el pase original; no generó un cobro adicional.</dd></div>}</dl>
                  : <p role="status">No hay datos históricos disponibles para esta modalidad.</p>}
        </section>
      </section>
    </div>}
  </div>
}
