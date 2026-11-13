import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { DoorOpen, Search } from 'lucide-react'
import { ApiError } from '../api/http'
import { obtenerHistorialIngresosRecepcion, type PaginaHistorialIngresos } from '../api/reportes'
import type { AuthContextValue } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

const PAGE_SIZE = 25

function hoyEnLima() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const part = (name: string) => parts.find((item) => item.type === name)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function fechaHoraLima(value: string) {
  const parsed = new Date(`${value}-05:00`)
  return Number.isNaN(parsed.getTime()) ? 'Fecha no disponible' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(parsed)
}

function errorText(reason: unknown) {
  return reason instanceof ApiError && reason.status === 403
    ? 'No tienes autorización para consultar el historial de ingresos.'
    : 'No se pudo cargar el historial de ingresos. Intenta nuevamente.'
}

export function IngresosClientesHistorialRecepcion({ auth }: { auth: AuthContextValue }) {
  const today = hoyEnLima()
  const [from, setFrom] = useState(today)
  const [to, setTo] = useState(today)
  const [search, setSearch] = useState('')
  const [modality, setModality] = useState('')
  const [filters, setFilters] = useState({ from: today, to: today, search: '', modality: '' })
  const [pageNumber, setPageNumber] = useState(0)
  const [result, setResult] = useState<PaginaHistorialIngresos | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async (page: number) => {
    setLoading(true); setError('')
    try {
      const data = await obtenerHistorialIngresosRecepcion(auth, {
        desde: filters.from, hasta: filters.to, pagina: page, tamano: PAGE_SIZE,
        buscar: filters.search, modalidad: filters.modality,
      })
      setResult(data); setPageNumber(data.pagina)
    } catch (reason) { setError(errorText(reason)) }
    finally { setLoading(false) }
  }, [auth, filters])

  useEffect(() => { void load(0) }, [load])

  function apply(event: FormEvent) {
    event.preventDefault()
    if (!from || !to || from > to) return
    setPageNumber(0)
    setFilters({ from, to, search: search.trim(), modality })
  }

  function clear() {
    setFrom(today); setTo(today); setSearch(''); setModality(''); setPageNumber(0)
    setFilters({ from: today, to: today, search: '', modality: '' })
  }

  return <div className="ingresos-supervision">
    <header className="ingresos-heading"><div><p className="ingresos-eyebrow">CONSULTA · ACCESOS</p><h2>Historial de ingresos</h2><p>Consulta los accesos registrados hoy o en fechas anteriores. Solo lectura.</p></div></header>
    <Card className="asistencia-card ingresos-history-card" title="Ingresos registrados">
      <form className="ingresos-toolbar ingresos-toolbar--reception" onSubmit={apply}>
        <label className="ingresos-search"><span className="asistencia-sr-only">Buscar por persona o documento</span><Search aria-hidden="true" size={17} /><input aria-label="Buscar persona o documento" maxLength={100} placeholder="Nombre o documento" type="search" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
        <label className="ingresos-filter">Tipo de ingreso<select aria-label="Tipo de ingreso" value={modality} onChange={(event) => setModality(event.target.value)}><option value="">Todos</option><option value="MEMBRESIA">Membresía</option><option value="PASE_DIARIO">Pase diario</option></select></label>
        <label className="ingresos-filter">Desde<input aria-label="Ingresos desde" type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label className="ingresos-filter">Hasta<input aria-label="Ingresos hasta" type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
        <div className="ingresos-filter-actions"><Button disabled={loading || !from || !to || from > to} type="submit" variant="secondary"><Search aria-hidden="true" size={15} />Aplicar filtros</Button><Button disabled={loading} onClick={clear} type="button" variant="ghost">Hoy</Button></div>
      </form>
      {from && to && from > to && <p className="asistencia-feedback asistencia-feedback--error" role="alert">La fecha «Desde» no puede ser posterior a «Hasta».</p>}
      {error && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{error}<Button onClick={() => void load(pageNumber)} type="button" variant="secondary">Reintentar</Button></p>}
      {loading ? <p className="ingresos-state" role="status">Cargando ingresos…</p> : error ? null : !result?.contenido.length ? <div className="ingresos-empty"><span className="ingresos-empty-icon"><DoorOpen aria-hidden="true" size={20} /></span><strong>No hay ingresos para mostrar</strong><span>Prueba con otras fechas o ajusta los filtros.</span></div> : <>
        <div className="ingresos-table-wrap"><table className="ingresos-table"><caption className="asistencia-sr-only">Historial de ingresos de socios y visitantes</caption><thead><tr><th scope="col">PERSONA</th><th scope="col">DOCUMENTO</th><th scope="col">FECHA Y HORA (LIMA)</th><th scope="col">TIPO DE INGRESO</th><th scope="col">MODALIDAD</th><th scope="col">RECEPCIONISTA</th></tr></thead><tbody>{result.contenido.map((entry) => <tr key={`${entry.tipo}-${entry.id}`}><td data-label="Persona"><strong>{entry.persona}</strong></td><td data-label="Documento">{entry.documentoProtegido ?? 'No disponible'}</td><td data-label="Fecha y hora (Lima)">{fechaHoraLima(entry.fecha)}</td><td data-label="Tipo de ingreso">{entry.tipo === 'SOCIO' ? 'Socio' : 'Visitante'}</td><td data-label="Modalidad"><span className={`ingresos-chip ${entry.modalidad ? `ingresos-chip--${entry.modalidad.toLowerCase()}` : ''}`}>{entry.modalidad === 'MEMBRESIA' ? 'Membresía' : entry.modalidad === 'PASE_DIARIO' ? 'Pase diario' : 'No especificada'}</span></td><td data-label="Recepcionista">{entry.responsable ?? 'No disponible'}</td></tr>)}</tbody></table></div>
        <nav aria-label="Paginación de ingresos" className="ingresos-pagination"><Button disabled={pageNumber <= 0 || loading} onClick={() => void load(pageNumber - 1)} type="button" variant="secondary">Anterior</Button><span>Página {pageNumber + 1} de {Math.max(1, result.totalPaginas)} · {result.totalElementos} ingresos</span><Button disabled={pageNumber + 1 >= result.totalPaginas || loading} onClick={() => void load(pageNumber + 1)} type="button" variant="secondary">Siguiente</Button></nav>
      </>}
    </Card>
  </div>
}
