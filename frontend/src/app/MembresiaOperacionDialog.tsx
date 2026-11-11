import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  registrarMembresia,
  registrarPagoPosterior,
  consultarOperacionPorClave,
  type Membresia,
  type PagoPosteriorMembresiaRespuesta,
  type Plan,
  type RegistroMembresiaRespuesta,
} from '../api/membresias'
import type { Socio } from '../api/socios'
import type { AuthenticatedRequestContext } from '../api/http'
import { Button } from '../components/Button'
import { addMembresiaWriteMarker, errorIsUncertain, markerBlocks, removeMembresiaWriteMarker, sessionIsCurrent, updateMembresiaWriteMarker, type AnyMembresiaWriteMarker, type MembresiaWriteMarker } from './membresiasUncertainWrites'

const METODOS_PAGO = ['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA', 'OTRO'] as const
type Operacion = 'nueva' | 'pago'
type Props = {
  auth: AuthenticatedRequestContext
  account: string
  blockedMarkers: AnyMembresiaWriteMarker[]
  onMarkerChange: (marker: MembresiaWriteMarker, blocked: boolean) => void
  operacion: Operacion
  socios: Socio[]
  planes: Plan[]
  membresia?: Membresia
  saldoPendiente?: number
  onClose: () => void
  onCreated: (response: RegistroMembresiaRespuesta) => void
  onPaid: (response: PagoPosteriorMembresiaRespuesta) => void
}

function mantenerFocoEnDialogo(event: ReactKeyboardEvent<HTMLElement>) {
  if (event.key !== 'Tab') return
  const elements = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
    'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )).filter((element) => !element.hidden)
  if (!elements.length) { event.preventDefault(); return }
  if (event.shiftKey && document.activeElement === elements[0]) {
    event.preventDefault(); elements[elements.length - 1].focus()
  } else if (!event.shiftKey && document.activeElement === elements[elements.length - 1]) {
    event.preventDefault(); elements[0].focus()
  }
}

function centavos(raw: string): bigint | null {
  const value = raw.trim()
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  try { return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0')) } catch { return null }
}

function numeroDesdeCentavos(value: bigint): number {
  const whole = value / 100n
  const cents = String(value % 100n).padStart(2, '0')
  return Number(`${whole}.${cents}`)
}

function aCentavos(value: number): bigint {
  const [whole, fraction = ''] = value.toFixed(2).split('.')
  return BigInt(whole) * 100n + BigInt(fraction)
}

function formatear(value: number) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(value)
}

function descripcionError(reason: unknown) {
  if (reason instanceof ApiError && (reason.code === 'network' || reason.code === 'invalid-response')) {
    return 'No se pudo confirmar el resultado del cobro. Podría haberse registrado. Verifica el estado de la membresía antes de intentar otra vez.'
  }
  if (reason instanceof ApiError) return reason.message
  return 'No se pudo confirmar el resultado del cobro. Verifica el estado de la membresía antes de intentar otra vez.'
}

export function MembresiaOperacionDialog({ auth, account, blockedMarkers, onMarkerChange, operacion, socios, planes, membresia, saldoPendiente, onClose, onCreated, onPaid }: Props) {
  const [idSocio, setIdSocio] = useState('')
  const [idPlan, setIdPlan] = useState('')
  const [importe, setImporte] = useState('')
  const [metodoPago, setMetodoPago] = useState('')
  const [referencia, setReferencia] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [error, setError] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)
  const [recuperando, setRecuperando] = useState(false)
  const [attempt, setAttempt] = useState<MembresiaWriteMarker | null>(null)
  const lock = useRef(false)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const plan = planes.find((item) => String(item.idPlan) === idPlan)
  const socio = socios.find((item) => String(item.idSocio) === idSocio)
  const valorCentavos = centavos(importe)
  const precioCentavos = plan ? aCentavos(plan.precio) : null
  const saldoCentavos = saldoPendiente === undefined ? null : aCentavos(saldoPendiente)
  const operationId = operacion === 'pago' ? membresia?.idMembresia : undefined
  const matching = blockedMarkers.find((item) => item.operation === operacion
    && (operacion === 'nueva' || item.operation === 'pago' && (item.idMembresia === operationId || item.idMembresia === -1)))
  const unresolvedMarker: AnyMembresiaWriteMarker | null = attempt ?? matching ?? null
  const blocked = markerBlocks(blockedMarkers, operacion, operationId) && !attempt

  useEffect(() => {
    if (matching && 'key' in matching) setAttempt(matching)
  }, [matching])

  useEffect(() => { closeButtonRef.current?.focus() }, [])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !guardando) onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [guardando, onClose])

  async function resolverResultado(keyed: MembresiaWriteMarker, authVersion: number) {
    setRecuperando(true)
    try {
      if (!sessionIsCurrent(auth, authVersion)) {
        setError('La sesión cambió. El cobro aún no está confirmado; vuelve a ingresar con la misma cuenta para consultar su estado.')
        return
      }
      const result = await consultarOperacionPorClave(auth, keyed.key)
      if (!sessionIsCurrent(auth, authVersion)) return
      if (result.estado === 'CONFIRMED') {
        if (operacion === 'nueva' && result.resultado && 'montoPrimerPago' in result.resultado) onCreated(result.resultado)
        else if (operacion === 'pago' && result.resultado && 'montoPago' in result.resultado) onPaid(result.resultado)
        else throw new ApiError('invalid-response')
        await removeMembresiaWriteMarker(account, keyed)
        onMarkerChange(keyed, false)
        setAttempt(null)
        setError('Operación confirmada y recuperada correctamente.')
      } else if (result.estado === 'REJECTED') {
        await removeMembresiaWriteMarker(account, keyed)
        onMarkerChange(keyed, false)
        setAttempt(null)
        setError(result.rechazoMensaje || 'El servidor rechazó la operación.')
      } else {
        const pending = { ...keyed, state: 'pending' as const }
        await updateMembresiaWriteMarker(account, pending)
        onMarkerChange(pending, true)
        setAttempt(pending)
        setError('La operación aún está en proceso. Puedes volver a enviar los mismos datos de forma segura.')
      }
    } catch (reason) {
      // 404 after a lost response is not evidence that the original POST never arrived.
      setError(reason instanceof ApiError && reason.status === 404
        ? 'Todavía no se encuentra el resultado. No vuelvas a iniciar el cobro; consulta su estado nuevamente más tarde.'
        : 'No se pudo consultar el estado. El cobro aún no está confirmado; vuelve a consultar antes de intentar otra vez.')
    } finally { setRecuperando(false) }
  }

  function continuar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (!metodoPago) { setError('Selecciona un método de pago.'); return }
    if (operacion === 'nueva' && (!socio || !plan)) { setError('Selecciona un socio activo y un plan activo.'); return }
    if (operacion === 'pago' && !membresia) { setError('No se encontró la membresía seleccionada.'); return }
    if (valorCentavos === null || valorCentavos <= 0n) { setError('Ingresa un importe válido con hasta dos decimales.'); return }
    if (operacion === 'nueva' && valorCentavos < 4000n) { setError('El primer pago debe ser de al menos S/ 40.00.'); return }
    if (operacion === 'nueva' && precioCentavos !== null && valorCentavos > precioCentavos) { setError('El primer pago no puede superar el precio del plan.'); return }
    if (operacion === 'pago' && saldoCentavos !== null && valorCentavos > saldoCentavos) { setError('El pago no puede superar el saldo pendiente.'); return }
    if (referencia.length > 100) { setError('La referencia no puede superar 100 caracteres.'); return }
    if (observaciones.length > 255) { setError('Las observaciones no pueden superar 255 caracteres.'); return }
    setConfirmando(true)
  }

  async function confirmar() {
    if (lock.current || (!attempt && valorCentavos === null) || blocked || (operacion === 'pago' && (operationId ?? 0) <= 0)) return
    lock.current = true
    setGuardando(true)
    setError('')
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 30_000)
    const sessionVersion = auth.getSessionVersion()
    let markerSaved = false
    let currentMarker: MembresiaWriteMarker | null = attempt
    try {
      const optional = (value: string) => value.trim() || null
      const requestBody: Record<string, unknown> = currentMarker?.body ?? (operacion === 'nueva' && socio && plan && valorCentavos !== null
        ? { idSocio: socio.idSocio, idPlan: plan.idPlan, montoPrimerPago: numeroDesdeCentavos(valorCentavos), metodoPago,
            referencia: optional(referencia), observaciones: optional(observaciones) }
        : { monto: valorCentavos === null ? 0 : numeroDesdeCentavos(valorCentavos), metodoPago, referencia: optional(referencia), observaciones: optional(observaciones) })
      currentMarker ??= {
        key: globalThis.crypto.randomUUID(), operation: operacion, ...(operationId ? { idMembresia: operationId } : {}),
        body: requestBody, state: 'uncertain',
      }
      setAttempt(currentMarker)
      await addMembresiaWriteMarker(account, currentMarker)
      markerSaved = true
      onMarkerChange(currentMarker, true)
      if (currentMarker.operation === 'nueva') {
        const response = await registrarMembresia(auth, requestBody as Parameters<typeof registrarMembresia>[1], currentMarker.key, controller.signal)
        if (!Number.isSafeInteger(response.idMembresia) || !Number.isSafeInteger(response.idPago) || !Number.isSafeInteger(response.idAplicacion)) throw new ApiError('invalid-response')
        if (sessionIsCurrent(auth, sessionVersion)) onCreated(response)
        await removeMembresiaWriteMarker(account, currentMarker)
        onMarkerChange(currentMarker, false)
        setAttempt(null)
      } else if (currentMarker.operation === 'pago' && currentMarker.idMembresia) {
        const response = await registrarPagoPosterior(auth, currentMarker.idMembresia, requestBody as Parameters<typeof registrarPagoPosterior>[2], currentMarker.key, controller.signal)
        if (!Number.isSafeInteger(response.idPago) || !Number.isSafeInteger(response.idAplicacion) || response.idMembresia !== currentMarker.idMembresia) throw new ApiError('invalid-response')
        if (sessionIsCurrent(auth, sessionVersion)) onPaid(response)
        await removeMembresiaWriteMarker(account, currentMarker)
        onMarkerChange(currentMarker, false)
        setAttempt(null)
      }
    } catch (reason) {
      setConfirmando(false)
      if (markerSaved && !errorIsUncertain(reason) && !(reason instanceof ApiError && reason.code === 'conflict')) {
        try { await removeMembresiaWriteMarker(account, currentMarker!); onMarkerChange(currentMarker!, false); setAttempt(null) }
        catch { /* Keep blocked if persistent state cannot be reconciled. */ }
      }
      if (markerSaved && (errorIsUncertain(reason) || reason instanceof ApiError && reason.code === 'conflict')) {
        setError('No se pudo confirmar el resultado del cobro. Consultando su estado…')
        await resolverResultado(currentMarker!, sessionVersion)
      } else setError(markerSaved ? descripcionError(reason)
        : 'No se inició el cobro porque no fue posible verificar su estado de seguridad.')
    } finally {
      window.clearTimeout(timeout)
      lock.current = false
      setGuardando(false)
    }
  }

  const encabezado = operacion === 'nueva' ? 'Nueva membresía' : 'Registrar pago'
  return <div className="membresias-backdrop" onClick={() => { if (!guardando) onClose() }}><section aria-labelledby="membresias-operation-title" aria-modal="true" className="membresias-dialog membresias-operation-dialog" onClick={(event) => event.stopPropagation()} onKeyDown={mantenerFocoEnDialogo} role="dialog"><button ref={closeButtonRef} aria-label="Cerrar formulario" className="membresias-dialog-close" disabled={guardando} onClick={onClose} type="button"><X aria-hidden="true" size={18} /></button><p className="membresias-dialog-eyebrow">{operacion === 'nueva' ? 'RECEPCIÓN · SERVICIOS' : 'REGISTRO DE PAGO'}</p><h2 id="membresias-operation-title">{confirmando ? 'Confirmar operación' : encabezado}</h2>{!confirmando && operacion === 'nueva' && <p className="membresias-dialog-description">Selecciona el socio y el plan activos, y registra el primer pago.</p>}
      {unresolvedMarker && <div className="membresias-feedback" role="alert"><strong>Hay un cobro pendiente de confirmar.</strong> No inicies otro hasta verificar el resultado.{!('key' in unresolvedMarker) ? <p>Este cobro requiere revisión administrativa antes de continuar.</p> : <div className="membresias-operation-actions"><Button disabled={recuperando || guardando} onClick={() => void resolverResultado(unresolvedMarker, auth.getSessionVersion())} type="button" variant="secondary">{recuperando ? 'Consultando…' : 'Consultar estado'}</Button>{attempt && <Button disabled={guardando || recuperando} onClick={() => void confirmar()} type="button">{guardando ? 'Procesando…' : 'Reintentar envío'}</Button>}</div>}</div>}
    {!confirmando ? <form className="membresias-operation-form" noValidate onSubmit={continuar}>
      {operacion === 'nueva' ? <>
        {!socios.length && <p className="membresias-feedback" role="status">No hay socios activos disponibles para registrar una membresía.</p>}
        {!planes.length && <p className="membresias-feedback" role="status">No hay planes activos disponibles.</p>}
        <label className="membresias-operation-field"><span>Socio activo *</span><select aria-label="Socio activo" onChange={(event) => setIdSocio(event.target.value)} required value={idSocio}><option value="">Seleccionar socio</option>{socios.map((item) => <option key={item.idSocio} value={item.idSocio}>{item.codigoSocio} · {item.nombres} {item.apellidos}</option>)}</select></label>
        <label className="membresias-operation-field"><span>Plan activo *</span><select aria-label="Plan activo" onChange={(event) => setIdPlan(event.target.value)} required value={idPlan}><option value="">Seleccionar plan</option>{planes.map((item) => <option key={item.idPlan} value={item.idPlan}>{item.nombre} · {formatear(item.precio)}</option>)}</select></label>
        {plan && <p className="membresias-operation-note">Precio del plan: <strong>{formatear(plan.precio)}</strong></p>}
       </> : <div className="membresias-operation-note"><strong>{membresia?.socio?.codigoSocio} · {membresia?.socio?.nombres} {membresia?.socio?.apellidos}</strong><span>{membresia?.plan?.nombre} · Saldo actual: {saldoPendiente === undefined ? 'No disponible' : formatear(saldoPendiente)}</span><span className="membresias-allowed-states">Los pagos se aceptan si la membresía está vigente o pendiente de pago, y dentro de la fecha límite.</span></div>}
      <label className="membresias-operation-field"><span>{operacion === 'nueva' ? 'Monto del primer pago *' : 'Monto del abono *'}</span><span className="membresias-money-input"><span>S/</span><input aria-label={operacion === 'nueva' ? 'Monto del primer pago' : 'Monto del abono'} inputMode="decimal" onChange={(event) => setImporte(event.target.value)} placeholder="0.00" required value={importe} /></span></label>
      <label className="membresias-operation-field"><span>Método de pago *</span><select aria-label="Método de pago" onChange={(event) => setMetodoPago(event.target.value)} required value={metodoPago}><option value="">Seleccionar método</option>{METODOS_PAGO.map((method) => <option key={method} value={method}>{method}</option>)}</select></label>
      <label className="membresias-operation-field"><span>Referencia (opcional)</span><input maxLength={100} onChange={(event) => setReferencia(event.target.value)} value={referencia} /></label>
      <label className="membresias-operation-field"><span>Observaciones (opcional)</span><textarea maxLength={255} onChange={(event) => setObservaciones(event.target.value)} rows={3} value={observaciones} /></label>
      {error && <p className="membresias-feedback" role="alert">{error}</p>}
       <div className="membresias-operation-actions"><Button disabled={guardando} onClick={onClose} type="button" variant="secondary">Cancelar</Button><Button disabled={guardando || blocked || !!unresolvedMarker || (operacion === 'nueva' && (!socios.length || !planes.length))} type="submit">Revisar y confirmar</Button></div>
    </form> : <div className="membresias-confirm-summary"><p>Revisa los datos antes de enviar el cobro:</p><dl><div><dt>Socio</dt><dd>{operacion === 'nueva' ? `${socio?.nombres} ${socio?.apellidos}` : `${membresia?.socio?.nombres} ${membresia?.socio?.apellidos}`}</dd></div><div><dt>Plan</dt><dd>{operacion === 'nueva' ? plan?.nombre : membresia?.plan?.nombre}</dd></div>{operacion === 'pago' && saldoPendiente !== undefined && <div><dt>Saldo pendiente actual</dt><dd>{formatear(saldoPendiente)}</dd></div>}<div><dt>Importe</dt><dd>{valorCentavos === null ? '' : formatear(numeroDesdeCentavos(valorCentavos))}</dd></div><div><dt>Método</dt><dd>{metodoPago}</dd></div>{referencia.trim() && <div><dt>Referencia</dt><dd>{referencia.trim()}</dd></div>}</dl>{error && <p className="membresias-feedback" role="alert">{error}</p>}<div className="membresias-operation-actions"><Button disabled={guardando} onClick={() => setConfirmando(false)} type="button" variant="secondary">Volver</Button><Button disabled={guardando} onClick={() => void confirmar()} type="button">{guardando ? 'Procesando…' : 'Confirmar cobro'}</Button></div></div>}
  </section></div>
}
