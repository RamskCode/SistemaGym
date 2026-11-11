import { useEffect, useState, type FormEvent } from 'react'
import { Dumbbell, Eye, EyeOff, LockKeyhole, LoaderCircle, LogIn, Mail, ShieldCheck, UserRound } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LoginError } from '../api/auth'
import { useAuth } from './AuthContext'
import { getInitialSetupStatus } from '../api/onboarding'
import { InitialAdminSetup } from './InitialAdminSetup'
import './login.css'

function messageFor(error: unknown): string {
  if (error instanceof LoginError) return error.message
  if (error instanceof Error && error.name === 'InvalidSessionError') return error.message
  return 'No fue posible iniciar sesión. Inténtalo de nuevo.'
}

export function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [mostrarContrasena, setMostrarContrasena] = useState(false)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')
  const [exito, setExito] = useState(false)
  const [setupRequired, setSetupRequired] = useState(false)

  useEffect(() => {
    let current = true
    getInitialSetupStatus().then((required) => {
      if (current) setSetupRequired(required)
    }).catch(() => { /* A transient status failure must not expose onboarding access. */ })
    return () => { current = false }
  }, [])

  if (setupRequired) {
    return <InitialAdminSetup onComplete={(email) => {
      setCorreo(email)
      setSetupRequired(false)
      setError('')
    }} />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (cargando) return
    setError('')
    setExito(false)

    const email = correo.trim()
    if (!email || !contrasena) {
      setError('Completa tu correo electrónico y contraseña.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Ingresa un correo electrónico válido.')
      return
    }

    setCargando(true)
    try {
      const authenticated = await login({ correo: email, contrasena })
      if (authenticated) {
        setContrasena('')
        navigate('/', { replace: true })
      }
    } catch (loginError) {
      setError(messageFor(loginError))
    } finally {
      setCargando(false)
    }
  }

  return (
    <main className="login-page">
      <section aria-label="Acceso a SistemaGym" className="login-visual">
        <div aria-hidden="true" className="login-visual__photo" />
        <div aria-hidden="true" className="login-visual__brand-wash" />
        <div className="login-brand">
          <span aria-hidden="true" className="login-brand__icon"><Dumbbell size={54} strokeWidth={2.5} /></span>
          <p className="login-brand__name">Sistema<span>Gym</span></p>
          <p className="login-brand__tagline">Gestión integral del gimnasio</p>
        </div>
        <div aria-hidden="true" className="login-visual__slash" />
        <span className="login-visual__caption">FUERZA · DISCIPLINA · PROGRESO</span>
      </section>

      <section aria-labelledby="login-title" className="login-panel">
        <div className="login-card">
          <div aria-hidden="true" className="login-card__user-icon"><UserRound size={31} strokeWidth={2.1} /></div>
          <p className="login-card__eyebrow">PORTAL DEL PERSONAL</p>
          <h1 id="login-title">Iniciar sesión</h1>
          <p className="login-card__intro">Ingresa tus credenciales para continuar</p>

          <form className="login-form" noValidate onSubmit={handleSubmit}>
            <div className="login-field">
              <label className="login-field__required" htmlFor="login-email">Correo electrónico</label>
              <div className="login-input-wrap">
                <Mail aria-hidden="true" className="login-input-icon" size={19} />
                <input
                  autoComplete="username"
                  autoFocus
                  id="login-email"
                  name="correo"
                  onChange={(event) => setCorreo(event.target.value)}
                  placeholder="nombre@ejemplo.com"
                  type="email"
                  value={correo}
                />
              </div>
            </div>

            <div className="login-field">
              <label className="login-field__required" htmlFor="login-password">Contraseña</label>
              <div className="login-input-wrap">
                <LockKeyhole aria-hidden="true" className="login-input-icon" size={19} />
                <input
                  autoComplete="current-password"
                  id="login-password"
                  name="contrasena"
                  onChange={(event) => setContrasena(event.target.value)}
                  placeholder="Ingresa tu contraseña"
                  type={mostrarContrasena ? 'text' : 'password'}
                  value={contrasena}
                />
                <button
                  aria-label={mostrarContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-pressed={mostrarContrasena}
                  className="login-password-toggle"
                  onClick={() => setMostrarContrasena((visible) => !visible)}
                  type="button"
                >
                  {mostrarContrasena ? <EyeOff aria-hidden="true" size={19} /> : <Eye aria-hidden="true" size={19} />}
                </button>
              </div>
            </div>

            {error && <p aria-live="polite" className="login-message login-message--error" role="alert">{error}</p>}
            {typeof location.state === 'object' && location.state !== null
                && 'notice' in location.state && location.state.notice === 'password-changed'
              && <p className="login-message login-message--success" role="status">Contraseña actualizada. Inicia sesión con tu nueva contraseña.</p>}
            {exito && <p aria-live="polite" className="login-message login-message--success" role="status">Sesión iniciada correctamente.</p>}

            <button className="login-submit" disabled={cargando} type="submit">
              {cargando
                ? <><LoaderCircle aria-hidden="true" className="login-spinner" size={19} /> Validando acceso…</>
                : <><LogIn aria-hidden="true" size={19} /> Ingresar</>}
            </button>
            <Link className="recovery-back" to="/recuperar-contrasena">¿Olvidaste tu contraseña?</Link>
          </form>

          <div className="login-card__secure">
            <ShieldCheck aria-hidden="true" size={23} />
            <p><strong>Acceso seguro para trabajadores</strong><span>El uso del sistema está restringido al personal autorizado.</span></p>
          </div>
        </div>
        <p className="login-panel__footer">© {new Date().getFullYear()} SistemaGym <span>·</span> Todos los derechos reservados</p>
      </section>
    </main>
  )
}
