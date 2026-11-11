import { useCallback, useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { ApiError } from '../api/http'
import {
  consultarMisAsistencias,
  listarPersonalSeleccionable,
  registrarEntrada,
  registrarMiEntrada,
  registrarMiSalida,
  registrarSalida,
  type AsistenciaLaboral,
  type AsistenciaLaboralPagina,
  type PersonalSeleccionable,
} from '../api/asistenciasLaborales'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import { useSearchParams } from 'react-router-dom'
import './asistencia.css'
import { AsistenciaAdminView } from './AsistenciaAdminView'
import { IngresosClientesAdminView } from './IngresosClientesAdminView'
import { IngresoMembresiaPanel } from './IngresoMembresiaPanel'
import { PaseDiarioVentaPanel } from './PaseDiarioVentaPanel'
import { addUncertainWriteLock, isSessionCurrent, readUncertainWriteLocks, removeUncertainWriteLock, uncertainWriteResult } from './uncertainWriteLocks'

type Accion = 'entrada' | 'salida'
type Destino = { tipo: 'propio' } | { tipo: 'empleado'; empleado: PersonalSeleccionable }
type Confirmacion = { accion: Accion; destino: Destino }
const TAMANO_PAGINA = 10 as const

function horaLima(value: string | null) {
  if (!value) return 'Pendiente'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Hora no disponible'
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone: 'America/Lima',
  }).format(date)
}

function explicarError(error: unknown, esMarcacion = false) {
  if (esMarcacion && uncertainWriteResult(error)) {
    return 'Resultado incierto: la marcación pudo registrarse. No reintentes; existe riesgo de duplicación y se requiere revisión administrativa.'
  }
  if (error instanceof ApiError) return error.message
  if (esMarcacion) return 'No se pudo confirmar la marcación; podría haberse registrado. Actualiza la consulta antes de volver a intentarlo.'
  return 'Ocurrió un error inesperado al consultar las asistencias.'
}

function AsistenciaNoDisponible() {
  return <div className="dashboard asistencia-page"><PageTitle description="La marcación laboral de esta pantalla está habilitada para recepción." eyebrow="ASISTENCIA LABORAL" title="Asistencia" /><Card className="asistencia-card"><p className="asistencia-message">Esta pantalla está destinada al rol RECEPCIONISTA. No se ofrecen consultas administrativas ni de auditoría aquí.</p></Card></div>
}

export function AsistenciaPage() {
  const auth = useAuth()
  const esAdministrador = auth.user?.roles.includes('ADMINISTRADOR') ?? false
  const esRecepcionista = auth.user?.roles.includes('RECEPCIONISTA') ?? false
  const consultaPropiaPermitida = auth.user?.roles.some((role) =>
    role === 'ENTRENADOR' || role === 'NUTRICIONISTA') ?? false
  if (esAdministrador) return <AsistenciaAdminPage auth={auth} />
  if (esRecepcionista) return <AsistenciaRecepcionPage auth={auth} />
  if (!consultaPropiaPermitida) return <AsistenciaNoDisponible />
  return <AsistenciaLaboralView auth={auth} esRecepcionista={false} />
}

function AsistenciaAdminPage({ auth }: { auth: ReturnType<typeof useAuth> }) {
  const [pestana, setPestana] = useState<'ingresos' | 'laboral'>('ingresos')
  const [modalidad, setModalidad] = useState<'MEMBRESIA' | 'PASE_DIARIO'>('MEMBRESIA')
  const tabsRef = useRef<Array<HTMLButtonElement | null>>([])
  const subtabsRef = useRef<Array<HTMLButtonElement | null>>([])
  function teclado(event: ReactKeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight' && event.key !== 'Home' && event.key !== 'End') return
    event.preventDefault()
    const destino = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : index === 0 ? 1 : 0
    setPestana(destino === 0 ? 'ingresos' : 'laboral')
    tabsRef.current[destino]?.focus()
  }
  function tecladoModalidad(event: ReactKeyboardEvent<HTMLButtonElement>, index: number) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : index === 0 ? 1 : 0
    const value = next === 0 ? 'MEMBRESIA' : 'PASE_DIARIO'
    setModalidad(value); subtabsRef.current[next]?.focus()
  }
  return <div className="dashboard asistencia-page">
    <PageTitle description="Supervisa los ingresos de clientes y la asistencia del personal." eyebrow="OPERACIÓN DEL GIMNASIO" title="Asistencia" />
    <div className="asistencia-tabs" role="tablist" aria-label="Control de asistencias y accesos">
      <button ref={(node) => { tabsRef.current[0] = node }} id="asistencia-tab-ingresos" aria-controls="asistencia-panel-ingresos" aria-selected={pestana === 'ingresos'} role="tab" tabIndex={pestana === 'ingresos' ? 0 : -1} onClick={() => setPestana('ingresos')} onKeyDown={(event) => teclado(event, 0)} type="button">Ingresos de clientes</button>
      <button ref={(node) => { tabsRef.current[1] = node }} id="asistencia-tab-laboral" aria-controls="asistencia-panel-laboral" aria-selected={pestana === 'laboral'} role="tab" tabIndex={pestana === 'laboral' ? 0 : -1} onClick={() => setPestana('laboral')} onKeyDown={(event) => teclado(event, 1)} type="button">Asistencia laboral</button>
    </div>
    <section id={pestana === 'ingresos' ? 'asistencia-panel-ingresos' : 'asistencia-panel-laboral'} aria-labelledby={pestana === 'ingresos' ? 'asistencia-tab-ingresos' : 'asistencia-tab-laboral'} role="tabpanel" tabIndex={0}>
      {pestana === 'ingresos' ? <>
        <div aria-label="Modalidad de ingreso" className="asistencia-subtabs" role="tablist">
          <button aria-controls="admin-ingreso-membership" aria-selected={modalidad === 'MEMBRESIA'} id="admin-ingreso-tab-membership" onClick={() => setModalidad('MEMBRESIA')} onKeyDown={(event) => tecladoModalidad(event, 0)} ref={(node) => { subtabsRef.current[0] = node }} role="tab" tabIndex={modalidad === 'MEMBRESIA' ? 0 : -1} type="button">Con membresía</button>
          <button aria-controls="admin-ingreso-pass" aria-selected={modalidad === 'PASE_DIARIO'} id="admin-ingreso-tab-pass" onClick={() => setModalidad('PASE_DIARIO')} onKeyDown={(event) => tecladoModalidad(event, 1)} ref={(node) => { subtabsRef.current[1] = node }} role="tab" tabIndex={modalidad === 'PASE_DIARIO' ? 0 : -1} type="button">Pase diario</button>
        </div>
        <div aria-labelledby={modalidad === 'MEMBRESIA' ? 'admin-ingreso-tab-membership' : 'admin-ingreso-tab-pass'} id={modalidad === 'MEMBRESIA' ? 'admin-ingreso-membership' : 'admin-ingreso-pass'} role="tabpanel" tabIndex={0}>
          <IngresosClientesAdminView auth={auth} key={modalidad} modalidadFija modalidadInicial={modalidad} />
        </div>
      </> : <AsistenciaAdminView auth={auth} />}
    </section>
  </div>
}

function AsistenciaRecepcionPage({ auth }: { auth: ReturnType<typeof useAuth> }) {
  const [searchParams] = useSearchParams()
  const [pestana, setPestana] = useState<'ingresos' | 'laboral'>(
    searchParams.get('seccion') === 'laboral' ? 'laboral' : 'ingresos',
  )
  const [modalidad, setModalidad] = useState<'MEMBRESIA' | 'PASE_DIARIO'>(
    searchParams.get('modalidad') === 'PASE_DIARIO' ? 'PASE_DIARIO' : 'MEMBRESIA',
  )
  const tabsRef = useRef<Array<HTMLButtonElement | null>>([])
  const subtabsRef = useRef<Array<HTMLButtonElement | null>>([])
  function moveFocus(event: ReactKeyboardEvent<HTMLButtonElement>, index: number, inner = false) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : index === 0 ? 1 : 0
    if (inner) {
      setModalidad(next === 0 ? 'MEMBRESIA' : 'PASE_DIARIO')
      subtabsRef.current[next]?.focus()
    } else {
      setPestana(next === 0 ? 'ingresos' : 'laboral')
      tabsRef.current[next]?.focus()
    }
  }
  return <div className="dashboard asistencia-page">
    <PageTitle description="Registra accesos de clientes y consulta las asistencias del personal." eyebrow="RECEPCIÓN" title="Asistencia" />
    <div aria-label="Secciones de asistencia" className="asistencia-tabs" role="tablist">
      <button aria-controls="recepcion-panel-ingresos" aria-selected={pestana === 'ingresos'} id="recepcion-tab-ingresos" onClick={() => setPestana('ingresos')} onKeyDown={(event) => moveFocus(event, 0)} ref={(node) => { tabsRef.current[0] = node }} role="tab" tabIndex={pestana === 'ingresos' ? 0 : -1} type="button">Ingresos de clientes</button>
      <button aria-controls="recepcion-panel-laboral" aria-selected={pestana === 'laboral'} id="recepcion-tab-laboral" onClick={() => setPestana('laboral')} onKeyDown={(event) => moveFocus(event, 1)} ref={(node) => { tabsRef.current[1] = node }} role="tab" tabIndex={pestana === 'laboral' ? 0 : -1} type="button">Asistencia laboral</button>
    </div>
    <section aria-labelledby={pestana === 'ingresos' ? 'recepcion-tab-ingresos' : 'recepcion-tab-laboral'} className="asistencia-main-panel" id={pestana === 'ingresos' ? 'recepcion-panel-ingresos' : 'recepcion-panel-laboral'} role="tabpanel" tabIndex={0}>
      {pestana === 'ingresos' ? <>
        <div aria-label="Tipo de ingreso de clientes" className="asistencia-subtabs" role="tablist">
          <button aria-controls="recepcion-ingreso-membership" aria-selected={modalidad === 'MEMBRESIA'} id="recepcion-ingreso-tab-membership" onClick={() => setModalidad('MEMBRESIA')} onKeyDown={(event) => moveFocus(event, 0, true)} ref={(node) => { subtabsRef.current[0] = node }} role="tab" tabIndex={modalidad === 'MEMBRESIA' ? 0 : -1} type="button">Con membresía</button>
          <button aria-controls="recepcion-ingreso-pass" aria-selected={modalidad === 'PASE_DIARIO'} id="recepcion-ingreso-tab-pass" onClick={() => setModalidad('PASE_DIARIO')} onKeyDown={(event) => moveFocus(event, 1, true)} ref={(node) => { subtabsRef.current[1] = node }} role="tab" tabIndex={modalidad === 'PASE_DIARIO' ? 0 : -1} type="button">Pase diario</button>
        </div>
        <div aria-labelledby={modalidad === 'MEMBRESIA' ? 'recepcion-ingreso-tab-membership' : 'recepcion-ingreso-tab-pass'} className="asistencia-subpanel" id={modalidad === 'MEMBRESIA' ? 'recepcion-ingreso-membership' : 'recepcion-ingreso-pass'} role="tabpanel" tabIndex={0}>
          {modalidad === 'MEMBRESIA' ? <IngresoMembresiaPanel auth={auth} /> : <PaseDiarioVentaPanel auth={auth} />}
        </div>
      </> : <AsistenciaLaboralView auth={auth} esRecepcionista mostrarTitulo={false} />}
    </section>
  </div>
}

function AsistenciaLaboralView({ auth, esRecepcionista, mostrarTitulo = true }: {
  auth: ReturnType<typeof useAuth>
  esRecepcionista: boolean
  mostrarTitulo?: boolean
}) {
  const [personal, setPersonal] = useState<PersonalSeleccionable[]>([])
  const [idSeleccionado, setIdSeleccionado] = useState('')
  const [pagina, setPagina] = useState<AsistenciaLaboralPagina | null>(null)
  const [paginaActual, setPaginaActual] = useState(0)
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [cargando, setCargando] = useState(true)
  const [actualizando, setActualizando] = useState(false)
  const [errorPersonal, setErrorPersonal] = useState('')
  const [errorAsistencias, setErrorAsistencias] = useState('')
  const [cargandoPersonal, setCargandoPersonal] = useState(true)
  const [exito, setExito] = useState('')
  const [confirmacion, setConfirmacion] = useState<Confirmacion | null>(null)
  const [procesando, setProcesando] = useState(false)
  const account = auth.user?.correo ?? ''
  const [locks, setLocks] = useState<string[]>([])
  const [locksAccount, setLocksAccount] = useState('')
  const [locksLoaded, setLocksLoaded] = useState(false)
  const [lockReadFailed, setLockReadFailed] = useState(false)
  const lock = useRef(false)
  const requestSequence = useRef(0)
  const confirmationDialogRef = useRef<HTMLElement | null>(null)
  const confirmationTriggerRef = useRef<HTMLButtonElement | null>(null)
  const confirmationWasOpen = useRef(false)
  const empleado = personal.find((item) => String(item.idEmpleado) === idSeleccionado)
  const lockKey = (destino: Destino) => destino.tipo === 'propio' ? 'self' : `employee:${destino.empleado.idEmpleado}`
  const currentLocks = locksAccount === account ? locks : []
  const estaBloqueado = (destino: Destino) => currentLocks.includes(lockKey(destino))

  useEffect(() => {
    if (confirmacion) {
      confirmationWasOpen.current = true
      const first = confirmationDialogRef.current?.querySelector<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)',
      )
      first?.focus()
      return
    }
    if (confirmationWasOpen.current) {
      confirmationWasOpen.current = false
      confirmationTriggerRef.current?.focus()
      confirmationTriggerRef.current = null
    }
  }, [confirmacion])

  function handleConfirmationKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape' && !procesando) {
      event.preventDefault()
      setConfirmacion(null)
      return
    }
    if (event.key !== 'Tab') return
    const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )).filter((item) => !item.hidden)
    if (!focusable.length) { event.preventDefault(); return }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && (document.activeElement === first || !event.currentTarget.contains(document.activeElement))) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && (document.activeElement === last || !event.currentTarget.contains(document.activeElement))) {
      event.preventDefault(); first.focus()
    }
  }

  useEffect(() => {
    let active = true
    if (!esRecepcionista || !account) { setLocks([]); setLocksLoaded(true); return }
    setLocksLoaded(false)
    void readUncertainWriteLocks(account, 'staff-attendance').then((stored) => {
      if (active) { setLocks(stored); setLocksAccount(account); setLocksLoaded(true); setLockReadFailed(false) }
    }).catch(() => {
      if (active) { setLocksLoaded(true); setLockReadFailed(true) }
    })
    return () => { active = false }
  }, [account, esRecepcionista])

  const cargarPersonal = useCallback(async () => {
    setCargandoPersonal(true)
    try {
      const lista = await listarPersonalSeleccionable(auth)
      setPersonal(lista)
      setErrorPersonal('')
      setIdSeleccionado((current) => current && !lista.some((item) => String(item.idEmpleado) === current) ? '' : current)
    } catch (reason) {
      setErrorPersonal(explicarError(reason))
    } finally {
      setCargandoPersonal(false)
    }
  }, [auth])

  const cargarAsistencias = useCallback(async (page: number, silencioso = false) => {
    const request = ++requestSequence.current
    if (silencioso) setActualizando(true)
    else setCargando(true)
    setErrorAsistencias('')
    try {
      const result = await consultarMisAsistencias(auth, {
        page,
        size: TAMANO_PAGINA,
        ...(fechaDesde ? { fechaDesde } : {}),
        ...(fechaHasta ? { fechaHasta } : {}),
      })
      if (request === requestSequence.current) {
        setPagina(result)
        setPaginaActual(result.pagina)
      }
    } catch (reason) {
      if (request === requestSequence.current) setErrorAsistencias(explicarError(reason))
    } finally {
      if (request === requestSequence.current) {
        setCargando(false)
        setActualizando(false)
      }
    }
  }, [auth, fechaDesde, fechaHasta])

  useEffect(() => {
    if (esRecepcionista) void cargarPersonal()
  }, [cargarPersonal, esRecepcionista])

  useEffect(() => {
    setPaginaActual(0)
    void cargarAsistencias(0)
  }, [cargarAsistencias])

  async function confirmarMarcacion() {
    if (!confirmacion || lock.current || !locksLoaded || lockReadFailed || estaBloqueado(confirmacion.destino)) return
    lock.current = true
    setProcesando(true)
    setExito('')
    setErrorAsistencias('')
    const operation = lockKey(confirmacion.destino)
    const sessionVersion = auth.getSessionVersion()
    let persisted = false
    try {
      await addUncertainWriteLock(account, 'staff-attendance', operation)
      persisted = true
      if (!isSessionCurrent(auth, sessionVersion)) {
        await removeUncertainWriteLock(account, 'staff-attendance', operation)
        return
      }
      setLocks((current) => current.includes(operation) ? current : [...current, operation])
      let result: AsistenciaLaboral
      if (confirmacion.destino.tipo === 'propio') {
        result = confirmacion.accion === 'entrada'
          ? await registrarMiEntrada(auth)
          : await registrarMiSalida(auth)
      } else {
        const id = confirmacion.destino.empleado.idEmpleado
        result = confirmacion.accion === 'entrada'
          ? await registrarEntrada(auth, id)
          : await registrarSalida(auth, id)
      }
      const quien = confirmacion.destino.tipo === 'propio'
        ? 'Tu asistencia'
        : `Asistencia de ${confirmacion.destino.empleado.nombres} ${confirmacion.destino.empleado.apellidos}`
      const momento = confirmacion.accion === 'entrada' ? result.fechaEntradaUtc : result.fechaSalidaUtc
      if (isSessionCurrent(auth, sessionVersion)) {
        setExito(`${quien}: ${confirmacion.accion === 'entrada' ? 'entrada' : 'salida'} registrada a las ${horaLima(momento)} (hora de Lima).`)
        setConfirmacion(null)
        await Promise.all([cargarAsistencias(paginaActual, true), cargarPersonal()])
      }
      try {
        await removeUncertainWriteLock(account, 'staff-attendance', operation)
        if (isSessionCurrent(auth, sessionVersion)) setLocks((current) => current.filter((item) => item !== operation))
      } catch { if (isSessionCurrent(auth, sessionVersion)) setLockReadFailed(true) }
    } catch (reason) {
      if (persisted && !uncertainWriteResult(reason)) {
        try {
          await removeUncertainWriteLock(account, 'staff-attendance', operation)
          if (isSessionCurrent(auth, sessionVersion)) setLocks((current) => current.filter((item) => item !== operation))
        } catch { if (isSessionCurrent(auth, sessionVersion)) setLockReadFailed(true) }
      }
      if (isSessionCurrent(auth, sessionVersion)) {
        setErrorAsistencias(!persisted
          ? 'No se envió: no se pudo establecer el bloqueo persistente.'
          : uncertainWriteResult(reason) ? '' : explicarError(reason, true))
        setConfirmacion(null)
        if (persisted && uncertainWriteResult(reason)) {
          if (confirmacion.destino.tipo === 'propio') void cargarAsistencias(paginaActual, true)
          else void cargarPersonal()
        }
      }
    } finally {
      lock.current = false
      if (isSessionCurrent(auth, sessionVersion)) setProcesando(false)
    }
  }

  function confirmarSeleccion(accion: Accion, destino: Destino) {
    setExito('')
    setErrorAsistencias('')
    setConfirmacion({ accion, destino })
  }

  return <div className="dashboard asistencia-page">
    {mostrarTitulo && <PageTitle description={esRecepcionista ? 'Registra entradas y salidas autorizadas y consulta tus propias asistencias.' : 'Consulta exclusivamente tus propias asistencias laborales.'} eyebrow={esRecepcionista ? 'RECEPCIÓN · PERSONAL' : 'PORTAL DEL PERSONAL'} title={esRecepcionista ? 'Asistencia laboral' : 'Mis asistencias'} />}
    {lockReadFailed && <p className="asistencia-feedback asistencia-feedback--error" role="alert">Las marcaciones están bloqueadas porque no se pudo leer el estado persistente de seguridad.</p>}
    {currentLocks.length > 0 && <div className="asistencia-feedback asistencia-feedback--error" role="alert">Marcación bloqueada por resultado incierto ({currentLocks.map((item) => item === 'self' ? 'propia' : 'personal seleccionado').join(', ')}). Pudo registrarse; no reintentes. La consulta se actualizará, pero no confirma por sí sola la marcación. Solicita revisión administrativa.<Button className="asistencia-reload" disabled={actualizando} onClick={() => void Promise.all([cargarAsistencias(paginaActual, true), cargarPersonal()])} type="button" variant="secondary">Actualizar estado consultable</Button></div>}
    {exito && <p className="asistencia-feedback asistencia-feedback--success" role="status">{exito}</p>}

    {esRecepcionista && <Card className="asistencia-card" title="Registrar asistencia de personal">
      {errorPersonal && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{errorPersonal}</p>}
      <div className="asistencia-controls">
        <label className="asistencia-field" htmlFor="personal-asistencia">Entrenador o nutricionista activo</label>
        <select id="personal-asistencia" onChange={(event) => setIdSeleccionado(event.target.value)} value={idSeleccionado}>
          <option value="">Selecciona una persona</option>
          {personal.map((persona) => <option key={persona.idEmpleado} value={persona.idEmpleado}>{persona.apellidos}, {persona.nombres} · {persona.rol === 'ENTRENADOR' ? 'Entrenador' : 'Nutricionista'}</option>)}
        </select>
        <div className="asistencia-actions">
          <Button disabled={!locksLoaded || lockReadFailed || !empleado || procesando || Boolean(empleado && estaBloqueado({ tipo: 'empleado', empleado }))} onClick={(event) => { if (empleado) { confirmationTriggerRef.current = event.currentTarget; confirmarSeleccion('entrada', { tipo: 'empleado', empleado }) } }} type="button">Registrar entrada</Button>
          <Button disabled={!locksLoaded || lockReadFailed || !empleado || procesando || Boolean(empleado && estaBloqueado({ tipo: 'empleado', empleado }))} onClick={(event) => { if (empleado) { confirmationTriggerRef.current = event.currentTarget; confirmarSeleccion('salida', { tipo: 'empleado', empleado }) } }} type="button" variant="secondary">Registrar salida</Button>
        </div>
      </div>
      {cargandoPersonal && <p aria-live="polite" className="asistencia-message">Cargando personal autorizado…</p>}
      {!cargandoPersonal && !errorPersonal && personal.length === 0 && <p className="asistencia-message">No hay entrenadores o nutricionistas activos disponibles.</p>}
    </Card>}

    {esRecepcionista && <Card className="asistencia-card asistencia-self-card" title="Mi asistencia">
      <p className="asistencia-description">El servidor identifica tu usuario autenticado; estas acciones no envían un ID de empleado.</p>
      <div className="asistencia-actions">
        <Button disabled={!locksLoaded || lockReadFailed || procesando || estaBloqueado({ tipo: 'propio' })} onClick={(event) => { confirmationTriggerRef.current = event.currentTarget; confirmarSeleccion('entrada', { tipo: 'propio' }) }} type="button">Registrar mi entrada</Button>
        <Button disabled={!locksLoaded || lockReadFailed || procesando || estaBloqueado({ tipo: 'propio' })} onClick={(event) => { confirmationTriggerRef.current = event.currentTarget; confirmarSeleccion('salida', { tipo: 'propio' }) }} type="button" variant="secondary">Registrar mi salida</Button>
      </div>
    </Card>}

    <Card className="asistencia-card" title="Mis asistencias">
      <form className="asistencia-filters" onSubmit={(event) => { event.preventDefault(); setPaginaActual(0); void cargarAsistencias(0) }}>
        <label className="asistencia-date-field">Desde<input aria-label="Fecha desde" onChange={(event) => setFechaDesde(event.target.value)} type="date" value={fechaDesde} /></label>
        <label className="asistencia-date-field">Hasta<input aria-label="Fecha hasta" onChange={(event) => setFechaHasta(event.target.value)} type="date" value={fechaHasta} /></label>
        <Button disabled={cargando || actualizando} type="submit">Aplicar filtros</Button>
        <Button disabled={cargando || actualizando} onClick={() => { setFechaDesde(''); setFechaHasta(''); setPaginaActual(0); void cargarAsistencias(0) }} type="button" variant="secondary">Limpiar</Button>
      </form>
      {errorAsistencias && <p className="asistencia-feedback asistencia-feedback--error" role="alert">{errorAsistencias}<Button className="asistencia-reload" disabled={actualizando} onClick={() => void Promise.all([cargarAsistencias(paginaActual, true), cargarPersonal()])} type="button" variant="secondary">Actualizar</Button></p>}
      {cargando ? <p aria-live="polite" className="asistencia-message">Cargando tus asistencias…</p> : errorAsistencias ? null : !pagina?.contenido.length ? <p className="asistencia-message">No hay asistencias propias para el período seleccionado.</p> : <>
        <div className="table-wrap"><table className="activity-table asistencia-table asistencia-self-table"><thead><tr><th scope="col">FECHA LABORAL</th><th scope="col">ENTRADA (LIMA)</th><th scope="col">SALIDA (LIMA)</th><th scope="col">ESTADO</th></tr></thead><tbody>{pagina.contenido.map((fila) => <tr key={fila.idAsistencia}><td>{fila.fechaLaboral}</td><td>{horaLima(fila.fechaEntradaUtc)}</td><td>{horaLima(fila.fechaSalidaUtc)}</td><td><span className={`asistencia-state asistencia-state--${fila.estado.toLowerCase()}`}>{fila.estado === 'ACTIVA' ? 'Activa' : 'Anulada'}</span></td></tr>)}</tbody></table></div>
        <nav aria-label="Paginación de mis asistencias" className="asistencia-pagination"><button disabled={paginaActual <= 0 || actualizando} onClick={() => void cargarAsistencias(paginaActual - 1, true)} type="button">Anterior</button><span>Página {paginaActual + 1} de {Math.max(1, pagina.totalPaginas)} · {pagina.totalElementos} registros</span><button disabled={paginaActual + 1 >= pagina.totalPaginas || actualizando} onClick={() => void cargarAsistencias(paginaActual + 1, true)} type="button">Siguiente</button></nav>
      </>}
    </Card>

    {confirmacion && <div className="asistencia-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !procesando) setConfirmacion(null) }}><section ref={confirmationDialogRef} tabIndex={-1} aria-labelledby="asistencia-confirm-title" aria-modal="true" className="asistencia-dialog" onKeyDown={handleConfirmationKeyDown} onClick={(event) => event.stopPropagation()} role="dialog"><p className="asistencia-dialog-eyebrow">CONFIRMACIÓN DE MARCACIÓN</p><h2 id="asistencia-confirm-title">Confirmar {confirmacion.accion === 'entrada' ? 'entrada' : 'salida'}</h2><p>{confirmacion.destino.tipo === 'propio' ? 'Se registrará tu propia' : `Se registrará la ${confirmacion.accion} de ${confirmacion.destino.empleado.nombres} ${confirmacion.destino.empleado.apellidos}`} asistencia.</p><p className="asistencia-dialog-time">Hora local actual: {horaLima(new Date().toISOString())} (America/Lima)</p><div className="asistencia-actions"><Button disabled={procesando} onClick={() => setConfirmacion(null)} type="button" variant="secondary">Cancelar</Button><Button disabled={procesando} onClick={() => void confirmarMarcacion()} type="button">{procesando ? 'Registrando…' : 'Confirmar marcación'}</Button></div></section></div>}
  </div>
}
