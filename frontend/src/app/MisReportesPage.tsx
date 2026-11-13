import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/http'
import {
  crearReporteIncidencia,
  listarElementosRevisionActivos,
  listarMisReportesIncidencia,
  obtenerReporteIncidencia,
  type CrearReporteIncidenciaRequest,
  type ElementoRevision,
  type PrioridadIncidencia,
  type ReporteIncidenciaRespuesta,
} from '../api/checklists'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './mis-reportes.css'
import { addUncertainWriteLock, isSessionCurrent, readUncertainWriteLocks, removeUncertainWriteLock, uncertainWriteResult } from './uncertainWriteLocks'

function fechaLima(value: string | null) {
  if (!value) return 'No disponible'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(date)
}

function mensajeError(error: unknown) {
  return error instanceof ApiError ? error.message : 'Ocurrió un error inesperado al consultar los reportes.'
}

function resultadoIncierto(error: unknown) {
  return uncertainWriteResult(error)
}

const estados: Record<string, string> = {
  PENDIENTE: 'Pendiente', EN_REVISION: 'En proceso', SOLUCIONADO: 'Resuelta',
}

const prioridades: Record<PrioridadIncidencia, string> = { BAJA: 'Baja', MEDIA: 'Media', ALTA: 'Alta' }

export function MisReportesPage() {
  const auth = useAuth()
  const entrenador = auth.user?.roles.includes('ENTRENADOR') ?? false
  const account = auth.user?.correo ?? ''
  const [reportes, setReportes] = useState<ReporteIncidenciaRespuesta[]>([])
  const [elementos, setElementos] = useState<ElementoRevision[]>([])
  const [cargando, setCargando] = useState(true)
  const [errorLista, setErrorLista] = useState('')
  const [errorElementos, setErrorElementos] = useState('')
  const [reporte, setReporte] = useState<ReporteIncidenciaRespuesta | null>(null)
  const [idCargando, setIdCargando] = useState<number | null>(null)
  const [idErrorDetalle, setIdErrorDetalle] = useState<number | null>(null)
  const [errorDetalle, setErrorDetalle] = useState('')
  const [formulario, setFormulario] = useState(false)
  const [idElemento, setIdElemento] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [prioridad, setPrioridad] = useState<PrioridadIncidencia | ''>('')
  const [errorFormulario, setErrorFormulario] = useState('')
  const [confirmar, setConfirmar] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [exito, setExito] = useState<ReporteIncidenciaRespuesta | null>(null)
  const [incierto, setIncierto] = useState(false)
  const [bloqueoListo, setBloqueoListo] = useState(false)
  const [errorBloqueo, setErrorBloqueo] = useState(false)
  const detailLock = useRef(false)
  const createLock = useRef(false)

  useEffect(() => {
    let active = true
    if (!entrenador || !account) { setBloqueoListo(true); setIncierto(false); return }
    setBloqueoListo(false)
    void readUncertainWriteLocks(account, 'direct-report').then((locks) => {
      if (active) { setIncierto(locks.includes('create')); setBloqueoListo(true); setErrorBloqueo(false) }
    }).catch(() => {
      if (active) { setIncierto(true); setBloqueoListo(true); setErrorBloqueo(true) }
    })
    return () => { active = false }
  }, [account, entrenador])

  async function cargarLista() {
    const sessionVersion = auth.getSessionVersion()
    setCargando(true)
    setErrorLista('')
    try {
      // Se conserva el orden descendente que entrega el backend.
      const result = await listarMisReportesIncidencia(auth)
      if (isSessionCurrent(auth, sessionVersion)) setReportes(result)
    } catch (reason) {
      if (isSessionCurrent(auth, sessionVersion)) setErrorLista(mensajeError(reason))
    } finally {
      if (isSessionCurrent(auth, sessionVersion)) setCargando(false)
    }
  }

  async function cargarElementos() {
    setErrorElementos('')
    try {
      const activos = await listarElementosRevisionActivos(auth)
      setElementos(activos.filter((elemento) => elemento.activo))
    } catch (reason) {
      setErrorElementos(mensajeError(reason))
    }
  }

  useEffect(() => {
    if (entrenador) {
      void cargarLista()
      void cargarElementos()
    }
  }, [auth, entrenador])

  async function verDetalle(id: number) {
    if (detailLock.current) return
    detailLock.current = true
    setIdCargando(id)
    setErrorDetalle('')
    setIdErrorDetalle(null)
    try {
      setReporte(await obtenerReporteIncidencia(auth, id))
    } catch (reason) {
      setErrorDetalle(mensajeError(reason))
      setIdErrorDetalle(id)
    } finally {
      detailLock.current = false
      setIdCargando(null)
    }
  }

  function validar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorFormulario('')
    if (!elementos.some((elemento) => String(elemento.idElemento) === idElemento && elemento.activo)) {
      setErrorFormulario('Selecciona un elemento activo de la lista.')
      return
    }
    if (!descripcion.trim()) {
      setErrorFormulario('La descripción de la incidencia es obligatoria.')
      return
    }
    if (descripcion.length > 4000) {
      setErrorFormulario('La descripción no puede superar los 4000 caracteres.')
      return
    }
    if (!prioridad) {
      setErrorFormulario('Selecciona una prioridad.')
      return
    }
    setConfirmar(true)
  }

  async function enviar() {
    if (createLock.current || incierto || !bloqueoListo || errorBloqueo) return
    const request: CrearReporteIncidenciaRequest = {
      idElemento: Number(idElemento), descripcion: descripcion.trim(), prioridad: prioridad as PrioridadIncidencia,
    }
    createLock.current = true
    setGuardando(true)
    setErrorFormulario('')
    const sessionVersion = auth.getSessionVersion()
    let persisted = false
    try {
      await addUncertainWriteLock(account, 'direct-report', 'create')
      persisted = true
      if (!isSessionCurrent(auth, sessionVersion)) {
        await removeUncertainWriteLock(account, 'direct-report', 'create')
        return
      }
      setIncierto(true)
      const creado = await crearReporteIncidencia(auth, request)
      if (!Number.isSafeInteger(creado.idReporte) || !Array.isArray(creado.historial)) throw new ApiError('invalid-response')
      if (isSessionCurrent(auth, sessionVersion)) {
        setExito(creado)
        setConfirmar(false)
        setFormulario(false)
        setIdElemento('')
        setDescripcion('')
        setPrioridad('')
        setReporte(creado)
        await cargarLista()
      }
      try {
        await removeUncertainWriteLock(account, 'direct-report', 'create')
        if (isSessionCurrent(auth, sessionVersion)) setIncierto(false)
      } catch { if (isSessionCurrent(auth, sessionVersion)) setErrorBloqueo(true) }
    } catch (reason) {
      if (persisted && !resultadoIncierto(reason)) {
        try { await removeUncertainWriteLock(account, 'direct-report', 'create'); if (isSessionCurrent(auth, sessionVersion)) setIncierto(false) }
        catch { if (isSessionCurrent(auth, sessionVersion)) setErrorBloqueo(true) }
      } else if (persisted && isSessionCurrent(auth, sessionVersion)) setIncierto(true)
      if (isSessionCurrent(auth, sessionVersion)) setErrorFormulario(persisted
        ? 'Resultado incierto: el reporte pudo haberse registrado; existe riesgo de duplicación. No reintentes. Solicita revisión administrativa; el listado no permite reconciliarlo inequívocamente.'
        : 'No se envió: no se pudo establecer el bloqueo persistente.')
      if (isSessionCurrent(auth, sessionVersion)) setConfirmar(false)
    } finally {
      createLock.current = false
      if (isSessionCurrent(auth, sessionVersion)) setGuardando(false)
    }
  }

  if (!entrenador) {
    return <div className="dashboard mis-reportes-page"><PageTitle eyebrow="PORTAL DEL PERSONAL" title="Mis incidencias" /><Card><p>Esta funcionalidad está disponible únicamente para entrenadores.</p></Card></div>
  }

  if (reporte) {
    return <div className="dashboard mis-reportes-page">
      <PageTitle description={`Registrada ${fechaLima(reporte.fechaReporte)}`} eyebrow="ENTRENAMIENTO · MIS INCIDENCIAS" title={`Incidencia #${reporte.idReporte}`} />
      {exito && <p className="report-feedback report-feedback--success" role="status">Incidencia #{exito.idReporte} registrada. Estado: {estados[exito.estado] ?? exito.estado}.</p>}
      <div className="report-nav-actions"><Button onClick={() => setReporte(null)} type="button" variant="secondary">Volver a mis incidencias</Button></div>
      <Card className="report-detail-card" title="Incidencia">
        <dl className="report-main-data">
          <div><dt>Elemento afectado</dt><dd>{reporte.nombreElemento}</dd></div>
          <div><dt>Origen</dt><dd><span className={`report-origin ${reporte.idChecklist !== null ? 'report-origin--checklist' : 'report-origin--direct'}`}>{reporte.idChecklist !== null ? `Revisión #${reporte.idChecklist}` : 'Registro directo'}</span></dd></div>
          <div><dt>Prioridad</dt><dd>{prioridades[reporte.prioridad] ?? reporte.prioridad}</dd></div>
          <div><dt>Estado actual</dt><dd><span className={`report-state report-state--${reporte.estado.toLowerCase()}`}>{estados[reporte.estado] ?? reporte.estado}</span></dd></div>
          <div><dt>Fecha de creación</dt><dd>{fechaLima(reporte.fechaReporte)}</dd></div>
          <div><dt>Última actualización</dt><dd>{fechaLima(reporte.fechaActualizacion)}</dd></div>
          {reporte.fechaSolucion && <div><dt>Fecha de solución</dt><dd>{fechaLima(reporte.fechaSolucion)}</dd></div>}
          {reporte.idChecklist !== null && <div><dt>Checklist asociado</dt><dd>#{reporte.idChecklist}</dd></div>}
        </dl>
        <section className="report-description"><h2>Descripción</h2><p>{reporte.descripcion}</p></section>
      </Card>
      <Card className="report-history-card" title="Historial de estados">
        {reporte.historial.length === 0 ? <p className="report-empty">No hay eventos de historial disponibles.</p> : <ol className="report-history">
          {reporte.historial.map((evento) => <li key={evento.idHistorial}>
            <span aria-hidden="true" className="report-history__marker" />
            <div><p className="report-history__transition">{evento.estadoAnterior ? `${estados[evento.estadoAnterior] ?? evento.estadoAnterior} → ` : ''}{estados[evento.estadoNuevo] ?? evento.estadoNuevo}</p>
              <p className="report-history__meta">{fechaLima(evento.fechaHora)} · {evento.correoUsuario}</p>
              {evento.observacion && <p className="report-history__note">{evento.observacion}</p>}
            </div>
          </li>)}
        </ol>}
        <p className="report-readonly-note">El historial es de solo lectura.</p>
      </Card>
    </div>
  }

  return <div className="dashboard mis-reportes-page">
    <PageTitle description="Sigue los problemas que reportaste, su prioridad y el avance de atención." eyebrow="ENTRENAMIENTO · SEGUIMIENTO" title="Mis incidencias" actions={<>
      <Button disabled={!bloqueoListo || incierto || errorBloqueo} onClick={() => { setFormulario(true); setExito(null); setErrorFormulario('') }} type="button">Nueva incidencia</Button>
    </>} />
    {exito && <div className="report-feedback report-feedback--success" role="status"><strong>Incidencia #{exito.idReporte} registrada.</strong> Estado: {estados[exito.estado] ?? exito.estado}. <Button onClick={() => setReporte(exito)} type="button" variant="secondary">Ver reporte</Button></div>}
    {incierto && <div className="report-feedback report-feedback--error" role="alert">{errorBloqueo ? 'Escrituras bloqueadas: no se pudo leer el almacenamiento de seguridad.' : 'Creación de reporte bloqueada por resultado incierto. Pudo registrarse; hay riesgo de duplicación. No reintentes. Solicita revisión administrativa; el listado no permite reconciliarlo inequívocamente.'}</div>}
    {errorLista && <div className="report-feedback report-feedback--error" role="alert"><span>{errorLista}</span><Button disabled={cargando} onClick={() => void cargarLista()} type="button" variant="secondary">Reintentar</Button></div>}
    {cargando ? <p aria-live="polite" className="report-empty">Cargando tus incidencias…</p>
      : errorLista ? null
        : reportes.length === 0 ? <Card><p className="report-empty">Aún no has registrado incidencias.</p></Card>
          : <section aria-label="Lista de mis incidencias" className="report-list">{reportes.map((item) => <Card className="report-record" key={item.idReporte}>
            <div className="report-record__top"><div><p className="report-record__eyebrow">INCIDENCIA #{item.idReporte}</p><h2>{item.nombreElemento}</h2></div><span className={`report-state report-state--${item.estado.toLowerCase()}`}>{estados[item.estado] ?? item.estado}</span></div>
            <span className={`report-origin ${item.idChecklist !== null ? 'report-origin--checklist' : 'report-origin--direct'}`}>{item.idChecklist !== null ? `Generada en revisión #${item.idChecklist}` : 'Registrada directamente'}</span>
            <p className="report-record__description">{item.descripcion}</p>
            <dl className="report-record__meta"><div><dt>Prioridad</dt><dd>{prioridades[item.prioridad] ?? item.prioridad}</dd></div><div><dt>Fecha</dt><dd>{fechaLima(item.fechaReporte)}</dd></div></dl>
            <Button disabled={idCargando !== null} onClick={() => void verDetalle(item.idReporte)} type="button" variant="secondary">{idCargando === item.idReporte ? 'Cargando…' : 'Ver detalle e historial'}</Button>
          </Card>)}</section>}
    {errorElementos && formulario && <div className="report-feedback report-feedback--error" role="alert">{errorElementos} <Button onClick={() => void cargarElementos()} type="button" variant="secondary">Reintentar elementos</Button></div>}
    {errorDetalle && idErrorDetalle !== null && <div className="report-feedback report-feedback--error" role="alert"><span>{errorDetalle}</span><Button onClick={() => void verDetalle(idErrorDetalle)} type="button" variant="secondary">Reintentar detalle</Button></div>}

    {formulario && <div className="report-dialog-backdrop" onClick={() => { if (!guardando) setFormulario(false) }}><section aria-labelledby="new-report-title" aria-modal="true" className="report-dialog" onClick={(event) => event.stopPropagation()} role="dialog">
      <div className="report-dialog__heading"><h2 id="new-report-title">Nueva incidencia</h2><Button aria-label="Cerrar formulario" disabled={guardando} onClick={() => setFormulario(false)} type="button" variant="secondary">Cerrar</Button></div>
      {errorFormulario && !incierto && <p className="report-feedback report-feedback--error" role="alert">{errorFormulario}</p>}
      {errorElementos ? <p role="status">Carga los elementos activos para continuar.</p> : elementos.length === 0 ? <p className="report-empty">No hay elementos activos disponibles.</p> : <form onSubmit={validar}>
        <label className="report-field" htmlFor="report-element">Elemento afectado <span aria-hidden="true">*</span>
          <select id="report-element" onChange={(event) => setIdElemento(event.target.value)} required value={idElemento}><option value="">Selecciona un elemento activo</option>{elementos.map((elemento) => <option key={elemento.idElemento} value={elemento.idElemento}>{elemento.nombre}{elemento.ubicacion ? ` · ${elemento.ubicacion}` : ''}</option>)}</select>
        </label>
        <label className="report-field" htmlFor="report-description">Descripción <span aria-hidden="true">*</span>
          <textarea id="report-description" maxLength={4000} onChange={(event) => setDescripcion(event.target.value)} required value={descripcion} />
          <small>{descripcion.length}/4000</small>
        </label>
        <label className="report-field" htmlFor="report-priority">Prioridad <span aria-hidden="true">*</span>
          <select id="report-priority" onChange={(event) => setPrioridad(event.target.value as PrioridadIncidencia | '')} required value={prioridad}><option value="">Selecciona una prioridad</option><option value="BAJA">Baja</option><option value="MEDIA">Media</option><option value="ALTA">Alta</option></select>
        </label>
        <p className="report-form-note">Este registro crea un reporte directo, sin crear un checklist adicional.</p>
        <div className="report-dialog__actions"><Button disabled={guardando} onClick={() => setFormulario(false)} type="button" variant="secondary">Cancelar</Button><Button disabled={guardando || incierto || elementos.length === 0} type="submit">Revisar y confirmar</Button></div>
      </form>}
    </section></div>}
    {confirmar && <div className="report-dialog-backdrop"><section aria-labelledby="confirm-report-title" aria-modal="true" className="report-dialog" role="dialog">
      <h2 id="confirm-report-title">Confirmar incidencia</h2><dl className="report-confirm-summary"><div><dt>Elemento</dt><dd>{elementos.find((elemento) => String(elemento.idElemento) === idElemento)?.nombre}</dd></div><div><dt>Descripción</dt><dd>{descripcion.trim()}</dd></div><div><dt>Prioridad</dt><dd>{prioridad}</dd></div></dl>
      <div className="report-dialog__actions"><Button disabled={guardando} onClick={() => setConfirmar(false)} type="button" variant="secondary">Volver</Button><Button disabled={guardando} onClick={() => void enviar()} type="button">{guardando ? 'Registrando…' : 'Confirmar y registrar'}</Button></div>
    </section></div>}
  </div>
}
