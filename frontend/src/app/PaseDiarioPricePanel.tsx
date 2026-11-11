import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { TicketCheck } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  actualizarConfiguracion,
  CONFIGURACION_EDITABLE,
  obtenerConfiguracion,
  validarPrecioPaseDiario,
  type Configuracion,
} from '../api/administracion'
import type { AuthenticatedRequestContext } from '../api/http'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

function priceError(error: unknown) {
  return error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'No fue posible consultar el precio.'
}

function isUncertain(error: unknown) {
  return error instanceof ApiError && ['network', 'invalid-response', 'server'].includes(error.code)
}

export function PaseDiarioPricePanel({ auth, canManage }: { auth: AuthenticatedRequestContext; canManage: boolean }) {
  const [price, setPrice] = useState<Configuracion | null>(null)
  const [missing, setMissing] = useState(false)
  const [draft, setDraft] = useState('')
  const [editing, setEditing] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const lock = useRef(false)

  const reload = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const latest = await obtenerConfiguracion(auth, CONFIGURACION_EDITABLE)
      setPrice(latest)
      setMissing(false)
      if (!blocked) setDraft(latest.valor)
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'not-found') {
        setPrice(null); setMissing(true); setError('La tarifa del pase diario aún no está configurada.')
      } else setError(priceError(cause))
    }
    finally { setLoading(false) }
  }, [auth, blocked])

  useEffect(() => { void reload() }, [reload])

  async function save(event: FormEvent) {
    event.preventDefault()
    if ((!price && !missing) || lock.current || blocked || !canManage) return
    const value = draft.trim()
    if (!validarPrecioPaseDiario(value)) { setError('El precio debe ser positivo y tener hasta dos decimales.'); return }
    if (price && value === price.valor) { setError('El precio propuesto debe ser distinto del vigente.'); return }
    if (!confirmed) { setError('Confirma explícitamente la actualización.'); return }
    lock.current = true; setSaving(true); setError(''); setStatus('')
    let writeStarted = false
    try {
      if (price) {
        const current = await obtenerConfiguracion(auth, CONFIGURACION_EDITABLE)
        if (current.valor !== price.valor) {
          setPrice(current); setDraft(current.valor); setConfirmed(false)
          throw new Error('La tarifa cambió desde que abriste la edición. Revisa el valor actualizado.')
        }
      }
      writeStarted = true
      const updated = await actualizarConfiguracion(auth, CONFIGURACION_EDITABLE, value)
      setBlocked(true)
      const verified = await obtenerConfiguracion(auth, CONFIGURACION_EDITABLE)
      if (updated.valor !== value || verified.valor !== value) throw new Error('El servidor no confirmó la tarifa propuesta.')
      setPrice(verified); setMissing(false); setDraft(verified.valor); setEditing(false); setConfirmed(false)
      setBlocked(false); setStatus('Tarifa actualizada y verificada en el servidor.')
    } catch (cause) {
      const uncertain = writeStarted && isUncertain(cause)
      if (uncertain) setBlocked(true)
      setError(uncertain
        ? `Resultado incierto. Consulta la tarifa vigente antes de volver a editar. ${priceError(cause)}`
        : priceError(cause))
    } finally { lock.current = false; setSaving(false) }
  }

  async function verify() {
    if (!blocked || loading || saving) return
    setLoading(true); setError('')
    try {
      const latest = await obtenerConfiguracion(auth, CONFIGURACION_EDITABLE)
      setPrice(latest); setMissing(false); setDraft(latest.valor); setEditing(false); setConfirmed(false); setBlocked(false)
      setStatus('Tarifa vigente consultada. Ya puedes revisar cualquier cambio necesario.')
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'not-found' && canManage) {
        setPrice(null); setMissing(true); setDraft(''); setEditing(false); setConfirmed(false); setBlocked(false)
        setStatus('Se verificó que la tarifa aún no existe. Puedes inicializarla con el importe aprobado.')
      } else setError(`No se pudo verificar la tarifa. La edición permanece bloqueada. ${priceError(cause)}`)
    }
    finally { setLoading(false) }
  }

  return <Card className="planes-daily-price">
    <div className="planes-daily-price__summary">
      <div className="planes-daily-price__identity"><span className="planes-daily-price__icon"><TicketCheck aria-hidden="true" size={21} /></span><div><strong>Pase diario</strong><span>Vigencia · 1 día calendario</span></div></div>
      <div className="planes-daily-price__value"><span>Tarifa vigente</span>{loading ? <span role="status">Consultando tarifa…</span> : <strong>{price ? `S/ ${Number(price.valor).toFixed(2)}` : missing ? 'Sin configurar' : 'No disponible'}</strong>}</div>
    </div>
    {error && <p className="planes-feedback planes-feedback--error" role="alert">{error}</p>}
    {status && <p className="planes-feedback planes-feedback--success" role="status">{status}</p>}
    {!price && !loading && !missing && !error && <p role="alert">No se recibió una tarifa válida.</p>}
    {missing && canManage && !editing && !blocked && <Button disabled={loading || saving} onClick={() => { setDraft(''); setEditing(true); setConfirmed(false); setError(''); setStatus('') }} type="button" variant="secondary">Configurar tarifa</Button>}
    {missing && !canManage && !loading && <p>La tarifa diaria aún no está configurada. Solicita a un administrador que la establezca.</p>}
    {blocked && <div className="planes-daily-price__blocked"><p>La actualización anterior debe verificarse antes de permitir otra.</p><Button disabled={loading || saving} onClick={() => void verify()} type="button" variant="secondary">Consultar tarifa vigente</Button></div>}
    {price && canManage && !blocked && !editing && <Button disabled={loading || saving} onClick={() => { setDraft(price.valor); setEditing(true); setConfirmed(false); setError(''); setStatus('') }} type="button" variant="secondary">Editar tarifa</Button>}
    {(price || (missing && canManage)) && editing && !blocked && <form className="planes-daily-price__form" onSubmit={(event) => void save(event)}>
      <label htmlFor="daily-price">{price ? 'Nueva tarifa (S/)' : 'Tarifa inicial (S/)'}<input autoComplete="off" id="daily-price" inputMode="decimal" maxLength={255} onChange={(event) => { setDraft(event.target.value); setError('') }} value={draft} /></label>
      <label className="planes-daily-price__confirm"><input checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} type="checkbox" /> {price ? 'Confirmo actualizar la tarifa vigente del pase diario.' : 'Confirmo establecer esta tarifa para el pase diario.'}</label>
      <div><Button disabled={saving || loading || !confirmed || !validarPrecioPaseDiario(draft) || (price !== null && draft.trim() === price.valor)} type="submit">{saving ? 'Guardando y verificando…' : 'Guardar tarifa'}</Button><Button disabled={saving} onClick={() => { setEditing(false); setError(''); setConfirmed(false) }} type="button" variant="secondary">Cancelar</Button></div>
    </form>}
  </Card>
}
