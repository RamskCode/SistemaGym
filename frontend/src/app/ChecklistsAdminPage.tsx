import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Search } from 'lucide-react'
import { ApiError } from '../api/http'
import { listarTodosChecklists, obtenerChecklist, type ChecklistSalaRespuesta, type ResultadoChecklist } from '../api/checklists'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './checklists-admin.css'

const ZONA_LIMA = 'America/Lima'

function fechaLima(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: ZONA_LIMA }).format(date)
}

function diaLima(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en', { timeZone: ZONA_LIMA, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date)
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function errorText(reason: unknown) {
  return reason instanceof ApiError ? reason.message : 'Ocurrió un error inesperado al consultar los checklists.'
}

function resultadoNombre(value: ResultadoChecklist) {
  return value === 'CORRECTO' ? 'Correcto' : 'Con problema'
}

export function ChecklistsAdminPage() {
  const auth = useAuth()
  const [checklists, setChecklists] = useState<ChecklistSalaRespuesta[]>([])
  const [detalle, setDetalle] = useState<ChecklistSalaRespuesta | null>(null)
  const [cargando, setCargando] = useState(true)
  const [loadingId, setLoadingId] = useState<number | null>(null)
  const [errorLista, setErrorLista] = useState('')
  const [errorDetalle, setErrorDetalle] = useState('')
  const [failedDetailId, setFailedDetailId] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [fecha, setFecha] = useState('')
  const [resultado, setResultado] = useState<ResultadoChecklist | ''>('')
  const detailLock = useRef(false)

  const cargarLista = useCallback(async () => {
    const version = auth.getSessionVersion()
    setCargando(true); setErrorLista('')
    try {
      const data = await listarTodosChecklists(auth)
      if (auth.getSessionVersion() === version) setChecklists(data)
    } catch (reason) {
      if (auth.getSessionVersion() === version) setErrorLista(errorText(reason))
    } finally { if (auth.getSessionVersion() === version) setCargando(false) }
  }, [auth])

  useEffect(() => { void cargarLista() }, [cargarLista])

  const filtered = useMemo(() => {
    const term = query.trim().toLocaleLowerCase()
    return checklists.filter((item) => {
      const matchesText = !term || `${item.idChecklist} ${item.idEntrenador} ${item.correoEntrenador}`.toLocaleLowerCase().includes(term)
      const matchesDate = !fecha || diaLima(item.fechaHora) === fecha
      const matchesResult = !resultado || item.detalles.some((detail) => detail.resultado === resultado)
      return matchesText && matchesDate && matchesResult
    })
  }, [checklists, fecha, query, resultado])

  async function abrirDetalle(id: number) {
    if (detailLock.current) return
    detailLock.current = true
    const version = auth.getSessionVersion()
    setLoadingId(id); setErrorDetalle('')
    setFailedDetailId(null)
    try {
      const fresh = await obtenerChecklist(auth, id)
      if (auth.getSessionVersion() === version) setDetalle(fresh)
    } catch (reason) {
      if (auth.getSessionVersion() === version) { setErrorDetalle(errorText(reason)); setFailedDetailId(id) }
    } finally {
      detailLock.current = false
      if (auth.getSessionVersion() === version) setLoadingId(null)
    }
  }

  if (detalle) {
    return <div className="dashboard checklists-admin-page">
      <PageTitle description={`Registrado ${fechaLima(detalle.fechaHora)}`} eyebrow="ADMINISTRACIÓN · GIMNASIO" title="Detalle del checklist" />
      <div className="check-admin-back-actions"><Button onClick={() => { setDetalle(null); setErrorDetalle('') }} type="button" variant="secondary">Volver a todos los checklists</Button></div>
      {errorDetalle && <p className="check-admin-feedback check-admin-feedback--error" role="alert">{errorDetalle}</p>}
      <Card className="check-admin-card" title="Registro de revisión">
        <dl className="check-admin-summary">
          <div><dt>Identificador</dt><dd>#{detalle.idChecklist}</dd></div>
          <div><dt>Entrenador responsable</dt><dd>Entrenador #{detalle.idEntrenador}<small>{detalle.correoEntrenador}</small></dd></div>
          <div><dt>Fecha de registro</dt><dd>{fechaLima(detalle.fechaHora)}</dd></div>
          <div><dt>Elementos revisados</dt><dd>{detalle.detalles.length}</dd></div>
        </dl>
        {detalle.observaciones && <section className="check-admin-general"><h2>Observaciones generales</h2><p>{detalle.observaciones}</p></section>}
      </Card>
      <Card className="check-admin-card" title="Elementos inspeccionados">
        {detalle.detalles.length === 0 ? <p className="check-admin-empty">Este checklist no contiene detalles.</p> : <div className="check-admin-details">{detalle.detalles.map((item) => <article className="check-admin-item" key={item.idDetalle}>
          <header><div><h2>{item.nombreElemento}</h2><p>{item.tipoElemento === 'MAQUINA' ? 'Máquina' : 'Área'}{item.ubicacionElemento ? ` · ${item.ubicacionElemento}` : ''}</p></div><span className={`check-admin-result check-admin-result--${item.resultado.toLowerCase()}`}>{resultadoNombre(item.resultado)}</span></header>
          {item.observaciones && <section className="check-admin-note"><h3>Observaciones del elemento</h3><p>{item.observaciones}</p></section>}
          {item.incidencia && <section className="check-admin-incidence" aria-label={`Incidencia asociada al elemento ${item.nombreElemento}`}><h3>Incidencia asociada #{item.incidencia.idReporte}</h3><p>{item.incidencia.descripcion}</p><dl><div><dt>Prioridad</dt><dd>{item.incidencia.prioridad}</dd></div><div><dt>Estado</dt><dd>{item.incidencia.estado}</dd></div><div><dt>Fecha del reporte</dt><dd>{fechaLima(item.incidencia.fechaReporte)}</dd></div></dl><p className="check-admin-hint">Referencia real de incidencia; la gestión de estados está disponible en el módulo Incidencias.</p></section>}
        </article>)}</div>}
        <p className="check-admin-readonly">Consulta de solo lectura. No se pueden modificar ni repetir checklists.</p>
      </Card>
    </div>
  }

  return <div className="dashboard checklists-admin-page">
    <PageTitle description="Supervisa los checklists registrados por el equipo de entrenamiento." eyebrow="ADMINISTRACIÓN · GIMNASIO" title="Checklists del gimnasio" />
    <Card className="check-admin-card" title="Todos los checklists">
      <p className="check-admin-hint">La lista global llega ordenada de más reciente a más antigua y no tiene paginación del servidor. Los filtros se aplican localmente.</p>
      <div className="check-admin-toolbar"><label className="check-admin-search"><Search aria-hidden="true" size={16}/><input aria-label="Buscar por entrenador o identificador" onChange={(event) => setQuery(event.target.value)} placeholder="Entrenador, correo o ID" value={query}/></label><label className="check-admin-filter">Fecha de revisión<input aria-label="Filtrar por fecha" onChange={(event) => setFecha(event.target.value)} type="date" value={fecha}/></label><label className="check-admin-filter">Resultado<select aria-label="Filtrar por resultado" onChange={(event) => setResultado(event.target.value as ResultadoChecklist | '')} value={resultado}><option value="">Todos</option><option value="CORRECTO">Correcto</option><option value="CON_PROBLEMA">Con problema</option></select></label></div>
      {errorLista && <div className="check-admin-feedback check-admin-feedback--error" role="alert"><span>{errorLista}</span><Button disabled={cargando} onClick={() => void cargarLista()} type="button" variant="secondary">Reintentar</Button></div>}
      {errorDetalle && failedDetailId !== null && <div className="check-admin-feedback check-admin-feedback--error" role="alert"><span>No se pudo abrir el detalle: {errorDetalle}</span><Button disabled={loadingId !== null} onClick={() => void abrirDetalle(failedDetailId)} type="button" variant="secondary">Reintentar detalle</Button></div>}
      {cargando ? <p aria-live="polite" className="check-admin-empty">Cargando checklists…</p> : errorLista ? null : filtered.length === 0 ? <p className="check-admin-empty">{checklists.length ? 'No hay checklists que coincidan con los filtros.' : 'Aún no hay checklists registrados.'}</p> : <div className="table-wrap check-admin-table-wrap"><table className="check-admin-table"><caption className="check-admin-sr-only">Checklists registrados por entrenadores</caption><thead><tr><th scope="col">FECHA</th><th scope="col">ENTRENADOR RESPONSABLE</th><th scope="col">ELEMENTOS</th><th scope="col">RESULTADO</th><th scope="col">ACCIÓN</th></tr></thead><tbody>{filtered.map((item) => {
        const problems = item.detalles.filter((detail) => detail.resultado === 'CON_PROBLEMA').length
        return <tr key={item.idChecklist}><td data-label="Fecha"><strong>#{item.idChecklist}</strong><small>{fechaLima(item.fechaHora)}</small></td><td data-label="Entrenador responsable"><strong>Entrenador #{item.idEntrenador}</strong><small>{item.correoEntrenador}</small></td><td data-label="Elementos">{item.detalles.length}</td><td data-label="Resultado"><span className={`check-admin-result check-admin-result--${problems ? 'con_problema' : 'correcto'}`}>{problems ? `${problems} con problema` : item.detalles.length ? 'Sin problemas' : 'Sin detalles'}</span></td><td data-label="Acción"><Button aria-label={`Ver detalle del checklist ${item.idChecklist}`} disabled={loadingId !== null} onClick={() => void abrirDetalle(item.idChecklist)} type="button" variant="secondary">{loadingId === item.idChecklist ? 'Cargando…' : 'Ver detalle'}</Button></td></tr>
      })}</tbody></table></div>}
    </Card>
  </div>
}
