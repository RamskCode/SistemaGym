import { useCallback, useEffect, useState } from 'react'
import { BadgeDollarSign, CalendarCheck2, Clock3, Dumbbell, UserRoundCheck, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/http'
import { getDashboardSummary, type DashboardSummary } from '../api/dashboard'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './reception-dashboard.css'

const money = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })
const dateTime = new Intl.DateTimeFormat('es-PE', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
})

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'unauthenticated') return 'Tu sesión expiró. Inicia sesión nuevamente.'
  if (error instanceof ApiError && error.code === 'forbidden') return 'No tienes autorización para consultar el resumen.'
  if (error instanceof ApiError && error.code === 'network') return 'No se pudo conectar con el servidor. Comprueba tu conexión.'
  return 'No fue posible cargar el resumen del día. Inténtalo nuevamente.'
}

export function ReceptionDashboard() {
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

  const metrics = summary ? [
    { label: 'Entradas hoy', value: summary.ingresosHoy.toLocaleString('es-PE'), Icon: UsersRound },
    { label: 'Cobrado hoy', value: money.format(summary.ingresosCobradosHoy), Icon: BadgeDollarSign },
    { label: 'Con membresía', value: summary.ingresosMembresiaHoy.toLocaleString('es-PE'), Icon: UserRoundCheck },
    { label: 'Con pase diario', value: summary.ingresosPaseDiarioHoy.toLocaleString('es-PE'), Icon: CalendarCheck2 },
  ] : []

  return <div className="dashboard reception-dashboard">
    <PageTitle description="Accesos, cobros y actividad del gimnasio" eyebrow="RECEPCIÓN" title="Resumen del día" />

    {error && <div className="reception-dashboard__error" role="alert">
      <span>{error}</span><Button onClick={() => void load()} type="button" variant="secondary">Reintentar</Button>
    </div>}

    <section aria-label="Indicadores del día" aria-busy={loading} className="reception-dashboard__metrics">
      {loading ? <p className="reception-dashboard__state" role="status">Cargando resumen…</p>
        : error ? null : metrics.map(({ label, value, Icon }) => <Card className="reception-dashboard__metric" key={label}>
          <div className="reception-dashboard__metric-heading"><span>{label}</span><span className="reception-dashboard__metric-icon"><Icon aria-hidden="true" size={19} strokeWidth={1.8} /></span></div>
          <strong>{value}</strong>
        </Card>)}
    </section>

    <section aria-labelledby="recepcion-quick-title" className="reception-dashboard__quick-section">
      <h2 id="recepcion-quick-title">Accesos rápidos</h2>
      <div className="reception-dashboard__quick-links">
        <Link className="reception-dashboard__quick-link" to="/asistencia?seccion=ingresos&modalidad=MEMBRESIA">
          <UserRoundCheck aria-hidden="true" size={19} /><span>Ingreso con membresía</span>
        </Link>
        <Link className="reception-dashboard__quick-link" to="/asistencia?seccion=ingresos&modalidad=PASE_DIARIO">
          <BadgeDollarSign aria-hidden="true" size={19} /><span>Pase diario</span>
        </Link>
        <Link className="reception-dashboard__quick-link" to="/asistencia?seccion=laboral">
          <Dumbbell aria-hidden="true" size={19} /><span>Asistencia laboral</span>
        </Link>
        <a className="reception-dashboard__quick-link" href="#movimientos">
          <Clock3 aria-hidden="true" size={19} /><span>Ver movimientos</span>
        </a>
      </div>
    </section>

    <Card className="reception-dashboard__recent" title="Últimos ingresos" id="movimientos">
      <p className="reception-dashboard__note">Los accesos más recientes registrados.</p>
      {loading ? <p className="reception-dashboard__state" role="status">Cargando ingresos…</p>
        : error ? null : !summary?.actividadReciente.length
          ? <div className="reception-dashboard__empty" role="status">
            <span className="reception-dashboard__empty-icon"><UsersRound aria-hidden="true" size={20} /></span>
            <h3>Aún no hay ingresos</h3><p>Cuando se registre un acceso, aparecerá aquí.</p>
          </div>
          : <div className="reception-dashboard__table-wrap"><table className="reception-dashboard__table">
            <caption className="reception-dashboard__sr-only">Últimos ingresos, del más reciente al más antiguo</caption>
            <thead><tr><th scope="col">Persona</th><th scope="col">Tipo de acceso</th><th scope="col">Fecha y hora (Lima)</th></tr></thead>
            <tbody>{summary.actividadReciente.map((entry) => <tr key={entry.idIngreso}>
              <td data-label="Persona">{entry.nombre}</td>
              <td data-label="Tipo de acceso">{entry.tipo === 'SOCIO' ? 'Con membresía' : 'Pase diario'}</td>
              <td data-label="Fecha y hora (Lima)">{dateTime.format(new Date(entry.fechaHora))}</td>
            </tr>)}</tbody>
          </table></div>}
    </Card>
  </div>
}
