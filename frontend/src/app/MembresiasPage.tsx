import { useEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { ArrowDownUp, Eye, Search, X } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  listarMembresias,
  listarPlanesActivos,
  listarSociosActivos,
  buscarMembresiaPorId,
  obtenerEstadoMembresia,
  type EstadoMembresia,
  type Membresia,
  type PagoPosteriorMembresiaRespuesta,
  type Plan,
} from '../api/membresias'
import type { Socio } from '../api/socios'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './membresias.css'
import { MembresiaOperacionDialog } from './MembresiaOperacionDialog'
import { readMembresiaWriteMarkers, type AnyMembresiaWriteMarker } from './membresiasUncertainWrites'

type Registro = { membresia: Membresia; estado: EstadoMembresia }
type Orden = 'socio' | 'plan' | 'fechaInicio' | 'fechaVencimiento' | 'saldoPendiente'

function mantenerFocoEnDialogo(event: ReactKeyboardEvent<HTMLElement>) {
  if (event.key !== 'Tab') return
  const elements = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
    'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )).filter((element) => !element.hidden)
  if (!elements.length) { event.preventDefault(); return }
  if (event.shiftKey && document.activeElement === elements[0]) {
    event.preventDefault(); elements[elements.length - 1].focus()
  } else if (!event.shiftKey && document.activeElement === elements[elements.length - 1]) {
    event.preventDefault(); elements[0].focus()
  }
}

function fechaPresentacion(value: string) {
  const date = new Date(`${value.slice(0, 10)}T00:00:00`)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(date)
}

function mensaje(error: unknown) {
  return error instanceof ApiError ? error.message : 'Ocurrió un error inesperado. Inténtalo nuevamente.'
}

function nombreSocio(membresia: Membresia) {
  const socio = membresia.socio
  return socio ? `${socio.nombres} ${socio.apellidos}` : 'Socio no disponible'
}

function nombrePlan(membresia: Membresia) {
  return membresia.plan?.nombre ?? 'Plan no disponible'
}

function moneda(value: number) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(value)
}

function fechaLocalHoy() {
  const hoy = new Date()
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`
}

function etiquetaEstado(estado: string) {
  const labels: Record<string, string> = {
    ACTIVA: 'Activa',
    PENDIENTE_PAGO: 'Pendiente de pago',
    CANCELADA: 'Cancelada',
    VENCIDA: 'Vencida',
  }
  return labels[estado] ?? estado.replaceAll('_', ' ').toLowerCase()
}

export function MembresiasPage() {
  const auth = useAuth()
  const [registros, setRegistros] = useState<Registro[]>([])
  const [planes, setPlanes] = useState<Plan[]>([])
  const [sociosActivos, setSociosActivos] = useState<Socio[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [estadoFiltro, setEstadoFiltro] = useState('TODOS')
  const [planFiltro, setPlanFiltro] = useState('TODOS')
  const [orden, setOrden] = useState<Orden>('fechaInicio')
  const [descendente, setDescendente] = useState(true)
  const [pagina, setPagina] = useState(1)
  const [porPagina, setPorPagina] = useState(10)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [detalle, setDetalle] = useState<Registro | null>(null)
  const [detalleCargando, setDetalleCargando] = useState(false)
  const [operacion, setOperacion] = useState<'nueva' | 'pago' | null>(null)
  const [membresiaPago, setMembresiaPago] = useState<Registro | null>(null)
  const [exito, setExito] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [uncertainMarkers, setUncertainMarkers] = useState<AnyMembresiaWriteMarker[]>([])
  const [resaltadaId, setResaltadaId] = useState<number | null>(null)
  const account = auth.user?.correo ?? ''
  const sequence = useRef(0)
  const detalleCloseRef = useRef<HTMLButtonElement>(null)
  const detalleTriggerRef = useRef<HTMLButtonElement | null>(null)
  const operacionTriggerRef = useRef<HTMLButtonElement | null>(null)
  const nuevaTriggerRef = useRef<HTMLButtonElement | null>(null)
  const detalleWasOpen = useRef(false)
  const operacionWasOpen = useRef(false)

  useEffect(() => {
    let active = true
    if (!account) { setUncertainMarkers([]); return }
    void readMembresiaWriteMarkers(account).then((markers) => { if (active) setUncertainMarkers(markers) })
      .catch(() => { if (active) setUncertainMarkers([{ operation: 'nueva' }, { operation: 'pago', idMembresia: -1 }]) })
    return () => { active = false }
  }, [account])

  useEffect(() => {
    const current = ++sequence.current
    let active = true
    setCargando(true)
    setError('')
    void Promise.all([listarMembresias(auth), listarPlanesActivos(auth), listarSociosActivos(auth)])
      .then(async ([membresias, planesActivos, sociosActivos]) => {
        const estados = await Promise.all(membresias.map((m) => obtenerEstadoMembresia(auth, m.idMembresia)))
        if (active && sequence.current === current) {
          setRegistros(membresias.map((membresia, index) => ({ membresia, estado: estados[index] })))
          setPlanes(planesActivos)
          setSociosActivos(sociosActivos)
        }
      })
      .catch((reason: unknown) => {
        if (active && sequence.current === current) setError(mensaje(reason))
      })
      .finally(() => {
        if (active && sequence.current === current) setCargando(false)
      })
    return () => { active = false }
  }, [auth, reloadKey])

  useEffect(() => {
    if (!detalle) return
    detalleWasOpen.current = true
    detalleCloseRef.current?.focus()
  }, [detalle])

  useEffect(() => {
    if (detalle || detalleCargando) return
    if (detalleWasOpen.current) {
      detalleWasOpen.current = false
      detalleTriggerRef.current?.focus()
      detalleTriggerRef.current = null
    }
  }, [detalle, detalleCargando])

  useEffect(() => {
    if (operacion) { operacionWasOpen.current = true; return }
    if (operacionWasOpen.current) {
      operacionWasOpen.current = false
      const trigger = operacionTriggerRef.current?.isConnected ? operacionTriggerRef.current : nuevaTriggerRef.current
      trigger?.focus()
      operacionTriggerRef.current = null
    }
  }, [operacion])

  const registrosFiltrados = useMemo(() => {
    const query = busqueda.trim().toLocaleLowerCase('es')
    return registros.filter(({ membresia }) => {
      const matchesSearch = !query || [
        nombreSocio(membresia), membresia.socio?.codigoSocio ?? '',
         membresia.socio?.numeroDocumento ?? membresia.socio?.dni ?? '', nombrePlan(membresia), String(membresia.idMembresia),
      ].some((value) => value.toLocaleLowerCase('es').includes(query))
      const matchesState = estadoFiltro === 'TODOS' || membresia.estado === estadoFiltro
      const matchesPlan = planFiltro === 'TODOS' || String(membresia.plan?.idPlan ?? '') === planFiltro
      return matchesSearch && matchesState && matchesPlan
    }).sort((a, b) => {
      let comparison: number
      if (orden === 'socio') comparison = nombreSocio(a.membresia).localeCompare(nombreSocio(b.membresia), 'es', { sensitivity: 'base' })
      else if (orden === 'plan') comparison = nombrePlan(a.membresia).localeCompare(nombrePlan(b.membresia), 'es', { sensitivity: 'base' })
      else if (orden === 'saldoPendiente') comparison = a.estado.saldoPendiente - b.estado.saldoPendiente
      else comparison = a.membresia[orden].localeCompare(b.membresia[orden])
      return descendente ? -comparison : comparison
    })
  }, [registros, busqueda, estadoFiltro, planFiltro, orden, descendente])

  const totalPaginas = Math.max(1, Math.ceil(registrosFiltrados.length / porPagina))
  const paginaRegistros = registrosFiltrados.slice((pagina - 1) * porPagina, pagina * porPagina)
  useEffect(() => { setPagina(1) }, [busqueda, estadoFiltro, planFiltro, orden, descendente, porPagina])
  useEffect(() => { if (pagina > totalPaginas) setPagina(totalPaginas) }, [pagina, totalPaginas])

  async function verDetalle(id: number, trigger: HTMLButtonElement) {
    if (detalleCargando) return
    detalleTriggerRef.current = trigger
    setResaltadaId(id)
    setDetalleCargando(true)
    setError('')
    try {
      const [membresia, estado] = await Promise.all([
        buscarMembresiaPorId(auth, id),
        obtenerEstadoMembresia(auth, id),
      ])
      setDetalle({ membresia, estado })
    } catch (reason) {
      setError(mensaje(reason))
      setResaltadaId(null)
      detalleTriggerRef.current?.focus()
    } finally {
      setDetalleCargando(false)
    }
  }

  function cerrarDetalle() {
    setDetalle(null)
    setResaltadaId(null)
  }

  function comenzarNueva(trigger: HTMLButtonElement) {
    nuevaTriggerRef.current = trigger
    operacionTriggerRef.current = trigger
    setExito('')
    setOperacion('nueva')
  }

  function reintentarCarga() {
    setReloadKey((value) => value + 1)
  }

  function limpiarFiltros() {
    setBusqueda('')
    setEstadoFiltro('TODOS')
    setPlanFiltro('TODOS')
    setOrden('fechaInicio')
    setDescendente(true)
    setPagina(1)
  }

  function registrarNueva() {
    setOperacion(null)
    setExito('Membresía registrada correctamente. El primer pago fue aplicado.')
    setReloadKey((value) => value + 1)
  }

  function registrarPago(response: PagoPosteriorMembresiaRespuesta) {
    setOperacion(null)
    setExito(`Pago registrado. Saldo actualizado: ${moneda(response.saldoPendiente)}.`)
    setReloadKey((value) => value + 1)
    setDetalle(null)
  }

  function puedeRegistrarPago(registro: Registro) {
    const estadoPermite = registro.estado.estado === 'PENDIENTE_PAGO' || registro.estado.estado === 'ACTIVA'
    const dentroDelPlazo = !registro.estado.fechaLimitePago || registro.estado.fechaLimitePago >= fechaLocalHoy()
    return estadoPermite && dentroDelPlazo && registro.estado.saldoPendiente > 0
  }

  return (
    <div className="dashboard membresias-page">
      <PageTitle actions={<Button className="membresias-create-button" onClick={(event) => comenzarNueva(event.currentTarget)} type="button">Nueva membresía</Button>} description="Consulta las membresías, su vigencia y el saldo pendiente asociado." eyebrow="RECEPCIÓN · SERVICIOS" title="Membresías" />
      {exito && <p className="membresias-success" role="status">{exito}</p>}
      <Card className="membresias-card" title="Directorio de membresías">
        <div aria-label="Buscar y filtrar membresías" className="membresias-toolbar" role="search">
          <label className="membresias-search search-field"><Search aria-hidden="true" size={17} /><span className="membresias-sr-only">Buscar por socio, código, documento, plan o número de membresía</span><input aria-label="Buscar por socio, código, documento, plan o número de membresía" onChange={(event) => setBusqueda(event.target.value)} placeholder="Buscar por socio, código, documento, plan o número de membresía" type="search" value={busqueda} /></label>
          <label className="membresias-filter"><span>Estado</span><select aria-label="Filtrar por estado de membresía" onChange={(event) => setEstadoFiltro(event.target.value)} value={estadoFiltro}><option value="TODOS">Todos</option><option value="ACTIVA">Activa</option><option value="PENDIENTE_PAGO">Pendiente de pago</option><option value="CANCELADA">Cancelada</option><option value="VENCIDA">Vencida</option></select></label>
          <label className="membresias-filter"><span>Plan</span><select aria-label="Filtrar por plan" onChange={(event) => setPlanFiltro(event.target.value)} value={planFiltro}><option value="TODOS">Todos</option>{planes.map((plan) => <option key={plan.idPlan} value={plan.idPlan}>{plan.nombre}</option>)}</select></label>
          <div className="membresias-sort"><label className="membresias-filter"><span>Ordenar por</span><select aria-label="Ordenar membresías" onChange={(event) => setOrden(event.target.value as Orden)} value={orden}><option value="fechaInicio">Fecha de inicio</option><option value="fechaVencimiento">Vencimiento</option><option value="socio">Socio</option><option value="plan">Plan</option><option value="saldoPendiente">Saldo pendiente</option></select></label><button aria-label={descendente ? 'Orden descendente, cambiar a ascendente' : 'Orden ascendente, cambiar a descendente'} className="icon-button membresias-sort-direction" onClick={() => setDescendente((value) => !value)} type="button"><ArrowDownUp aria-hidden="true" size={16} /></button></div>
          <Button className="membresias-clear-button" disabled={!busqueda && estadoFiltro === 'TODOS' && planFiltro === 'TODOS' && orden === 'fechaInicio' && descendente} onClick={limpiarFiltros} type="button" variant="secondary">Limpiar filtros</Button>
        </div>
        <div aria-live="polite" className="membresias-summary"><span><strong>{registrosFiltrados.length} {registrosFiltrados.length === 1 ? 'membresía' : 'membresías'}</strong><span>{registrosFiltrados.length ? `Mostrando ${(pagina - 1) * porPagina + 1}–${Math.min(pagina * porPagina, registrosFiltrados.length)} de ${registros.length}` : registros.length ? 'No hay registros para estos filtros' : 'Sin registros'}</span></span><label>Registros por página<select aria-label="Membresías por página" onChange={(event) => setPorPagina(Number(event.target.value))} value={porPagina}><option value={10}>10</option><option value={20}>20</option><option value={50}>50</option></select></label></div>
        {error && !cargando && <div className="membresias-feedback membresias-feedback--error" role="alert"><span>{error}</span><Button disabled={cargando} onClick={reintentarCarga} type="button" variant="secondary">Reintentar consulta</Button></div>}
        {cargando ? <p aria-live="polite" className="membresias-message">Cargando membresías…</p> : error ? null : registrosFiltrados.length === 0 ? <div className="membresias-empty"><span className="membresias-empty-icon"><Search aria-hidden="true" size={20} /></span><p>{registros.length ? 'No se encontraron membresías con estos filtros.' : 'Aún no hay membresías registradas.'}</p><span>{registros.length ? 'Prueba con otros criterios o limpia los filtros.' : 'Las membresías registradas aparecerán en este directorio.'}</span>{registros.length === 0 && <Button onClick={(event) => comenzarNueva(event.currentTarget)} type="button">Nueva membresía</Button>}</div> : <div className="table-wrap membresias-table-wrap"><table className="activity-table membresias-table"><caption className="membresias-sr-only">Directorio de membresías registradas</caption><thead><tr><th scope="col">Socio</th><th scope="col">Plan</th><th scope="col">Inicio</th><th scope="col">Vencimiento</th><th scope="col">Estado</th><th scope="col">Saldo pendiente</th><th scope="col">Acciones</th></tr></thead><tbody>{paginaRegistros.map((registro) => { const { membresia, estado } = registro; return <tr className={resaltadaId === membresia.idMembresia ? 'membresias-row-selected' : undefined} key={membresia.idMembresia}><td data-label="Socio"><strong>{nombreSocio(membresia)}</strong><small className="membresias-code">{membresia.socio?.codigoSocio ?? 'Código no disponible'}</small></td><td data-label="Plan">{nombrePlan(membresia)}</td><td data-label="Inicio">{fechaPresentacion(membresia.fechaInicio)}</td><td data-label="Vencimiento">{fechaPresentacion(membresia.fechaVencimiento)}</td><td data-label="Estado"><span className={`membresias-state membresias-state--${membresia.estado.toLowerCase()}`}><span aria-hidden="true" className="membresias-state-dot" />{etiquetaEstado(membresia.estado)}</span></td><td data-label="Saldo pendiente" className="membresias-balance">{moneda(estado.saldoPendiente)}</td><td data-label="Acciones"><div className="membresias-actions"><Button aria-label={`Ver detalle de membresía ${membresia.idMembresia}`} className="membresias-row-action" disabled={detalleCargando} onClick={(event) => void verDetalle(membresia.idMembresia, event.currentTarget)} type="button" variant="secondary" icon={<Eye aria-hidden="true" size={15} />}>Ver detalle</Button>{puedeRegistrarPago(registro) && <Button aria-label={`Registrar pago de membresía ${membresia.idMembresia}`} onClick={(event) => { operacionTriggerRef.current = event.currentTarget; setMembresiaPago(registro); setOperacion('pago'); setExito('') }} type="button">Registrar pago</Button>}</div></td></tr> })}</tbody></table></div>}
        {!cargando && !error && registrosFiltrados.length > 0 && <nav aria-label="Paginación de membresías" className="membresias-pagination"><button disabled={pagina === 1} onClick={() => setPagina((current) => current - 1)} type="button">Anterior</button><span>Página {pagina} de {totalPaginas}</span><button disabled={pagina === totalPaginas} onClick={() => setPagina((current) => current + 1)} type="button">Siguiente</button></nav>}
      </Card>
      {detalle && <div className="membresias-backdrop" onClick={cerrarDetalle}><section aria-labelledby="membresias-detail-title" aria-modal="true" className="membresias-dialog membresias-detail-dialog" onClick={(event) => event.stopPropagation()} onKeyDown={(event) => { if (event.key === 'Escape') cerrarDetalle(); else mantenerFocoEnDialogo(event) }} role="dialog"><button ref={detalleCloseRef} aria-label="Cerrar detalle" className="membresias-dialog-close" onClick={cerrarDetalle} type="button"><X aria-hidden="true" size={18} /></button><p className="membresias-dialog-eyebrow">INFORMACIÓN DE LA MEMBRESÍA</p><h2 id="membresias-detail-title">Detalle de membresía</h2><div className="membresias-detail-heading"><div><p>{nombreSocio(detalle.membresia)}</p><span>{detalle.membresia.socio?.codigoSocio ?? 'Código de socio no disponible'}</span></div><span className={`membresias-state membresias-state--${detalle.membresia.estado.toLowerCase()}`}><span aria-hidden="true" className="membresias-state-dot" />{etiquetaEstado(detalle.membresia.estado)}</span></div><section className="membresias-detail-section" aria-labelledby="membresias-detail-plan"><h3 id="membresias-detail-plan">Plan y vigencia</h3><dl className="membresias-detail-grid"><div><dt>Plan</dt><dd>{nombrePlan(detalle.membresia)}</dd></div><div><dt>Precio acordado</dt><dd>{moneda(detalle.estado.precioAcordado)}</dd></div><div><dt>Fecha de inicio</dt><dd>{fechaPresentacion(detalle.estado.fechaInicio)}</dd></div><div><dt>Fecha de vencimiento</dt><dd>{fechaPresentacion(detalle.estado.fechaVencimiento)}</dd></div><div><dt>Fecha límite de pago</dt><dd>{detalle.estado.fechaLimitePago ? fechaPresentacion(detalle.estado.fechaLimitePago) : 'No definida'}</dd></div></dl></section><section className="membresias-detail-section" aria-labelledby="membresias-detail-balance"><h3 id="membresias-detail-balance">Estado de pago</h3><dl className="membresias-detail-grid"><div><dt>Saldo pendiente</dt><dd>{moneda(detalle.estado.saldoPendiente)}</dd></div><div><dt>Situación</dt><dd>{etiquetaEstado(detalle.estado.situacion)}</dd></div><div><dt>Fecha de registro</dt><dd>{fechaPresentacion(detalle.estado.fechaCreacion)}</dd></div>{detalle.estado.motivoCancelacion && <div><dt>Motivo de cancelación</dt><dd>{detalle.estado.motivoCancelacion}</dd></div>}</dl></section>{puedeRegistrarPago(detalle) && <div className="membresias-detail-actions"><Button onClick={(event) => { operacionTriggerRef.current = event.currentTarget; setMembresiaPago(detalle); setOperacion('pago'); setDetalle(null); setExito('') }} type="button">Registrar pago</Button></div>}</section></div>}
      {operacion && <MembresiaOperacionDialog account={account} auth={auth} blockedMarkers={uncertainMarkers} membresia={membresiaPago?.membresia} onMarkerChange={(marker, blocked) => setUncertainMarkers((current) => blocked ? [...current.filter((item) => !('key' in item && item.key === marker.key) && !(item.operation === marker.operation && (item.operation === 'nueva' || marker.operation === 'pago' && item.operation === 'pago' && item.idMembresia === marker.idMembresia))), marker] : current.filter((item) => !('key' in item && item.key === marker.key)))} onClose={() => { setOperacion(null); setMembresiaPago(null) }} onCreated={registrarNueva} onPaid={registrarPago} operacion={operacion} planes={planes} saldoPendiente={membresiaPago?.estado.saldoPendiente} socios={sociosActivos} />}
    </div>
  )
}
