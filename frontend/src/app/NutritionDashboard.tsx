import { useCallback, useEffect, useState } from 'react'
import { CalendarDays, ClipboardList, Search, UserRound, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/http'
import { consultarResumenNutricion, type ResumenNutricion } from '../api/evaluacionesNutricionales'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import { formatPersonName } from '../utils/personName'
import './nutrition-dashboard.css'

const dateTimeLima = new Intl.DateTimeFormat('es-PE', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
})

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'unauthenticated') return 'Tu sesión expiró. Inicia sesión nuevamente.'
  if (error instanceof ApiError && error.code === 'forbidden') return 'No tienes autorización para consultar estos datos.'
  if (error instanceof ApiError && error.code === 'network') return 'No se pudo conectar con el servidor. Comprueba tu conexión.'
  return 'No fue posible cargar el panel de nutrición. Inténtalo nuevamente.'
}

export function NutritionDashboard() {
  const auth = useAuth()
  const [summary, setSummary] = useState<ResumenNutricion | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const sessionVersion = auth.getSessionVersion()
    setLoading(true)
    setError('')
    try {
      const result = await consultarResumenNutricion(auth)
      if (auth.getSessionVersion() === sessionVersion) setSummary(result)
    } catch (cause) {
      if (auth.getSessionVersion() === sessionVersion) setError(errorMessage(cause))
    } finally {
      if (auth.getSessionVersion() === sessionVersion) setLoading(false)
    }
  }, [auth])

  useEffect(() => { void load() }, [load])

  const metrics = summary ? [
    { label: 'Socios disponibles', value: summary.sociosDisponibles, detail: 'Activos para atención nutricional', Icon: UsersRound },
    { label: 'Evaluaciones este mes', value: summary.evaluacionesEsteMes, detail: 'Registros del mes en curso', Icon: ClipboardList },
    { label: 'Socios evaluados este mes', value: summary.sociosEvaluadosEsteMes, detail: 'Personas únicas', Icon: UserRound },
    { label: 'Evaluaciones últimos 7 días', value: summary.evaluacionesUltimosSieteDias, detail: 'Registros de los últimos siete días', Icon: CalendarDays },
  ] : []

  return <div className="dashboard nutrition-dashboard">
    <PageTitle description="Da seguimiento a las evaluaciones y continúa la atención de cada socio." eyebrow="PORTAL DEL NUTRICIONISTA" title="Panel de nutrición" />

    {error && <div className="nutrition-dashboard__error" role="alert"><span>{error}</span><Button disabled={loading} onClick={() => void load()} type="button" variant="secondary">Reintentar</Button></div>}

    <section aria-label="Indicadores de nutrición" aria-busy={loading} className="nutrition-dashboard__metrics">
      {loading ? <p className="nutrition-dashboard__state" role="status">Cargando indicadores…</p>
        : error ? null : metrics.map(({ label, value, detail, Icon }) => <Card className="nutrition-dashboard__metric" key={label}>
          <div className="nutrition-dashboard__metric-heading"><span>{label}</span><span className="nutrition-dashboard__metric-icon"><Icon aria-hidden="true" size={19} /></span></div>
          <strong>{value.toLocaleString('es-PE')}</strong><small>{detail}</small>
        </Card>)}
    </section>

    <section aria-labelledby="nutrition-dashboard-quick-title" className="nutrition-dashboard__quick-section">
      <h2 id="nutrition-dashboard-quick-title">Accesos rápidos</h2>
      <div className="nutrition-dashboard__quick-links">
        <Link className="nutrition-dashboard__quick-link" to="/nutricion"><Search aria-hidden="true" size={19} /><span>Buscar socio</span></Link>
        <Link className="nutrition-dashboard__quick-link" to="/nutricion?accion=nueva"><ClipboardList aria-hidden="true" size={19} /><span>Nueva evaluación</span></Link>
      </div>
    </section>

    <Card className="nutrition-dashboard__recent" title="Evaluaciones recientes">
      <p className="nutrition-dashboard__note">Últimos registros nutricionales.</p>
      {loading ? <p className="nutrition-dashboard__state" role="status">Cargando evaluaciones…</p>
        : error ? null : !summary?.evaluacionesRecientes.length
          ? <div className="nutrition-dashboard__empty" role="status">Todavía no hay evaluaciones nutricionales registradas.</div>
          : <div className="nutrition-dashboard__list">{summary.evaluacionesRecientes.map((evaluation) => <article className="nutrition-dashboard__item" key={evaluation.idEvaluacion}>
            <span aria-hidden="true" className="nutrition-dashboard__item-icon"><UserRound size={18} /></span>
            <div className="nutrition-dashboard__item-main"><strong>{formatPersonName(evaluation.nombres)} {formatPersonName(evaluation.apellidos)}</strong><span>{evaluation.codigoSocio} · {dateTimeLima.format(new Date(evaluation.fechaEvaluacion))}</span></div>
            <Link aria-label={`Ver historial de ${evaluation.nombres} ${evaluation.apellidos}`} className="nutrition-dashboard__detail-link" to={`/nutricion?vista=historial&socio=${evaluation.idSocio}`}>Ver historial</Link>
          </article>)}</div>}
    </Card>
  </div>
}
