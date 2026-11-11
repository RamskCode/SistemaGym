import { useEffect, useState, type FormEvent } from 'react'
import { CheckCircle2, KeyRound, LoaderCircle, ShieldCheck } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { HttpError } from '../api/http'
import { activateAccount, validateActivationToken } from '../api/accountActivation'
import { passwordPolicyError } from '../utils/passwordPolicy'
import { PasswordInput, PasswordMatchIndicator, PasswordPolicyFeedback, PasswordRequirements } from '../components/PasswordFields'
import './login.css'
import './password-recovery.css'
import './account-activation.css'

export function AccountActivationPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [token] = useState(() => new URLSearchParams(location.search).get('token') ?? '')
  const [state, setState] = useState<'checking' | 'valid' | 'invalid'>('checking')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [complete, setComplete] = useState(false)

  useEffect(() => {
    window.history.replaceState({}, '', window.location.pathname)
    if (!token) { setState('invalid'); return }
    let active = true
    validateActivationToken(token).then(() => { if (active) setState('valid') })
      .catch(() => { if (active) setState('invalid') })
    return () => { active = false }
  }, [token])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError('')
    const issue = passwordPolicyError(password)
    if (issue) { setError(issue); return }
    if (password !== confirmation) { setError('Las contraseñas no coinciden.'); return }
    setBusy(true)
    try { await activateAccount(token, password); setComplete(true); setPassword(''); setConfirmation('') }
    catch (cause) {
      setError(cause instanceof HttpError && cause.status === 400
        ? 'La contraseña debe cumplir los cuatro requisitos de seguridad y no ser común o predecible.'
        : 'No se pudo activar la cuenta. El enlace pudo caducar o ya haberse utilizado; solicita una nueva invitación.')
    }
    finally { setBusy(false) }
  }

  return <main className="login-page recovery-page activation-page">
    <section aria-label="SistemaGym" className="login-visual">
      <div aria-hidden="true" className="login-visual__photo"/>
      <div aria-hidden="true" className="login-visual__brand-wash"/>
      <div className="login-brand">
        <span aria-hidden="true" className="login-brand__icon"><KeyRound size={54} strokeWidth={2.5}/></span>
        <p className="login-brand__name">Sistema<span>Gym</span></p>
        <p className="login-brand__tagline">Gestión integral del gimnasio</p>
      </div>
      <div aria-hidden="true" className="login-visual__slash"/>
      <span aria-hidden="true" className="login-visual__caption">ACCESO SEGURO</span>
    </section>

    <section aria-labelledby="activation-title" className="login-panel">
      <div className="login-card">
        <div aria-hidden="true" className="login-card__user-icon"><ShieldCheck size={30}/></div>
        <p className="login-card__eyebrow">PORTAL DEL PERSONAL</p>
        {state === 'checking' ? <>
          <h1 id="activation-title">Verificando invitación</h1>
          <p className="login-card__intro">Estamos comprobando que tu enlace siga vigente.</p>
          <p className="recovery-status" role="status"><LoaderCircle className="login-spinner" aria-hidden="true"/> Verificando…</p>
        </> : state === 'invalid' ? <>
          <h1 id="activation-title">Invitación no disponible</h1>
          <p className="login-card__intro">El enlace no es válido, ya se utilizó o caducó. Solicita al administrador que reenvíe la invitación.</p>
          <Link className="login-submit activation-link" to="/login">Ir al inicio de sesión</Link>
        </> : complete ? <>
          <h1 id="activation-title">Cuenta activada</h1>
          <p className="login-card__intro">Tu contraseña quedó configurada. Ya puedes iniciar sesión.</p>
          <CheckCircle2 className="recovery-success-icon" aria-hidden="true"/>
          <button className="login-submit" onClick={() => navigate('/login', { replace: true })} type="button">Iniciar sesión</button>
        </> : <>
          <h1 id="activation-title">Crea tu contraseña</h1>
          <p className="login-card__intro">Elige una contraseña segura para acceder a SistemaGym.</p>
          <form className="login-form recovery-form activation-form" onSubmit={submit}>
            <div className="password-entry">
              <PasswordInput id="activation-password" label="Nueva contraseña" value={password}
                onChange={(value) => { setPassword(value); setError('') }} className="activation-field"
                describedBy="activation-password-requirements" />
              <PasswordRequirements id="activation-password-requirements" password={password} />
              <PasswordPolicyFeedback password={password} />
            </div>
            <div className="password-entry">
              <PasswordInput id="activation-confirmation" label="Confirmar contraseña" value={confirmation}
                onChange={(value) => { setConfirmation(value); setError('') }} className="activation-field" />
              <PasswordMatchIndicator password={password} confirmation={confirmation} />
            </div>
            {error && <p className="login-message login-message--error" role="alert">{error}</p>}
            <button className="login-submit" disabled={busy || Boolean(passwordPolicyError(password)) || !confirmation || confirmation !== password} type="submit">
              {busy ? <><LoaderCircle className="login-spinner" aria-hidden="true" size={19}/> Activando…</> : 'Activar cuenta'}
            </button>
          </form>
        </>}
      </div>
      <p className="login-panel__footer">© {new Date().getFullYear()} SistemaGym · Todos los derechos reservados</p>
    </section>
  </main>
}
