import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { BriefcaseBusiness, Check, KeyRound, Mail, Pencil, UserRound, X } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import {
  actualizarMiCuenta,
  cambiarMiContrasena,
  obtenerMiCuenta,
  type MiCuenta,
  type TipoDocumentoCuenta,
} from '../api/miCuenta'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PasswordInput, PasswordMatchIndicator, PasswordPolicyFeedback, PasswordRequirements } from '../components/PasswordFields'
import { PageTitle } from '../components/PageTitle'
import './mi-cuenta.css'
import { formatPersonName } from '../utils/personName'
import { passwordPolicyError } from '../utils/passwordPolicy'
import { documentHelp, isValidDocument, normalizeDocumentInput } from '../utils/documentIdentity'

type ProfileDraft = {
  nombres: string
  apellidos: string
  tipoDocumento: TipoDocumentoCuenta | ''
  numeroDocumento: string
}

function mensaje(error: unknown) {
  return error instanceof ApiError ? error.message
    : error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
}

function draftDesde(profile: MiCuenta): ProfileDraft {
  return {
    nombres: profile.nombres,
    apellidos: profile.apellidos,
    tipoDocumento: profile.tipoDocumento ?? '',
    numeroDocumento: profile.numeroDocumento ?? '',
  }
}

function perfilValido(draft: ProfileDraft) {
  const namesValid = Boolean(draft.nombres.trim()) && draft.nombres.trim().length <= 100
    && Boolean(draft.apellidos.trim()) && draft.apellidos.trim().length <= 100
  const docValid = !draft.tipoDocumento ? !draft.numeroDocumento : isValidDocument(draft.tipoDocumento, draft.numeroDocumento)
  return namesValid && docValid
}

export function MiCuentaPage() {
  const auth = useAuth()
  const { user } = auth
  const location = useLocation()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<MiCuenta | null>(null)
  const [draft, setDraft] = useState<ProfileDraft | null>(null)
  const [loading, setLoading] = useState(true)
  const [savingProfile, setSavingProfile] = useState(false)
  const [editingProfile, setEditingProfile] = useState(false)
  const [draftError, setDraftError] = useState('')
  const [profileError, setProfileError] = useState('')
  const [profileStatus, setProfileStatus] = useState('')
  const [passwordCurrent, setPasswordCurrent] = useState('')
  const [passwordNext, setPasswordNext] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)
  const [passwordError, setPasswordError] = useState('')
  const [passwordStatus, setPasswordStatus] = useState('')

  async function loadProfile() {
    const version = auth.getSessionVersion()
    setLoading(true)
    setProfileError('')
    try {
      const result = await obtenerMiCuenta(auth)
      if (auth.getSessionVersion() !== version) return
      setProfile(result)
      setDraft(draftDesde(result))
      auth.updatePersonalProfile({
        nombres: result.nombres,
        apellidos: result.apellidos,
        tipoDocumento: result.tipoDocumento,
        numeroDocumento: result.numeroDocumento,
        contrasenaInicial: result.contrasenaInicial,
      })
    } catch (error) {
      if (auth.getSessionVersion() === version) setProfileError(mensaje(error))
    } finally {
      if (auth.getSessionVersion() === version) setLoading(false)
    }
  }

  useEffect(() => {
    if (user) void loadProfile()
  }, [auth.getSessionVersion, auth.updatePersonalProfile, user?.correo])

  useEffect(() => {
    if (!location.hash) return
    const targetId = decodeURIComponent(location.hash.slice(1))
    requestAnimationFrame(() => document.getElementById(targetId)?.scrollIntoView())
  }, [location.hash, profile])

  const isDirty = useMemo(() => {
    if (!profile || !draft) return false
    return draft.nombres.trim() !== profile.nombres || draft.apellidos.trim() !== profile.apellidos
      || draft.tipoDocumento !== (profile.tipoDocumento ?? '')
      || draft.numeroDocumento !== (profile.numeroDocumento ?? '')
  }, [profile, draft])

  function cancelProfileEdit() {
    if (isDirty && !window.confirm('Tienes cambios sin guardar. ¿Quieres descartarlos?')) return
    if (profile) setDraft(draftDesde(profile))
    setDraftError('')
    setProfileError('')
    setEditingProfile(false)
  }

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!profile || !draft || savingProfile) return
    if (!perfilValido(draft)) {
      setDraftError('Revisa nombres, apellidos y documento. El DNI requiere 8 dígitos y el CE entre 1 y 12.')
      return
    }
    const version = auth.getSessionVersion()
    setSavingProfile(true); setProfileError(''); setProfileStatus(''); setDraftError('')
    try {
      const result = await actualizarMiCuenta(auth, {
        nombres: formatPersonName(draft.nombres),
        apellidos: formatPersonName(draft.apellidos),
        tipoDocumento: draft.tipoDocumento || null,
        numeroDocumento: draft.numeroDocumento || null,
      })
      if (auth.getSessionVersion() !== version) return
      setProfile(result); setDraft(draftDesde(result))
      setEditingProfile(false)
      auth.updatePersonalProfile({
        nombres: result.nombres,
        apellidos: result.apellidos,
        tipoDocumento: result.tipoDocumento,
        numeroDocumento: result.numeroDocumento,
        contrasenaInicial: result.contrasenaInicial,
      })
      setProfileStatus('Tus datos se actualizaron correctamente.')
    } catch (error) {
      if (auth.getSessionVersion() === version) setProfileError(mensaje(error))
    } finally {
      if (auth.getSessionVersion() === version) setSavingProfile(false)
    }
  }

  async function savePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingPassword) return
    setPasswordError(''); setPasswordStatus('')
    const policyError = passwordPolicyError(passwordNext)
    if (policyError) {
      setPasswordError(policyError)
      return
    }
    if (passwordNext !== passwordConfirm) {
      setPasswordError('La confirmación no coincide con la contraseña nueva.')
      return
    }
    const version = auth.getSessionVersion()
    setSavingPassword(true)
    try {
      await cambiarMiContrasena(auth, {
        contrasenaActual: passwordCurrent,
        contrasenaNueva: passwordNext,
      })
      if (auth.getSessionVersion() !== version) return
      setPasswordCurrent(''); setPasswordNext(''); setPasswordConfirm('')
      navigate('/login', { replace: true, state: { notice: 'password-changed' } })
      auth.logout()
    } catch (error) {
      if (auth.getSessionVersion() === version) setPasswordError(mensaje(error))
    } finally {
      if (auth.getSessionVersion() === version) setSavingPassword(false)
    }
  }

  const initials = `${profile?.nombres.trim().charAt(0) ?? ''}${profile?.apellidos.trim().charAt(0) ?? ''}`

  return <div className="dashboard mi-cuenta-page">
    <PageTitle eyebrow="CUENTA PERSONAL" title="Mi perfil" description="Administra tu información personal y consulta los datos de acceso de tu cuenta." />
    {loading && <p aria-live="polite" className="mi-cuenta-state" role="status">Cargando tu cuenta…</p>}
    {profileError && !profile && <div className="mi-cuenta-feedback mi-cuenta-feedback--error" role="alert"><span>{profileError}</span><Button onClick={() => void loadProfile()} type="button" variant="secondary">Reintentar</Button></div>}
    {profile && draft && <>
      <Card className="mi-cuenta-card mi-cuenta-summary-card">
        <div className="mi-cuenta-summary">
          <span aria-hidden="true" className="mi-cuenta-avatar">{initials || <UserRound size={22} />}</span>
          <div className="mi-cuenta-summary__text"><span className="mi-cuenta-eyebrow">CUENTA PERSONAL</span><h2>{profile.nombres} {profile.apellidos}</h2><span className="mi-cuenta-role">{profile.rol}</span></div>
        </div>
      </Card>

      <Card action={!editingProfile && <Button aria-label="Editar información personal" className="mi-cuenta-edit-button" icon={<Pencil aria-hidden="true" size={15} />} onClick={() => { setProfileStatus(''); setProfileError(''); setEditingProfile(true) }} type="button" variant="secondary">Editar información personal</Button>} className="mi-cuenta-card" title="Información personal">
        {editingProfile ? <form className="mi-cuenta-form" noValidate onSubmit={(event) => void saveProfile(event)}>
          <p className="mi-cuenta-section-intro">Actualiza los datos personales permitidos en tu cuenta.</p>
          <div className="mi-cuenta-fields">
            <div className="mi-cuenta-field"><label htmlFor="mi-cuenta-nombres">Nombres</label><input id="mi-cuenta-nombres" aria-describedby="mi-cuenta-name-help" aria-invalid={Boolean(draftError) && (!draft.nombres.trim() || draft.nombres.trim().length > 100)} autoComplete="given-name" maxLength={100} onChange={(event) => { setDraft({ ...draft, nombres: event.target.value }); setDraftError('') }} value={draft.nombres} /><small id="mi-cuenta-name-help">Hasta 100 caracteres.</small></div>
            <div className="mi-cuenta-field"><label htmlFor="mi-cuenta-apellidos">Apellidos</label><input id="mi-cuenta-apellidos" aria-describedby="mi-cuenta-lastname-help" aria-invalid={Boolean(draftError) && (!draft.apellidos.trim() || draft.apellidos.trim().length > 100)} autoComplete="family-name" maxLength={100} onChange={(event) => { setDraft({ ...draft, apellidos: event.target.value }); setDraftError('') }} value={draft.apellidos} /><small id="mi-cuenta-lastname-help">Hasta 100 caracteres.</small></div>
            <div className="mi-cuenta-field"><label htmlFor="mi-cuenta-tipo-documento">Tipo de documento</label><select id="mi-cuenta-tipo-documento" aria-describedby="mi-cuenta-document-help" onChange={(event) => { const nextType = event.target.value as ProfileDraft['tipoDocumento']; setDraft({ ...draft, tipoDocumento: nextType, numeroDocumento: nextType ? normalizeDocumentInput(nextType, draft.numeroDocumento) : '' }); setDraftError('') }} value={draft.tipoDocumento}><option value="">Sin documento</option><option value="DNI">DNI</option><option value="CE">Carné de extranjería (CE)</option></select><small id="mi-cuenta-document-help">El tipo y número deben estar completos y cumplir el formato.</small></div>
            <div className="mi-cuenta-field"><label htmlFor="mi-cuenta-numero-documento">Número de documento</label><input id="mi-cuenta-numero-documento" aria-describedby="mi-cuenta-number-help" aria-invalid={Boolean(draftError) && Boolean(draft.tipoDocumento) && !isValidDocument(draft.tipoDocumento, draft.numeroDocumento)} disabled={!draft.tipoDocumento} inputMode="numeric" maxLength={draft.tipoDocumento === 'DNI' ? 8 : 12} onChange={(event) => { setDraft({ ...draft, numeroDocumento: normalizeDocumentInput(draft.tipoDocumento, event.target.value) }); setDraftError('') }} value={draft.numeroDocumento} /><small id="mi-cuenta-number-help">{documentHelp(draft.tipoDocumento)}</small></div>
          </div>
          {draftError && <p className="mi-cuenta-feedback mi-cuenta-feedback--error" role="alert">{draftError}</p>}
          {profileError && <p className="mi-cuenta-feedback mi-cuenta-feedback--error" role="alert">{profileError}</p>}
          {profileStatus && <p className="mi-cuenta-feedback mi-cuenta-feedback--success" role="status">{profileStatus}</p>}
          <div className="mi-cuenta-actions"><Button disabled={savingProfile} onClick={cancelProfileEdit} type="button" variant="secondary"><X aria-hidden="true" size={15} /> Cancelar</Button><Button disabled={savingProfile || !isDirty} type="submit">{savingProfile ? 'Guardando…' : <><Check aria-hidden="true" size={15} /> Guardar cambios</>}</Button></div>
        </form> : <div className="mi-cuenta-details">
          <div className="mi-cuenta-detail"><span>Nombres</span><strong>{profile.nombres}</strong></div>
          <div className="mi-cuenta-detail"><span>Apellidos</span><strong>{profile.apellidos}</strong></div>
          <div className="mi-cuenta-detail"><span>Documento de identidad</span><strong>{profile.tipoDocumento && profile.numeroDocumento ? `${profile.tipoDocumento} · ${profile.numeroDocumento}` : 'Sin documento registrado'}</strong></div>
          {profileStatus && <p className="mi-cuenta-feedback mi-cuenta-feedback--success" role="status">{profileStatus}</p>}
        </div>}
      </Card>

      <div className="mi-cuenta-info-grid">
        <Card className="mi-cuenta-card mi-cuenta-info-card" title="Información de acceso">
          <div className="mi-cuenta-detail"><span className="mi-cuenta-detail-label"><Mail aria-hidden="true" size={15} /> Correo electrónico</span><strong>{profile.correo}</strong><small>El correo no se puede cambiar desde Mi perfil.</small></div>
        </Card>
        <Card className="mi-cuenta-card mi-cuenta-info-card" title="Información laboral">
          <div className="mi-cuenta-detail"><span className="mi-cuenta-detail-label"><BriefcaseBusiness aria-hidden="true" size={15} /> Rol asignado</span><strong>{profile.rol}</strong><small>Los permisos se administran según el rol.</small></div>
        </Card>
      </div>

      <Card className="mi-cuenta-card mi-cuenta-security-card" title="Seguridad" id="seguridad">
        <div className="mi-cuenta-security-intro"><span aria-hidden="true"><KeyRound size={18} /></span><div><strong>Cambiar contraseña</strong><p>El cambio es opcional. Al actualizarla, se cerrará tu sesión y tendrás que iniciar nuevamente con tu nueva contraseña.</p></div></div>
        <form className="mi-cuenta-password" noValidate onSubmit={(event) => void savePassword(event)}>
          <PasswordInput autoComplete="current-password" className="mi-cuenta-field" id="mi-cuenta-password-current" label="Contraseña actual" onChange={setPasswordCurrent} value={passwordCurrent} />
          <div className="password-entry">
            <PasswordInput className="mi-cuenta-field" describedBy="mi-cuenta-password-requirements" id="mi-cuenta-password-next" label="Contraseña nueva" onChange={(value) => { setPasswordNext(value); setPasswordError('') }} value={passwordNext} />
            <PasswordRequirements id="mi-cuenta-password-requirements" password={passwordNext} />
            <PasswordPolicyFeedback password={passwordNext} />
          </div>
          <div className="password-entry">
            <PasswordInput className="mi-cuenta-field" id="mi-cuenta-password-confirm" label="Confirmar contraseña nueva" onChange={setPasswordConfirm} value={passwordConfirm} />
            <PasswordMatchIndicator password={passwordNext} confirmation={passwordConfirm} />
          </div>
          {passwordError && <p className="mi-cuenta-feedback mi-cuenta-feedback--error" role="alert">{passwordError}</p>}
          {passwordStatus && <p className="mi-cuenta-feedback mi-cuenta-feedback--success" role="status">{passwordStatus}</p>}
          <div className="mi-cuenta-actions"><Button disabled={savingPassword || !passwordCurrent || Boolean(passwordPolicyError(passwordNext)) || !passwordConfirm || passwordConfirm !== passwordNext} type="submit">{savingPassword ? 'Actualizando…' : 'Actualizar contraseña'}</Button></div>
        </form>
      </Card>
    </>}
  </div>
}
