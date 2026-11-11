import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/http'
import {
  listarMisChecklists,
  obtenerChecklist,
  type ChecklistSalaRespuesta,
} from '../api/checklists'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './mis-checklists.css'

function mensajeError(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : 'Ocurrió un error inesperado al consultar los checklists.'
}

function fechaLima(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(date)
}

function resultadoTexto(resultado: string) {
  return resultado === 'CORRECTO' ? 'Correcto' : 'Con problema'
}

export function MisChecklistsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const auth = useAuth()
  const entrenador = auth.user?.roles.includes('ENTRENADOR') ?? false
  const [checklists, setChecklists] = useState<ChecklistSalaRespuesta[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [detalle, setDetalle] = useState<ChecklistSalaRespuesta | null>(null)
  const [idDetalleCargando, setIdDetalleCargando] = useState<number | null>(null)
  const [errorDetalle, setErrorDetalle] = useState('')
  const [idDetalleError, setIdDetalleError] = useState<number | null>(null)
  const detalleLock = useRef(false)

  async function cargarChecklists() {
    setCargando(true)
    setError('')
    try {
      // El backend ya devuelve esta lista ordenada de más reciente a más antigua.
      setChecklists(await listarMisChecklists(auth))
    } catch (reason) {
      setError(mensajeError(reason))
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    if (entrenador) void cargarChecklists()
  }, [auth, entrenador])

  async function verDetalle(idChecklist: number) {
    if (detalleLock.current) return
    detalleLock.current = true
    setIdDetalleCargando(idChecklist)
    setErrorDetalle('')
    setIdDetalleError(null)
    try {
      setDetalle(await obtenerChecklist(auth, idChecklist))
    } catch (reason) {
      setErrorDetalle(mensajeError(reason))
      setIdDetalleError(idChecklist)
    } finally {
      detalleLock.current = false
      setIdDetalleCargando(null)
    }
  }

  useEffect(() => {
    const requestedId = Number(searchParams.get('checklist'))
    if (entrenador && Number.isSafeInteger(requestedId) && requestedId > 0) void verDetalle(requestedId)
  }, [entrenador, searchParams])

  function volverALista() {
    setDetalle(null)
    setErrorDetalle('')
    if (searchParams.has('checklist')) setSearchParams({}, { replace: true })
  }

  if (!entrenador) {
    return <div className="dashboard mis-checklists-page"><PageTitle eyebrow="PORTAL DEL PERSONAL" title="Mis checklists" /><Card><p>Esta funcionalidad está disponible únicamente para entrenadores.</p></Card></div>
  }

  if (detalle) {
    const correctos = detalle.detalles.filter((item) => item.resultado === 'CORRECTO').length
    const problemas = detalle.detalles.filter((item) => item.resultado === 'CON_PROBLEMA').length
    return <div className="dashboard mis-checklists-page">
      <PageTitle description={`Registrado ${fechaLima(detalle.fechaHora)}`} eyebrow="ENTRENAMIENTO · HISTORIAL" title="Detalle de revisión" actions={<Link className="button button--secondary" to="/entrenamiento">Nueva revisión</Link>} />
      <div className="checklist-detail-actions"><Button onClick={volverALista} type="button" variant="secondary">Volver a mis checklists</Button></div>
      <Card className="checklist-summary-card" title="Resumen del checklist">
        <dl className="checklist-summary-grid">
          <div><dt>Elementos revisados</dt><dd>{detalle.detalles.length}</dd></div>
          <div><dt>Correctos</dt><dd>{correctos}</dd></div>
          <div><dt>Con problemas</dt><dd>{problemas}</dd></div>
        </dl>
        {detalle.observaciones && <div className="checklist-general-observations"><h2>Observaciones generales</h2><p>{detalle.observaciones}</p></div>}
      </Card>
      <Card className="checklist-details-card" title="Elementos revisados">
        {detalle.detalles.length === 0 ? <p className="checklist-empty">Este checklist no contiene detalles.</p> : <div className="checklist-detail-list">
          {detalle.detalles.map((item) => <article className="checklist-detail-item" key={item.idDetalle}>
            <header className="checklist-detail-item__header">
              <div><h2>{item.nombreElemento}</h2><p>{item.tipoElemento === 'MAQUINA' ? 'Máquina' : 'Área'}{item.ubicacionElemento ? ` · ${item.ubicacionElemento}` : ''}</p></div>
              <span className={`checklist-result checklist-result--${item.resultado.toLowerCase()}`}>{resultadoTexto(item.resultado)}</span>
            </header>
            {item.observaciones && <div className="checklist-detail-note"><h3>Observaciones</h3><p>{item.observaciones}</p></div>}
            {item.incidencia && <section aria-label={`Incidencia asociada al elemento ${item.nombreElemento}`} className="checklist-incidence">
              <h3>Incidencia asociada #{item.incidencia.idReporte}</h3>
              <p>{item.incidencia.descripcion}</p>
              <dl><div><dt>Prioridad</dt><dd>{item.incidencia.prioridad}</dd></div><div><dt>Estado</dt><dd>{item.incidencia.estado}</dd></div><div><dt>Fecha del reporte</dt><dd>{fechaLima(item.incidencia.fechaReporte)}</dd></div></dl>
            </section>}
          </article>)}
        </div>}
      </Card>
    </div>
  }

  return <div className="dashboard mis-checklists-page">
    <PageTitle description="Consulta los checklists registrados desde tu cuenta y revisa sus detalles." eyebrow="ENTRENAMIENTO · HISTORIAL" title="Mis checklists" actions={<Link className="button button--primary" to="/entrenamiento">Nueva revisión</Link>} />
    {error && <p className="checklist-feedback checklist-feedback--error" role="alert">{error} <Button disabled={cargando} onClick={() => void cargarChecklists()} type="button" variant="secondary">Reintentar</Button></p>}
    {cargando ? <p aria-live="polite" className="checklist-empty">Cargando tus checklists…</p>
      : error ? null
        : checklists.length === 0 ? <Card><p className="checklist-empty">Aún no has registrado checklists.</p><Link className="button button--primary checklist-empty__link" to="/entrenamiento">Registrar una revisión</Link></Card>
          : <section aria-label="Lista de mis checklists" className="checklist-list">
            {checklists.map((checklist) => {
              const correctos = checklist.detalles.filter((item) => item.resultado === 'CORRECTO').length
              const problemas = checklist.detalles.filter((item) => item.resultado === 'CON_PROBLEMA').length
              return <Card className="checklist-record" key={checklist.idChecklist}>
                <div className="checklist-record__heading"><div><p className="checklist-record__eyebrow">CHECKLIST #{checklist.idChecklist}</p><h2>{fechaLima(checklist.fechaHora)}</h2></div><Button aria-label={`Ver detalle del checklist ${checklist.idChecklist}`} disabled={idDetalleCargando !== null} onClick={() => void verDetalle(checklist.idChecklist)} type="button" variant="secondary">{idDetalleCargando === checklist.idChecklist ? 'Cargando detalle…' : 'Ver detalle'}</Button></div>
                <dl className="checklist-record__counts"><div><dt>Revisados</dt><dd>{checklist.detalles.length}</dd></div><div><dt>Correctos</dt><dd>{correctos}</dd></div><div><dt>Con problemas</dt><dd>{problemas}</dd></div></dl>
                {checklist.observaciones && <p className="checklist-record__observations"><strong>Observaciones generales:</strong> {checklist.observaciones}</p>}
              </Card>
            })}
          </section>}
    {errorDetalle && idDetalleError !== null && <div className="checklist-feedback checklist-feedback--error" role="alert"><p>{errorDetalle}</p><Button disabled={idDetalleCargando !== null} onClick={() => void verDetalle(idDetalleError)} type="button" variant="secondary">Reintentar detalle</Button></div>}
  </div>
}
