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

  const metrics = summary ? [
    { label: 'Entradas hoy', value: summary.ingresosHoy.toLocaleString('es-PE'), Icon: CalendarCheck2 },
    { label: 'Entradas esta semana', value: summary.ingresosSemana.reduce((sum, day) => sum + day.cantidad, 0).toLocaleString('es-PE'), Icon: Activity },
    { label: 'Socios nuevos este mes', value: summary.sociosNuevosMes.toLocaleString('es-PE'), Icon: UserRoundPlus },
    { label: 'Pagos confirmados este mes', value: money.format(summary.ingresosMes), Icon: BadgeDollarSign },
    { label: 'Ingresos cobrados hoy', value: money.format(summary.ingresosCobradosHoy), Icon: BadgeDollarSign },
  ] : []

  return <div className="dashboard actividad-page">
    <PageTitle eyebrow="OPERACIÓN DEL GIMNASIO" title="Actividad" description="Entradas recientes e indicadores operativos del gimnasio." />
    <aside className="actividad-scope"><span className="actividad-scope__mark" aria-hidden="true" /><p>Vista resumida: las entradas recientes no representan el historial completo.</p></aside>
    {error && <div className="actividad-error" role="alert"><span>{error}</span><Button onClick={() => setRetry((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></div>}
    {loading ? <p className="actividad-state" role="status">Cargando actividad…</p> : !error && summary && <>
      <section aria-label="Indicadores de actividad" aria-busy={loading} className="actividad-metrics">
        {metrics.map(({ label, value, Icon }) => <Card className="actividad-metric" key={label}>
          <div className="actividad-metric__top"><span>{label}</span><span className="actividad-metric__icon"><Icon aria-hidden="true" size={19} strokeWidth={1.8} /></span></div>
          <strong>{value}</strong>
        </Card>)}
      </section>
      <Card className="actividad-card" title="Entradas recientes">
        <p className="actividad-note">Se muestran las entradas más recientes disponibles.</p>
        <div className="actividad-filters">
          <label className="actividad-search" htmlFor="actividad-search">Buscar persona<input id="actividad-search" onChange={(event) => setQuery(event.target.value)} placeholder="Nombre" type="search" value={query} /></label>
          <label htmlFor="actividad-tipo">Tipo<select id="actividad-tipo" onChange={(event) => setTipo(event.target.value as TipoFiltro)} value={tipo}><option value="TODOS">Todos</option><option value="SOCIO">Socio</option><option value="VISITANTE">Visitante</option></select></label>
          <label htmlFor="actividad-desde">Desde<input id="actividad-desde" onChange={(event) => setDesde(event.target.value)} type="date" value={desde} /></label>
          <label htmlFor="actividad-hasta">Hasta<input id="actividad-hasta" onChange={(event) => setHasta(event.target.value)} type="date" value={hasta} /></label>
          {hasFilters && events.length > 0 && <Button onClick={clearFilters} type="button" variant="secondary">Limpiar filtros</Button>}
        </div>
        {summary.actividadReciente.length === 0 ? <div className="actividad-empty" role="status"><span className="actividad-empty__icon"><UsersRound aria-hidden="true" size={20} /></span><h3>Sin entradas recientes</h3><p>Cuando se registren entradas, aparecerán aquí.</p></div>
          : events.length === 0 ? <div className="actividad-empty" role="status"><span className="actividad-empty__icon"><SearchX aria-hidden="true" size={20} /></span><h3>Sin resultados</h3><p>No encontramos entradas para estos filtros.</p>{hasFilters && <Button onClick={clearFilters} type="button" variant="secondary">Limpiar filtros</Button>}</div>
            : <div className="actividad-table-wrap"><table className="actividad-table"><caption className="visually-hidden">Entradas recientes registradas, ordenadas de más nuevas a más antiguas</caption><thead><tr><th scope="col">Persona</th><th scope="col">Tipo</th><th scope="col">Fecha y hora (Lima)</th></tr></thead><tbody>
              {events.map((event) => <tr key={event.idIngreso}><td data-label="Persona">{event.nombre}</td><td data-label="Tipo">{event.tipo === 'SOCIO' ? 'Socio' : 'Visitante'}</td><td data-label="Fecha y hora (Lima)">{dateTime.format(new Date(event.fechaHora))}</td></tr>)}
            </tbody></table></div>}
      </Card>
    </>}
  </div>
}
