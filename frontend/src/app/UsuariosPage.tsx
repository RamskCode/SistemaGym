import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Check, Eye, LoaderCircle, Mail, Pencil, RotateCcw, Search, UserPlus, UserX, X } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  actualizarUsuario,
  buscarUsuarioPorId,
  cambiarEstadoUsuario,
  cambiarRolUsuario,
  crearUsuario,
  reenviarInvitacion,
  listarUsuarios,
  type ActualizarUsuarioRequest,
  type CrearUsuarioRequest,
  type RolUsuario,
  type TipoDocumentoUsuario,
  type Usuario,
} from '../api/usuarios'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './usuarios.css'
import { formatPersonName } from '../utils/personName'

type FormValues = { nombres: string; apellidos: string; correo: string; rol: RolUsuario; tipoDocumento: TipoDocumentoUsuario | ''; numeroDocumento: string; editarDocumento: boolean }
type Pending = { type: 'crear'; payload: CrearUsuarioRequest } | { type: 'editar'; id: number; payload: ActualizarUsuarioRequest }
  | { type: 'rol'; usuario: Usuario; nuevoRol: RolUsuario }
  | { type: 'estado'; usuario: Usuario; activo: boolean }
type Uncertain = { type: 'crear' } | { type: 'editar'; id: number }
  | { type: 'account-action'; userId: number; action: 'rol' | 'activar' | 'desactivar' | 'contrasena'; targetRole?: RolUsuario; targetActive?: boolean }
const STORAGE_KEY = 'usuarios:escritura-incierta'
const ROLES: RolUsuario[] = ['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA']
const EMPTY: FormValues = { nombres: '', apellidos: '', correo: '', rol: 'ENTRENADOR', tipoDocumento: '', numeroDocumento: '', editarDocumento: false }

function readUncertain(): Uncertain | null {
  try { const value = sessionStorage.getItem(STORAGE_KEY); return value ? JSON.parse(value) as Uncertain : null }
  catch { return null }
}

function apiError(error: unknown) {
  if (!(error instanceof ApiError)) return 'Ocurrió un error inesperado. No compartas datos sensibles en reportes de error.'
  const messages: Record<ApiError['code'], string> = {
    unauthenticated: 'La sesión expiró. Inicia sesión nuevamente.', forbidden: 'El servidor rechazó la operación por permisos insuficientes.',
    'not-found': 'El usuario ya no existe o no se encontró.', validation: 'El servidor rechazó los datos. Revisa el formulario.',
    conflict: 'El cambio entra en conflicto con datos o reglas del servidor (por ejemplo, conservar un administrador activo). No se muestran detalles potencialmente privados.',
    server: 'El servidor devolvió un error; la operación podría haberse aplicado.', http: 'No fue posible completar la operación.',
    network: 'No se pudo confirmar la respuesta del servidor.', 'invalid-response': 'El servidor devolvió una respuesta no válida.',
  }
  return messages[error.code]
}

function uncertainResult(error: unknown) {
  return error instanceof ApiError && ['network', 'invalid-response', 'server'].includes(error.code)
}

function validate(values: FormValues, editing: boolean): { payload?: CrearUsuarioRequest | ActualizarUsuarioRequest; error?: string } {
  const nombres = formatPersonName(values.nombres)
  const apellidos = formatPersonName(values.apellidos)
  const correo = values.correo.trim()
  const numeroDocumento = values.numeroDocumento.trim().toUpperCase()
  if (!nombres || nombres.length > 100) return { error: 'Los nombres son obligatorios y no pueden superar 100 caracteres.' }
  if (!apellidos || apellidos.length > 100) return { error: 'Los apellidos son obligatorios y no pueden superar 100 caracteres.' }
  if (!correo || correo.length > 150 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)) return { error: 'Ingresa un correo válido de hasta 150 caracteres.' }
  if (!ROLES.includes(values.rol)) return { error: 'Selecciona uno de los cuatro roles disponibles.' }
  if (editing && !values.editarDocumento) return { payload: { nombres, apellidos, correo } }
  if (!values.tipoDocumento && !numeroDocumento) return { error: 'Selecciona un tipo e ingresa el número de documento.' }
  if (!values.tipoDocumento || !numeroDocumento) return { error: 'Tipo y número de documento deben proporcionarse juntos.' }
  if (!/^\d{1,12}$/.test(numeroDocumento)) return { error: 'El documento debe contener entre 1 y 12 dígitos numéricos.' }
  if (values.tipoDocumento === 'DNI' && !/^\d{8}$/.test(numeroDocumento)) return { error: 'El DNI debe contener exactamente 8 dígitos.' }
  if (editing) return { payload: { nombres, apellidos, correo, tipoDocumento: values.tipoDocumento, numeroDocumento } }
  return { payload: { nombres, apellidos, correo, rol: values.rol, tipoDocumento: values.tipoDocumento, numeroDocumento } }
}

function maskedDocument(type: TipoDocumentoUsuario | null, number: string | null) {
  if (!type || !number) return 'Sin documento'
  return `${type} · ••••••${number.slice(-2)}`
}

function stateLabel(state: string) {
  return state === 'PENDIENTE' ? 'Pendiente de activación' : state === 'ACTIVO' ? 'Activo' : 'Inactivo'
}

function roleLabel(role: RolUsuario) {
  return role.charAt(0) + role.slice(1).toLocaleLowerCase('es-PE')
}

function formatCreatedAt(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'No disponible'
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeStyle: 'short' }).format(date)
}

function trapDialogFocus(event: ReactKeyboardEvent<HTMLElement>) {
  if (event.key !== 'Tab') return
  const dialog = event.currentTarget
  const items = Array.from(dialog.querySelectorAll<HTMLElement>(
    'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )).filter((item) => !item.hidden && item.getAttribute('aria-hidden') !== 'true')
  if (!items.length) { event.preventDefault(); return }
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

function hasEditedUserChanges(values: FormValues, user: Usuario) {
  const result = validate(values, true)
  if (!result.payload) return false
  const payload = result.payload as ActualizarUsuarioRequest
  return payload.nombres !== formatPersonName(user.nombres)
    || payload.apellidos !== formatPersonName(user.apellidos)
    || payload.correo !== user.correo.trim()
    || ('tipoDocumento' in payload && (payload.tipoDocumento !== user.tipoDocumento
      || payload.numeroDocumento !== user.numeroDocumento))
}

export function UsuariosPage() {
  const auth = useAuth()
  const [users, setUsers] = useState<Usuario[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [feedbackTone, setFeedbackTone] = useState<'success' | 'error'>('success')
  const [query, setQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [stateFilter, setStateFilter] = useState('')
  const [detail, setDetail] = useState<Usuario | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Usuario | null>(null)
  const [values, setValues] = useState<FormValues>(EMPTY)
  const [formError, setFormError] = useState('')
  const [writeError, setWriteError] = useState('')
  const [pending, setPending] = useState<Pending | null>(null)
  const [uncertain, setUncertain] = useState<Uncertain | null>(() => readUncertain())
  const [verificationLoaded, setVerificationLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [resendingId, setResendingId] = useState<number | null>(null)
  const [verificationEmail, setVerificationEmail] = useState('')
  const [passwordVerifiedOutOfBand, setPasswordVerifiedOutOfBand] = useState(false)
  const [roleDraft, setRoleDraft] = useState<RolUsuario>('ENTRENADOR')
  const [confirmDeactivation, setConfirmDeactivation] = useState(false)
  const lock = useRef(false)
  const detailCloseRef = useRef<HTMLButtonElement>(null)
  const formCloseRef = useRef<HTMLButtonElement>(null)
  const detailTriggerRef = useRef<HTMLButtonElement | null>(null)
  const formTriggerRef = useRef<HTMLButtonElement | null>(null)
  const formTriggerUserId = useRef<number | null>(null)
  const detailWasOpen = useRef(false)

  const loadUsers = useCallback(async () => {
    const sessionVersion = auth.getSessionVersion()
    setLoading(true); setError('')
    try {
      const result = await listarUsuarios(auth)
      if (auth.getSessionVersion() === sessionVersion) setUsers(result)
    } catch (reason) { if (auth.getSessionVersion() === sessionVersion) setError(apiError(reason)) }
    finally { if (auth.getSessionVersion() === sessionVersion) setLoading(false) }
  }, [auth])
  useEffect(() => { void loadUsers() }, [loadUsers])

  // Any credential remains only in component memory and is discarded when the authenticated identity changes.
  useEffect(() => {
    setPending(null); setFormOpen(false); setDetail(null)
  }, [auth.user?.correo, auth.user?.issuedAt])

  useLayoutEffect(() => {
    if (detail) {
      detailWasOpen.current = true
      detailCloseRef.current?.focus()
    } else if (detailWasOpen.current) {
      detailWasOpen.current = false
      detailTriggerRef.current?.focus()
      detailTriggerRef.current = null
    }
  }, [detail])

  useLayoutEffect(() => {
    if (formOpen) formCloseRef.current?.focus()
    else if (formTriggerRef.current || formTriggerUserId.current !== null) {
      const trigger = formTriggerRef.current?.isConnected ? formTriggerRef.current
        : document.querySelector<HTMLButtonElement>(`[data-user-edit-trigger="${formTriggerUserId.current}"]`)
      trigger?.focus()
      formTriggerRef.current = null
      formTriggerUserId.current = null
    }
  }, [formOpen])

  useEffect(() => {
    function handleEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      if (pending) { event.stopPropagation(); if (!saving) cancelPending(); return }
      if (formOpen) {
        event.stopPropagation()
        if (!saving) {
          setFormOpen(false); setPending(null); setValues(EMPTY); setEditing(null); setFormError('')
        }
        return
      }
      if (detail) { event.stopPropagation(); closeDetail(); return }
    }
    document.addEventListener('keydown', handleEscape, true)
    return () => document.removeEventListener('keydown', handleEscape, true)
  }, [detail, formOpen, pending, saving])

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase()
    return users.filter((user) => (!needle || `${user.nombres} ${user.apellidos} ${user.correo}`.toLocaleLowerCase().includes(needle))
      && (!roleFilter || user.rol === roleFilter) && (!stateFilter || user.estado === stateFilter))
  }, [users, query, roleFilter, stateFilter])
  const highlightedUserId = detail?.idUsuario ?? (formOpen ? editing?.idUsuario ?? null : null)

  function openCreate() {
    setEditing(null); setValues(EMPTY); setFormError(''); setWriteError(''); setFeedback(''); setFormOpen(true)
  }
  async function openEdit(user: Usuario) {
    const sessionVersion = auth.getSessionVersion()
    setLoading(true); setError(''); setFeedback('')
    try {
      const fresh = await buscarUsuarioPorId(auth, user.idUsuario)
      if (auth.getSessionVersion() !== sessionVersion) return
      setEditing(fresh)
      setValues({ ...EMPTY, nombres: fresh.nombres, apellidos: fresh.apellidos, correo: fresh.correo, editarDocumento: false })
      setFormError(''); setWriteError(''); setFormOpen(true)
    } catch (reason) { if (auth.getSessionVersion() === sessionVersion) setError(apiError(reason)) }
    finally { if (auth.getSessionVersion() === sessionVersion) setLoading(false) }
  }
  async function openDetail(user: Usuario) {
    const sessionVersion = auth.getSessionVersion()
    setLoading(true); setError('')
    try {
      const fresh = await buscarUsuarioPorId(auth, user.idUsuario)
      if (auth.getSessionVersion() !== sessionVersion) return
      setDetail(fresh); setRoleDraft(fresh.rol)
    }
    catch (reason) { if (auth.getSessionVersion() === sessionVersion) setError(apiError(reason)) }
    finally { if (auth.getSessionVersion() === sessionVersion) setLoading(false) }
  }
  function closeDetail() {
    setDetail(null)
    const trigger = detailTriggerRef.current
      ?? document.querySelector<HTMLButtonElement>('[data-user-detail-trigger]')
    window.setTimeout(() => trigger?.focus(), 0)
  }
  function closeForm() {
    if (saving) return
    setFormOpen(false); setPending(null); setValues(EMPTY); setEditing(null); setFormError(''); setWriteError('')
  }
  function review(event: FormEvent) {
    event.preventDefault()
    const result = validate(values, Boolean(editing))
    if (!result.payload) { setFormError(result.error ?? 'Revisa los datos.'); return }
    if (editing && !hasEditedUserChanges(values, editing)) {
      setFormError('No hay cambios para guardar.')
      return
    }
    setFormError('')
    setPending(editing
      ? { type: 'editar', id: editing.idUsuario, payload: result.payload as ActualizarUsuarioRequest }
      : { type: 'crear', payload: result.payload as CrearUsuarioRequest })
  }

  function markUncertain(marker: Uncertain) {
    setUncertain(marker); setVerificationLoaded(false)
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(marker)) } catch { /* Bloqueo de esta vista permanece en memoria. */ }
  }
  async function submit() {
    if (!pending || (pending.type !== 'crear' && pending.type !== 'editar') || lock.current || uncertain) return
    const operation = pending
    lock.current = true; setSaving(true); setWriteError(''); setError(''); setFeedback('')
    const sessionVersion = auth.getSessionVersion()
    try {
      let savedUser: Usuario
      let invitationMessage = ''
      if (operation.type === 'crear') {
        const invitation = await crearUsuario(auth, operation.payload)
        savedUser = invitation.usuario
        invitationMessage = invitation.mensaje
        setFeedbackTone(invitation.correoEnviado ? 'success' : 'error')
      } else {
        savedUser = await actualizarUsuario(auth, operation.id, operation.payload)
        setFeedbackTone('success')
      }
      if (auth.getSessionVersion() !== sessionVersion) return
      setUsers((current) => operation.type === 'crear'
        ? [...current, savedUser].sort((a, b) => a.idUsuario - b.idUsuario)
        : current.map((user) => user.idUsuario === savedUser.idUsuario ? savedUser : user))
      setFeedback(operation.type === 'crear'
        ? invitationMessage
        : `Usuario #${savedUser.idUsuario} actualizado correctamente.`)
      setPending(null); setFormOpen(false); setEditing(null); setValues(EMPTY)
    } catch (reason) {
      if (uncertainResult(reason)) {
        const marker: Uncertain = operation.type === 'crear' ? { type: 'crear' } : { type: 'editar', id: operation.id }
        if (auth.getSessionVersion() !== sessionVersion) { persistUncertain(marker); return }
        markUncertain(marker)
        setVerificationEmail(operation.type === 'crear' ? operation.payload.correo : '')
        setPending(null); setFormOpen(false); setValues(EMPTY)
        setError('No se confirmó el resultado. La operación pudo haberse aplicado. Se bloquean nuevas escrituras; consulta el listado o detalle y verifica el estado real antes de habilitarlas.')
      } else if (auth.getSessionVersion() === sessionVersion) setWriteError(apiError(reason))
    } finally { lock.current = false; if (auth.getSessionVersion() === sessionVersion) setSaving(false) }
  }

  async function resendInvitation(user: Usuario) {
    if (user.estado !== 'PENDIENTE' || lock.current || uncertain) return
    lock.current = true; setSaving(true); setResendingId(user.idUsuario); setError(''); setFeedback('')
    const sessionVersion = auth.getSessionVersion()
    try {
      const result = await reenviarInvitacion(auth, user.idUsuario)
      if (auth.getSessionVersion() !== sessionVersion) return
      setFeedbackTone(result.correoEnviado ? 'success' : 'error')
      setFeedback(result.mensaje)
      setUsers((current) => current.map((item) => item.idUsuario === user.idUsuario ? result.usuario : item))
      if (detail?.idUsuario === user.idUsuario) setDetail(result.usuario)
    } catch (reason) {
      if (auth.getSessionVersion() === sessionVersion) setError(apiError(reason))
    } finally {
      lock.current = false
      setResendingId(null)
      if (auth.getSessionVersion() === sessionVersion) setSaving(false)
    }
  }

  function persistUncertain(marker: Uncertain) {
    try { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(marker)) } catch { /* La vista actual mantiene el bloqueo en memoria. */ }
  }

  async function submitAccountAction() {
    if (!pending || (pending.type !== 'rol' && pending.type !== 'estado') || lock.current || uncertain) return
    const operation = pending
    lock.current = true; setSaving(true); setWriteError(''); setError(''); setFeedback('')
    const sessionVersion = auth.getSessionVersion()
    try {
      let updated: Usuario | null = null
      if (operation.type === 'rol') updated = await cambiarRolUsuario(auth, operation.usuario.idUsuario, operation.nuevoRol)
      else if (operation.type === 'estado') updated = await cambiarEstadoUsuario(auth, operation.usuario.idUsuario, operation.activo)
      if (auth.getSessionVersion() !== sessionVersion) return
      if (updated) {
        setUsers((current) => current.map((user) => user.idUsuario === updated!.idUsuario ? updated! : user))
        setDetail(updated)
      }
      setPending(null)
      if (operation.type === 'rol') {
        setFeedback(`El rol de ${operation.usuario.nombres} ${operation.usuario.apellidos} cambió a ${updated!.rol} según respuesta del servidor.`)
        const ownAccount = operation.usuario.correo.toLowerCase() === auth.user?.correo.toLowerCase()
        if (ownAccount) { setSaving(false); auth.logout() }
      } else {
        setFeedback(`${operation.usuario.nombres} ${operation.usuario.apellidos} quedó ${updated!.estado.toLowerCase()} según respuesta del servidor.`)
        const ownAccount = operation.usuario.correo.toLowerCase() === auth.user?.correo.toLowerCase()
        if (ownAccount && !operation.activo) { setSaving(false); auth.logout() }
      }
    } catch (reason) {
      if (uncertainResult(reason)) {
        const marker: Uncertain = {
          type: 'account-action', userId: operation.usuario.idUsuario,
          action: operation.type === 'rol' ? 'rol' : operation.activo ? 'activar' : 'desactivar',
          ...(operation.type === 'rol' ? { targetRole: operation.nuevoRol } : {}),
          ...(operation.type === 'estado' ? { targetActive: operation.activo } : {}),
        }
        persistUncertain(marker)
        if (auth.getSessionVersion() === sessionVersion) {
          setUncertain(marker); setVerificationLoaded(false); setPasswordVerifiedOutOfBand(false)
          setPending(null)
          setError('No se confirmó el cambio. Consulta el estado actual del usuario antes de habilitar otra operación.')
        }
      } else if (auth.getSessionVersion() === sessionVersion) setWriteError(apiError(reason))
    } finally {
      lock.current = false
      if (auth.getSessionVersion() === sessionVersion) setSaving(false)
    }
  }

  async function refreshForVerification() {
    const sessionVersion = auth.getSessionVersion()
    setLoading(true); setError(''); setVerificationLoaded(false)
    try {
      const latest = await listarUsuarios(auth)
      if (auth.getSessionVersion() !== sessionVersion) return
      setUsers(latest)
      if (uncertain?.type === 'account-action') {
        const account = await buscarUsuarioPorId(auth, uncertain.userId)
        if (auth.getSessionVersion() !== sessionVersion) return
        setUsers((current) => current.map((user) => user.idUsuario === account.idUsuario ? account : user))
        setDetail(account)
        if (uncertain.action === 'contrasena') {
          setFeedback('Se consultó el perfil, pero no permite verificar la contraseña. Contacta al titular por un canal seguro y confirma que pudo autenticarse con la contraseña que recibió. No se reintentará hasta esa verificación explícita.')
        } else if (uncertain.action === 'rol') {
          setFeedback(`Rol observado en servidor: ${account.rol}. Confirma si coincide con el cambio solicitado antes de habilitar otra operación.`)
        } else {
          setFeedback(`Estado observado en servidor: ${account.estado}. Confirma si coincide con el cambio solicitado antes de habilitar otra operación.`)
        }
      } else if (uncertain?.type === 'crear' && verificationEmail.trim()) {
        const match = latest.find((user) => user.correo.toLowerCase() === verificationEmail.trim().toLowerCase())
        setFeedback(match ? `Se encontró un usuario con el correo utilizado (ID ${match.idUsuario}); verifica sus datos antes de habilitar otra operación.` : 'No se encontró una coincidencia por correo en el listado actual; verifica manualmente con el servidor antes de reintentar.')
      } else if (uncertain?.type === 'editar') {
        const match = latest.find((user) => user.idUsuario === uncertain.id)
        setFeedback(match ? `Usuario #${match.idUsuario} actualizado desde el servidor; verifica los campos antes de habilitar otra operación.` : 'No se encontró el usuario en el listado actual; verifica manualmente el estado real.')
      }
      setVerificationLoaded(true)
    } catch (reason) { if (auth.getSessionVersion() === sessionVersion) setError(`No fue posible verificar el estado: ${apiError(reason)}`) }
    finally { if (auth.getSessionVersion() === sessionVersion) setLoading(false) }
  }
  function acknowledgeVerification() {
    if (!uncertain || !verificationLoaded) return
    if (uncertain.type === 'account-action' && uncertain.action === 'contrasena' && !passwordVerifiedOutOfBand) return
    setUncertain(null); setVerificationLoaded(false); setVerificationEmail('')
    setPasswordVerifiedOutOfBand(false)
    try { sessionStorage.removeItem(STORAGE_KEY) } catch { /* Ignorar almacenamiento no disponible. */ }
    setFeedback('Verificación confirmada. Las operaciones administrativas vuelven a estar habilitadas.')
  }

  function beginRoleChange(user: Usuario, role: RolUsuario) {
    if (role === user.rol) return
    setWriteError(''); setPending({ type: 'rol', usuario: user, nuevoRol: role })
  }

  function beginStatusChange(user: Usuario) {
    setWriteError(''); setConfirmDeactivation(false); setPending({ type: 'estado', usuario: user, activo: user.estado !== 'ACTIVO' })
  }

  function cancelPending() {
    setPending(null); setWriteError(''); setConfirmDeactivation(false)
  }

  function confirmPending() {
    if (pending?.type === 'crear' || pending?.type === 'editar') void submit()
    else void submitAccountAction()
  }

  return <div className="dashboard usuarios-page">
    <PageTitle description="Administra el acceso, los roles y el estado del personal. Las nuevas cuentas reciben una invitación por correo para crear su contraseña." eyebrow="ADMINISTRACIÓN DEL PERSONAL" title="Usuarios y empleados" actions={<Button className="usuarios-create-button" type="button" disabled={Boolean(uncertain) || saving} onClick={(event) => { formTriggerRef.current = event.currentTarget; openCreate() }} icon={<UserPlus size={17} aria-hidden="true"/>}>Nuevo usuario</Button>} />
    {feedback && <p className={`usuarios-feedback usuarios-feedback--${feedbackTone}`} role={feedbackTone === 'error' ? 'alert' : 'status'}>{feedback}</p>}
    {uncertain && <section className="usuarios-uncertain" role="alert"><div><strong>Operación pendiente de verificación</strong><p>{uncertain.type === 'crear' ? 'Alta de usuario sin confirmación.' : uncertain.type === 'editar' ? `Edición del usuario #${uncertain.id} sin confirmación.` : `${uncertain.action} del usuario #${uncertain.userId} sin confirmación.`} No se reintentará automáticamente; las escrituras permanecen bloqueadas.</p>{uncertain.type === 'account-action' && uncertain.action === 'contrasena' ? <><p>El perfil no expone ni permite verificar contraseñas. Contacta al titular por un canal seguro y solicita que pruebe iniciar sesión con la contraseña que recibió. No compartas contraseñas por esta interfaz.</p>{verificationLoaded && <label className="usuarios-check usuarios-out-of-band"><input type="checkbox" checked={passwordVerifiedOutOfBand} onChange={(event) => setPasswordVerifiedOutOfBand(event.target.checked)}/>Confirmo que el titular verificó el acceso por un canal independiente.</label>}</> : <p>Actualiza el listado/detalle, compara el estado real y confirma explícitamente la verificación.</p>}{uncertain.type === 'crear' && <label className="usuarios-verify-email">Correo utilizado para el alta<input type="email" aria-label="Correo para verificar alta incierta" autoComplete="off" value={verificationEmail} onChange={(event) => setVerificationEmail(event.target.value)}/></label>}</div><div className="usuarios-uncertain-actions"><Button type="button" variant="secondary" disabled={loading} onClick={() => void refreshForVerification()}>Consultar usuario</Button>{verificationLoaded && (uncertain.type !== 'account-action' || uncertain.action !== 'contrasena' || passwordVerifiedOutOfBand) && <Button type="button" disabled={loading} onClick={acknowledgeVerification}>Confirmo; habilitar operaciones</Button>}</div></section>}
    <Card className="usuarios-card" title="Directorio del personal" action={<span className="usuarios-total">{users.length} {users.length === 1 ? 'usuario' : 'usuarios'}</span>}>
      <div className="usuarios-toolbar" role="search" aria-label="Buscar y filtrar usuarios">
        <label className="usuarios-search search-field"><Search size={18} aria-hidden="true"/><span className="usuarios-sr-only">Buscar por nombre o correo</span><input type="search" aria-label="Buscar por nombre o correo" placeholder="Buscar por nombre o correo" value={query} onChange={(event) => setQuery(event.target.value)}/></label>
        <label className="usuarios-filter"><span>Rol</span><select aria-label="Filtrar por rol" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}><option value="">Todos los roles</option>{ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}</select></label>
        <label className="usuarios-filter"><span>Estado</span><select aria-label="Filtrar por estado" value={stateFilter} onChange={(event) => setStateFilter(event.target.value)}><option value="">Todos los estados</option><option value="PENDIENTE">Pendiente</option><option value="ACTIVO">Activo</option><option value="INACTIVO">Inactivo</option></select></label>
        <Button className="usuarios-clear-button" type="button" variant="secondary" disabled={!query && !roleFilter && !stateFilter} onClick={() => { setQuery(''); setRoleFilter(''); setStateFilter('') }} icon={<RotateCcw size={15} aria-hidden="true"/>}>Limpiar filtros</Button>
      </div>
      <div className="usuarios-result-summary" aria-live="polite">
        <span>{query || roleFilter || stateFilter ? `Mostrando ${filtered.length} de ${users.length} usuarios` : `Todos los usuarios · ${filtered.length} ${filtered.length === 1 ? 'resultado' : 'resultados'}`}</span>
        {(query || roleFilter || stateFilter) && <span className="usuarios-filter-active">Filtros activos</span>}
      </div>
      {error && <div className="usuarios-feedback usuarios-feedback--error" role="alert"><span>{error}</span><Button type="button" variant="secondary" disabled={loading} onClick={() => void loadUsers()}>Reintentar consulta</Button></div>}
       {loading ? <p className="usuarios-message" aria-live="polite"><LoaderCircle size={18} className="usuarios-spinner" aria-hidden="true"/>Cargando usuarios…</p> : error ? null : filtered.length === 0 ? <div className="usuarios-empty"><span className="usuarios-empty__icon"><Search size={21} aria-hidden="true"/></span><p>{users.length ? 'No hay usuarios que coincidan con estos filtros.' : 'Todavía no hay usuarios registrados.'}</p><span>{users.length ? 'Prueba con otros criterios o limpia los filtros.' : 'Cuando registres personal, aparecerá en este directorio.'}</span></div> : <div className="table-wrap usuarios-table-wrap"><table className="activity-table usuarios-table"><caption className="usuarios-sr-only">Directorio de usuarios y empleados</caption><thead><tr><th scope="col">Usuario</th><th scope="col">Rol</th><th scope="col">Documento</th><th scope="col">Estado</th><th className="usuarios-actions-heading" scope="col">Acciones</th></tr></thead><tbody>{filtered.map((user) => <tr className={highlightedUserId === user.idUsuario ? 'usuarios-row-selected' : undefined} key={user.idUsuario}><td data-label="Usuario"><strong>{user.apellidos}, {user.nombres}</strong><small>{user.correo}</small></td><td data-label="Rol">{roleLabel(user.rol)}</td><td data-label="Documento">{maskedDocument(user.tipoDocumento, user.numeroDocumento)}</td><td data-label="Estado"><span className={`usuarios-state usuarios-state--${user.estado.toLowerCase()}`}><span aria-hidden="true" className="usuarios-state__dot"/>{stateLabel(user.estado)}</span></td><td data-label="Acciones"><div className="usuarios-actions"><Button data-user-detail-trigger="true" className="usuarios-row-action" type="button" variant="secondary" onClick={(event) => { detailTriggerRef.current = event.currentTarget; void openDetail(user) }} icon={<Eye size={15} aria-hidden="true"/>}>Detalle</Button><Button data-user-edit-trigger={user.idUsuario} className="usuarios-row-action" type="button" variant="secondary" disabled={Boolean(uncertain) || loading || saving} onClick={(event) => { formTriggerRef.current = event.currentTarget; formTriggerUserId.current = user.idUsuario; void openEdit(user) }} icon={<Pencil size={15} aria-hidden="true"/>}>Editar</Button>{user.estado === 'PENDIENTE' ? <Button className="usuarios-row-action usuarios-resend-action" type="button" variant="secondary" disabled={Boolean(uncertain) || saving} onClick={() => void resendInvitation(user)} icon={resendingId === user.idUsuario ? <LoaderCircle className="usuarios-spinner" size={15} aria-hidden="true"/> : <Mail size={15} aria-hidden="true"/>}>{resendingId === user.idUsuario ? 'Enviando…' : 'Reenviar invitación'}</Button> : <Button className={`usuarios-row-action ${user.estado === 'ACTIVO' ? 'usuarios-row-action--danger' : 'usuarios-row-action--success'}`} type="button" variant="secondary" disabled={Boolean(uncertain) || saving} onClick={() => beginStatusChange(user)} icon={user.estado === 'ACTIVO' ? <UserX size={15} aria-hidden="true"/> : <Check size={15} aria-hidden="true"/>}>{user.estado === 'ACTIVO' ? 'Desactivar cuenta' : 'Activar cuenta'}</Button>}</div></td></tr>)}</tbody></table></div>}
    </Card>

    {formOpen && <div className="usuarios-backdrop" onClick={closeForm}><section className={`usuarios-dialog usuarios-dialog--form ${editing ? 'usuarios-dialog--edit' : ''}`} role="dialog" aria-modal="true" aria-labelledby="usuarios-form-title" onKeyDown={trapDialogFocus} onClick={(event) => event.stopPropagation()}>
      <button ref={formCloseRef} className="usuarios-close" type="button" aria-label="Cerrar formulario" disabled={saving} onClick={closeForm}><X size={18}/></button>
      <p className="usuarios-dialog__eyebrow">{editing ? 'PERFIL DEL PERSONAL' : 'NUEVO ACCESO'}</p>
      <h2 id="usuarios-form-title">{editing ? 'Editar empleado' : 'Crear usuario'}</h2>
      <p className="usuarios-dialog__intro">{editing ? `${editing.nombres} ${editing.apellidos}` : 'Registra los datos del empleado y define su rol en el sistema.'}</p>
      <form className={`usuarios-form ${editing ? 'usuarios-form--edit' : ''}`} onSubmit={review}>
        {editing && <section className="usuarios-form-section usuarios-form-section--personal" aria-labelledby="usuarios-edit-personal-title"><h3 id="usuarios-edit-personal-title">Información personal</h3>
          <label className="usuarios-form-field"><span>Nombres <b>*</b></span><input aria-label="Nombres" required maxLength={100} value={values.nombres} onChange={(event) => setValues({ ...values, nombres: event.target.value })}/></label>
          <label className="usuarios-form-field"><span>Apellidos <b>*</b></span><input aria-label="Apellidos" required maxLength={100} value={values.apellidos} onChange={(event) => setValues({ ...values, apellidos: event.target.value })}/></label>
        </section>}
        {!editing && <>
          <label className="usuarios-form-field"><span>Nombres <b>*</b></span><input aria-label="Nombres" required maxLength={100} value={values.nombres} onChange={(event) => setValues({ ...values, nombres: event.target.value })}/></label>
          <label className="usuarios-form-field"><span>Apellidos <b>*</b></span><input aria-label="Apellidos" required maxLength={100} value={values.apellidos} onChange={(event) => setValues({ ...values, apellidos: event.target.value })}/></label>
        </>}
        {editing && <section className="usuarios-form-section usuarios-form-section--account" aria-labelledby="usuarios-edit-account-title"><h3 id="usuarios-edit-account-title">Datos de cuenta</h3>
          <label className="usuarios-form-field usuarios-form-field--wide"><span>Correo electrónico <b>*</b></span><input aria-label="Correo" type="email" required maxLength={150} autoComplete="email" value={values.correo} onChange={(event) => setValues({ ...values, correo: event.target.value })}/></label>
        </section>}
        {!editing && <label className="usuarios-form-field usuarios-form-field--wide"><span>Correo electrónico <b>*</b></span><input aria-label="Correo" type="email" required maxLength={150} autoComplete="email" value={values.correo} onChange={(event) => setValues({ ...values, correo: event.target.value })}/></label>}
        {!editing && <>
          <p className="usuarios-invitation-note"><Mail size={18} aria-hidden="true"/><span><strong>Invitación por correo</strong><small>Enviaremos un enlace para que la persona cree su contraseña.</small></span></p>
          <label className="usuarios-form-field"><span>Rol <b>*</b></span><select aria-label="Rol del usuario" required value={values.rol} onChange={(event) => setValues({ ...values, rol: event.target.value as RolUsuario })}>{ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}</select></label>
        </>}
        {editing && <section className="usuarios-form-section usuarios-form-section--document" aria-labelledby="usuarios-edit-document-title"><h3 id="usuarios-edit-document-title">Documento de identidad</h3>
          <label className="usuarios-check"><input type="checkbox" checked={values.editarDocumento} onChange={(event) => setValues((old) => ({ ...old, editarDocumento: event.target.checked, tipoDocumento: event.target.checked ? editing.tipoDocumento ?? '' : '', numeroDocumento: event.target.checked ? editing.numeroDocumento ?? '' : '' }))}/>Actualizar documento de identidad</label>
        </section>}
        {(!editing || values.editarDocumento) && <div className={`usuarios-document ${editing ? 'usuarios-document--edit usuarios-form-field--wide' : ''}`}><div className="usuarios-document__intro"><strong>Documento de identidad</strong><p>Se utiliza para identificar de forma única a cada empleado.</p></div><div className="usuarios-document-types" role="group" aria-label="Tipo de documento">{(['DNI', 'CE'] as const).map((type) => <button aria-pressed={values.tipoDocumento === type} className={values.tipoDocumento === type ? 'usuarios-document-type usuarios-document-type--selected' : 'usuarios-document-type'} key={type} onClick={() => setValues({ ...values, tipoDocumento: type, numeroDocumento: '' })} type="button">{type}</button>)}</div><label className="usuarios-form-field"><span>Número de documento <b>*</b></span><input aria-label="Número de documento" type="text" inputMode="numeric" maxLength={values.tipoDocumento === 'DNI' ? 8 : 12} autoComplete="off" aria-describedby="usuarios-document-help" aria-invalid={Boolean(values.numeroDocumento && !/^\d+$/.test(values.numeroDocumento))} value={values.numeroDocumento} onChange={(event) => { const next = event.target.value; if (/^\d*$/.test(next)) setValues({ ...values, numeroDocumento: next }) }}/><small id="usuarios-document-help" aria-live="polite">{values.tipoDocumento ? values.tipoDocumento === 'DNI' ? 'DNI: exactamente 8 dígitos.' : 'CE: de 1 a 12 dígitos.' : 'Selecciona DNI o CE; escribe solo números.'} {values.numeroDocumento.length}/{values.tipoDocumento === 'DNI' ? 8 : 12} dígitos</small></label></div>}
        {formError && <p className="usuarios-inline-error" role="alert">{formError}</p>}
        <div className="usuarios-actions usuarios-form-actions"><Button type="button" variant="secondary" disabled={saving} onClick={closeForm}>Cancelar</Button><Button type="submit" disabled={Boolean(uncertain) || saving || (editing ? !hasEditedUserChanges(values, editing) : false)}>{editing ? 'Guardar cambios' : 'Revisar datos'}</Button></div>
      </form>
    </section></div>}

        {detail && <div className="usuarios-backdrop" onClick={closeDetail}><section className="usuarios-dialog usuarios-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="usuarios-detail-title" onKeyDown={trapDialogFocus} onClick={(event) => event.stopPropagation()}>
       <button ref={detailCloseRef} className="usuarios-close" type="button" aria-label="Cerrar información del empleado" onClick={closeDetail}><X size={18}/></button>
      <p className="usuarios-dialog__eyebrow">PERFIL DEL PERSONAL</p>
      <div className="usuarios-detail-heading"><div><h2 id="usuarios-detail-title">Información del empleado</h2><p>{detail.nombres} {detail.apellidos}</p></div><span className={`usuarios-state usuarios-state--${detail.estado.toLowerCase()}`}><span aria-hidden="true" className="usuarios-state__dot"/>{stateLabel(detail.estado)}</span></div>
      <section className="usuarios-detail-section" aria-labelledby="usuarios-personal-title"><h3 id="usuarios-personal-title">Información personal</h3><dl className="usuarios-detail"><div><dt>Nombre completo</dt><dd>{detail.nombres} {detail.apellidos}</dd></div><div><dt>Documento de identidad</dt><dd>{detail.tipoDocumento && detail.numeroDocumento ? `${detail.tipoDocumento} · ${detail.numeroDocumento}` : 'Sin documento registrado'}</dd></div></dl></section>
      <section className="usuarios-detail-section" aria-labelledby="usuarios-access-title"><h3 id="usuarios-access-title">Datos de acceso</h3><dl className="usuarios-detail"><div><dt>Correo electrónico</dt><dd>{detail.correo}</dd></div><div><dt>Rol</dt><dd>{roleLabel(detail.rol)}</dd></div><div><dt>Fecha de registro</dt><dd>{formatCreatedAt(detail.fechaCreacion)}</dd></div></dl></section>
      <section className="usuarios-account-actions" aria-label="Administrar rol del empleado"><h3>Rol del empleado</h3><div className="usuarios-role-change"><label>Rol<select aria-label="Nuevo rol" value={roleDraft} onChange={(event) => setRoleDraft(event.target.value as RolUsuario)}>{ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}</select></label><Button type="button" variant="secondary" disabled={roleDraft === detail.rol || Boolean(uncertain) || saving} onClick={() => beginRoleChange(detail, roleDraft)}>Cambiar rol</Button></div></section>
    </section></div>}

    
    {pending && <div className="usuarios-backdrop" onClick={() => { if (!saving) cancelPending() }}><section className="usuarios-dialog" role="dialog" aria-modal="true" aria-labelledby="usuarios-confirm-title" onKeyDown={trapDialogFocus} onClick={(event) => event.stopPropagation()}>
      {pending.type === 'crear' || pending.type === 'editar' ? <><h2 id="usuarios-confirm-title">Confirmar {pending.type === 'crear' ? 'alta' : 'edición'} de usuario</h2><p>Confirma los datos que se enviarán. La contraseña inicial no se muestra ni se incluye en esta revisión.</p><dl className="usuarios-detail"><div><dt>Nombres</dt><dd>{pending.payload.nombres}</dd></div><div><dt>Apellidos</dt><dd>{pending.payload.apellidos}</dd></div><div><dt>Correo</dt><dd>{pending.payload.correo}</dd></div>{pending.type === 'crear' && <div><dt>Rol</dt><dd>{pending.payload.rol}</dd></div>}<div><dt>Documento</dt><dd>{'tipoDocumento' in pending.payload ? pending.payload.tipoDocumento ? maskedDocument(pending.payload.tipoDocumento, pending.payload.numeroDocumento ?? null) : 'Sin documento / conservar documento existente según campos enviados' : 'Documento existente sin cambios'}</dd></div></dl></>
        : pending.type === 'rol' ? <><h2 id="usuarios-confirm-title">Confirmar cambio de rol</h2><p>Usuario afectado: <strong>{pending.usuario.nombres} {pending.usuario.apellidos}</strong> · {pending.usuario.correo}</p><dl className="usuarios-detail"><div><dt>Rol actual</dt><dd>{pending.usuario.rol}</dd></div><div><dt>Nuevo rol</dt><dd>{pending.nuevoRol}</dd></div></dl><p className="usuarios-warning">El cambio modifica los permisos efectivos del usuario en las siguientes solicitudes. El backend protegerá al último administrador activo. Si cambias tu propio rol, se cerrará tu sesión local para que vuelvas a ingresar con permisos actualizados.</p></>
         : <><h2 id="usuarios-confirm-title">Confirmar {pending.activo ? 'activación' : 'desactivación'} de cuenta</h2><p>Empleado: <strong>{pending.usuario.nombres} {pending.usuario.apellidos}</strong> · {pending.usuario.correo}</p><dl className="usuarios-detail"><div><dt>Estado actual</dt><dd>{stateLabel(pending.usuario.estado)}</dd></div><div><dt>Estado solicitado</dt><dd>{pending.activo ? 'Activo' : 'Inactivo'}</dd></div></dl><p className="usuarios-warning">{pending.activo ? 'La cuenta podrá iniciar sesión nuevamente.' : 'La persona no podrá iniciar nuevas sesiones mientras la cuenta esté desactivada. Sus datos e historial se conservarán.'}</p>{!pending.activo && <label className="usuarios-check usuarios-danger-check"><input type="checkbox" checked={confirmDeactivation} onChange={(event) => setConfirmDeactivation(event.target.checked)}/>Confirmo que deseo desactivar la cuenta de {pending.usuario.nombres} {pending.usuario.apellidos}.</label>}</>}
      {writeError && <p className="usuarios-inline-error" role="alert">{writeError}</p>}
      <div className="usuarios-actions"><Button type="button" variant="secondary" disabled={saving} onClick={cancelPending}>Cancelar</Button><Button type="button" disabled={saving || Boolean(uncertain) || (pending.type === 'estado' && !pending.activo && !confirmDeactivation)} onClick={confirmPending}>{saving ? 'Enviando…' : pending.type === 'crear' || pending.type === 'editar' ? 'Confirmar y guardar' : 'Confirmar y ejecutar'}</Button></div>
    </section></div>}
  </div>
}
