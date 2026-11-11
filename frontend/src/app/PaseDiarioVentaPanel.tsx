import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/http'
import { obtenerConfiguracion, CONFIGURACION_EDITABLE } from '../api/administracion'
import { listarSocios, registrarSocio, type Socio, type SocioRequest } from '../api/socios'
import { paseDiarioV2Habilitado, registrarIngreso, type IngresoResult } from '../api/ingresos'
import type { AuthContextValue } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { uncertainWriteResult } from './uncertainWriteLocks'

type Documento = 'DNI' | 'CE'
type Metodo = 'EFECTIVO' | 'YAPE' | 'PLIN' | 'TRANSFERENCIA' | 'TARJETA' | 'OTRO'
type Persona = Pick<Socio, 'idSocio' | 'tipoDocumento' | 'numeroDocumento' | 'dni' | 'nombres' | 'apellidos' | 'celular' | 'correo' | 'estado'>

const metodos: Array<{ value: Metodo; label: string }> = [
  { value: 'EFECTIVO', label: 'Efectivo' }, { value: 'YAPE', label: 'Yape' },
  { value: 'PLIN', label: 'Plin' }, { value: 'TRANSFERENCIA', label: 'Transferencia' },
  { value: 'TARJETA', label: 'Tarjeta' }, { value: 'OTRO', label: 'Otro' },
]
const STORAGE_PREFIX = 'sistemagym:pase-diario:idempotencia:'

function documentoDe(persona: Persona): { tipo: Documento; numero: string } | null {
  const numero = persona.numeroDocumento ?? persona.dni
  const tipo = persona.tipoDocumento ?? (persona.dni ? 'DNI' : null)
  return numero && (tipo === 'DNI' || tipo === 'CE') ? { tipo, numero } : null
}

function validarDocumento(tipo: Documento, value: string) {
  return tipo === 'DNI' ? /^\d{8}$/.test(value) : /^\d{1,12}$/.test(value)
}

async function storageKey(account: string, tipo: Documento, numero: string) {
  if (!globalThis.crypto?.subtle) throw new Error('El navegador no permite proteger la clave de reintento.')
  const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${account.toLocaleLowerCase('es')}|${tipo}|${numero}`))
  const hash = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${STORAGE_PREFIX}${hash}`
}

function crearClaveUuid() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  if (!globalThis.crypto?.getRandomValues) throw new Error('No se pudo generar una clave segura para la operación.')
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function money(value: number) {
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(value)
}

function message(error: unknown) {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : 'No se pudo completar la operación.'
}

export function PaseDiarioVentaPanel({ auth }: { auth: AuthContextValue }) {
  const account = auth.user?.correo ?? ''
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [price, setPrice] = useState<number | null>(null)
  const [documentType, setDocumentType] = useState<Documento>('DNI')
  const [documentNumber, setDocumentNumber] = useState('')
  const [person, setPerson] = useState<Persona | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [names, setNames] = useState('')
  const [surnames, setSurnames] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [method, setMethod] = useState<Metodo>('EFECTIVO')
  const [reference, setReference] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [searching, setSearching] = useState(false)
  const [processing, setProcessing] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [receipt, setReceipt] = useState<IngresoResult | null>(null)
  const [uncertainRetry, setUncertainRetry] = useState(false)
  const requestLock = useRef(false)

  useEffect(() => {
    let active = true
    void Promise.all([paseDiarioV2Habilitado(auth), obtenerConfiguracion(auth, CONFIGURACION_EDITABLE)])
      .then(([available, tariff]) => {
        if (!active) return
        setEnabled(available)
        const amount = Number(tariff.valor)
        if (Number.isFinite(amount) && amount > 0) setPrice(amount)
        else setError('La tarifa del pase diario no es válida; consulta Planes con Administración.')
      })
      .catch((reason: unknown) => { if (active) { setEnabled(false); setError(message(reason)) } })
    return () => { active = false }
  }, [auth])

  async function buscarPersona(event?: FormEvent) {
    event?.preventDefault()
    const normalized = documentNumber.trim()
    if (!validarDocumento(documentType, normalized)) {
      setError(documentType === 'DNI' ? 'El DNI debe tener 8 dígitos.' : 'El CE debe tener entre 1 y 12 dígitos.')
      return
    }
    setSearching(true); setError(''); setStatus(''); setReceipt(null); setPerson(null); setNotFound(false); setConfirmed(false)
    try {
      const matches = await listarSocios(auth, { query: normalized })
      const exact = matches.filter((item) => {
        const key = documentoDe(item)
        return key?.tipo === documentType && key.numero === normalized
      })
      if (exact.length > 1) {
        setError('Hay más de una ficha con este documento. No registres un pase; requiere revisión administrativa.')
      } else if (exact.length === 1) {
        if (exact[0].estado !== 'ACTIVO') setError('La persona está inactiva; no se puede registrar el acceso.')
        else { setPerson(exact[0]); setStatus('Persona existente identificada. Confirma sus datos antes de continuar.') }
      } else {
        setNotFound(true)
        setStatus('No encontramos una ficha con ese documento. Puedes registrar una persona nueva.')
      }
    } catch (reason) { setError(message(reason)) }
    finally { setSearching(false) }
  }

  function requestFromForm(): SocioRequest {
    return {
      dni: documentType === 'DNI' ? documentNumber.trim() : null,
      tipoDocumento: documentType,
      numeroDocumento: documentNumber.trim(),
      nombres: names.trim(), apellidos: surnames.trim(), fechaNacimiento: null,
      celular: phone.trim() || null, correo: email.trim() || null,
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!enabled || !confirmed || requestLock.current || processing) return
    const numero = documentNumber.trim()
    if (!validarDocumento(documentType, numero)) { setError('Revisa el documento antes de continuar.'); return }
    if (notFound && (!names.trim() || !surnames.trim())) { setError('Nombres y apellidos son obligatorios para una persona nueva.'); return }
    if (!person && !notFound) { setError('Busca e identifica a la persona antes de continuar.'); return }
    requestLock.current = true; setProcessing(true); setError(''); setStatus('')
    const sessionVersion = auth.getSessionVersion()
    let writeAttempted = false
    let idempotencyStorageKey = ''
    try {
      let selected = person
      if (!selected && notFound) {
        try { selected = await registrarSocio(auth, requestFromForm()) }
        catch (reason) {
          if (!(reason instanceof ApiError) || reason.code !== 'conflict') throw reason
          const latest = await listarSocios(auth, { query: numero })
          const key = latest.find((item) => documentoDe(item)?.tipo === documentType && documentoDe(item)?.numero === numero)
          if (!key) throw reason
          selected = key
        }
        setPerson(selected); setNotFound(false)
      }
      if (!selected) throw new Error('No fue posible identificar a la persona seleccionada.')
      idempotencyStorageKey = await storageKey(account, documentType, numero)
      let idempotencyKey = window.localStorage.getItem(idempotencyStorageKey)
      if (!idempotencyKey || !/^[0-9a-f-]{36}$/i.test(idempotencyKey)) {
        idempotencyKey = crearClaveUuid()
        window.localStorage.setItem(idempotencyStorageKey, idempotencyKey)
      }
      writeAttempted = true
      const result = await registrarIngreso(auth, {
        tipoDocumento: documentType, numeroDocumento: numero, idSocio: selected.idSocio,
        nombre: selected.nombres, apellidos: selected.apellidos,
        celular: selected.celular, correo: selected.correo,
        metodoPago: method, referencia: reference.trim() || null,
        claveIdempotencia: idempotencyKey,
      })
      if (auth.getSessionVersion() !== sessionVersion) return
      window.localStorage.removeItem(idempotencyStorageKey)
      setReceipt(result); setConfirmed(false); setUncertainRetry(false)
      setStatus(result.resultado === 'IDEMPOTENTE'
        ? 'La solicitud ya estaba registrada. Se devolvió el mismo resultado, sin crear otro acceso ni cobrar de nuevo.'
        : result.resultado === 'REINGRESO_AUTORIZADO'
          ? 'Reingreso autorizado con el pase vigente. No se realizó otro cobro.'
          : result.tipoIngreso === 'SOCIO'
            ? 'Acceso autorizado por membresía vigente. No se cobró pase diario.'
            : `Pase diario vendido por ${money(result.montoCobrado ?? price ?? 0)}. Acceso válido durante el día de hoy en America/Lima.`)
    } catch (reason) {
      if (idempotencyStorageKey && writeAttempted && !uncertainWriteResult(reason)) {
        try { window.localStorage.removeItem(idempotencyStorageKey) } catch { /* keep error response */ }
      }
      if (auth.getSessionVersion() === sessionVersion) {
        const uncertain = writeAttempted && uncertainWriteResult(reason)
        setUncertainRetry(uncertain)
        setError(uncertain
          ? `Resultado incierto. No cambies los datos: reintenta esta misma solicitud para recuperar el resultado sin duplicar el cobro. ${message(reason)}`
          : message(reason))
      }
    } finally { requestLock.current = false; setProcessing(false) }
  }

  function clearPerson() {
    setPerson(null); setNotFound(false); setNames(''); setSurnames(''); setPhone(''); setEmail('')
    setStatus(''); setReceipt(null); setError(''); setConfirmed(false)
  }

  return <Card className="asistencia-card pase-diario-panel" title="Pase diario · acceso">
    {enabled === null && <p role="status">Consultando disponibilidad y tarifa…</p>}
    {enabled === false && <p className="asistencia-message">La venta está temporalmente deshabilitada hasta completar y verificar la migración de personas, ingresos e idempotencia. No se consultan ni escriben esos datos todavía.</p>}
    {enabled && <>
      <div className="pase-diario-summary"><div><strong>Tarifa vigente</strong><p>Acceso diario independiente de una membresía; válida según el día calendario de America/Lima.</p></div><strong className="pase-diario-price">{price === null ? 'No disponible' : money(price)}</strong></div>
      {error && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{error}</p>}
      {status && <p className="asistencia-feedback asistencia-feedback--success" role="status">{status}</p>}
      {receipt && <dl className="pase-diario-receipt"><div><dt>Resultado</dt><dd>{receipt.resultado}</dd></div><div><dt>Persona</dt><dd>{receipt.numeroDocumento}</dd></div><div><dt>Cobro de esta operación</dt><dd>{receipt.paseDiarioCobrado ? money(receipt.montoCobrado ?? 0) : 'Sin cobro'}</dd></div><div><dt>Pago registrado</dt><dd>{receipt.idPago ? `#${receipt.idPago}` : 'No aplica'}</dd></div></dl>}
      <form className="pase-diario-form" onSubmit={(event) => void buscarPersona(event)}>
        <label>Tipo de documento<select disabled={processing || searching || uncertainRetry} onChange={(event) => { setDocumentType(event.target.value as Documento); clearPerson() }} value={documentType}><option value="DNI">DNI</option><option value="CE">CE</option></select></label>
        <label>Número de documento<input autoComplete="off" disabled={uncertainRetry} inputMode="numeric" maxLength={12} onChange={(event) => { setDocumentNumber(event.target.value.replace(/\s/g, '')); clearPerson() }} value={documentNumber} /></label>
        <Button disabled={searching || processing || uncertainRetry || !documentNumber.trim()} type="submit" variant="secondary">{searching ? 'Buscando…' : 'Buscar persona'}</Button>
      </form>
      {person && <section className="pase-diario-person" aria-label="Persona encontrada"><div><strong>{person.nombres} {person.apellidos}</strong><span>{documentType} {documentNumber} · {person.estado}</span></div><Button disabled={processing || uncertainRetry} onClick={clearPerson} type="button" variant="secondary">Cambiar persona</Button></section>}
      {notFound && <form className="pase-diario-person-form" onSubmit={(event) => void submit(event)}>
        <p className="asistencia-description">Nueva persona: se creará una ficha en Socios sin crear membresía.</p>
        <label>Nombres<input autoComplete="given-name" maxLength={100} onChange={(event) => setNames(event.target.value)} required value={names} /></label>
        <label>Apellidos<input autoComplete="family-name" maxLength={100} onChange={(event) => setSurnames(event.target.value)} required value={surnames} /></label>
        <label>Celular<input autoComplete="tel" maxLength={20} onChange={(event) => setPhone(event.target.value)} value={phone} /></label>
        <label>Correo<input autoComplete="email" maxLength={150} onChange={(event) => setEmail(event.target.value)} type="email" value={email} /></label>
        <PaymentControls disabled={uncertainRetry} method={method} onMethod={setMethod} onReference={setReference} reference={reference} />
        <Confirmation checked={confirmed} onChange={setConfirmed} amount={price} />
        <Button disabled={processing || !confirmed || price === null} type="submit">{processing ? 'Registrando…' : 'Registrar persona y pase diario'}</Button>
      </form>}
      {person && <form className="pase-diario-person-form" onSubmit={(event) => void submit(event)}>
        <PaymentControls disabled={uncertainRetry} method={method} onMethod={setMethod} onReference={setReference} reference={reference} />
        <p className="asistencia-description">Si la persona ya tiene membresía vigente, se autorizará su acceso sin cobrar el pase. Si ya compró un pase hoy, el reingreso no genera otro pago.</p>
        <Confirmation checked={confirmed} onChange={setConfirmed} amount={price} />
        <Button disabled={processing || !confirmed || price === null} type="submit">{processing ? 'Registrando acceso…' : uncertainRetry ? 'Reintentar misma solicitud' : 'Confirmar acceso / pase diario'}</Button>
      </form>}
    </>}
  </Card>
}

function PaymentControls({ method, onMethod, reference, onReference, disabled = false }: {
  method: Metodo; onMethod: (value: Metodo) => void; reference: string; onReference: (value: string) => void; disabled?: boolean
}) {
  return <div className="pase-diario-payment"><label>Método de pago<select disabled={disabled} onChange={(event) => onMethod(event.target.value as Metodo)} value={method}>{metodos.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><label>Referencia (si aplica)<input autoComplete="off" disabled={disabled} maxLength={100} onChange={(event) => onReference(event.target.value)} value={reference} /></label></div>
}

function Confirmation({ checked, onChange, amount }: { checked: boolean; onChange: (value: boolean) => void; amount: number | null }) {
  return <><p className="pase-diario-amount">Importe: <strong>{amount === null ? 'No disponible' : money(amount)}</strong></p><label className="pase-diario-confirm"><input checked={checked} onChange={(event) => onChange(event.target.checked)} type="checkbox" />Confirmo los datos y autorizo registrar el acceso y, si corresponde, el cobro de un pase diario.</label></>
}
