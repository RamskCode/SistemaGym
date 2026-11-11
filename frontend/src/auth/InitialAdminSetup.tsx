import { useState, type FormEvent } from 'react'
import { CheckCircle2, KeyRound, LoaderCircle, ShieldCheck, UserRound } from 'lucide-react'
import { createInitialAdministrator } from '../api/onboarding'
import { passwordPolicyError } from '../utils/passwordPolicy'
import { PasswordInput, PasswordMatchIndicator, PasswordPolicyFeedback, PasswordRequirements } from '../components/PasswordFields'
import './initial-admin-setup.css'

type Props = { onComplete: (email: string) => void }

export function InitialAdminSetup({ onComplete }: Props) {
  const [token, setToken] = useState('')
  const [nombres, setNombres] = useState('')
  const [apellidos, setApellidos] = useState('')
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setError('')
    if (contrasena !== confirmacion) {
      setError('Las contraseñas no coinciden.')
      return
    }
    const policyError = passwordPolicyError(contrasena)
    if (policyError) {
      setError(policyError)
      return
    }
    setBusy(true)
    try {
      await createInitialAdministrator(token.trim(), {
        nombres: nombres.trim(), apellidos: apellidos.trim(),
        correo: correo.trim(), contrasena,
      })
      setToken('')
      setContrasena('')
      setConfirmacion('')
      onComplete(correo.trim())
    } catch (cause) {
      const status = typeof cause === 'object' && cause !== null && 'status' in cause
        ? Number((cause as { status: unknown }).status) : 0
      setError(status === 403
        ? 'El token no es válido o el onboarding ya fue cerrado. Revisa la consola del servidor.'
        : status === 400
      ? 'Revisa los datos. La contraseña debe cumplir los cuatro requisitos y no ser común o predecible.'
          : 'No se pudo completar la configuración. Comprueba los datos e inténtalo de nuevo.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login-page">
      <section aria-label="SistemaGym" className="login-visual">
        <div aria-hidden="true" className="login-visual__photo" />
        <div aria-hidden="true" className="login-visual__brand-wash" />
        <div className="login-brand">
          <span aria-hidden="true" className="login-brand__icon"><UserRound size={54} strokeWidth={2.5} /></span>
          <p className="login-brand__name">Sistema<span>Gym</span></p>
          <p className="login-brand__tagline">Gestión integral del gimnasio</p>
        </div>
        <div aria-hidden="true" className="login-visual__slash" />
        <span aria-hidden="true" className="login-visual__caption">CONFIGURACIÓN SEGURA</span>
      </section>
      <section aria-labelledby="setup-title" className="login-panel setup-panel">
        <div className="setup-card">
          <div aria-hidden="true" className="setup-card__icon"><ShieldCheck size={28} /></div>
          <p className="login-card__eyebrow">PRIMER INICIO</p>
          <h1 id="setup-title">Configura tu gimnasio</h1>
          <p className="setup-card__intro">Crea la cuenta del primer administrador. Esta configuración solo puede completarse una vez.</p>
          <form className="setup-form" onSubmit={submit}>
            <label htmlFor="setup-token">Token de instalación</label>
            <div className="setup-input"><KeyRound aria-hidden="true" size={18} /><input autoComplete="off" id="setup-token" onChange={(event) => setToken(event.target.value)} required value={token} /></div>
            <div className="setup-form__row">
              <div><label htmlFor="setup-nombres">Nombres</label><input autoComplete="given-name" id="setup-nombres" maxLength={100} onChange={(event) => setNombres(event.target.value)} required value={nombres} /></div>
              <div><label htmlFor="setup-apellidos">Apellidos</label><input autoComplete="family-name" id="setup-apellidos" maxLength={100} onChange={(event) => setApellidos(event.target.value)} required value={apellidos} /></div>
            </div>
            <label htmlFor="setup-email">Correo electrónico</label>
            <input autoComplete="email" id="setup-email" maxLength={150} onChange={(event) => setCorreo(event.target.value)} required type="email" value={correo} />
            <div className="password-entry">
              <PasswordInput className="setup-password-field" describedBy="setup-password-requirements" id="setup-password"
                label="Contraseña" onChange={(value) => { setContrasena(value); setError('') }} value={contrasena} />
              <PasswordRequirements id="setup-password-requirements" password={contrasena} />
              <PasswordPolicyFeedback password={contrasena} />
            </div>
            <div className="password-entry">
              <PasswordInput id="setup-confirm-password" label="Confirmar contraseña"
                onChange={(value) => { setConfirmacion(value); setError('') }} value={confirmacion} />
              <PasswordMatchIndicator password={contrasena} confirmation={confirmacion} />
            </div>
            {error && <p aria-live="polite" className="login-message login-message--error" role="alert">{error}</p>}
            <button className="login-submit" disabled={busy || Boolean(passwordPolicyError(contrasena)) || !confirmacion || confirmacion !== contrasena} type="submit">
              {busy ? <><LoaderCircle aria-hidden="true" className="login-spinner" size={19} /> Configurando…</> : <><CheckCircle2 aria-hidden="true" size={19} /> Crear administrador</>}
            </button>
          </form>
          <p className="setup-security">El token solo se valida en el servidor y no se guarda en el navegador.</p>
        </div>
        <p className="login-panel__footer">© {new Date().getFullYear()} SistemaGym</p>
      </section>
    </main>
  )
}
