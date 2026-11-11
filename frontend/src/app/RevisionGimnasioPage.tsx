import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { ApiError } from '../api/http'
import {
  listarElementosRevisionActivos,
  registrarChecklist,
  type ChecklistSalaRespuesta,
  type CrearChecklistRequest,
  type ElementoRevision,
  type PrioridadIncidencia,
  type ResultadoChecklist,
} from '../api/checklists'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './revision-gimnasio.css'
import { addUncertainWriteLock, isSessionCurrent, readUncertainWriteLocks, removeUncertainWriteLock, uncertainWriteResult } from './uncertainWriteLocks'

type RevisionDetalle = {
  resultado: ResultadoChecklist
  observaciones: string
  descripcionIncidencia: string
  prioridadIncidencia: PrioridadIncidencia | ''
}

const detalleVacio = (): RevisionDetalle => ({
  resultado: 'CORRECTO', observaciones: '', descripcionIncidencia: '', prioridadIncidencia: '',
})

function explicarError(error: unknown, envio = false) {
  if (envio && uncertainWriteResult(error)) {
    return 'No se pudo confirmar el resultado del checklist; podría haberse registrado. Actualiza la página antes de volver a intentarlo. No se enviará automáticamente otra solicitud.'
  }
  if (error instanceof ApiError) return error.message
  return envio
    ? 'No se pudo confirmar el resultado del checklist; podría haberse registrado. Actualiza la página antes de volver a intentarlo. No se enviará automáticamente otra solicitud.'
    : 'Ocurrió un error inesperado al cargar los elementos.'
}

function fechaHora(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(date)
}

export function RevisionGimnasioPage() {
  const auth = useAuth()
  const permisos = auth.user?.roles.includes('ENTRENADOR') ?? false
  const account = auth.user?.correo ?? ''
  const [elementos, setElementos] = useState<ElementoRevision[]>([])
  const [seleccionados, setSeleccionados] = useState<Record<number, RevisionDetalle>>({})
  const [observaciones, setObservaciones] = useState('')
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [resultadoIncierto, setResultadoIncierto] = useState(false)
  const [bloqueoListo, setBloqueoListo] = useState(false)
  const [errorBloqueo, setErrorBloqueo] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState<ChecklistSalaRespuesta | null>(null)
  const [confirmar, setConfirmar] = useState(false)
  const lock = useRef(false)
  const confirmDialogRef = useRef<HTMLElement>(null)
  const confirmTriggerRef = useRef<HTMLButtonElement | null>(null)
  const dialogWasOpen = useRef(false)

  useEffect(() => {
    if (confirmar) {
      dialogWasOpen.current = true
      confirmDialogRef.current?.querySelector<HTMLElement>('button:not(:disabled)')?.focus()
    } else if (dialogWasOpen.current) {
      dialogWasOpen.current = false
      confirmTriggerRef.current?.focus()
      confirmTriggerRef.current = null
    }
  }, [confirmar])

  function handleConfirmKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape' && !guardando) {
      event.preventDefault()
      setConfirmar(false)
      return
    }
    if (event.key !== 'Tab') return
    const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )).filter((item) => !item.hidden)
    if (!focusable.length) { event.preventDefault(); return }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && (document.activeElement === first || !event.currentTarget.contains(document.activeElement))) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && (document.activeElement === last || !event.currentTarget.contains(document.activeElement))) {
      event.preventDefault(); first.focus()
    }
  }

  useEffect(() => {
    let active = true
    if (!permisos || !account) { setBloqueoListo(true); setResultadoIncierto(false); return }
    setBloqueoListo(false)
    void readUncertainWriteLocks(account, 'checklist').then((locks) => {
      if (active) { setResultadoIncierto(locks.includes('create')); setBloqueoListo(true); setErrorBloqueo(false) }
    }).catch(() => {
      if (active) { setResultadoIncierto(true); setBloqueoListo(true); setErrorBloqueo(true) }
    })
    return () => { active = false }
  }, [account, permisos])

  async function cargarElementos() {
    setCargando(true)
    setError('')
    try {
      const activos = await listarElementosRevisionActivos(auth)
      setElementos(activos.filter((item) => item.activo))
    } catch (reason) {
      setError(explicarError(reason))
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    if (permisos) void cargarElementos()
  }, [auth, permisos])

  function alternarElemento(id: number, checked: boolean) {
    setSeleccionados((current) => {
      if (!checked) {
        const next = { ...current }
        delete next[id]
        return next
      }
      if (Object.keys(current).length >= 100) return current
      return { ...current, [id]: detalleVacio() }
    })
  }

  function actualizarDetalle(id: number, cambios: Partial<RevisionDetalle>) {
    setSeleccionados((current) => ({
      ...current,
      [id]: { ...current[id], ...cambios },
    }))
  }

  function construirRequest(): CrearChecklistRequest | null {
    const ids = Object.keys(seleccionados).map(Number)
    if (ids.length < 1 || ids.length > 100) {
      setError('Selecciona entre 1 y 100 elementos para registrar la revisión.')
      return null
    }
    const elementosActivos = new Map(elementos.map((item) => [item.idElemento, item]))
    const detalles = []
    for (const id of ids) {
      const elemento = elementosActivos.get(id)
      const detalle = seleccionados[id]
      if (!elemento || !elemento.activo) {
        setError('Uno de los elementos seleccionados ya no está disponible. Actualiza la lista.')
        return null
      }
      if (detalle.observaciones.length > 1000) {
        setError(`Las observaciones de ${elemento.nombre} superan los 1000 caracteres.`)
        return null
      }
      if (detalle.resultado === 'CON_PROBLEMA') {
        if (!detalle.descripcionIncidencia.trim()) {
          setError(`Describe el problema de ${elemento.nombre}.`)
          return null
        }
        if (detalle.descripcionIncidencia.length > 4000) {
          setError(`La descripción de ${elemento.nombre} supera los 4000 caracteres.`)
          return null
        }
        if (!detalle.prioridadIncidencia) {
          setError(`Selecciona una prioridad para el problema de ${elemento.nombre}.`)
          return null
        }
        detalles.push({
          idElemento: id,
          resultado: detalle.resultado,
          ...(detalle.observaciones.trim() ? { observaciones: detalle.observaciones.trim() } : {}),
          descripcionIncidencia: detalle.descripcionIncidencia.trim(),
          prioridadIncidencia: detalle.prioridadIncidencia,
        })
      } else {
        detalles.push({
          idElemento: id,
          resultado: detalle.resultado,
          ...(detalle.observaciones.trim() ? { observaciones: detalle.observaciones.trim() } : {}),
        })
      }
    }
    if (observaciones.length > 1000) {
      setError('Las observaciones generales no pueden superar los 1000 caracteres.')
      return null
    }
    setError('')
    return {
      detalles,
      ...(observaciones.trim() ? { observaciones: observaciones.trim() } : {}),
    }
  }

  function abrirConfirmacion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (construirRequest()) {
      const submitter = (event.nativeEvent as SubmitEvent).submitter
      confirmTriggerRef.current = submitter instanceof HTMLButtonElement ? submitter : null
      setConfirmar(true)
    }
  }

  async function enviarChecklist() {
    if (lock.current || resultadoIncierto || !bloqueoListo || errorBloqueo) return
    const request = construirRequest()
    if (!request) {
      setConfirmar(false)
      return
    }
    lock.current = true
    setGuardando(true)
    setError('')
    setExito(null)
    const sessionVersion = auth.getSessionVersion()
    let persisted = false
    try {
      await addUncertainWriteLock(account, 'checklist', 'create')
      persisted = true
      if (!isSessionCurrent(auth, sessionVersion)) {
        await removeUncertainWriteLock(account, 'checklist', 'create')
        return
      }
      setResultadoIncierto(true)
      const response = await registrarChecklist(auth, request)
      if (!Number.isSafeInteger(response.idChecklist) || !Array.isArray(response.detalles)) throw new ApiError('invalid-response')
      if (isSessionCurrent(auth, sessionVersion)) {
        setExito(response)
        setConfirmar(false)
        setSeleccionados({})
        setObservaciones('')
      }
      try {
        await removeUncertainWriteLock(account, 'checklist', 'create')
        if (isSessionCurrent(auth, sessionVersion)) setResultadoIncierto(false)
      } catch { setErrorBloqueo(true) }
    } catch (reason) {
      if (persisted && !uncertainWriteResult(reason)) {
        try { await removeUncertainWriteLock(account, 'checklist', 'create'); if (isSessionCurrent(auth, sessionVersion)) setResultadoIncierto(false) }
        catch { if (isSessionCurrent(auth, sessionVersion)) setErrorBloqueo(true) }
      } else if (persisted && isSessionCurrent(auth, sessionVersion)) setResultadoIncierto(true)
      if (isSessionCurrent(auth, sessionVersion)) setError(!persisted
        ? 'No se envió: no se pudo establecer el bloqueo persistente.'
        : uncertainWriteResult(reason) ? '' : explicarError(reason, true))
      if (isSessionCurrent(auth, sessionVersion)) setConfirmar(false)
    } finally {
      lock.current = false
      if (isSessionCurrent(auth, sessionVersion)) setGuardando(false)
    }
  }

  const seleccionadosIds = Object.keys(seleccionados).map(Number)
  const cantidadProblemas = Object.values(seleccionados).filter((item) => item.resultado === 'CON_PROBLEMA').length
  if (!permisos) {
    return <div className="dashboard revision-page"><PageTitle eyebrow="PORTAL DEL PERSONAL" title="Revisión de equipos" /><Card><p>Esta funcionalidad está disponible únicamente para entrenadores.</p></Card></div>
  }

  return <div className="dashboard revision-page">
    <PageTitle description="Selecciona los elementos revisados y registra su estado. Los reportes de incidencias se generan automáticamente para los problemas enviados." eyebrow="ENTRENAMIENTO · GIMNASIO" title="Revisión de equipos" />
    {error && <p className="revision-feedback revision-feedback--error" role="alert">{error}</p>}
    {resultadoIncierto && <p className="revision-feedback revision-feedback--error" role="alert">{errorBloqueo ? 'Escrituras bloqueadas: no se pudo leer el almacenamiento de seguridad.' : 'Checklist bloqueado por resultado incierto. Pudo registrarse y generar incidencias automáticas. No reintentes; requiere revisión administrativa.'}</p>}
    {exito && <section aria-labelledby="revision-success-title" className="revision-feedback revision-feedback--success" role="status">
      <h2 id="revision-success-title">Checklist registrado</h2>
      <p>Registro #{exito.idChecklist} · {fechaHora(exito.fechaHora)}</p>
      <p>{exito.detalles.length} elementos revisados · {exito.detalles.filter((item) => item.resultado === 'CON_PROBLEMA').length} con problema.</p>
      {exito.detalles.some((item) => item.incidencia) && <ul>{exito.detalles.filter((item) => item.incidencia).map((item) => <li key={item.idDetalle}>{item.nombreElemento}: incidencia #{item.incidencia?.idReporte} · {item.incidencia?.estado}</li>)}</ul>}
    </section>}

    <form onSubmit={abrirConfirmacion}>
      <Card className="revision-card" title="Elementos de revisión" action={<span className="revision-count">{seleccionadosIds.length} / 100 seleccionados</span>}>
        {cargando ? <p aria-live="polite" className="revision-empty">Cargando elementos activos…</p>
          : error && elementos.length === 0 ? <Button onClick={() => void cargarElementos()} type="button" variant="secondary">Reintentar carga</Button>
            : elementos.length === 0 ? <p className="revision-empty">No hay elementos activos para revisar.</p>
              : <div className="revision-list">{elementos.map((elemento) => {
                const detalle = seleccionados[elemento.idElemento]
                const idBase = `elemento-${elemento.idElemento}`
                return <fieldset className={`revision-item${detalle ? ' revision-item--selected' : ''}`} key={elemento.idElemento}>
                  <legend className="revision-item__legend">
                    <label className="revision-item__select" htmlFor={`${idBase}-seleccionar`}>
                      <input checked={Boolean(detalle)} disabled={!detalle && seleccionadosIds.length >= 100} id={`${idBase}-seleccionar`} onChange={(event) => alternarElemento(elemento.idElemento, event.target.checked)} type="checkbox" />
                      <span><strong>{elemento.nombre}</strong><small>{elemento.tipo === 'MAQUINA' ? 'Máquina' : 'Área'}{elemento.ubicacion ? ` · ${elemento.ubicacion}` : ''}</small></span>
                    </label>
                  </legend>
                  {elemento.descripcion && <p className="revision-item__description">{elemento.descripcion}</p>}
                  {detalle && <div className="revision-item__fields">
                    <label className="revision-field" htmlFor={`${idBase}-resultado`}>Resultado
                      <select id={`${idBase}-resultado`} onChange={(event) => {
                        const resultado = event.target.value as ResultadoChecklist
                        actualizarDetalle(elemento.idElemento, {
                          resultado,
                          ...(resultado === 'CORRECTO' ? { descripcionIncidencia: '', prioridadIncidencia: '' } : {}),
                        })
                      }} value={detalle.resultado}>
                        <option value="CORRECTO">Correcto</option><option value="CON_PROBLEMA">Con problema</option>
                      </select>
                    </label>
                    <label className="revision-field" htmlFor={`${idBase}-observaciones`}>Observaciones del elemento
                      <textarea id={`${idBase}-observaciones`} maxLength={1000} onChange={(event) => actualizarDetalle(elemento.idElemento, { observaciones: event.target.value })} value={detalle.observaciones} />
                      <small>{detalle.observaciones.length}/1000</small>
                    </label>
                    {detalle.resultado === 'CON_PROBLEMA' && <div className="revision-problem-fields">
                      <label className="revision-field" htmlFor={`${idBase}-incidencia`}>Descripción de la incidencia <span aria-hidden="true">*</span>
                        <textarea aria-required="true" id={`${idBase}-incidencia`} maxLength={4000} onChange={(event) => actualizarDetalle(elemento.idElemento, { descripcionIncidencia: event.target.value })} required value={detalle.descripcionIncidencia} />
                        <small>{detalle.descripcionIncidencia.length}/4000</small>
                      </label>
                      <label className="revision-field" htmlFor={`${idBase}-prioridad`}>Prioridad <span aria-hidden="true">*</span>
                        <select aria-required="true" id={`${idBase}-prioridad`} onChange={(event) => actualizarDetalle(elemento.idElemento, { prioridadIncidencia: event.target.value as PrioridadIncidencia | '' })} required value={detalle.prioridadIncidencia}>
                          <option value="">Selecciona una prioridad</option><option value="BAJA">Baja</option><option value="MEDIA">Media</option><option value="ALTA">Alta</option>
                        </select>
                      </label>
                    </div>}
                  </div>}
                </fieldset>
              })}</div>}
      </Card>

      <Card className="revision-card revision-general-card" title="Observaciones generales">
        <label className="revision-field" htmlFor="observaciones-generales">Observaciones del checklist
          <textarea id="observaciones-generales" maxLength={1000} onChange={(event) => setObservaciones(event.target.value)} value={observaciones} />
          <small>{observaciones.length}/1000</small>
        </label>
        <div className="revision-submit"><Button disabled={!bloqueoListo || cargando || guardando || resultadoIncierto || errorBloqueo || elementos.length === 0 || seleccionadosIds.length === 0} type="submit">{resultadoIncierto ? 'Checklist bloqueado' : 'Revisar y confirmar'}</Button></div>
      </Card>
    </form>

    {confirmar && <div className="revision-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !guardando) setConfirmar(false) }}><section ref={confirmDialogRef} aria-labelledby="revision-confirm-title" aria-modal="true" className="revision-dialog" onKeyDown={handleConfirmKeyDown} onClick={(event) => event.stopPropagation()} role="dialog">
      <h2 id="revision-confirm-title">Confirmar checklist</h2>
      <p>Vas a registrar {seleccionadosIds.length} {seleccionadosIds.length === 1 ? 'elemento' : 'elementos'}: {seleccionadosIds.length - cantidadProblemas} correcto(s) y {cantidadProblemas} con problema.</p>
      <ul>{seleccionadosIds.map((id) => <li key={id}>{elementos.find((item) => item.idElemento === id)?.nombre}: {seleccionados[id].resultado === 'CORRECTO' ? 'Correcto' : `Con problema · prioridad ${seleccionados[id].prioridadIncidencia || 'pendiente'}`}</li>)}</ul>
      {cantidadProblemas > 0 && <p>El servidor creará automáticamente las incidencias correspondientes; no se enviarán solicitudes adicionales.</p>}
      <div className="revision-dialog__actions"><Button disabled={guardando} onClick={() => setConfirmar(false)} type="button" variant="secondary">Volver a revisar</Button><Button disabled={guardando} onClick={() => void enviarChecklist()} type="button">{guardando ? 'Registrando…' : 'Confirmar y registrar'}</Button></div>
    </section></div>}
  </div>
}
