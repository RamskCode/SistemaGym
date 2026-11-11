import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Eye, Pencil, Plus, Search, UserRoundX, X } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  actualizarPlan,
  buscarPlanPorId,
  buscarPlanesPorNombre,
  crearPlan,
  desactivarPlan,
  listarPlanes,
  listarPlanesPorEstado,
  type Plan,
  type PlanRequest,
} from '../api/planes'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './planes.css'
import { PaseDiarioPricePanel } from './PaseDiarioPricePanel'

type FormValues = { nombre: string; descripcion: string; precio: string; duracionDias: string }
type PendingWrite = { type: 'crear'; payload: PlanRequest } | { type: 'editar'; id: number; payload: PlanRequest } | { type: 'desactivar'; id: number; nombre: string }
type UncertainWrite = { type: string; planId?: number; nombre: string; occurredAt: string }
const STORAGE_KEY = 'planes:escritura-incierta'
const EMPTY_FORM: FormValues = { nombre: '', descripcion: '', precio: '', duracionDias: '' }

function readUncertain(): UncertainWrite | null {
  try {
    const value = window.sessionStorage.getItem(STORAGE_KEY)
    return value ? JSON.parse(value) as UncertainWrite : null
  } catch { return null }
}

function formatPrice(value: number) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(value)
}

function errorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
  const messages: Record<ApiError['code'], string> = {
    unauthenticated: 'La sesión expiró. Inicia sesión nuevamente.',
    forbidden: 'No tienes permiso para realizar esta acción.',
    'not-found': 'El plan ya no existe o no se encontró.',
    validation: 'Revisa los datos ingresados y vuelve a intentarlo.',
    conflict: 'El nombre del plan entra en conflicto con otro plan existente.',
    server: 'No se pudo completar la consulta. Inténtalo más tarde.',
    http: 'No fue posible completar la solicitud.',
    network: 'No se pudo conectar con el servidor.',
    'invalid-response': 'El servidor devolvió una respuesta no válida.',
  }
  return messages[error.code]
}

function esResultadoIncierto(error: unknown) {
  return error instanceof ApiError && ['network', 'invalid-response', 'server'].includes(error.code)
}

function validate(values: FormValues): { payload?: PlanRequest; error?: string } {
  const nombre = values.nombre.trim()
  const descripcion = values.descripcion.trim()
  if (!nombre) return { error: 'El nombre del plan es obligatorio.' }
  if (nombre.length > 80) return { error: 'El nombre no puede superar 80 caracteres.' }
  if (descripcion.length > 255) return { error: 'La descripción no puede superar 255 caracteres.' }
  if (!/^\d+(?:\.\d{1,2})?$/.test(values.precio.trim()) || Number(values.precio) <= 0) {
    return { error: 'El precio debe ser mayor que cero y admitir como máximo dos decimales.' }
  }
  if (!/^\d+$/.test(values.duracionDias.trim()) || Number(values.duracionDias) <= 0 || !Number.isSafeInteger(Number(values.duracionDias))) {
    return { error: 'La duración debe ser un número entero mayor que cero.' }
  }
  return { payload: { nombre, descripcion: descripcion || null, precio: Number(values.precio), duracionDias: Number(values.duracionDias) } }
}

export function PlanesPage() {
  const auth = useAuth()
  const [activeTab, setActiveTab] = useState<'planes' | 'pase'>('planes')
  const tabsRef = useRef<Array<HTMLButtonElement | null>>([])
  const [plans, setPlans] = useState<Plan[]>([])
  const [query, setQuery] = useState('')
  const [stateFilter, setStateFilter] = useState('')
  const [appliedQuery, setAppliedQuery] = useState('')
  const [appliedState, setAppliedState] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [detail, setDetail] = useState<Plan | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Plan | null>(null)
  const [values, setValues] = useState<FormValues>(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [writeError, setWriteError] = useState('')
  const [pending, setPending] = useState<PendingWrite | null>(null)
  const [uncertain, setUncertain] = useState<UncertainWrite | null>(() => readUncertain())
  const [verificationLoaded, setVerificationLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const lock = useRef(false)
  const sequence = useRef(0)
  const dialogRef = useRef<HTMLElement | null>(null)
  const dialogTriggerRef = useRef<HTMLElement | null>(null)
  const dialogTriggerSelectorRef = useRef('')
  const wasDialogOpen = useRef(false)
  const savingRef = useRef(saving)
  savingRef.current = saving
  const dialogOpen = formOpen || detail !== null || pending !== null

  useLayoutEffect(() => {
    if (!dialogOpen) {
      if (wasDialogOpen.current) {
        const trigger = dialogTriggerSelectorRef.current
          ? document.querySelector<HTMLElement>(dialogTriggerSelectorRef.current)
          : dialogTriggerRef.current?.isConnected ? dialogTriggerRef.current : null
        trigger?.focus()
      }
      wasDialogOpen.current = false
      return
    }

    wasDialogOpen.current = true
    const dialog = dialogRef.current
    const firstFocusable = dialog?.querySelector<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
    )
    firstFocusable?.focus()

    function handleDialogKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        if (savingRef.current) return
        event.preventDefault()
        if (pending) setPending(null)
        else if (detail) {
          setDetail(null)
          window.setTimeout(() => {
            const trigger = dialogTriggerSelectorRef.current
              ? document.querySelector<HTMLElement>(dialogTriggerSelectorRef.current)
              : dialogTriggerRef.current
            trigger?.focus()
          }, 0)
        } else { setFormOpen(false); setPending(null) }
        return
      }
      if (event.key !== 'Tab') return
      const currentDialog = dialogRef.current
      const focusable = currentDialog?.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
      )
      if (!focusable?.length) { event.preventDefault(); currentDialog?.focus(); return }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && (document.activeElement === first || !currentDialog?.contains(document.activeElement))) {
        event.preventDefault(); last.focus()
      } else if (!event.shiftKey && (document.activeElement === last || !currentDialog?.contains(document.activeElement))) {
        event.preventDefault(); first.focus()
      }
    }
    document.addEventListener('keydown', handleDialogKeyDown, true)
    return () => document.removeEventListener('keydown', handleDialogKeyDown, true)
  }, [dialogOpen, formOpen, detail?.idPlan, pending?.type])

  const loadPlans = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setError('')
    try {
      let results: Plan[]
      if (appliedQuery && appliedState) {
        const [byName, byState] = await Promise.all([
          buscarPlanesPorNombre(auth, appliedQuery),
          listarPlanesPorEstado(auth, appliedState as 'ACTIVO' | 'INACTIVO'),
        ])
        const stateIds = new Set(byState.map((plan) => plan.idPlan))
        results = byName.filter((plan) => stateIds.has(plan.idPlan))
      } else if (appliedQuery) results = await buscarPlanesPorNombre(auth, appliedQuery)
      else if (appliedState) results = await listarPlanesPorEstado(auth, appliedState as 'ACTIVO' | 'INACTIVO')
      else results = await listarPlanes(auth)
      if (request === sequence.current) setPlans(results)
    } catch (reason) {
      if (request === sequence.current) setError(errorMessage(reason))
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [auth, appliedQuery, appliedState])

  useEffect(() => { void loadPlans() }, [loadPlans])

  function openCreate(trigger?: HTMLElement) {
    if (trigger) dialogTriggerRef.current = trigger
    setEditing(null); setValues(EMPTY_FORM); setFormError(''); setWriteError(''); setFormOpen(true); setFeedback('')
  }

  async function openEdit(plan: Plan) {
    setLoading(true); setError(''); setFeedback('')
    try {
      const fresh = await buscarPlanPorId(auth, plan.idPlan)
      setEditing(fresh)
      setValues({ nombre: fresh.nombre, descripcion: fresh.descripcion ?? '', precio: String(fresh.precio), duracionDias: String(fresh.duracionDias) })
      setFormError(''); setWriteError(''); setFormOpen(true)
    } catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }

  async function openDetail(plan: Plan) {
    setDetail(null); setLoading(true); setError('')
    try { setDetail(await buscarPlanPorId(auth, plan.idPlan)) }
    catch (reason) { setError(errorMessage(reason)) }
    finally { setLoading(false) }
  }

  function reviewForm(event: FormEvent) {
    event.preventDefault()
    const validated = validate(values)
    if (!validated.payload) { setFormError(validated.error ?? 'Revisa los datos ingresados.'); return }
    setFormError('')
    setWriteError('')
    setPending(editing
      ? { type: 'editar', id: editing.idPlan, payload: validated.payload }
      : { type: 'crear', payload: validated.payload })
  }

  async function confirmWrite() {
    if (!pending || pending.type === 'desactivar' || lock.current || uncertain) return
    lock.current = true; setSaving(true); setError(''); setWriteError(''); setFeedback('')
    try {
      const result = pending.type === 'crear'
        ? await crearPlan(auth, pending.payload)
        : await actualizarPlan(auth, pending.id, pending.payload)
      setPlans((current) => pending.type === 'crear'
        ? [result, ...current.filter((item) => item.idPlan !== result.idPlan)]
        : current.map((item) => item.idPlan === result.idPlan ? result : item))
      setPending(null); setFormOpen(false); setEditing(null)
      setFeedback(pending.type === 'crear' ? `Plan “${result.nombre}” creado.` : `Plan “${result.nombre}” actualizado. El precio acordado de membresías existentes no cambia.`)
      setAppliedQuery(''); setQuery(''); setAppliedState(''); setStateFilter('')
    } catch (reason) {
      if (esResultadoIncierto(reason)) {
        const marker: UncertainWrite = { type: pending.type, ...(pending.type === 'editar' ? { planId: pending.id } : {}), nombre: pending.payload.nombre, occurredAt: new Date().toISOString() }
        setUncertain(marker)
        try { window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(marker)) } catch { /* lock remains for this page session */ }
        setPending(null); setFormOpen(false)
        setError('No pudimos confirmar el cambio. Revisa los planes antes de volver a intentarlo.')
      } else setWriteError(errorMessage(reason))
    } finally { lock.current = false; setSaving(false) }
  }

  async function confirmDeactivation() {
    if (!pending || pending.type !== 'desactivar' || lock.current || uncertain) return
    lock.current = true; setSaving(true); setError(''); setWriteError(''); setFeedback('')
    try {
      const result = await desactivarPlan(auth, pending.id)
      setPlans((current) => current.map((item) => item.idPlan === result.idPlan ? result : item))
      setAppliedQuery(''); setQuery(''); setAppliedState(''); setStateFilter('')
      setFeedback(`El plan “${result.nombre}” quedó ${result.estado.toLowerCase()}.`)
      setPending(null)
    } catch (reason) {
      if (esResultadoIncierto(reason)) {
        const marker: UncertainWrite = { type: 'desactivar', planId: pending.id, nombre: pending.nombre, occurredAt: new Date().toISOString() }
        setUncertain(marker)
        try { window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(marker)) } catch { /* lock remains for this page session */ }
        setPending(null)
        setError('No pudimos confirmar la desactivación. Revisa el estado del plan antes de volver a intentarlo.')
      } else setWriteError(errorMessage(reason))
    } finally { lock.current = false; setSaving(false) }
  }

  async function reconcileAndUnlock() {
    setLoading(true); setError('')
    try {
      const latest = await listarPlanes(auth)
      setPlans(latest)
      setAppliedQuery(''); setQuery(''); setAppliedState(''); setStateFilter('')
      setVerificationLoaded(true)
      setFeedback('Planes actualizados. Revisa el resultado antes de volver a operar.')
    } catch (reason) { setError(`No fue posible verificar el estado: ${errorMessage(reason)}`) }
    finally { setLoading(false) }
  }

  function acknowledgeVerification() {
    if (!uncertain || !verificationLoaded) return
    setUncertain(null)
    setVerificationLoaded(false)
    try { window.sessionStorage.removeItem(STORAGE_KEY) } catch { /* ignore unavailable storage */ }
      setFeedback('Revisión confirmada. Ya puedes continuar con los cambios.')
  }

  async function confirmPending() {
    if (!pending) return
    if (pending.type === 'desactivar') await confirmDeactivation()
    else await confirmWrite()
  }

  function handleTabKeyDown(event: ReactKeyboardEvent<HTMLButtonElement>, index: number) {
    const nextIndex = event.key === 'ArrowRight' ? (index + 1) % 2
      : event.key === 'ArrowLeft' ? (index + 1) % 2
        : event.key === 'Home' ? 0 : event.key === 'End' ? 1 : -1
    if (nextIndex < 0) return
    event.preventDefault()
    const nextTab = nextIndex === 0 ? 'planes' : 'pase'
    setActiveTab(nextTab)
    tabsRef.current[nextIndex]?.focus()
  }

  return <div className="dashboard planes-page">
    <PageTitle description="Gestiona los planes de membresía y las tarifas de acceso al gimnasio." eyebrow="ADMINISTRACIÓN · ACCESOS" title="Planes y tarifas" />
    {feedback && <p className="planes-feedback planes-feedback--success" role="status">{feedback}</p>}
    <div className="planes-tabs" role="tablist" aria-label="Planes y tarifas">
      <button ref={(element) => { tabsRef.current[0] = element }} id="planes-tab-memberships" aria-controls="planes-panel-memberships" aria-selected={activeTab === 'planes'} className="planes-tab" onClick={() => setActiveTab('planes')} onKeyDown={(event) => handleTabKeyDown(event, 0)} role="tab" tabIndex={activeTab === 'planes' ? 0 : -1} type="button">Planes de membresía</button>
      <button ref={(element) => { tabsRef.current[1] = element }} id="planes-tab-daily" aria-controls="planes-panel-daily" aria-selected={activeTab === 'pase'} className="planes-tab" onClick={() => setActiveTab('pase')} onKeyDown={(event) => handleTabKeyDown(event, 1)} role="tab" tabIndex={activeTab === 'pase' ? 0 : -1} type="button">Pase diario</button>
    </div>
    <section id="planes-panel-memberships" aria-labelledby="planes-tab-memberships" className="planes-tabpanel" hidden={activeTab !== 'planes'} role="tabpanel" tabIndex={0}>
      <header className="planes-section-header"><div><h2>Planes de membresía</h2><p>Administra precios, duración y disponibilidad.</p></div><Button type="button" onClick={(event) => { dialogTriggerRef.current = event.currentTarget; dialogTriggerSelectorRef.current = ''; openCreate(event.currentTarget) }} disabled={Boolean(uncertain) || saving} icon={<Plus size={16} />}>Nuevo plan</Button></header>
      {uncertain && <section className="planes-uncertain" role="alert"><div><strong>No se confirmó el último cambio</strong><p>Revisa el catálogo antes de volver a operar. Las acciones de escritura están pausadas hasta verificar el resultado.</p></div><div className="planes-uncertain-actions"><Button type="button" variant="secondary" disabled={loading} onClick={() => void reconcileAndUnlock()}>Actualizar planes</Button>{verificationLoaded && <Button type="button" onClick={acknowledgeVerification}>Confirmar revisión</Button>}</div></section>}
      <Card className="planes-card">
      <form className="planes-toolbar" role="search" aria-label="Buscar y filtrar planes" onSubmit={(event) => { event.preventDefault(); setAppliedQuery(query.trim()); setAppliedState(stateFilter) }}>
        <label className="planes-search search-field"><Search size={17} aria-hidden="true"/><span className="planes-sr-only">Buscar por nombre del plan</span><input aria-label="Buscar plan por nombre" value={query} maxLength={80} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por nombre del plan" /></label>
        <label className="planes-filter"><span>Estado</span><select aria-label="Filtrar por estado" value={stateFilter} onChange={(event) => setStateFilter(event.target.value)}><option value="">Todos los estados</option><option value="ACTIVO">Activo</option><option value="INACTIVO">Inactivo</option></select></label>
        <div className="planes-toolbar__actions"><Button type="submit" disabled={loading}>Aplicar filtros</Button>
        <Button type="button" variant="secondary" disabled={loading || (!query && !stateFilter && !appliedQuery && !appliedState)} onClick={() => { setQuery(''); setStateFilter(''); setAppliedQuery(''); setAppliedState('') }}>Limpiar filtros</Button></div>
      </form>
      {error && <div className="planes-feedback planes-feedback--error" role="alert"><span>{error}</span><Button type="button" variant="secondary" disabled={loading} onClick={() => void loadPlans()}>Reintentar consulta</Button></div>}
      {loading ? <p className="planes-message" role="status">Cargando planes…</p> : error ? null : plans.length === 0 ? <div className="planes-empty"><span aria-hidden="true"><Search size={20}/></span><p>{appliedQuery || appliedState ? 'No hay planes que coincidan con los filtros.' : 'Aún no hay planes registrados.'}</p><small>{appliedQuery || appliedState ? 'Prueba otros criterios o limpia los filtros.' : 'Los planes registrados aparecerán en este catálogo.'}</small></div> : <div className="table-wrap planes-table-wrap"><table className="activity-table planes-table"><caption className="planes-sr-only">Planes disponibles en el catálogo</caption><thead><tr><th scope="col">PLAN</th><th scope="col">PRECIO</th><th scope="col">DURACIÓN</th><th scope="col">ESTADO</th><th scope="col">ACCIONES</th></tr></thead><tbody>{plans.map((plan) => <tr className={detail?.idPlan === plan.idPlan || editing?.idPlan === plan.idPlan ? 'planes-row-selected' : undefined} key={plan.idPlan}><td data-label="Plan"><strong>{plan.nombre}</strong><small>{plan.descripcion || 'Sin descripción'}</small></td><td data-label="Precio">{formatPrice(plan.precio)}</td><td data-label="Duración">{plan.duracionDias} días</td><td data-label="Estado"><span className={`planes-state planes-state--${plan.estado.toLowerCase()}`}>{plan.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}</span></td><td data-label="Acciones"><div className="planes-actions"><Button data-plan-detail-trigger={plan.idPlan} type="button" variant="secondary" aria-label={`Ver detalle de ${plan.nombre}`} onClick={(event) => { dialogTriggerRef.current = event.currentTarget; dialogTriggerSelectorRef.current = `[data-plan-detail-trigger="${plan.idPlan}"]`; void openDetail(plan) }} icon={<Eye size={14}/>}>Detalle</Button><Button data-plan-edit-trigger={plan.idPlan} type="button" variant="secondary" aria-label={`Editar ${plan.nombre}`} disabled={Boolean(uncertain) || loading || saving} onClick={(event) => { dialogTriggerRef.current = event.currentTarget; dialogTriggerSelectorRef.current = `[data-plan-edit-trigger="${plan.idPlan}"]`; void openEdit(plan) }} icon={<Pencil size={14}/>}>Editar</Button>{plan.estado === 'ACTIVO' && <Button data-plan-state-trigger={plan.idPlan} type="button" variant="secondary" aria-label={`Desactivar ${plan.nombre}`} disabled={Boolean(uncertain) || loading || saving} onClick={(event) => { dialogTriggerRef.current = event.currentTarget; dialogTriggerSelectorRef.current = `[data-plan-state-trigger="${plan.idPlan}"]`; setPending({ type: 'desactivar', id: plan.idPlan, nombre: plan.nombre }); setWriteError(''); setFeedback('') }} icon={<UserRoundX size={14}/>}>Desactivar</Button>}</div></td></tr>)}</tbody></table></div>}
      </Card>
    </section>
    <section id="planes-panel-daily" aria-labelledby="planes-tab-daily" className="planes-tabpanel" hidden={activeTab !== 'pase'} role="tabpanel" tabIndex={0}>
      <header className="planes-section-header"><div><h2>Tarifa de acceso diario</h2><p>Precio vigente para ingresar durante un día calendario.</p></div></header>
      <PaseDiarioPricePanel auth={auth} canManage={auth.user?.roles.includes('ADMINISTRADOR') === true} />
    </section>

    {formOpen && !pending && <div className="planes-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) { setFormOpen(false); setPending(null) } }}><section ref={dialogRef} tabIndex={-1} className="planes-dialog planes-dialog--form" role="dialog" aria-modal="true" aria-labelledby="planes-form-title" onClick={(event) => event.stopPropagation()}><button className="planes-close" type="button" aria-label="Cerrar formulario" disabled={saving} onClick={() => { setFormOpen(false); setPending(null) }}><X size={18}/></button><p className="planes-dialog__eyebrow">CATÁLOGO DE PLANES</p><h2 id="planes-form-title">{editing ? 'Editar plan' : 'Crear plan'}</h2><p className="planes-dialog__intro">{editing ? `Actualiza la información de ${editing.nombre}.` : 'Define el nombre, precio y vigencia del plan.'}</p><form className="planes-form" noValidate onSubmit={reviewForm}>
      <section className="planes-form-section" aria-labelledby="planes-details-title"><h3 id="planes-details-title">Información del plan</h3>
      <label htmlFor="plan-name">Nombre <b aria-hidden="true">*</b><input id="plan-name" aria-label="Nombre del plan" required maxLength={80} aria-describedby={formError ? 'planes-form-error' : undefined} aria-invalid={Boolean(formError && !values.nombre.trim())} value={values.nombre} onChange={(event) => setValues({ ...values, nombre: event.target.value })}/></label>
      <label htmlFor="plan-description">Descripción <span className="planes-optional">Opcional</span><textarea id="plan-description" aria-label="Descripción del plan" maxLength={255} aria-describedby="plan-description-count" value={values.descripcion} onChange={(event) => setValues({ ...values, descripcion: event.target.value })}/><small id="plan-description-count">{values.descripcion.length}/255 caracteres</small></label>
      <div className="planes-form-grid"><label htmlFor="plan-price">Precio (S/) <b aria-hidden="true">*</b><input id="plan-price" aria-label="Precio" required inputMode="decimal" type="number" min="0.01" step="0.01" aria-describedby={formError ? 'planes-form-error' : undefined} aria-invalid={Boolean(formError && (!values.precio.trim() || Number(values.precio) <= 0))} value={values.precio} onChange={(event) => setValues({ ...values, precio: event.target.value })}/></label>
      <label htmlFor="plan-duration">Duración <b aria-hidden="true">*</b><span className="planes-input-suffix"><input id="plan-duration" aria-label="Duración en días" required inputMode="numeric" type="number" min="1" step="1" aria-describedby={formError ? 'planes-form-error' : undefined} aria-invalid={Boolean(formError && (!values.duracionDias.trim() || Number(values.duracionDias) <= 0))} value={values.duracionDias} onChange={(event) => setValues({ ...values, duracionDias: event.target.value })}/><span>días</span></span></label></div>
      </section>
      {formError && <p id="planes-form-error" className="planes-inline-error" role="alert">{formError}</p>}
      <p className="planes-hint">Cambiar el precio del plan no implica que cambie el precio acordado de membresías existentes.</p>
      <div className="planes-actions planes-form-actions"><Button type="button" variant="secondary" disabled={saving} onClick={() => setFormOpen(false)}>Cancelar</Button><Button type="submit" disabled={Boolean(uncertain) || saving}>{editing ? 'Revisar cambios' : 'Revisar plan'}</Button></div>
    </form></section></div>}

    {detail && <div className="planes-backdrop" onClick={() => setDetail(null)}><section ref={dialogRef} tabIndex={-1} className="planes-dialog" role="dialog" aria-modal="true" aria-labelledby="planes-detail-title" onClick={(event) => event.stopPropagation()}><button className="planes-close" type="button" aria-label="Cerrar detalle" onClick={() => setDetail(null)}><X size={18}/></button><p className="planes-dialog__eyebrow">CATÁLOGO DE PLANES</p><h2 id="planes-detail-title">Detalle del plan</h2><dl className="planes-detail"><div><dt>Nombre</dt><dd>{detail.nombre}</dd></div><div><dt>Precio</dt><dd>{formatPrice(detail.precio)}</dd></div><div><dt>Duración</dt><dd>{detail.duracionDias} días</dd></div><div><dt>Estado</dt><dd><span className={`planes-state planes-state--${detail.estado.toLowerCase()}`}>{detail.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}</span></dd></div><div className="planes-detail__wide"><dt>Descripción</dt><dd>{detail.descripcion || 'Sin descripción'}</dd></div><div><dt>Creado</dt><dd>{detail.fechaCreacion}</dd></div></dl><Button type="button" variant="secondary" onClick={() => setDetail(null)}>Cerrar</Button></section></div>}

    {pending && <div className="planes-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setPending(null) }}><section ref={dialogRef} tabIndex={-1} className="planes-dialog planes-dialog--confirm" role="dialog" aria-modal="true" aria-labelledby="planes-confirm-title" onClick={(event) => event.stopPropagation()}><p className="planes-dialog__eyebrow">REVISIÓN ANTES DE GUARDAR</p><h2 id="planes-confirm-title">Confirmar {pending.type === 'crear' ? 'creación' : pending.type === 'editar' ? 'edición' : 'desactivación'}</h2>{pending.type === 'desactivar' ? <><p>El plan <strong>“{pending.nombre}”</strong> no estará disponible para nuevas membresías. Las membresías existentes conservan las condiciones acordadas.</p></> : <><p>Confirma los datos del plan antes de enviar los cambios.</p><dl className="planes-detail"><div><dt>Nombre</dt><dd>{pending.payload.nombre}</dd></div><div><dt>Descripción</dt><dd>{pending.payload.descripcion || 'Sin descripción'}</dd></div><div><dt>Precio</dt><dd>{formatPrice(pending.payload.precio)}</dd></div><div><dt>Duración</dt><dd>{pending.payload.duracionDias} días</dd></div></dl>{pending.type === 'editar' && <p>El precio acordado de membresías existentes no se modifica por este cambio.</p>}</>}{writeError && <p className="planes-inline-error" role="alert">{writeError}</p>}<div className="planes-actions"><Button type="button" variant="secondary" disabled={saving} onClick={() => { setPending(null); setWriteError('') }}>Volver a editar</Button><Button type="button" disabled={saving || Boolean(uncertain)} onClick={() => void confirmPending()}>{saving ? 'Enviando…' : pending.type === 'desactivar' ? 'Confirmar desactivación' : 'Confirmar y guardar'}</Button></div></section></div>}
  </div>
}
