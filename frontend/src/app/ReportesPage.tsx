import { useEffect, useMemo, useState } from 'react'
import { CalendarClock, CreditCard, DoorOpen, FileSpreadsheet, FileText, RefreshCw, TrendingUp, UserCheck, UserPlus, Users } from 'lucide-react'
import { ApiError } from '../api/http'
import { obtenerDetalleReporte, obtenerPlanesReporte, obtenerResumenReportes, type PlanReporte, type ReporteFila, type ReporteResumen, type ReporteTipo } from '../api/reportes'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import { downloadXlsx } from '../utils/xlsxExport'
import './reportes.css'

const tipos: { id: ReporteTipo; label: string }[] = [
  { id: 'pagos', label: 'Pagos confirmados' }, { id: 'socios', label: 'Socios registrados' },
  { id: 'membresias', label: 'Membresías' }, { id: 'ingresos', label: 'Entradas' },
]
const currency = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })
const defaultPeriod = () => {
  const now = new Date()
  const date = new Date(now.getFullYear(), now.getMonth(), 1)
  return [date.toLocaleDateString('sv-SE'), now.toLocaleDateString('sv-SE')]
}

function MiniChart({ title, points, money = false }: { title: string; points: { etiqueta: string; cantidad: number; monto: number }[]; money?: boolean }) {
  const vals = points.map(p => money ? p.monto : p.cantidad)
  const max = Math.max(1, ...vals)
  const width = Math.max(336, points.length * 76 + 64)
  const left = 52
  const right = width - 16
  const top = 18
  const base = 156
  const chartHeight = base - top
  const tickValues = [max, max / 2, 0]
  const labelFor = (value: number) => money
    ? new Intl.NumberFormat('es-PE', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
    : new Intl.NumberFormat('es-PE', { maximumFractionDigits: 0 }).format(value)
  return <Card className="reportes-chart" title={title} action={<span className="reportes-chart__unit">{money ? 'Soles (PEN)' : 'Registros'}</span>}>
    {points.length === 0 ? <div className="reportes-empty"><span className="reportes-empty__icon"><TrendingUp size={19} aria-hidden="true" /></span><strong>Sin datos en este período</strong><span>Prueba con otro rango de fechas o plan.</span></div> : <div className="reportes-chart__scroll" tabIndex={points.length > 3 ? 0 : undefined} aria-label={points.length > 3 ? 'Gráfico desplazable horizontalmente' : undefined}><svg aria-label={title} className="reportes-chart__svg" role="img" preserveAspectRatio="none" viewBox={`0 0 ${width} 206`}>
      {tickValues.map((tick, index) => {
        const y = top + (chartHeight / 2) * index
        return <g key={`${tick}-${index}`}><line x1={left} y1={y} x2={right} y2={y} className="reportes-chart__gridline" /><text x={left - 9} y={y + 4} textAnchor="end" className="reportes-chart__tick">{labelFor(tick)}</text></g>
      })}
      {points.map((point, index) => {
        const value = vals[index] ?? 0
        const slot = (right - left) / points.length
        const barWidth = Math.min(32, slot * .42)
        const x = left + slot * index + (slot - barWidth) / 2
        const barHeight = value > 0 ? Math.max(3, (value / max) * chartHeight) : 0
        const y = base - barHeight
        const [year, month] = point.etiqueta.split('-')
        const monthLabel = year && month ? `${month}/${year.slice(-2)}` : point.etiqueta
        return <g key={`${point.etiqueta}-${index}`}>
          <rect x={x} y={y} width={barWidth} height={barHeight} rx="4" className="reportes-chart__bar"><title>{`${point.etiqueta}: ${money ? currency.format(value) : value}`}</title></rect>
          <text x={x + barWidth / 2} y={Math.max(top + 12, y - 7)} textAnchor="middle" className="reportes-chart__value">{labelFor(value)}</text>
          <text x={x + barWidth / 2} y="181" textAnchor="middle" className="reportes-chart__label">{monthLabel}</text>
        </g>
      })}
    </svg></div>}
  </Card>
}

export function ReportesPage() {
  const auth = useAuth()
  const [initialPeriod] = useState(defaultPeriod)
  const [desde, hasta] = initialPeriod
  const [start, setStart] = useState(desde)
  const [end, setEnd] = useState(hasta)
  const [planId, setPlanId] = useState('')
  const [summary, setSummary] = useState<ReporteResumen | null>(null)
  const [plans, setPlans] = useState<PlanReporte[]>([])
  const [rows, setRows] = useState<ReporteFila[]>([])
  const [type, setType] = useState<ReporteTipo>('pagos')
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterError, setFilterError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    if (!start || !end || start > end) { setFilterError('La fecha inicial debe ser anterior o igual a la fecha final.'); setLoading(false); return }
    if ((new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime()) / 86400000 > 730) { setFilterError('El período no puede superar dos años.'); setLoading(false); return }
    setFilterError(''); setLoading(true); setError('')
    let active = true
    Promise.all([obtenerResumenReportes(auth, start, end, planId ? Number(planId) : undefined), obtenerDetalleReporte(auth, type, start, end, page, planId ? Number(planId) : undefined), obtenerPlanesReporte(auth)])
      .then(([data, detail, planOptions]) => { if (active) { setSummary(data); setRows(detail); setPlans(planOptions) } })
      .catch((cause: unknown) => { if (active) setError(cause instanceof ApiError ? cause.message : 'No se pudieron cargar los reportes.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [auth, start, end, planId, type, page, retryKey])

  const filteredRows = useMemo(() => rows.filter(row => `${row.persona} ${row.detalle} ${row.estado} ${row.tipo}`.toLocaleLowerCase('es').includes(search.toLocaleLowerCase('es'))), [rows, search])
  const period = `${start} — ${end}`
  async function exportXlsx() {
    downloadXlsx(`sistemagym-${type}-${start}-${end}.xlsx`, [
      ['SistemaGym'], [`Reporte: ${tipos.find(item => item.id === type)?.label}`], [`Período: ${period}`], [`Página: ${page + 1}`], [`Generado: ${new Date().toLocaleString('es-PE')}`], [],
      ['Tipo', 'Persona / referencia', 'Detalle', 'Estado', 'Monto', 'Fecha'],
      ...filteredRows.map(row => [row.tipo, row.persona, row.detalle, row.estado, row.monto ?? '', new Date(row.fecha).toLocaleString('es-PE')]),
    ])
  }
  async function exportPdf() {
    const [{ jsPDF }, { default: autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')])
    const pdf = new jsPDF({ orientation: 'landscape' }); pdf.setFontSize(16); pdf.text('SistemaGym · Reportes', 14, 17); pdf.setFontSize(10)
    pdf.text(`${tipos.find(item => item.id === type)?.label} | Período: ${period} | Página ${page + 1} | Generado: ${new Date().toLocaleString('es-PE')}`, 14, 25)
    autoTable(pdf, { startY: 32, head: [['Tipo', 'Persona', 'Detalle', 'Estado', 'Monto', 'Fecha']], body: filteredRows.map(row => [row.tipo, row.persona, row.detalle, row.estado, row.monto == null ? '—' : currency.format(row.monto), new Date(row.fecha).toLocaleString('es-PE')]) })
    pdf.save(`sistemagym-${type}-${start}-${end}.pdf`)
  }

  return <main className="dashboard reportes-page">
    <PageTitle eyebrow="ANÁLISIS Y SEGUIMIENTO" title="Reportes" description="Consulta indicadores operativos y financieros basados en los registros del gimnasio." />
    <Card className="reportes-filter" title="Período del reporte">
      <div className="reportes-filter__grid">
        <label>Desde<input type="date" value={start} onChange={e => setStart(e.target.value)} /></label>
        <label>Hasta<input type="date" value={end} onChange={e => setEnd(e.target.value)} /></label>
        <label>Mes y año<input aria-label="Mes y año" type="month" value={start.slice(0, 7) === end.slice(0, 7) ? start.slice(0, 7) : ''} onChange={e => { if (!e.target.value) return; const [year, month] = e.target.value.split('-').map(Number); setStart(`${e.target.value}-01`); setEnd(new Date(year, month, 0).toLocaleDateString('sv-SE')) }} /></label>
        <label>Plan de membresía (opcional)<select value={planId} onChange={e => { setPlanId(e.target.value); setPage(0) }}><option value="">Todos los planes</option>{plans.map(item => <option key={item.id} value={item.id}>{item.nombre}</option>)}</select></label>
        <Button type="button" variant="secondary" onClick={() => { setStart(desde); setEnd(hasta); setPlanId(''); setPage(0) }}><RefreshCw size={16} /> Restablecer</Button>
      </div>
      {filterError && <p className="reportes-error" role="alert">{filterError}</p>}
    </Card>
    {error && <div className="reportes-error" role="alert">{error} <Button variant="secondary" type="button" onClick={() => setRetryKey(key => key + 1)}>Reintentar</Button></div>}
    {loading && <p role="status" className="reportes-status">Cargando reportes…</p>}
    {!loading && summary && !filterError && <>
      <section className="reportes-kpis" aria-label="Indicadores del período">
        {[
          { label: 'Ingresos confirmados', value: currency.format(summary.ingresosConfirmados), note: 'En el período seleccionado', Icon: TrendingUp },
          { label: 'Pagos confirmados', value: summary.pagosConfirmados, note: 'En el período seleccionado', Icon: CreditCard },
          { label: 'Socios nuevos', value: summary.sociosNuevos, note: 'En el período seleccionado', Icon: UserPlus },
          { label: 'Membresías activas', value: summary.membresiasActivas, note: 'Estado actual', Icon: Users },
          { label: 'Membresías vencidas', value: summary.membresiasVencidas, note: 'Estado actual', Icon: CalendarClock },
          { label: 'Próximas a vencer', value: summary.membresiasProximasAVencer, note: 'Dentro de 7 días', Icon: CalendarClock },
          { label: 'Entradas de socios', value: summary.ingresosSocios, note: 'En el período seleccionado', Icon: UserCheck },
          { label: 'Entradas de visitantes', value: summary.ingresosVisitantes, note: 'En el período seleccionado', Icon: DoorOpen },
        ].map(({ label, value, note, Icon }) => <Card className="reportes-kpi" key={label}>
          <div className="reportes-kpi__top"><span>{label}</span><span className="reportes-kpi__icon"><Icon size={18} aria-hidden="true" /></span></div>
          <strong className={typeof value === 'string' ? 'reportes-kpi__value--currency' : undefined}>{value}</strong><small>{note}</small>
        </Card>)}
      </section>
      <section className="reportes-charts" aria-label="Tendencias del período">
        <MiniChart title="Ingresos económicos por mes" points={summary.pagosPorFecha} money />
        <MiniChart title="Pagos confirmados por mes" points={summary.pagosPorFecha} />
        <MiniChart title="Socios nuevos por mes" points={summary.sociosPorFecha} />
        <MiniChart title="Entradas al gimnasio por mes" points={summary.ingresosPorFecha} />
      </section>
      <Card className="reportes-plans-card" title="Membresías por plan" action={<span className="reportes-chart__unit">Período seleccionado</span>}>
        {summary.membresiasPorPlan.length === 0 ? <div className="reportes-empty"><span className="reportes-empty__icon"><Users size={19} aria-hidden="true" /></span><strong>Sin membresías en este período</strong><span>La distribución aparecerá cuando haya registros.</span></div> : <ul className="reportes-plan-list">{summary.membresiasPorPlan.map(p => {
          const maxPlan = Math.max(...summary.membresiasPorPlan.map(item => item.cantidad), 1)
          return <li key={p.etiqueta}><div className="reportes-plan-list__heading"><span>{p.etiqueta}</span><strong>{p.cantidad}</strong></div><span className="reportes-plan-list__track" aria-hidden="true"><span style={{ width: `${(p.cantidad / maxPlan) * 100}%` }} /></span></li>
        })}</ul>}
      </Card>
      <Card className="reportes-table-card" title="Detalle del reporte">
        <p className="reportes-table-description">Consulta los registros del período y exporta la página visible.</p>
        <div className="reportes-table-toolbar"><label>Tipo de reporte<select value={type} onChange={e => { setType(e.target.value as ReporteTipo); setPage(0) }}>{tipos.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label><label>Buscar en esta página<input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Persona, detalle o estado" /></label><div className="reportes-export"><Button type="button" variant="secondary" onClick={exportPdf}><FileText size={16} /> PDF</Button><Button type="button" variant="secondary" onClick={exportXlsx}><FileSpreadsheet size={16} /> Excel</Button></div></div>
        <div className="reportes-table-scroll"><table><caption className="reportes-sr-only">Registros de {tipos.find(item => item.id === type)?.label ?? 'reportes'}, página {page + 1}</caption><thead><tr><th scope="col">Tipo</th><th scope="col">Persona / referencia</th><th scope="col">Detalle</th><th scope="col">Estado</th><th scope="col" className="reportes-th-amount">Monto</th><th scope="col">Fecha</th></tr></thead><tbody>{filteredRows.map(row => <tr key={`${row.tipo}-${row.id}`}><td data-label="Tipo">{row.tipo}</td><td data-label="Persona / referencia">{row.persona || '—'}</td><td data-label="Detalle">{row.detalle || '—'}</td><td data-label="Estado"><span className={`reportes-state ${row.estado.toLocaleLowerCase('es').replace(/[^a-z0-9]+/g, '-')}`}>{row.estado || 'Sin estado'}</span></td><td data-label="Monto" className="reportes-amount">{row.monto == null ? '—' : currency.format(row.monto)}</td><td data-label="Fecha">{new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(row.fecha))}</td></tr>)}</tbody></table></div>
        {filteredRows.length === 0 && <div className="reportes-table-empty"><strong>{search ? 'No hay coincidencias' : 'No hay registros en esta página'}</strong><span>{search ? 'Prueba con otros términos de búsqueda.' : 'No se encontraron registros para el tipo y período seleccionados.'}</span></div>}
        <div className="reportes-pagination"><span>Página {page + 1} · hasta 25 registros</span><Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>Anterior</Button><Button type="button" variant="secondary" disabled={rows.length < 25} onClick={() => setPage(p => p + 1)}>Siguiente</Button></div>
      </Card>
    </>}
  </main>
}
