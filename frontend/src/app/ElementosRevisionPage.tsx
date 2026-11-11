import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Eye, Pencil, Plus, Search, ToggleLeft, ToggleRight, X } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  actualizarElementoRevision,
  buscarElementoRevisionPorId,
  cambiarEstadoElementoRevision,
  crearElementoRevision,
  listarElementosRevision,
  type ElementoRevision,
  type ElementoRevisionRequest,
  type TipoElementoRevision,
} from '../api/elementosRevision'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './elementos-revision.css'

type FormValues = { nombre: string; tipo: TipoElementoRevision; descripcion: string; ubicacion: string }
type PendingWrite = { type: 'crear'; payload: ElementoRevisionRequest } | { type: 'editar'; id: number; payload: ElementoRevisionRequest } | { type: 'estado'; id: number; nombre: string; activo: boolean }
type UncertainWrite = { type: string; elementId?: number; nombre: string; occurredAt: string }
const STORAGE_KEY = 'elementos-revision:escritura-incierta'
const EMPTY_FORM: FormValues = { nombre: '', tipo: 'MAQUINA', descripcion: '', ubicacion: '' }

function readUncertain(): UncertainWrite | null {
  try { const value = window.sessionStorage.getItem(STORAGE_KEY); return value ? JSON.parse(value) as UncertainWrite : null }
  catch { return null }
}

function errorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
  const messages: Record<ApiError['code'], string> = {
    unauthenticated: 'La sesión expiró. Inicia sesión nuevamente.', forbidden: 'El servidor rechazó esta acción por permisos insuficientes.',
    'not-found': 'El elemento ya no existe o no se encontró.', validation: 'El servidor rechazó los datos. Revisa los campos.',
    conflict: 'La operación entra en conflicto con el estado actual.', server: 'El servidor no pudo completar la operación.',
    http: 'No fue posible completar la operación.', network: 'No se pudo conectar con el servidor.',
    'invalid-response': 'El servidor devolvió una respuesta no válida.',
  }
  return messages[error.code]
}

function isUncertain(error: unknown) {
  return error instanceof ApiError && ['network', 'invalid-response', 'server'].includes(error.code)
}

function validate(values: FormValues): { payload?: ElementoRevisionRequest; error?: string } {
  const nombre = values.nombre.trim()
  const descripcion = values.descripcion.trim()
  const ubicacion = values.ubicacion.trim()
  if (!nombre) return { error: 'El nombre es obligatorio.' }
  if (nombre.length > 120) return { error: 'El nombre no puede superar 120 caracteres.' }
  if (values.tipo !== 'MAQUINA' && values.tipo !== 'AREA') return { error: 'Selecciona un tipo válido.' }
  if (descripcion.length > 500) return { error: 'La descripción no puede superar 500 caracteres.' }
  if (ubicacion.length > 120) return { error: 'La ubicación no puede superar 120 caracteres.' }
  return { payload: { nombre, tipo: values.tipo, descripcion: descripcion || null, ubicacion: ubicacion || null } }
}

export function ElementosRevisionPage() {
  const auth = useAuth()
  const [elements, setElements] = useState<ElementoRevision[]>([])
  const [query, setQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [detail, setDetail] = useState<ElementoRevision | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ElementoRevision | null>(null)
  const [values, setValues] = useState<FormValues>(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [writeError, setWriteError] = useState('')
  const [pending, setPending] = useState<PendingWrite | null>(null)
  const [uncertain, setUncertain] = useState<UncertainWrite | null>(() => readUncertain())
  const [verificationLoaded, setVerificationLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const lock = useRef(false)

  const loadElements = useCallback(async () => {
    setLoading(true); setError('')
    try { setElements(await listarElementosRevision(auth)) }
    catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }, [auth])

  useEffect(() => { void loadElements() }, [loadElements])

  const visibleElements = elements.filter((element) => {
    const search = query.trim().toLocaleLowerCase()
    return (!search || [element.nombre, element.descripcion ?? '', element.ubicacion ?? ''].some((value) => value.toLocaleLowerCase().includes(search)))
      && (!typeFilter || element.tipo === typeFilter)
      && (!statusFilter || String(element.activo) === statusFilter)
  })

  function openCreate() {
    setEditing(null); setValues(EMPTY_FORM); setFormError(''); setWriteError(''); setFormOpen(true); setFeedback('')
  }

  async function openEdit(element: ElementoRevision) {
    setLoading(true); setError('')
    try {
      const fresh = await buscarElementoRevisionPorId(auth, element.idElemento)
      setEditing(fresh)
      setValues({ nombre: fresh.nombre, tipo: fresh.tipo, descripcion: fresh.descripcion ?? '', ubicacion: fresh.ubicacion ?? '' })
      setFormError(''); setWriteError(''); setFormOpen(true)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }

  async function openDetail(element: ElementoRevision) {
    setLoading(true); setError('')
    try { setDetail(await buscarElementoRevisionPorId(auth, element.idElemento)) }
    catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }

  function reviewForm(event: FormEvent) {
    event.preventDefault()
    const result = validate(values)
    if (!result.payload) { setFormError(result.error ?? 'Revisa los datos.'); return }
    setPending(editing ? { type: 'editar', id: editing.idElemento, payload: result.payload } : { type: 'crear', payload: result.payload })
    setFormError('')
  }

  function storeUncertain(marker: UncertainWrite) {
    setUncertain(marker); setVerificationLoaded(false)
    try { window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(marker)) } catch { /* Mantener el bloqueo en memoria de esta página. */ }
  }

  async function confirmWrite() {
    if (!pending || pending.type === 'estado' || lock.current || uncertain) return
    lock.current = true; setSaving(true); setWriteError(''); setFeedback('')
    try {
      const result = pending.type === 'crear'
        ? await crearElementoRevision(auth, pending.payload)
        : await actualizarElementoRevision(auth, pending.id, pending.payload)
      setElements((current) => pending.type === 'crear'
        ? [...current.filter((item) => item.idElemento !== result.idElemento), result].sort((a, b) => a.nombre.localeCompare(b.nombre) || a.idElemento - b.idElemento)
        : current.map((item) => item.idElemento === result.idElemento ? result : item).sort((a, b) => a.nombre.localeCompare(b.nombre) || a.idElemento - b.idElemento))
      setFeedback(`Elemento “${result.nombre}” ${pending.type === 'crear' ? 'creado' : 'actualizado'} según la respuesta del servidor. Los checklists e incidencias existentes no se modifican.`)
      setPending(null); setFormOpen(false); setEditing(null)
    } catch (reason) {
      if (isUncertain(reason)) {
        storeUncertain({ type: pending.type, ...(pending.type === 'editar' ? { elementId: pending.id } : {}), nombre: pending.payload.nombre, occurredAt: new Date().toISOString() })
        setPending(null); setFormOpen(false)
        setError('No se confirmó la respuesta. El cambio podría haberse aplicado; las escrituras quedan bloqueadas. Actualiza y verifica el estado real antes de habilitar otra operación.')
      } else setWriteError(errorMessage(reason))
    } finally { lock.current = false; setSaving(false) }
  }

  async function confirmStateChange() {
    if (!pending || pending.type !== 'estado' || lock.current || uncertain) return
    lock.current = true; setSaving(true); setWriteError(''); setFeedback('')
    try {
      const result = await cambiarEstadoElementoRevision(auth, pending.id, pending.activo)
      setElements((current) => current.map((item) => item.idElemento === result.idElemento ? result : item))
      setFeedback(`Elemento “${result.nombre}” ${result.activo ? 'activado' : 'desactivado'} según el servidor. Checklists e incidencias existentes se conservan sin cambios.`)
      setPending(null)
    } catch (reason) {
      if (isUncertain(reason)) {
        storeUncertain({ type: pending.activo ? 'activar' : 'desactivar', elementId: pending.id, nombre: pending.nombre, occurredAt: new Date().toISOString() })
        setPending(null)
        setError('No se confirmó la respuesta. El estado podría haber cambiado; las escrituras quedan bloqueadas hasta verificar el catálogo.')
      } else setWriteError(errorMessage(reason))
    } finally { lock.current = false; setSaving(false) }
  }

  async function refreshForVerification() {
    setLoading(true); setError('')
    try { setElements(await listarElementosRevision(auth)); setVerificationLoaded(true); setFeedback('Catálogo actualizado desde el servidor. Comprueba el elemento afectado antes de habilitar otra escritura.') }
    catch (reason) { setError(`No fue posible verificar el estado: ${errorMessage(reason)}`) }
    finally { setLoading(false) }
  }

  function acknowledgeVerification() {
    if (!uncertain || !verificationLoaded) return
    setUncertain(null); setVerificationLoaded(false)
    try { window.sessionStorage.removeItem(STORAGE_KEY) } catch { /* ignorar almacenamiento no disponible */ }
    setFeedback('Verificación confirmada. Las operaciones administrativas vuelven a estar habilitadas.')
  }

  const confirmPending = () => pending?.type === 'estado' ? void confirmStateChange() : void confirmWrite()

  return <div className="dashboard elementos-revision-page">
    <PageTitle description="Administra máquinas y áreas disponibles para las futuras revisiones. Solo el administrador puede gestionar este catálogo." eyebrow="ADMINISTRACIÓN · CATÁLOGO" title="Equipos y áreas de revisión" actions={<Button type="button" disabled={Boolean(uncertain) || saving} onClick={openCreate} icon={<Plus size={16}/>}>Nuevo elemento</Button>} />
    {feedback && <p className="elementos-feedback elementos-feedback--success" role="status">{feedback}</p>}
    {uncertain && <section className="elementos-uncertain" role="alert"><div><strong>Escritura pendiente de verificación</strong><p>{uncertain.type} del elemento “{uncertain.nombre}” sin confirmación del servidor. Las escrituras están bloqueadas para evitar duplicados o repetir cambios.</p><p>Actualiza el catálogo y revisa el estado real. Si no puedes determinarlo, no habilites nuevas operaciones.</p></div><div className="elementos-uncertain-actions"><Button type="button" variant="secondary" disabled={loading} onClick={() => void refreshForVerification()}>Actualizar catálogo</Button>{verificationLoaded && <Button type="button" disabled={loading} onClick={acknowledgeVerification}>Ya verifiqué; habilitar escritura</Button>}</div></section>}
    <Card className="elementos-card" title="Catálogo completo">
      <p className="elementos-hint">Búsqueda, tipo y estado se filtran localmente sobre el listado completo; el backend no ofrece endpoints de búsqueda, filtros ni paginación.</p>
      <div className="elementos-toolbar">
        <label className="elementos-search"><Search size={16} aria-hidden="true"/><span className="elementos-sr-only">Buscar por nombre, descripción o ubicación</span><input aria-label="Buscar elementos" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por nombre, descripción o ubicación" /></label>
        <label className="elementos-filter">Tipo<select aria-label="Filtrar por tipo" value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}><option value="">Todos</option><option value="MAQUINA">Máquina</option><option value="AREA">Área</option></select></label>
        <label className="elementos-filter">Estado<select aria-label="Filtrar por estado" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">Todos</option><option value="true">Activo</option><option value="false">Inactivo</option></select></label>
        <Button type="button" variant="secondary" disabled={!query && !typeFilter && !statusFilter} onClick={() => { setQuery(''); setTypeFilter(''); setStatusFilter('') }}>Limpiar filtros</Button>
      </div>
      {error && <div className="elementos-feedback elementos-feedback--error" role="alert"><span>{error}</span><Button type="button" variant="secondary" disabled={loading} onClick={() => void loadElements()}>Reintentar</Button></div>}
      {loading ? <p className="elementos-message" aria-live="polite">Cargando elementos…</p> : error ? null : visibleElements.length === 0 ? <p className="elementos-message">{elements.length ? 'No hay elementos que coincidan con los filtros.' : 'Todavía no hay elementos de revisión registrados.'}</p> : <div className="table-wrap elementos-table-wrap"><table className="activity-table elementos-table"><caption className="elementos-sr-only">Catálogo de equipos y áreas de revisión</caption><thead><tr><th scope="col">ELEMENTO</th><th scope="col">TIPO</th><th scope="col">UBICACIÓN</th><th scope="col">ESTADO</th><th scope="col">ACCIONES</th></tr></thead><tbody>{visibleElements.map((element) => <tr key={element.idElemento}><td data-label="Elemento"><strong>{element.nombre}</strong><small>{element.descripcion || 'Sin descripción'}</small></td><td data-label="Tipo">{element.tipo === 'MAQUINA' ? 'Máquina' : 'Área'}</td><td data-label="Ubicación">{element.ubicacion || '—'}</td><td data-label="Estado"><span className={`elementos-state elementos-state--${element.activo ? 'activo' : 'inactivo'}`}>{element.activo ? 'ACTIVO' : 'INACTIVO'}</span></td><td data-label="Acciones"><div className="elementos-actions"><Button type="button" variant="secondary" aria-label={`Detalle de ${element.nombre}`} onClick={() => void openDetail(element)} icon={<Eye size={14}/>}>Detalle</Button><Button type="button" variant="secondary" aria-label={`Editar ${element.nombre}`} disabled={Boolean(uncertain) || loading || saving} onClick={() => void openEdit(element)} icon={<Pencil size={14}/>}>Editar</Button><Button type="button" variant="secondary" aria-label={`${element.activo ? 'Desactivar' : 'Activar'} ${element.nombre}`} disabled={Boolean(uncertain) || loading || saving} onClick={() => { setWriteError(''); setPending({ type: 'estado', id: element.idElemento, nombre: element.nombre, activo: !element.activo }) }} icon={element.activo ? <ToggleLeft size={14}/> : <ToggleRight size={14}/>}>{element.activo ? 'Desactivar' : 'Activar'}</Button></div></td></tr>)}</tbody></table></div>}
    </Card>

    {formOpen && <div className="elementos-backdrop" onClick={() => { if (!saving) { setFormOpen(false); setPending(null) } }}><section className="elementos-dialog" role="dialog" aria-modal="true" aria-labelledby="elementos-form-title" onClick={(event) => event.stopPropagation()}><button className="elementos-close" type="button" aria-label="Cerrar formulario" disabled={saving} onClick={() => { setFormOpen(false); setPending(null) }}><X size={18}/></button><h2 id="elementos-form-title">{editing ? 'Editar elemento' : 'Crear elemento'}</h2><form className="elementos-form" onSubmit={reviewForm}>
      <label>Nombre <b>*</b><input aria-label="Nombre del elemento" required maxLength={120} value={values.nombre} onChange={(event) => setValues({ ...values, nombre: event.target.value })}/><small>{values.nombre.length}/120</small></label>
      <label>Tipo <b>*</b><select aria-label="Tipo de elemento" required value={values.tipo} onChange={(event) => setValues({ ...values, tipo: event.target.value as TipoElementoRevision })}><option value="MAQUINA">Máquina</option><option value="AREA">Área</option></select></label>
      <label>Descripción<textarea aria-label="Descripción del elemento" maxLength={500} value={values.descripcion} onChange={(event) => setValues({ ...values, descripcion: event.target.value })}/><small>{values.descripcion.length}/500</small></label>
      <label>Ubicación<input aria-label="Ubicación del elemento" maxLength={120} value={values.ubicacion} onChange={(event) => setValues({ ...values, ubicacion: event.target.value })}/><small>{values.ubicacion.length}/120</small></label>
      {formError && <p className="elementos-inline-error" role="alert">{formError}</p>}
      <p className="elementos-hint">Modificar el catálogo no altera snapshots de checklists ni incidencias ya registradas.</p>
      <div className="elementos-actions"><Button type="button" variant="secondary" onClick={() => setFormOpen(false)}>Cancelar</Button><Button type="submit" disabled={Boolean(uncertain) || saving}>Revisar cambios</Button></div>
    </form></section></div>}

    {detail && <div className="elementos-backdrop" onClick={() => setDetail(null)}><section className="elementos-dialog" role="dialog" aria-modal="true" aria-labelledby="elementos-detail-title" onClick={(event) => event.stopPropagation()}><button className="elementos-close" type="button" aria-label="Cerrar detalle" onClick={() => setDetail(null)}><X size={18}/></button><h2 id="elementos-detail-title">Detalle del elemento</h2><dl className="elementos-detail"><div><dt>Nombre</dt><dd>{detail.nombre}</dd></div><div><dt>Tipo</dt><dd>{detail.tipo === 'MAQUINA' ? 'Máquina' : 'Área'}</dd></div><div><dt>Estado</dt><dd>{detail.activo ? 'Activo' : 'Inactivo'}</dd></div><div><dt>Ubicación</dt><dd>{detail.ubicacion || 'Sin ubicación'}</dd></div><div><dt>Descripción</dt><dd>{detail.descripcion || 'Sin descripción'}</dd></div><div><dt>Última actualización</dt><dd>{detail.fechaActualizacion}</dd></div></dl><Button type="button" variant="secondary" onClick={() => setDetail(null)}>Cerrar</Button></section></div>}

    {pending && <div className="elementos-backdrop" onClick={() => { if (!saving) setPending(null) }}><section className="elementos-dialog" role="dialog" aria-modal="true" aria-labelledby="elementos-confirm-title" onClick={(event) => event.stopPropagation()}><h2 id="elementos-confirm-title">Confirmar {pending.type === 'crear' ? 'creación' : pending.type === 'editar' ? 'edición' : pending.activo ? 'activación' : 'desactivación'}</h2>{pending.type === 'estado' ? <p>{pending.activo ? `El elemento “${pending.nombre}” volverá a estar disponible para futuras revisiones.` : `El elemento “${pending.nombre}” se retirará de futuras selecciones del entrenador.`} Los checklists e incidencias existentes no se modifican.</p> : <><p>Revisa los datos que se enviarán:</p><dl className="elementos-detail"><div><dt>Nombre</dt><dd>{pending.payload.nombre}</dd></div><div><dt>Tipo</dt><dd>{pending.payload.tipo === 'MAQUINA' ? 'Máquina' : 'Área'}</dd></div><div><dt>Descripción</dt><dd>{pending.payload.descripcion || 'Sin descripción'}</dd></div><div><dt>Ubicación</dt><dd>{pending.payload.ubicacion || 'Sin ubicación'}</dd></div></dl><p>La edición actualiza el catálogo únicamente; los registros históricos se conservan.</p></>}{writeError && <p className="elementos-inline-error" role="alert">{writeError}</p>}<div className="elementos-actions"><Button type="button" variant="secondary" disabled={saving} onClick={() => { setPending(null); setWriteError('') }}>Volver</Button><Button type="button" disabled={saving || Boolean(uncertain)} onClick={confirmPending}>{saving ? 'Enviando…' : 'Confirmar y guardar'}</Button></div></section></div>}
  </div>
}
