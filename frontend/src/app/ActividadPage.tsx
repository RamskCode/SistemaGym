import { useEffect, useMemo, useState } from 'react'
import { Activity, BadgeDollarSign, CalendarCheck2, SearchX, UserRoundPlus, UsersRound } from 'lucide-react'
import { ApiError } from '../api/http'
import { getDashboardSummary, type DashboardSummary } from '../api/dashboard'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './actividad.css'

type TipoFiltro = 'TODOS' | 'SOCIO' | 'VISITANTE'
const dateTime = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima' })
const money = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'unauthenticated') return 'Tu sesión expiró. Inicia sesión nuevamente.'
  if (error instanceof ApiError && error.code === 'forbidden') return 'No tienes autorización para consultar la actividad.'
  if (error instanceof ApiError && error.code === 'network') return 'No se pudo conectar con el servidor. Comprueba tu conexión.'
  return 'No fue posible cargar la actividad. Inténtalo nuevamente.'
}

function dayInLima(value: string) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value))
}

export function ActividadPage() {
  const auth = useAuth()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [query, setQuery] = useState('')
  const [tipo, setTipo] = useState<TipoFiltro>('TODOS')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    void getDashboardSummary(auth).then((result) => {
      if (active) setSummary(result)
    }).catch((reason: unknown) => {
      if (active) { setSummary(null); setError(errorMessage(reason)) }
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [auth, retry])

  const events = useMemo(() => (summary?.actividadReciente ?? [])
    .filter((event) => event.nombre.toLocaleLowerCase('es-PE').includes(query.trim().toLocaleLowerCase('es-PE')))
    .filter((event) => tipo === 'TODOS' || event.tipo === tipo)
    .filter((event) => !desde || dayInLima(event.fechaHora) >= desde)
    .filter((event) => !hasta || dayInLima(event.fechaHora) <= hasta)
    .sort((a, b) => Date.parse(b.fechaHora) - Date.parse(a.fechaHora)), [summary, query, tipo, desde, hasta])

  const hasFilters = Boolean(query || tipo !== 'TODOS' || desde || hasta)
  const clearFilters = () => { setQuery(''); setTipo('TODOS'); setDesde(''); setHasta('') }

  const metricGroups = summary ? [
    { label: 'Hoy', metrics: [
      { label: 'Entradas', value: summary.ingresosHoy.toLocaleString('es-PE'), Icon: CalendarCheck2 },
      { label: 'Ingresos cobrados', value: money.format(summary.ingresosCobradosHoy), Icon: BadgeDollarSign },
    ] },
    { label: 'Esta semana', metrics: [
      { label: 'Entradas registradas', value: summary.ingresosSemana.reduce((sum, day) => sum + day.cantidad, 0).toLocaleString('es-PE'), Icon: Activity },
    ] },
    { label: 'Este mes', metrics: [
      { label: 'Socios nuevos', value: summary.sociosNuevosMes.toLocaleString('es-PE'), Icon: UserRoundPlus },
      { label: 'Pagos confirmados', value: money.format(summary.ingresosMes), Icon: BadgeDollarSign },
    ] },
  ] : []

  return <div className="dashboard actividad-page">
    <PageTitle eyebrow="OPERACIÓN DEL GIMNASIO" title="Actividad" description="Entradas recientes e indicadores operativos del gimnasio." />
    {error && <div className="actividad-error" role="alert"><span>{error}</span><Button onClick={() => setRetry((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></div>}
    {loading ? <p className="actividad-state" role="status">Cargando actividad…</p> : !error && summary && <>
      <div aria-busy={loading} className="actividad-metric-groups">
        {metricGroups.map(({ label: period, metrics }) => <section aria-label={`Indicadores: ${period.toLocaleLowerCase('es-PE')}`} className="actividad-metric-group" key={period}>
          <h2>{period}</h2>
          <div className="actividad-metrics">{metrics.map(({ label, value, Icon }) => <Card className="actividad-metric" key={label}>
            <div className="actividad-metric__top"><span>{label}</span><span className="actividad-metric__icon"><Icon aria-hidden="true" size={18} strokeWidth={1.8} /></span></div>
            <strong>{value}</strong>
          </Card>)}</div>
        </section>)}
      </div>
      <Card className="actividad-card" title="Entradas recientes">
        <div className="actividad-filters">
          <label className="actividad-search" htmlFor="actividad-search">Buscar persona<input id="actividad-search" onChange={(event) => setQuery(event.target.value)} placeholder="Nombre" type="search" value={query} /></label>
          <label htmlFor="actividad-tipo">Tipo<select id="actividad-tipo" onChange={(event) => setTipo(event.target.value as TipoFiltro)} value={tipo}><option value="TODOS">Todos</option><option value="SOCIO">Socio</option><option value="VISITANTE">Visitante</option></select></label>
          <label htmlFor="actividad-desde">Desde<input id="actividad-desde" onChange={(event) => setDesde(event.target.value)} type="date" value={desde} /></label>
          <label htmlFor="actividad-hasta">Hasta<input id="actividad-hasta" onChange={(event) => setHasta(event.target.value)} type="date" value={hasta} /></label>
          {hasFilters && <Button onClick={clearFilters} type="button" variant="secondary">Limpiar filtros</Button>}
        </div>
        {summary.actividadReciente.length === 0 ? <div className="actividad-empty" role="status"><span className="actividad-empty__icon"><UsersRound aria-hidden="true" size={20} /></span><h3>Sin entradas recientes</h3><p>Cuando se registren entradas, aparecerán aquí.</p></div>
          : events.length === 0 ? <div className="actividad-empty" role="status"><span className="actividad-empty__icon"><SearchX aria-hidden="true" size={20} /></span><h3>Sin resultados</h3><p>No encontramos entradas que coincidan con los filtros aplicados.</p></div>
            : <>
              <p aria-live="polite" className="actividad-results">{events.length.toLocaleString('es-PE')} {events.length === 1 ? 'entrada encontrada' : 'entradas encontradas'}</p>
              <div className="actividad-table-wrap"><table className="actividad-table"><caption className="actividad-sr-only">Entradas recientes registradas, ordenadas de más nuevas a más antiguas</caption><thead><tr><th scope="col">Persona</th><th scope="col">Tipo</th><th scope="col">Fecha y hora (Lima)</th></tr></thead><tbody>
              {events.map((event) => <tr key={event.idIngreso}><td data-label="Persona"><strong>{event.nombre}</strong></td><td data-label="Tipo"><span className={`actividad-person-type actividad-person-type--${event.tipo.toLowerCase()}`}>{event.tipo === 'SOCIO' ? 'Socio' : 'Visitante'}</span></td><td data-label="Fecha y hora (Lima)"><time dateTime={event.fechaHora}>{dateTime.format(new Date(event.fechaHora))}</time></td></tr>)}
            </tbody></table></div>
            </>}
      </Card>
    </>}
  </div>
}
