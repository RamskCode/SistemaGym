import { useEffect, useMemo, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, Search, UserRound } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/http'
import { consultarHistorialNutricional, type EvaluacionNutricional } from '../api/evaluacionesNutricionales'
import { listarSociosNutricion, buscarResumenSocioNutricion, type SocioResumenNutricion } from '../api/socios'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import { EvaluacionNutricionalForm } from './EvaluacionNutricionalForm'
import { HistorialNutricionalPanel } from './HistorialNutricionalPanel'
import './nutricion.css'

type EstadoFiltro = 'TODOS' | SocioResumenNutricion['estado']
type VistaNutricion = 'socios' | 'ficha' | 'nueva' | 'historial'

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : 'No fue posible cargar la información. Inténtalo nuevamente.'
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Fecha no disponible' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(date)
}

function viewFromParams(params: URLSearchParams): VistaNutricion {
  const view = params.get('vista')
  return view === 'ficha' || view === 'nueva' || view === 'historial' ? view : 'socios'
}

function stateLabel(estado: SocioResumenNutricion['estado']) { return estado === 'ACTIVO' ? 'Activo' : 'Inactivo' }

export function NutricionPage() {
  const auth = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const vista = viewFromParams(searchParams)
  const socioIdParam = searchParams.get('socio')
  const socioId = socioIdParam && /^\d+$/.test(socioIdParam) ? Number(socioIdParam) : null
  const seleccionandoParaEvaluacion = vista === 'socios' && searchParams.get('accion') === 'nueva'
  const [busqueda, setBusqueda] = useState('')
  const [busquedaAplicada, setBusquedaAplicada] = useState('')
  const [estado, setEstado] = useState<EstadoFiltro>('TODOS')
  const [socios, setSocios] = useState<SocioResumenNutricion[]>([])
  const [seleccionado, setSeleccionado] = useState<SocioResumenNutricion | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [reintento, setReintento] = useState(0)
  const [reintentoSocio, setReintentoSocio] = useState(0)
  const [enviosInciertos, setEnviosInciertos] = useState<Set<number>>(() => {
    try {
      const stored: unknown = JSON.parse(window.sessionStorage.getItem('nutricion:envios-inciertos') ?? '[]')
      return new Set(Array.isArray(stored) ? stored.filter((id): id is number => Number.isInteger(id)) : [])
    } catch { return new Set() }
  })
  const [ultimaEvaluacion, setUltimaEvaluacion] = useState<EvaluacionNutricional | null>(null)
  const [cargandoResumen, setCargandoResumen] = useState(false)
  const [errorResumen, setErrorResumen] = useState('')
  const [reintentoResumen, setReintentoResumen] = useState(0)
  const requestId = useRef(0)
  const socioRequestId = useRef(0)
  const listaSocios = useMemo(() => socios, [socios])

  useEffect(() => {
    const timer = window.setTimeout(() => setBusquedaAplicada(busqueda.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [busqueda])

  useEffect(() => {
    const id = ++requestId.current
    let mounted = true
    setCargando(true)
    setError('')
    void listarSociosNutricion(auth, { query: busquedaAplicada, estado })
      .then((result) => { if (mounted && requestId.current === id) setSocios(result) })
      .catch((reason: unknown) => {
        if (mounted && requestId.current === id) { setSocios([]); setError(errorMessage(reason)) }
      })
      .finally(() => { if (mounted && requestId.current === id) setCargando(false) })
    return () => { mounted = false }
  }, [auth, busquedaAplicada, estado, reintento])

  useEffect(() => {
    const id = ++socioRequestId.current
    let mounted = true
    if (socioId === null || !Number.isSafeInteger(socioId) || socioId <= 0) {
      setSeleccionado(null)
      return () => { mounted = false }
    }
    const known = listaSocios.find((socio) => socio.idSocio === socioId)
    if (known) {
      setSeleccionado(known)
      return () => { mounted = false }
    }
    setSeleccionado(null)
    void buscarResumenSocioNutricion(auth, socioId)
      .then((socio) => { if (mounted && socioRequestId.current === id) setSeleccionado(socio) })
      .catch((reason: unknown) => {
        if (mounted && socioRequestId.current === id) setErrorResumen(errorMessage(reason))
      })
    return () => { mounted = false }
  }, [auth, listaSocios, socioId, reintentoSocio])

  useEffect(() => {
    if (vista !== 'ficha' || !seleccionado) return
    let mounted = true
    setCargandoResumen(true)
    setErrorResumen('')
    setUltimaEvaluacion(null)
    void consultarHistorialNutricional(auth, seleccionado.idSocio)
      .then((evaluaciones) => {
        if (mounted) setUltimaEvaluacion([...evaluaciones].sort((a, b) => Date.parse(b.fechaEvaluacion) - Date.parse(a.fechaEvaluacion))[0] ?? null)
      })
      .catch((reason: unknown) => { if (mounted) setErrorResumen(errorMessage(reason)) })
      .finally(() => { if (mounted) setCargandoResumen(false) })
    return () => { mounted = false }
  }, [auth, seleccionado, vista, reintentoResumen])

  function goTo(view: VistaNutricion, socio: SocioResumenNutricion | null = seleccionado) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (view === 'socios' || !socio) {
        next.delete('vista')
        next.delete('socio')
        next.delete('accion')
      } else {
        next.set('vista', view)
        next.set('socio', String(socio.idSocio))
        next.delete('accion')
      }
      return next
    })
    if (view === 'socios' || !socio) {
      setSeleccionado(null)
      setUltimaEvaluacion(null)
    } else setSeleccionado(socio)
    setErrorResumen('')
  }

  function updateSocioState(nextState: SocioResumenNutricion['estado']) {
    if (!seleccionado) return
    const updated = { ...seleccionado, estado: nextState }
    setSeleccionado(updated)
    setSocios((current) => current.map((socio) => socio.idSocio === updated.idSocio ? updated : socio))
  }

  const identity = seleccionado && <div className="nutricion-member-banner">
    <span aria-hidden="true" className="nutricion-avatar"><UserRound size={20} /></span>
    <div><strong>{seleccionado.nombres} {seleccionado.apellidos}</strong><span>{seleccionado.codigoSocio} · {stateLabel(seleccionado.estado)}</span></div>
  </div>

  return <div className="dashboard nutricion-page">
    <PageTitle
      description={vista === 'socios' ? 'Busca y selecciona un socio para continuar con su atención nutricional.' : 'Atención nutricional individual del socio.'}
      eyebrow="ATENCIÓN NUTRICIONAL"
      title="Nutrición"
    />

    {vista === 'socios' && <>
      <Card className="nutricion-directory" title="Socios">
        <div className="nutricion-toolbar">
          <label className="nutricion-search" htmlFor="nutricion-busqueda">
            <Search aria-hidden="true" size={18} />
            <input aria-label="Buscar socio por nombre, apellido o código" autoComplete="off" id="nutricion-busqueda" onChange={(event) => setBusqueda(event.target.value)} placeholder="Buscar socio por nombre, apellido o código" type="search" value={busqueda} />
          </label>
          <label className="nutricion-filter" htmlFor="nutricion-estado">Estado
            <select id="nutricion-estado" onChange={(event) => setEstado(event.target.value as EstadoFiltro)} value={estado}>
              <option value="TODOS">Todos</option><option value="ACTIVO">Activo</option><option value="INACTIVO">Inactivo</option>
            </select>
          </label>
        </div>
        <div aria-live="polite" aria-busy={cargando} className="nutricion-results">
          {cargando && <p className="nutricion-message" role="status">Cargando socios…</p>}
          {!cargando && error && <div className="nutricion-error" role="alert"><p>{error}</p><Button onClick={() => setReintento((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></div>}
          {!cargando && !error && socios.length === 0 && <p className="nutricion-message">No se encontraron socios con esos criterios.</p>}
          {!cargando && !error && socios.length > 0 && <ul aria-label="Socios disponibles para atención nutricional" className="nutricion-list">
            {socios.map((socio) => <li className="nutricion-row" key={socio.idSocio}>
              <div className="nutricion-person"><span aria-hidden="true" className="nutricion-avatar"><UserRound size={19} /></span><div>
                <strong>{socio.nombres} {socio.apellidos}</strong><span>Código: {socio.codigoSocio}</span>
              </div></div>
              <span className={`nutricion-state nutricion-state--${socio.estado.toLowerCase()}`}>{stateLabel(socio.estado)}</span>
              <Button aria-label={`${seleccionandoParaEvaluacion && socio.estado === 'ACTIVO' ? 'Seleccionar socio' : 'Ver ficha de'} ${socio.nombres} ${socio.apellidos}`} onClick={() => goTo(seleccionandoParaEvaluacion && socio.estado === 'ACTIVO' ? 'nueva' : 'ficha', socio)} type="button" variant="secondary">{seleccionandoParaEvaluacion && socio.estado === 'ACTIVO' ? 'Seleccionar socio' : 'Ver ficha'} <ArrowRight aria-hidden="true" size={16} /></Button>
            </li>)}
          </ul>}
        </div>
      </Card>
    </>}

    {vista !== 'socios' && !seleccionado && <Card className="nutricion-state-card">
      {errorResumen ? <div className="nutricion-error" role="alert"><p>{errorResumen}</p><Button onClick={() => setReintentoSocio((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></div>
        : <p className="nutricion-message" role="status">Cargando la ficha del socio…</p>}
      <Button onClick={() => goTo('socios', null)} type="button" variant="secondary"><ArrowLeft aria-hidden="true" size={16} /> Volver al listado</Button>
    </Card>}

    {vista === 'ficha' && seleccionado && <>
      <Card className="nutricion-member-card" title="Ficha nutricional">
        {identity}
        <div className="nutricion-member-summary">
          <div><span>Nombre completo</span><strong>{seleccionado.nombres} {seleccionado.apellidos}</strong></div>
          <div><span>Código del socio</span><strong>{seleccionado.codigoSocio}</strong></div>
          <div><span>Estado</span><strong>{stateLabel(seleccionado.estado)}</strong></div>
        </div>
        <div className="nutricion-next-actions">
          {seleccionado.estado === 'ACTIVO' && !enviosInciertos.has(seleccionado.idSocio)
            ? <Button onClick={() => goTo('nueva')} type="button">Nueva evaluación</Button>
            : seleccionado.estado === 'ACTIVO' && <Button disabled title="No se puede repetir un envío de resultado incierto" type="button">Envío bloqueado</Button>}
          <Button onClick={() => goTo('historial')} type="button" variant="secondary">Ver historial</Button>
          <Button onClick={() => goTo('socios', null)} type="button" variant="ghost"><ArrowLeft aria-hidden="true" size={16} /> Volver al listado</Button>
        </div>
        {seleccionado.estado === 'INACTIVO' && <p className="nutricion-note">Los socios inactivos no pueden recibir nuevas evaluaciones.</p>}
        {enviosInciertos.has(seleccionado.idSocio) && <p className="nutricion-note" role="status">El resultado del envío anterior no pudo confirmarse. No reenvíes la solicitud.</p>}
      </Card>
      <Card className="nutricion-latest-card" title="Última evaluación">
        {cargandoResumen ? <p className="nutricion-message" role="status">Consultando evaluación…</p>
          : errorResumen ? <div className="nutricion-error" role="alert"><p>{errorResumen}</p><Button onClick={() => setReintentoResumen((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></div>
            : ultimaEvaluacion ? <div className="nutricion-latest-summary">
              <p>Registrada {formatDate(ultimaEvaluacion.fechaEvaluacion)}</p>
              <dl><div><dt>Peso</dt><dd>{ultimaEvaluacion.pesoKg} kg</dd></div><div><dt>Talla</dt><dd>{ultimaEvaluacion.tallaM} m</dd></div><div><dt>Grasa corporal</dt><dd>{ultimaEvaluacion.porcentajeGrasa}%</dd></div><div><dt>Masa muscular</dt><dd>{ultimaEvaluacion.masaMuscularKg} kg</dd></div></dl>
              <Button onClick={() => goTo('historial')} type="button" variant="secondary">Ver evaluación en historial</Button>
            </div> : <p className="nutricion-message">Este socio todavía no tiene evaluaciones nutricionales.</p>}
      </Card>
    </>}

    {vista === 'nueva' && seleccionado && (seleccionado.estado !== 'ACTIVO'
      ? <Card className="nutricion-state-card" title="Nueva evaluación"><p className="nutricion-note">Los socios inactivos no pueden recibir nuevas evaluaciones.</p><Button onClick={() => goTo('ficha')} type="button" variant="secondary">Cancelar y volver a la ficha</Button></Card>
      : <>
        <div className="nutricion-view-heading">{identity}<Button onClick={() => goTo('ficha')} type="button" variant="secondary"><ArrowLeft aria-hidden="true" size={16} /> Volver a la ficha</Button></div>
        <EvaluacionNutricionalForm
          envioIncierto={enviosInciertos.has(seleccionado.idSocio)}
          onClose={() => goTo('ficha')}
          onConflicto={updateSocioState}
          onIncierto={() => setEnviosInciertos((current) => {
            const next = new Set(current).add(seleccionado.idSocio)
            try { window.sessionStorage.setItem('nutricion:envios-inciertos', JSON.stringify([...next])) } catch { /* Keep the in-memory lock. */ }
            return next
          })}
          onVerHistorial={() => goTo('historial')}
          socio={seleccionado}
        />
      </>)}

    {vista === 'historial' && seleccionado && <>
      <div className="nutricion-view-heading">{identity}<Button onClick={() => goTo('ficha')} type="button" variant="secondary"><ArrowLeft aria-hidden="true" size={16} /> Volver a la ficha</Button></div>
      <HistorialNutricionalPanel envioIncierto={enviosInciertos.has(seleccionado.idSocio)} onVolver={() => goTo('ficha')} socio={seleccionado} />
    </>}
  </div>
}
