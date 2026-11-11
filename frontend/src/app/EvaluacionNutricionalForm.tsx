import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/http'
import {
  registrarEvaluacionNutricional,
  type EvaluacionNutricional,
  type RegistrarEvaluacionNutricionalRequest,
} from '../api/evaluacionesNutricionales'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import type { SocioResumenNutricion } from '../api/socios'
import { buscarResumenSocioNutricion } from '../api/socios'

type Values = {
  pesoKg: string
  tallaM: string
  porcentajeGrasa: string
  masaMuscularKg: string
  habitosAlimenticios: string
  objetivosNutricionales: string
  recomendacionesNutricionales: string
  observacionesSeguimiento: string
}

const initialValues: Values = {
  pesoKg: '', tallaM: '', porcentajeGrasa: '', masaMuscularKg: '',
  habitosAlimenticios: '', objetivosNutricionales: '',
  recomendacionesNutricionales: '', observacionesSeguimiento: '',
}

type Props = {
  socio: SocioResumenNutricion
  envioIncierto: boolean
  onIncierto: () => void
  onConflicto: (estado: SocioResumenNutricion['estado']) => void
  onClose: () => void
  onVerHistorial: () => void
}

function validarDecimal(value: string, integerDigits: number, allowZero = false): boolean {
  if (!new RegExp(`^\\d{1,${integerDigits}}(?:\\.\\d{1,2})?$`).test(value)) return false
  const number = Number(value)
  return Number.isFinite(number) && (allowZero ? number >= 0 : number > 0)
}

function requestFrom(values: Values): RegistrarEvaluacionNutricionalRequest {
  return {
    pesoKg: Number(values.pesoKg),
    tallaM: Number(values.tallaM),
    porcentajeGrasa: Number(values.porcentajeGrasa),
    masaMuscularKg: Number(values.masaMuscularKg),
    habitosAlimenticios: values.habitosAlimenticios.trim(),
    objetivosNutricionales: values.objetivosNutricionales.trim(),
    recomendacionesNutricionales: values.recomendacionesNutricionales.trim(),
    observacionesSeguimiento: values.observacionesSeguimiento.trim() || null,
  }
}

function fieldMessage(value: string, label: string, digits: number, max: number, allowZero = false) {
  if (!value.trim()) return `${label} es obligatorio.`
  if (!validarDecimal(value, digits, allowZero)) {
    return `${label} debe ser ${allowZero ? 'igual o mayor que cero' : 'mayor que cero'}, con hasta ${digits} dígitos enteros y 2 decimales.`
  }
  if (Number(value) > max) return `${label} no puede superar ${max}.`
  return ''
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(date)
}

function knownError(error: unknown) {
  if (!(error instanceof ApiError)) return null
  if (error.code === 'network' || error.code === 'invalid-response' || error.status >= 500) return null
  if (error.status === 400) return 'El servidor rechazó algunos datos. Revísalos e inténtalo nuevamente.'
  if (error.status === 401) return error.message
  if (error.status === 403) return 'Tu sesión no tiene autorización para registrar evaluaciones.'
  if (error.status === 404) return 'No se encontró el socio. Conservamos el formulario para que puedas revisarlo.'
  if (error.status === 409) return error.serverMessage ?? 'El servidor rechazó el registro por un conflicto. Revisa el estado del socio e inténtalo según corresponda.'
  return error.message
}

export function EvaluacionNutricionalForm({
  socio, envioIncierto, onIncierto, onConflicto, onClose, onVerHistorial,
}: Props) {
  const auth = useAuth()
  const [values, setValues] = useState(initialValues)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [confirmando, setConfirmando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorEnvio, setErrorEnvio] = useState('')
  const [respuesta, setRespuesta] = useState<EvaluacionNutricional | null>(null)
  const lock = useRef(false)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  const validar = () => {
    const next: Record<string, string> = {
      pesoKg: fieldMessage(values.pesoKg, 'El peso', 4, 9999.99),
      tallaM: fieldMessage(values.tallaM, 'La talla', 2, 99.99),
      porcentajeGrasa: fieldMessage(values.porcentajeGrasa, 'El porcentaje de grasa', 3, 100, true),
      masaMuscularKg: fieldMessage(values.masaMuscularKg, 'La masa muscular', 4, 9999.99),
    }
    for (const field of ['habitosAlimenticios', 'objetivosNutricionales', 'recomendacionesNutricionales'] as const) {
      if (!values[field].trim()) next[field] = 'Este campo es obligatorio.'
    }
    const filtered = Object.fromEntries(Object.entries(next).filter(([, message]) => message))
    setErrores(filtered)
    return Object.keys(filtered).length === 0
  }

  const update = (field: keyof Values, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setErrores((current) => ({ ...current, [field]: '' }))
    setErrorEnvio('')
  }

  const continuarAConfirmacion = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (validar()) setConfirmando(true)
  }

  const enviar = async () => {
    if (lock.current || envioIncierto || socio.estado !== 'ACTIVO' || !validar()) return
    lock.current = true
    setEnviando(true)
    setErrorEnvio('')
    const sessionVersion = auth.getSessionVersion()
    const isCurrent = () => mounted.current && auth.getSessionVersion() === sessionVersion
    try {
      const result = await registrarEvaluacionNutricional(auth, socio.idSocio, requestFrom(values))
      if (!isCurrent()) return
      setRespuesta(result)
    } catch (error) {
      if (!isCurrent()) return
      const message = knownError(error)
      if (message === null) {
        onIncierto()
        setErrorEnvio('No fue posible confirmar el resultado del envío. La evaluación pudo haberse registrado. No la enviaremos otra vez; verifica el estado antes de cualquier nuevo intento.')
      } else {
        if (error instanceof ApiError && error.status === 409) {
          try {
            const socioActual = await buscarResumenSocioNutricion(auth, socio.idSocio)
            if (isCurrent()) onConflicto(socioActual.estado)
          } catch {
            // Do not infer the status from the conflict; leave the current UI state unchanged.
          }
        }
        setErrorEnvio(message)
        lock.current = false
      }
    } finally {
      if (isCurrent()) setEnviando(false)
    }
  }

  const numericField = (
    name: 'pesoKg' | 'tallaM' | 'porcentajeGrasa' | 'masaMuscularKg', label: string,
    unit: string, integerDigits: number, max: number, allowZero = false,
  ) => (
    <label className="nutri-form-field" htmlFor={`nutri-${name}`}>
      {label} ({unit}) <span aria-hidden="true">*</span>
      <input
        aria-describedby={errores[name] ? `error-${name}` : undefined}
        aria-invalid={Boolean(errores[name])}
        autoComplete="off"
        disabled={enviando}
        id={`nutri-${name}`}
        inputMode="decimal"
        onChange={(event) => update(name, event.target.value)}
        step="0.01"
        type="number"
        value={values[name]}
      />
      {errores[name] && <small id={`error-${name}`}>{errores[name]}</small>}
      <span className="visually-hidden">{`Máximo ${integerDigits} dígitos enteros, 2 decimales; ${allowZero ? 'mínimo cero' : 'mayor que cero'} y máximo ${max}.`}</span>
    </label>
  )

  const narrativeField = (name: 'habitosAlimenticios' | 'objetivosNutricionales' | 'recomendacionesNutricionales' | 'observacionesSeguimiento', label: string, required: boolean) => (
    <label className="nutri-form-field nutri-form-field--wide" htmlFor={`nutri-${name}`}>
      {label}{required && <span aria-hidden="true"> *</span>}
      <textarea
        aria-describedby={errores[name] ? `error-${name}` : undefined}
        aria-invalid={Boolean(errores[name])}
        disabled={enviando}
        id={`nutri-${name}`}
        onChange={(event) => update(name, event.target.value)}
        required={required}
        rows={3}
        value={values[name]}
      />
      {errores[name] && <small id={`error-${name}`}>{errores[name]}</small>}
    </label>
  )

  return (
    <Card className="nutricion-evaluation" title={respuesta ? 'Evaluación registrada' : envioIncierto ? 'Envío sin confirmar' : 'Nueva evaluación'}>
      <p className="nutricion-evaluation__member"><strong>{socio.nombres} {socio.apellidos}</strong> · {socio.codigoSocio}</p>

      {socio.estado !== 'ACTIVO' && !respuesta && (
        <p className="nutri-submit-error" role="status">El estado del socio cambió a inactivo. El registro fue rechazado; conservamos los datos introducidos.</p>
      )}

      {respuesta ? (
        <div aria-live="polite" className="nutricion-confirmed" role="status">
          <p>Registro confirmado por el servidor (evaluación #{respuesta.idEvaluacion}).</p>
          <dl>
            <div><dt>Fecha (Lima)</dt><dd>{formatDate(respuesta.fechaEvaluacion)}</dd></div>
            <div><dt>Peso</dt><dd>{respuesta.pesoKg} kg</dd></div>
            <div><dt>Talla</dt><dd>{respuesta.tallaM} m</dd></div>
            <div><dt>Grasa corporal</dt><dd>{respuesta.porcentajeGrasa}%</dd></div>
            <div><dt>Masa muscular</dt><dd>{respuesta.masaMuscularKg} kg</dd></div>
            <div><dt>Hábitos</dt><dd>{respuesta.habitosAlimenticios}</dd></div>
            <div><dt>Objetivos</dt><dd>{respuesta.objetivosNutricionales}</dd></div>
            <div><dt>Recomendaciones</dt><dd>{respuesta.recomendacionesNutricionales}</dd></div>
            {respuesta.observacionesSeguimiento && <div><dt>Observaciones</dt><dd>{respuesta.observacionesSeguimiento}</dd></div>}
          </dl>
          <div className="nutri-form-actions">
            <Button onClick={onClose} type="button" variant="secondary">Volver a ficha</Button>
            <Button onClick={onVerHistorial} type="button">Ver historial actualizado</Button>
          </div>
        </div>
      ) : envioIncierto ? (
        <div aria-live="assertive" className="nutricion-error" role="alert">
          <p>{errorEnvio || 'No es seguro volver a enviar esta evaluación. Verifica el resultado antes de intentar otro registro.'}</p>
          <dl>
            <div><dt>Peso</dt><dd>{values.pesoKg} kg</dd></div>
            <div><dt>Talla</dt><dd>{values.tallaM} m</dd></div>
            <div><dt>Grasa corporal</dt><dd>{values.porcentajeGrasa}%</dd></div>
            <div><dt>Masa muscular</dt><dd>{values.masaMuscularKg} kg</dd></div>
            <div><dt>Hábitos</dt><dd>{values.habitosAlimenticios}</dd></div>
            <div><dt>Objetivos</dt><dd>{values.objetivosNutricionales}</dd></div>
            <div><dt>Recomendaciones</dt><dd>{values.recomendacionesNutricionales}</dd></div>
            {values.observacionesSeguimiento && <div><dt>Observaciones</dt><dd>{values.observacionesSeguimiento}</dd></div>}
          </dl>
        </div>
      ) : (
        <>
          <form className="nutri-evaluation-form" noValidate onSubmit={continuarAConfirmacion}>
            <fieldset className="nutri-form-section">
              <legend>Mediciones corporales</legend>
              <div className="nutri-form-grid">
                {numericField('pesoKg', 'Peso', 'kg', 4, 9999.99)}
                {numericField('tallaM', 'Talla', 'm', 2, 99.99)}
                {numericField('porcentajeGrasa', 'Porcentaje de grasa', '%', 3, 100, true)}
                {numericField('masaMuscularKg', 'Masa muscular', 'kg', 4, 9999.99)}
              </div>
            </fieldset>
            <fieldset className="nutri-form-section">
              <legend>Plan nutricional</legend>
              <div className="nutri-form-grid">
                {narrativeField('habitosAlimenticios', 'Hábitos alimenticios', true)}
                {narrativeField('objetivosNutricionales', 'Objetivos nutricionales', true)}
                {narrativeField('recomendacionesNutricionales', 'Recomendaciones nutricionales', true)}
              </div>
            </fieldset>
            <fieldset className="nutri-form-section">
              <legend>Seguimiento</legend>
              <div className="nutri-form-grid">{narrativeField('observacionesSeguimiento', 'Observaciones de seguimiento (opcional)', false)}</div>
            </fieldset>
            {errorEnvio && !confirmando && <p className="nutri-submit-error" role="alert">{errorEnvio}</p>}
            <div className="nutri-form-actions">
              <Button disabled={enviando} onClick={onClose} type="button" variant="secondary">Cancelar</Button>
              <Button disabled={enviando || socio.estado !== 'ACTIVO'} type="submit">Guardar evaluación</Button>
            </div>
          </form>

          {confirmando && (
            <div className="nutri-confirm-backdrop">
              <section aria-labelledby="nutri-confirm-title" aria-modal="true" className="nutri-confirm-dialog" role="dialog">
                <h3 id="nutri-confirm-title">Confirmar nueva evaluación</h3>
                <p>Se registrará una evaluación para <strong>{socio.nombres} {socio.apellidos}</strong> ({socio.codigoSocio}).</p>
                <dl>
                  <div><dt>Peso</dt><dd>{values.pesoKg} kg</dd></div>
                  <div><dt>Talla</dt><dd>{values.tallaM} m</dd></div>
                  <div><dt>Grasa corporal</dt><dd>{values.porcentajeGrasa}%</dd></div>
                  <div><dt>Masa muscular</dt><dd>{values.masaMuscularKg} kg</dd></div>
                  <div><dt>Hábitos</dt><dd>{values.habitosAlimenticios.trim()}</dd></div>
                  <div><dt>Objetivos</dt><dd>{values.objetivosNutricionales.trim()}</dd></div>
                  <div><dt>Recomendaciones</dt><dd>{values.recomendacionesNutricionales.trim()}</dd></div>
                  {values.observacionesSeguimiento.trim() && <div><dt>Observaciones</dt><dd>{values.observacionesSeguimiento.trim()}</dd></div>}
                </dl>
                {errorEnvio && <p className="nutri-submit-error" role="alert">{errorEnvio}</p>}
                <div className="nutri-form-actions">
                  <Button disabled={enviando} onClick={() => setConfirmando(false)} type="button" variant="secondary">Volver a editar</Button>
                  <Button disabled={enviando || socio.estado !== 'ACTIVO'} onClick={() => void enviar()} type="button">
                    {enviando ? 'Registrando…' : 'Confirmar y registrar'}
                  </Button>
                </div>
              </section>
            </div>
          )}
        </>
      )}
    </Card>
  )
}
