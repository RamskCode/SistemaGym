import { useCallback, useEffect, useMemo, useState } from 'react'
import { Activity, ClipboardCheck, Dumbbell, FileWarning, ListChecks, LogIn, UserCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { consultarMisAsistencias, type AsistenciaLaboral } from '../api/asistenciasLaborales'
import { listarMisChecklists, listarMisReportesIncidencia, type ChecklistSalaRespuesta, type ReporteIncidenciaRespuesta } from '../api/checklists'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './trainer-dashboard.css'

const dateTime = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima' })
const limaDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' })

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'unauthenticated') return 'Tu sesión expiró. Inicia sesión nuevamente.'
  if (error instanceof ApiError && error.code === 'forbidden') return 'No tienes autorización para consultar estos datos.'
  if (error instanceof ApiError && error.code === 'network') return 'No se pudo conectar con el servidor. Comprueba tu conexión.'
  return 'No fue posible cargar tu jornada. Inténtalo nuevamente.'
}

function dateKey(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : limaDate.format(date)
}

function todayKey() { return limaDate.format(new Date()) }

function attendanceLabel(rows: AsistenciaLaboral[]) {
  const today = rows.filter((row) => row.estado === 'ACTIVA' && row.fechaLaboral === todayKey())
    .sort((a, b) => Date.parse(b.fechaEntradaUtc) - Date.parse(a.fechaEntradaUtc))
  if (!today.length) return 'Sin registro'
  return today[0].fechaSalidaUtc ? 'Entrada y salida registradas' : 'Entrada registrada'
}

export function TrainerDashboard() {
  const auth = useAuth()
  const [attendance, setAttendance] = useState<AsistenciaLaboral[]>([])
  const [checklists, setChecklists] = useState<ChecklistSalaRespuesta[]>([])
  const [reports, setReports] = useState<ReporteIncidenciaRespuesta[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const sessionVersion = auth.getSessionVersion()
    setLoading(true)
    setError('')
    try {
      const [attendancePage, ownChecklists, ownReports] = await Promise.all([
        consultarMisAsistencias(auth, { page: 0, size: 100, fechaDesde: todayKey(), fechaHasta: todayKey() }),
        listarMisChecklists(auth),
        listarMisReportesIncidencia(auth),
      ])
      if (auth.getSessionVersion() !== sessionVersion) return
      setAttendance(attendancePage.contenido)
      setChecklists(ownChecklists)
      setReports(ownReports)
    } catch (cause) {
      if (auth.getSessionVersion() === sessionVersion) setError(errorMessage(cause))
    } finally {
      if (auth.getSessionVersion() === sessionVersion) setLoading(false)
    }
  }, [auth])

  useEffect(() => { void load() }, [load])

  const todayChecklists = useMemo(() => checklists.filter((item) => dateKey(item.fechaHora) === todayKey()), [checklists])
  const problemCount = todayChecklists.reduce((sum, item) => sum + item.detalles.filter((detail) => detail.resultado === 'CON_PROBLEMA').length, 0)
  const pendingReports = reports.filter((item) => item.estado !== 'SOLUCIONADO').length
  const recentChecks = checklists.slice(0, 5)
  const metrics = [
    { label: 'Mi asistencia de hoy', value: attendanceLabel(attendance), Icon: UserCheck, detail: 'Registro de entrada y salida' },
    { label: 'Revisiones realizadas hoy', value: todayChecklists.length.toLocaleString('es-PE'), Icon: ClipboardCheck, detail: 'Checklists completados hoy' },
    { label: 'Incidencias sin resolver', value: pendingReports.toLocaleString('es-PE'), Icon: FileWarning, detail: 'Reportes propios pendientes o en revisión' },
    { label: 'Equipos con problema hoy', value: problemCount.toLocaleString('es-PE'), Icon: Activity, detail: 'Hallazgos de tus revisiones de hoy' },
  ]

  return <div className="dashboard trainer-dashboard">
    <PageTitle description="Consulta tus actividades y revisiones del gimnasio" eyebrow="PORTAL DEL ENTRENADOR" title="Mi jornada" />

    {error && <div className="trainer-dashboard__error" role="alert"><span>{error}</span><Button disabled={loading} onClick={() => void load()} type="button" variant="secondary">Reintentar</Button></div>}

    <section aria-label="Indicadores de mi jornada" aria-busy={loading} className="trainer-dashboard__metrics">
      {loading ? <p className="trainer-dashboard__state" role="status">Cargando tu jornada…</p>
        : error ? null : metrics.map(({ label, value, Icon, detail }) => <Card className="trainer-dashboard__metric" key={label}>
          <div className="trainer-dashboard__metric-heading"><span>{label}</span><span className="trainer-dashboard__metric-icon"><Icon aria-hidden="true" size={19} /></span></div>
          <strong>{value}</strong><small>{detail}</small>
        </Card>)}
    </section>

    <section aria-labelledby="trainer-quick-title" className="trainer-dashboard__quick-section">
      <h2 id="trainer-quick-title">Accesos rápidos</h2>
      <div className="trainer-dashboard__quick-links">
        <Link className="trainer-dashboard__quick-link" to="/mi-asistencia"><LogIn aria-hidden="true" size={19} /><span>Registrar mi asistencia</span></Link>
        <Link className="trainer-dashboard__quick-link" to="/entrenamiento"><Dumbbell aria-hidden="true" size={19} /><span>Revisar equipos</span></Link>
        <Link className="trainer-dashboard__quick-link" to="/mis-checklists"><ListChecks aria-hidden="true" size={19} /><span>Mis checklists</span></Link>
        <Link className="trainer-dashboard__quick-link" to="/mis-reportes"><FileWarning aria-hidden="true" size={19} /><span>Mis reportes</span></Link>
      </div>
    </section>

    <Card className="trainer-dashboard__recent" title="Actividad reciente">
      <p className="trainer-dashboard__note">Tus últimas revisiones registradas.</p>
      {loading ? <p className="trainer-dashboard__state" role="status">Cargando revisiones…</p>
        : error ? null : recentChecks.length === 0 ? <div className="trainer-dashboard__empty" role="status">Todavía no has registrado revisiones.</div>
          : <div className="trainer-dashboard__list">{recentChecks.map((checklist) => {
            const hasProblem = checklist.detalles.some((item) => item.resultado === 'CON_PROBLEMA')
            const result = hasProblem ? 'Con hallazgos' : 'Sin problemas'
            const names = checklist.detalles.slice(0, 2).map((item) => item.nombreElemento).join(', ')
            const summary = checklist.detalles.length > 2 ? `${names} y ${checklist.detalles.length - 2} más` : names || 'Revisión general'
            return <article className="trainer-dashboard__item" key={checklist.idChecklist}>
              <span aria-hidden="true" className={`trainer-dashboard__item-icon${hasProblem ? ' trainer-dashboard__item-icon--warning' : ''}`}><ClipboardCheck size={18} /></span>
              <div className="trainer-dashboard__item-main"><strong>{summary}</strong><span>{dateTime.format(new Date(checklist.fechaHora))}</span></div>
              <span className={`trainer-dashboard__status${hasProblem ? ' trainer-dashboard__status--warning' : ''}`}>{result}</span>
              <Link aria-label={`Ver detalle del checklist ${checklist.idChecklist}`} className="trainer-dashboard__detail-link" to={`/mis-checklists?checklist=${checklist.idChecklist}`}>Ver detalle</Link>
            </article>
          })}</div>}
      {!loading && !error && recentChecks.length > 0 && <Link className="trainer-dashboard__history-link" to="/mis-checklists">Ver mis checklists</Link>}
    </Card>
  </div>
}
