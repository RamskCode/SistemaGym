import { useCallback, useEffect, useState } from 'react'
import { BadgeDollarSign, CalendarCheck2, UserRoundPlus, UsersRound } from 'lucide-react'
import { ApiError } from '../api/http'
import { getDashboardSummary, type DashboardSummary } from '../api/dashboard'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './dashboard.css'

const money = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })
const dateTime = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima' })
const dayLabel = new Intl.DateTimeFormat('es-PE', { weekday: 'short', day: 'numeric', timeZone: 'UTC' })

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'unauthenticated') return 'Tu sesión expiró. Inicia sesión nuevamente.'
  if (error instanceof ApiError && error.code === 'forbidden') return 'No tienes autorización para consultar este resumen.'
  if (error instanceof ApiError && error.code === 'network') return 'No se pudo conectar con el servidor. Comprueba tu conexión.'
  return 'No fue posible cargar los datos del panel. Inténtalo nuevamente.'
}

export function DashboardPage() {
  const auth = useAuth()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setSummary(await getDashboardSummary(auth))
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setLoading(false)
    }
  }, [auth])

  useEffect(() => { void load() }, [load])

  const stats = summary ? [
    { label: 'Socios activos', detail: 'Estado activo', value: summary.sociosActivos.toLocaleString('es-PE'), icon: UsersRound, tone: 'lime' },
    { label: 'Ingresos cobrados', detail: 'Pagos confirmados este mes', value: money.format(summary.ingresosMes), icon: BadgeDollarSign, tone: 'blue' },
    { label: 'Entradas hoy', detail: 'Accesos registrados', value: summary.ingresosHoy.toLocaleString('es-PE'), icon: CalendarCheck2, tone: 'violet' },
    { label: 'Nuevos socios', detail: 'Registros de este mes', value: summary.sociosNuevosMes.toLocaleString('es-PE'), icon: UserRoundPlus, tone: 'orange' },
  ] : []
  const membershipTotal = summary
    ? summary.membresias.activas + summary.membresias.porPagar + summary.membresias.vencidas + summary.membresias.canceladas
    : 0
  const maxDaily = Math.max(0, ...(summary?.ingresosSemana.map((item) => item.cantidad) ?? []))
  const membershipColors = ['#27832a', '#8abc72', '#d39a3b', '#aab3aa']
  const membershipValues = summary ? [summary.membresias.activas, summary.membresias.porPagar, summary.membresias.vencidas, summary.membresias.canceladas] : []
  let membershipOffset = 0
  const membershipGradient = membershipValues.map((value, index) => {
    const start = membershipOffset / Math.max(1, membershipTotal) * 100
    membershipOffset += value
    return `${membershipColors[index]} ${start}% ${membershipOffset / Math.max(1, membershipTotal) * 100}%`
  }).join(', ')

  return (
    <div className="dashboard dashboard-page">
      <PageTitle description="Un vistazo a la actividad y el estado del gimnasio." eyebrow={new Intl.DateTimeFormat('es-PE', { dateStyle: 'full', timeZone: 'America/Lima' }).format(new Date())} title="Inicio" />
      {error && <div className="dashboard-error" role="alert"><span>{error}</span><Button onClick={() => void load()} type="button" variant="secondary">Reintentar</Button></div>}

      <section aria-label="Indicadores del gimnasio" aria-busy={loading} className="metrics-grid">
        {loading ? <p className="dashboard-state" role="status">Cargando indicadores…</p>
          : error ? null : stats.map(({ label, detail, value, icon: Icon, tone }) => (
            <Card className="metric-card" key={label}>
              <div className="metric-card__top"><span className={`metric-icon metric-icon--${tone}`}><Icon size={20} strokeWidth={1.9} /></span></div>
              <p className="metric-card__label">{label}</p><div className="metric-card__value-row"><strong>{value}</strong></div><p className="metric-card__detail">{detail}</p>
            </Card>
          ))}
      </section>

      {!loading && !error && summary && <>
        <section aria-label="Asistencia y membresías" className="dashboard-grid">
          <Card className="week-card" title="Entradas — últimos 7 días">
            <p className="week-card__note">Accesos registrados por día · hora local del gimnasio</p>
            {summary.ingresosSemana.length === 0 || summary.ingresosSemana.every(({ cantidad }) => cantidad === 0)
              ? <p className="dashboard-empty">Aún no hay entradas en este periodo.</p>
              : <div aria-label={`Entradas por día: ${summary.ingresosSemana.map(({ fecha, cantidad }) => `${dayLabel.format(new Date(`${fecha}T12:00:00Z`))}, ${cantidad}`).join('; ')}`} className="week-chart" role="img">
                {summary.ingresosSemana.map(({ fecha, cantidad }) => <div className="week-chart__column" key={fecha}>
                  <span className="week-chart__value">{cantidad}</span>
                  <div className="week-chart__track"><span style={{ height: `${maxDaily ? Math.max(cantidad ? 5 : 0, cantidad / maxDaily * 100) : 0}%` }} /></div>
                  <span className="week-chart__day">{dayLabel.format(new Date(`${fecha}T12:00:00Z`))}</span>
                </div>)}
              </div>}
          </Card>
          <Card className="membership-card" title="Resumen de membresías">
            {membershipTotal === 0 ? <p className="dashboard-empty">Todavía no hay membresías registradas.</p> : <div className="membership-summary">
              <div className="membership-ring" aria-label={`${membershipTotal} membresías en total`} role="img" style={{ background: `conic-gradient(${membershipGradient})` }}><span>{membershipTotal}<small><br />total</small></span></div>
              <div className="membership-legend">
                {([
                  ['Activas', summary.membresias.activas],
                  ['Por pagar', summary.membresias.porPagar],
                  ['Vencidas', summary.membresias.vencidas],
                  ['Canceladas', summary.membresias.canceladas],
                ] as const).map(([label, count], index) => <p key={label}><span className={`legend-dot legend-dot--${index}`} />{label}<strong>{count}</strong></p>)}
              </div>
            </div>}
          </Card>
        </section>

        <section aria-label="Actividad reciente" className="dashboard-recent">
          <Card className="activity-card" title="Entradas recientes">
            {summary.actividadReciente.length ? <div className="table-wrap">
              <table className="activity-table"><caption className="dashboard-sr-only">Entradas recientes registradas</caption><thead><tr><th scope="col">PERSONA</th><th scope="col">TIPO</th><th scope="col">FECHA Y HORA</th></tr></thead>
                <tbody>{summary.actividadReciente.map((row) => <tr key={row.idIngreso}>
                  <td data-label="Persona"><div className="person-cell"><span className="person-avatar" aria-hidden="true">{row.nombre.trim().charAt(0).toLocaleUpperCase('es-PE')}</span><strong>{row.nombre}</strong></div></td>
                  <td data-label="Tipo"><span className="type-label">{row.tipo === 'SOCIO' ? 'Socio' : 'Visitante'}</span></td><td className="time-cell" data-label="Fecha y hora">{dateTime.format(new Date(row.fechaHora))}</td>
                </tr>)}</tbody>
              </table>
            </div> : <p className="dashboard-empty">Todavía no hay actividad registrada.</p>}
          </Card>
        </section>
      </>}
    </div>
  )
}
