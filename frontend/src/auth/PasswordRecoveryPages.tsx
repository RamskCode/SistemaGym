import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { ArrowLeft, CheckCircle2, KeyRound, LoaderCircle, Mail, ShieldCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { HttpError } from '../api/http'
import { requestPasswordRecovery, resetPassword, validateRecoveryToken } from '../api/passwordRecovery'
import { passwordPolicyError } from '../utils/passwordPolicy'
import { PasswordInput, PasswordMatchIndicator, PasswordPolicyFeedback, PasswordRequirements } from '../components/PasswordFields'
import './login.css'
import './password-recovery.css'

function VisualBrand() {
  return <section aria-label="SistemaGym" className="login-visual">
    <div aria-hidden="true" className="login-visual__photo" /><div aria-hidden="true" className="login-visual__brand-wash" />
    <div className="login-brand"><span aria-hidden="true" className="login-brand__icon"><KeyRound size={54} strokeWidth={2.5} /></span>
      <p className="login-brand__name">Sistema<span>Gym</span></p><p className="login-brand__tagline">Gestión integral del gimnasio</p></div>
    <div aria-hidden="true" className="login-visual__slash" /><span aria-hidden="true" className="login-visual__caption">ACCESO SEGURO</span>
  </section>
}

function AuthFrame({ children, title, intro }: { children: ReactNode; title: string; intro: string }) {
  return <main className="login-page recovery-page"><VisualBrand /><section aria-labelledby="recovery-title" className="login-panel">
    <div className="login-card"><div aria-hidden="true" className="login-card__user-icon"><ShieldCheck size={30} /></div>
      <p className="login-card__eyebrow">PORTAL DEL PERSONAL</p><h1 id="recovery-title">{title}</h1>
      <p className="login-card__intro">{intro}</p>{children}</div>
    <p className="login-panel__footer">© {new Date().getFullYear()} SistemaGym · Todos los derechos reservados</p>
  </section></main>
}

function errorMessage(error: unknown) {
  if (error instanceof HttpError && error.status === 410) return 'Este enlace ya fue usado o caducó. Solicita uno nuevo.'
  if (error instanceof HttpError && error.status === 400) return 'La contraseña debe cumplir los requisitos de seguridad. Revísalos e inténtalo de nuevo.'
  return 'No se pudo completar la solicitud. Comprueba tu conexión e inténtalo de nuevo.'
}

export function RequestPasswordRecoveryPage() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('')
    try { setMessage(await requestPasswordRecovery(email.trim())) }
    catch { setError('No fue posible procesar la solicitud. Inténtalo nuevamente.') }
    finally { setBusy(false) }
  }
  return <AuthFrame intro="Ingresa el correo asociado a tu cuenta y te indicaremos cómo continuar." title="Recuperar contraseña">
    <form className="login-form recovery-form" noValidate onSubmit={submit}>
      <div className="login-field"><label className="login-field__required" htmlFor="recovery-email">Correo electrónico</label>
        <div className="login-input-wrap"><Mail aria-hidden="true" className="login-input-icon" size={19}/><input autoComplete="email" id="recovery-email" maxLength={150} onChange={(event) => setEmail(event.target.value)} required type="email" value={email}/></div>
      </div>
      {error && <p className="login-message login-message--error" role="alert">{error}</p>}
      {message && <p className="login-message login-message--success" role="status">{message}</p>}
      <button className="login-submit" disabled={busy || !email.trim()} type="submit">{busy ? <><LoaderCircle className="login-spinner" size={19}/> Procesando…</> : 'Enviar instrucciones'}</button>
      <Link className="recovery-back" to="/login"><ArrowLeft size={16}/> Volver al inicio de sesión</Link>
    </form>
  </AuthFrame>
}

export function ResetPasswordPage() {
  const navigate = useNavigate()
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token') ?? '')
  const [tokenState, setTokenState] = useState<'checking' | 'valid' | 'invalid'>('checking')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [complete, setComplete] = useState(false)

  useEffect(() => {
    window.history.replaceState({}, '', `${window.location.pathname}${window.location.hash}`)
    if (!token) { setTokenState('invalid'); return }
    let active = true
    validateRecoveryToken(token).then(() => { if (active) setTokenState('valid') })
      .catch(() => { if (active) setTokenState('invalid') })
    return () => { active = false }
  }, [token])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError('')
    const policyError = passwordPolicyError(password)
    if (policyError) { setError(policyError); return }
    if (password !== confirmation) { setError('Las contraseñas no coinciden.'); return }
    setBusy(true)
    try { await resetPassword(token, password); setComplete(true); setPassword(''); setConfirmation('') }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  if (tokenState === 'checking') return <AuthFrame intro="Estamos verificando tu enlace de recuperación." title="Verificando enlace"><p role="status" className="recovery-status"><LoaderCircle className="login-spinner"/> Verificando…</p></AuthFrame>
  if (tokenState === 'invalid') return <AuthFrame intro="El enlace no es válido o ya no está disponible." title="Enlace vencido"><div className="login-message login-message--error" role="alert">Solicita un nuevo enlace para continuar.</div><Link className="recovery-back" to="/recuperar-contrasena"><ArrowLeft size={16}/> Solicitar otro enlace</Link></AuthFrame>
  if (complete) return <AuthFrame intro="Tu contraseña se actualizó correctamente. Vuelve a iniciar sesión con la nueva contraseña." title="Contraseña actualizada"><CheckCircle2 className="recovery-success-icon"/><button className="login-submit" onClick={() => navigate('/login', { replace: true })} type="button">Ir al inicio de sesión</button></AuthFrame>

  const policyError = passwordPolicyError(password)
  return <AuthFrame intro="Crea una contraseña nueva para tu cuenta." title="Nueva contraseña">
    <form className="login-form recovery-form" onSubmit={submit}>
      <div className="password-entry">
        <PasswordInput describedBy="reset-password-requirements" id="new-password" label="Nueva contraseña"
          onChange={(value) => { setPassword(value); setError('') }} value={password} />
        <PasswordRequirements id="reset-password-requirements" password={password} />
        <PasswordPolicyFeedback password={password} />
      </div>
      <div className="password-entry">
        <PasswordInput id="confirm-password" label="Confirmar contraseña"
          onChange={(value) => { setConfirmation(value); setError('') }} value={confirmation} />
        <PasswordMatchIndicator password={password} confirmation={confirmation} />
      </div>
      {error && <p className="login-message login-message--error" role="alert">{error}</p>}
      <button className="login-submit" disabled={busy || Boolean(policyError) || !confirmation || confirmation !== password} type="submit">{busy ? <><LoaderCircle className="login-spinner" size={19}/> Actualizando…</> : 'Guardar nueva contraseña'}</button>
      <Link className="recovery-back" to="/login"><ArrowLeft size={16}/> Volver al inicio de sesión</Link>
    </form>
  </AuthFrame>
}
