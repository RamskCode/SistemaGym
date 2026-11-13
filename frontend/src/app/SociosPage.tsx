import { useEffect, useLayoutEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { ArrowDownUp, Eye, FilePlus2, LoaderCircle, Pencil, Search, UserRoundCheck, UserRoundX, X } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  actualizarSocio,
  buscarSocioPorId,
  cambiarEstadoSocio,
  listarSocios,
  registrarSocio,
  type Socio,
  type SocioRequest,
} from '../api/socios'
import { listarMembresias, listarMembresiasPorSocio, type Membresia } from '../api/membresias'
import { listarPasesDiariosPorSocio, type PaseDiarioHistorial } from '../api/ingresos'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { documentHelp, isValidDocument, normalizeDocumentInput } from '../utils/documentIdentity'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import './socios.css'
import { formatPersonName } from '../utils/personName'

type EstadoFiltro = 'TODOS' | Socio['estado']
type VinculoFiltro = 'TODOS' | 'CON_MEMBRESIA' | 'SIN_MEMBRESIA'
type Orden = 'nombres' | 'apellidos' | 'codigoSocio' | 'fechaRegistro'
type FormValues = Omit<SocioRequest, 'dni' | 'tipoDocumento' | 'numeroDocumento' | 'fechaNacimiento' | 'celular' | 'correo'> & {
  tipoDocumento: 'DNI' | 'CE'
  numeroDocumento: string
  fechaNacimiento: string
  celular: string
  correo: string
}

const emptyValues: FormValues = {
  tipoDocumento: 'DNI', numeroDocumento: '', nombres: '', apellidos: '', fechaNacimiento: '', celular: '', correo: '',
}

const TODAY = new Date().toISOString().slice(0, 10)

function messageFor(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Ocurrió un error inesperado. Inténtalo nuevamente.'
}

function valuesFromSocio(socio: Socio): FormValues {
  return {
    tipoDocumento: socio.tipoDocumento ?? 'DNI',
    numeroDocumento: socio.numeroDocumento ?? socio.dni ?? '',
    nombres: socio.nombres,
    apellidos: socio.apellidos,
    fechaNacimiento: socio.fechaNacimiento ?? '',
    celular: socio.celular ?? '',
    correo: socio.correo ?? '',
  }
}

function requestFromValues(values: FormValues): SocioRequest {
  const optional = (value: string) => value.trim() || null
  return {
    dni: values.tipoDocumento === 'DNI' ? optional(values.numeroDocumento) : null,
    tipoDocumento: values.numeroDocumento.trim() ? values.tipoDocumento : null,
    numeroDocumento: optional(values.numeroDocumento),
    nombres: formatPersonName(values.nombres),
    apellidos: formatPersonName(values.apellidos),
    fechaNacimiento: optional(values.fechaNacimiento),
    celular: optional(values.celular),
    correo: optional(values.correo),
  }
}

function validate(values: FormValues): string | null {
  if (!values.nombres.trim() || !values.apellidos.trim()) {
    return 'Los nombres y apellidos son obligatorios.'
  }
  if (values.nombres.trim().length > 100 || values.apellidos.trim().length > 100) {
    return 'Los nombres y apellidos no pueden superar 100 caracteres.'
  }
  if (values.numeroDocumento && !isValidDocument(values.tipoDocumento, values.numeroDocumento)) return values.tipoDocumento === 'DNI' ? 'El DNI debe tener exactamente 8 dígitos.' : 'El CE debe tener entre 1 y 12 dígitos.'
  if (values.fechaNacimiento && values.fechaNacimiento > TODAY) {
    return 'La fecha de nacimiento no puede ser futura.'
  }
  if (values.celular.length > 20) return 'El celular no puede superar 20 caracteres.'
  if (values.correo.length > 150) return 'El correo no puede superar 150 caracteres.'
  if (values.correo.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.correo.trim())) {
    return 'Ingresa un correo electrónico válido.'
  }
  return null
}

function formatDate(value: string | null, includeTime = false, emptyLabel = 'No registrado') {
  if (!value) return emptyLabel
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'No disponible'
  return new Intl.DateTimeFormat('es-PE', includeTime
    ? { dateStyle: 'long', timeStyle: 'short' }
    : { dateStyle: 'long', timeZone: 'UTC' }).format(date)
}

function trapDialogFocus(event: ReactKeyboardEvent<HTMLElement>) {
  if (event.key !== 'Tab') return
  const elements = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
    'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )).filter((element) => !element.hidden)
  if (!elements.length) { event.preventDefault(); return }
  if (event.shiftKey && document.activeElement === elements[0]) {
    event.preventDefault(); elements[elements.length - 1].focus()
  } else if (!event.shiftKey && document.activeElement === elements[elements.length - 1]) {
    event.preventDefault(); elements[0].focus()
  }
}

function hasChanges(values: FormValues, original: Socio) {
  const payload = requestFromValues(values)
  return payload.tipoDocumento !== (original.tipoDocumento ?? (original.dni ? 'DNI' : null)) || payload.numeroDocumento !== (original.numeroDocumento ?? original.dni)
    || payload.nombres !== formatPersonName(original.nombres)
    || payload.apellidos !== formatPersonName(original.apellidos)
    || payload.fechaNacimiento !== original.fechaNacimiento
    || payload.celular !== original.celular
    || payload.correo !== original.correo
}

export function SociosPage() {
  const auth = useAuth()
  const [socios, setSocios] = useState<Socio[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [busquedaAplicada, setBusquedaAplicada] = useState('')
  const [estado, setEstado] = useState<EstadoFiltro>('TODOS')
  const [vinculo, setVinculo] = useState<VinculoFiltro>('TODOS')
  const [sociosConMembresia, setSociosConMembresia] = useState<Set<number>>(new Set())
  const [errorMembresias, setErrorMembresias] = useState('')
  const [orden, setOrden] = useState<Orden>('nombres')
  const [ordenDesc, setOrdenDesc] = useState(false)
  const [tamanoPagina, setTamanoPagina] = useState(10)
  const [pagina, setPagina] = useState(1)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [socioEditando, setSocioEditando] = useState<Socio | null>(null)
  const [values, setValues] = useState<FormValues>(emptyValues)
  const [errorFormulario, setErrorFormulario] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [cargandoEdicion, setCargandoEdicion] = useState<number | null>(null)
  const [socioEstado, setSocioEstado] = useState<Socio | null>(null)
  const [socioDetalle, setSocioDetalle] = useState<Socio | null>(null)
  const [motivo, setMotivo] = useState('')
  const [guardandoEstado, setGuardandoEstado] = useState(false)
  const requestId = useRef(0)
  const formLock = useRef(false)
  const statusLock = useRef(false)
  const formCloseRef = useRef<HTMLButtonElement>(null)
  const detailCloseRef = useRef<HTMLButtonElement>(null)
  const formTriggerRef = useRef<HTMLButtonElement | null>(null)
  const detailTriggerRef = useRef<HTMLButtonElement | null>(null)
  const formTriggerId = useRef<number | null>(null)
  const detailTriggerId = useRef<number | null>(null)
  const dialogWasOpen = useRef(false)
  const detailWasOpen = useRef(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setBusquedaAplicada(busqueda.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [busqueda])

  useEffect(() => {
    const currentRequest = ++requestId.current
    let mounted = true
    setCargando(true)
    setError('')

    void listarSocios(auth, { query: busquedaAplicada, estado })
      .then((result) => {
        if (mounted && currentRequest === requestId.current) setSocios(result)
      })
      .catch((reason: unknown) => {
        if (mounted && currentRequest === requestId.current) setError(messageFor(reason))
      })
      .finally(() => {
        if (mounted && currentRequest === requestId.current) setCargando(false)
      })

    return () => {
      mounted = false
    }
  }, [auth, busquedaAplicada, estado])

  const puedeConsultarMembresias = auth.user?.roles.some((role) => role === 'ADMINISTRADOR' || role === 'RECEPCIONISTA') ?? false
  useEffect(() => {
    if (!puedeConsultarMembresias) return
    let active = true
    void listarMembresias(auth).then((items) => {
      if (active) {
        setSociosConMembresia(new Set(items.flatMap((item) => item.socio?.idSocio ? [item.socio.idSocio] : [])))
        setErrorMembresias('')
      }
    }).catch((reason: unknown) => {
      if (active) setErrorMembresias(messageFor(reason))
    })
    return () => { active = false }
  }, [auth, puedeConsultarMembresias])

  const sociosFiltrados = useMemo(() => socios.filter((socio) => vinculo === 'TODOS'
    || (vinculo === 'CON_MEMBRESIA' ? sociosConMembresia.has(socio.idSocio) : !sociosConMembresia.has(socio.idSocio))),
  [socios, sociosConMembresia, vinculo])
  const sociosOrdenados = useMemo(() => [...sociosFiltrados].sort((a, b) => {
    const left = a[orden] ?? ''
    const right = b[orden] ?? ''
    const result = orden === 'fechaRegistro'
      ? String(left).localeCompare(String(right))
      : String(left).localeCompare(String(right), 'es', { sensitivity: 'base' })
    return ordenDesc ? -result : result
  }), [sociosFiltrados, orden, ordenDesc])
  const totalPaginas = Math.max(1, Math.ceil(sociosOrdenados.length / tamanoPagina))
  const sociosPagina = sociosOrdenados.slice((pagina - 1) * tamanoPagina, pagina * tamanoPagina)

  useEffect(() => { setPagina(1) }, [estado, busquedaAplicada, vinculo, orden, ordenDesc, tamanoPagina])
  useEffect(() => { if (pagina > totalPaginas) setPagina(totalPaginas) }, [pagina, totalPaginas])

  useLayoutEffect(() => {
    if (formOpen) {
      dialogWasOpen.current = true
      formCloseRef.current?.focus()
    } else if (dialogWasOpen.current) {
      dialogWasOpen.current = false
      const trigger = formTriggerRef.current?.isConnected ? formTriggerRef.current
        : document.querySelector<HTMLButtonElement>(`[data-edit-socio="${formTriggerId.current}"]`)
      trigger?.focus()
      formTriggerRef.current = null
      formTriggerId.current = null
    }
  }, [formOpen])

  useLayoutEffect(() => {
    if (socioDetalle) {
      detailWasOpen.current = true
      detailCloseRef.current?.focus()
    } else if (detailWasOpen.current) {
      detailWasOpen.current = false
      const trigger = detailTriggerRef.current?.isConnected ? detailTriggerRef.current
        : document.querySelector<HTMLButtonElement>(`[data-detail-socio="${detailTriggerId.current}"]`)
      trigger?.focus()
      detailTriggerRef.current = null
      detailTriggerId.current = null
    }
  }, [socioDetalle])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || guardando || guardandoEstado) return
      if (socioDetalle) setSocioDetalle(null)
      else if (formOpen) closeForm()
      else if (socioEstado) { setSocioEstado(null); setMotivo('') }
    }
    document.addEventListener('keydown', onKeyDown, true)
    return () => document.removeEventListener('keydown', onKeyDown, true)
  }, [formOpen, socioDetalle, socioEstado, guardando, guardandoEstado])

  function comenzarRegistro() {
    setSocioEditando(null)
    setValues(emptyValues)
    setErrorFormulario('')
    setFormOpen(true)
    setExito('')
  }

  function closeForm() {
    if (guardando) return
    setFormOpen(false)
    setSocioEditando(null)
    setValues(emptyValues)
    setErrorFormulario('')
  }

  async function comenzarEdicion(socio: Socio) {
    if (cargandoEdicion !== null) return
    setCargandoEdicion(socio.idSocio)
    setError('')
    setExito('')
    try {
      const actual = await buscarSocioPorId(auth, socio.idSocio)
      setSocioEditando(actual)
      setValues(valuesFromSocio(actual))
      setErrorFormulario('')
      setFormOpen(true)
    } catch (reason) {
      setError(messageFor(reason))
    } finally {
      setCargandoEdicion(null)
    }
  }

  async function guardar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (formLock.current) return
    const validationError = validate(values)
    if (validationError) {
      setErrorFormulario(validationError)
      return
    }

    formLock.current = true
    setGuardando(true)
    setErrorFormulario('')
    setError('')
    try {
      const payload = requestFromValues(values)
      if (socioEditando) {
        await actualizarSocio(auth, socioEditando.idSocio, payload)
        setExito(`Se actualizaron los datos de ${payload.nombres} ${payload.apellidos}.`)
      } else {
        const creado = await registrarSocio(auth, payload)
        setExito(`Socio ${creado.codigoSocio} registrado correctamente.`)
        setBusqueda('')
        setEstado('TODOS')
      }
      setFormOpen(false)
      setSocioEditando(null)
      setValues(emptyValues)
      void recargar()
    } catch (reason) {
      setErrorFormulario(messageFor(reason))
    } finally {
      formLock.current = false
      setGuardando(false)
    }
  }

  async function recargar() {
    const currentRequest = ++requestId.current
    setCargando(true)
    setError('')
    try {
      const result = await listarSocios(auth, { query: busqueda, estado })
      if (currentRequest === requestId.current) setSocios(result)
    } catch (reason) {
      if (currentRequest === requestId.current) setError(messageFor(reason))
    } finally {
      if (currentRequest === requestId.current) setCargando(false)
    }
  }

  async function confirmarCambioEstado(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!socioEstado || !motivo.trim() || statusLock.current) return

    const nuevoEstado = socioEstado.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO'
    statusLock.current = true
    setGuardandoEstado(true)
    setError('')
    setExito('')
    try {
      const updated = await cambiarEstadoSocio(auth, socioEstado.idSocio, nuevoEstado, motivo.trim())
      setExito(`${updated.codigoSocio} ahora está ${updated.estado.toLowerCase()}.`)
      setSocioEstado(null)
      setMotivo('')
      await recargar()
    } catch (reason) {
      setError(messageFor(reason))
    } finally {
      statusLock.current = false
      setGuardandoEstado(false)
    }
  }

  function actualizarCampo(campo: keyof FormValues, valor: string) {
    setValues((actuales) => ({ ...actuales, [campo]: valor }))
    setErrorFormulario('')
  }

  function limpiarFiltros() {
    setBusqueda('')
    setBusquedaAplicada('')
    setEstado('TODOS')
    setVinculo('TODOS')
    setOrden('nombres')
    setOrdenDesc(false)
    setPagina(1)
  }

  function mostrarDetalle(socio: Socio) {
    setSocioDetalle(socio)
  }

  const socioResaltadoId = socioDetalle?.idSocio ?? (formOpen ? socioEditando?.idSocio ?? null : null)

  return (
    <div className="dashboard socios-page">
      <PageTitle
        description="Consulta y administra los datos de los socios registrados."
        eyebrow="RECEPCIÓN · REGISTROS"
        title="Socios"
        actions={<Button className="socios-create-button" onClick={(event) => { formTriggerRef.current = event.currentTarget; comenzarRegistro() }} type="button" icon={<FilePlus2 size={17} aria-hidden="true" />}>Nuevo socio</Button>}
      />

      {exito && <p className="socios-feedback socios-feedback--success" role="status">{exito}</p>}
      {error && <p className="socios-feedback socios-feedback--error" role="alert">{error}</p>}

      {formOpen && <div className="socios-dialog-backdrop" onClick={closeForm}>
        <section aria-labelledby="socios-form-title" aria-modal="true" className="socios-dialog socios-form-dialog" onClick={(event) => event.stopPropagation()} onKeyDown={trapDialogFocus} role="dialog">
          <button ref={formCloseRef} aria-label="Cerrar formulario de socio" className="socios-dialog-close" disabled={guardando} onClick={closeForm} type="button"><X size={19} /></button>
          <p className="socios-dialog-eyebrow">{socioEditando ? 'PERFIL DEL SOCIO' : 'NUEVO REGISTRO'}</p>
          <h2 id="socios-form-title">{socioEditando ? 'Editar socio' : 'Nuevo socio'}</h2>
          {socioEditando && <p className="socios-dialog-subtitle">{socioEditando.nombres} {socioEditando.apellidos}</p>}
          {!socioEditando && <p className="socios-dialog-description">Registra los datos personales y de contacto del socio.</p>}
          <form className="socios-form" noValidate onSubmit={guardar}>
            <section className="socios-form-section" aria-labelledby="socios-personales-title">
              <h3 id="socios-personales-title">Información personal</h3>
              <div className="socios-form-grid">
                <label className="socios-field">
                  <span>Nombres <b aria-hidden="true">*</b></span>
                  <input aria-describedby={errorFormulario.includes('nombres') || errorFormulario.includes('apellidos') ? 'socios-name-error' : undefined} aria-invalid={errorFormulario.includes('nombres') || errorFormulario.includes('apellidos')} autoComplete="given-name" maxLength={100} name="nombres" onChange={(event) => actualizarCampo('nombres', event.target.value)} required value={values.nombres} />
                  {(errorFormulario.includes('nombres') || errorFormulario.includes('apellidos')) && <small className="socios-field-error" id="socios-name-error">{errorFormulario}</small>}
                </label>
                <label className="socios-field">
                  <span>Apellidos <b aria-hidden="true">*</b></span>
                  <input aria-invalid={errorFormulario.includes('nombres') || errorFormulario.includes('apellidos')} autoComplete="family-name" maxLength={100} name="apellidos" onChange={(event) => actualizarCampo('apellidos', event.target.value)} required value={values.apellidos} />
                </label>
                <fieldset className="socios-document-field"><legend>Documento de identidad</legend><label className="socios-field"><span>Tipo de documento</span><select aria-label="Tipo de documento" onChange={(event) => setValues((current) => ({ ...current, tipoDocumento: event.target.value as 'DNI' | 'CE', numeroDocumento: normalizeDocumentInput(event.target.value as 'DNI' | 'CE', current.numeroDocumento) }))} value={values.tipoDocumento}><option value="DNI">DNI</option><option value="CE">Carné de Extranjería (CE)</option></select></label><label className="socios-field"><span>Número de documento</span><input aria-label="Número de documento" aria-describedby="socios-document-help" aria-invalid={Boolean(values.numeroDocumento) && !isValidDocument(values.tipoDocumento, values.numeroDocumento)} autoComplete="off" inputMode="numeric" maxLength={values.tipoDocumento === 'DNI' ? 8 : 12} onChange={(event) => actualizarCampo('numeroDocumento', normalizeDocumentInput(values.tipoDocumento, event.target.value))} value={values.numeroDocumento} /><small id="socios-document-help">Opcional · {documentHelp(values.tipoDocumento)} · {values.numeroDocumento.length}/{values.tipoDocumento === 'DNI' ? 8 : 12}</small></label></fieldset>
                <label className="socios-field">
                  <span>Fecha de nacimiento</span>
                  <input max={TODAY} name="fechaNacimiento" onChange={(event) => actualizarCampo('fechaNacimiento', event.target.value)} type="date" value={values.fechaNacimiento} />
                </label>
              </div>
            </section>
            <section className="socios-form-section" aria-labelledby="socios-contact-title">
              <h3 id="socios-contact-title">Datos de contacto</h3>
              <div className="socios-form-grid">
                <label className="socios-field">
                  <span>Celular</span>
                  <input autoComplete="tel" maxLength={20} name="celular" onChange={(event) => actualizarCampo('celular', event.target.value)} type="tel" value={values.celular} />
                </label>
                <label className="socios-field">
                  <span>Correo electrónico</span>
                  <input aria-label="Correo electrónico" autoComplete="email" maxLength={150} name="correo" onChange={(event) => actualizarCampo('correo', event.target.value)} type="email" value={values.correo} />
                </label>
              </div>
            </section>
            <p className="socios-form-note">Los campos marcados con <b>*</b> son obligatorios.</p>
            {errorFormulario && <p className="socios-feedback socios-feedback--error" role="alert">{errorFormulario}</p>}
            <div className="socios-form-actions">
              <Button disabled={guardando} onClick={closeForm} type="button" variant="secondary">Cancelar</Button>
              <Button disabled={guardando || Boolean(socioEditando && (!hasChanges(values, socioEditando) || validate(values)))} type="submit">{guardando ? <><LoaderCircle className="socios-spinner" size={16} aria-hidden="true"/>Guardando…</> : socioEditando ? 'Guardar cambios' : 'Registrar socio'}</Button>
            </div>
          </form>
        </section>
      </div>}

      <Card className="socios-list-card">
        <div className="socios-toolbar">
          <label className="socios-search search-field">
            <Search aria-hidden="true" size={17} />
            <span className="socios-sr-only">Buscar por nombre, apellidos, documento o código</span>
            <input
              onChange={(event) => setBusqueda(event.target.value)}
              placeholder="Buscar por nombre, apellidos, documento o código"
              type="search"
              value={busqueda}
            />
          </label>
          <label className="socios-filter">
            <span>Estado del socio</span>
            <select aria-label="Filtrar por estado" onChange={(event) => setEstado(event.target.value as EstadoFiltro)} value={estado}>
              <option value="TODOS">Todos</option>
              <option value="ACTIVO">Activos</option>
              <option value="INACTIVO">Inactivos</option>
            </select>
          </label>
          <div className="socios-sort-control">
            <label className="socios-filter socios-sort-filter">
              <span>Ordenar por</span>
              <select aria-label="Ordenar socios por" onChange={(event) => setOrden(event.target.value as Orden)} value={orden}>
                <option value="nombres">Nombres</option><option value="apellidos">Apellidos</option>
                <option value="codigoSocio">Código</option><option value="fechaRegistro">Fecha de registro</option>
              </select>
            </label>
            <button aria-label={ordenDesc ? 'Orden descendente, cambiar a ascendente' : 'Orden ascendente, cambiar a descendente'} className="icon-button socios-sort-direction" onClick={() => setOrdenDesc((value) => !value)} type="button"><ArrowDownUp size={16} aria-hidden="true" /></button>
          </div>
          {puedeConsultarMembresias && <label className="socios-filter"><span>Historial de membresía</span><select aria-label="Filtrar por historial de membresía" disabled={Boolean(errorMembresias)} onChange={(event) => setVinculo(event.target.value as VinculoFiltro)} value={vinculo}><option value="TODOS">Todas las personas</option><option value="CON_MEMBRESIA">Con membresía registrada</option><option value="SIN_MEMBRESIA">Sin membresía registrada</option></select></label>}
          <Button className="socios-clear-button" disabled={!busqueda && estado === 'TODOS' && vinculo === 'TODOS' && orden === 'nombres' && !ordenDesc} onClick={limpiarFiltros} type="button" variant="secondary">Limpiar filtros</Button>
        </div>
        {errorMembresias && puedeConsultarMembresias && <p className="socios-feedback socios-feedback--error" role="alert">No se pudo cargar el historial de membresías para filtrar. {errorMembresias}</p>}

        <div aria-live="polite" className="socios-result-summary">
          <span><strong>{sociosOrdenados.length} {sociosOrdenados.length === 1 ? 'resultado' : 'resultados'}</strong><span className="socios-summary-range">{sociosOrdenados.length ? `Mostrando ${(pagina - 1) * tamanoPagina + 1}–${Math.min(pagina * tamanoPagina, sociosOrdenados.length)}` : 'No hay registros para mostrar'}</span></span>
          <label>Registros por página <select aria-label="Registros por página" onChange={(event) => setTamanoPagina(Number(event.target.value))} value={tamanoPagina}><option value={10}>10</option><option value={20}>20</option><option value={50}>50</option></select></label>
        </div>
        {puedeConsultarMembresias && <p className="socios-history-note">“Sin membresía registrada” no clasifica a la persona como visitante. El historial de pases diarios se mostrará cuando exista una fuente vinculada y verificada.</p>}

        {cargando ? (
          <p aria-live="polite" className="socios-list-message"><LoaderCircle className="socios-spinner" size={18} aria-hidden="true"/>Cargando socios…</p>
        ) : error ? null : sociosOrdenados.length === 0 ? (
          <div className="socios-empty"><span className="socios-empty-icon"><Search size={20}/></span><p>{socios.length || busqueda.trim() || estado !== 'TODOS' || vinculo !== 'TODOS' ? 'No encontramos socios con esos criterios.' : 'Aún no hay socios registrados.'}</p><span>{socios.length || busqueda.trim() || estado !== 'TODOS' || vinculo !== 'TODOS' ? 'Prueba otra búsqueda o limpia los filtros.' : 'Los socios registrados aparecerán en este directorio.'}</span></div>
        ) : (
          <div className="table-wrap socios-table-wrap">
            <table className="activity-table socios-table">
              <caption className="socios-sr-only">Directorio de socios</caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Nombres</th>
                  <th scope="col">Apellidos</th>
                  <th scope="col">Documento</th>
                  <th scope="col">Estado</th>
                  <th scope="col" className="socios-actions-heading">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {sociosPagina.map((socio) => (
                  <tr className={socioResaltadoId === socio.idSocio ? 'socios-row-selected' : undefined} key={socio.idSocio}>
                    <td data-label="Código"><strong className="socios-code">{socio.codigoSocio}</strong></td>
                    <td data-label="Nombres"><strong className="socios-name">{socio.nombres}</strong></td>
                    <td data-label="Apellidos">{socio.apellidos}</td>
                    <td data-label="Documento">{(socio.numeroDocumento ?? socio.dni) ? `${socio.tipoDocumento ?? 'DNI'} ·${'•'.repeat(Math.max(1, (socio.numeroDocumento ?? socio.dni ?? '').length - 2))}${(socio.numeroDocumento ?? socio.dni ?? '').slice(-2)}` : '—'}</td>
                    <td data-label="Estado"><span className={`socios-state socios-state--${socio.estado.toLowerCase()}`}><span aria-hidden="true" className="socios-state-dot"/>{socio.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}</span></td>
                    <td data-label="Acciones">
                      <div className="socios-row-actions">
                        <Button data-detail-socio={socio.idSocio} className="socios-row-action" aria-label={`Ver detalle de ${socio.codigoSocio}`} onClick={(event) => { detailTriggerRef.current = event.currentTarget; detailTriggerId.current = socio.idSocio; mostrarDetalle(socio) }} type="button" variant="secondary" icon={<Eye size={15} aria-hidden="true" />}>Detalle</Button>
                        <Button data-edit-socio={socio.idSocio} className="socios-row-action" aria-label={`Editar ${socio.codigoSocio}`} disabled={cargandoEdicion !== null || guardando} onClick={(event) => { formTriggerRef.current = event.currentTarget; formTriggerId.current = socio.idSocio; void comenzarEdicion(socio) }} type="button" variant="secondary" icon={<Pencil size={15} aria-hidden="true" />}>{cargandoEdicion === socio.idSocio ? 'Cargando…' : 'Editar'}</Button>
                        <Button
                          className={`socios-row-action ${socio.estado === 'ACTIVO' ? 'socios-row-action--danger' : 'socios-row-action--success'}`}
                          aria-label={`${socio.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'} ${socio.codigoSocio}`}
                          disabled={guardandoEstado}
                          onClick={() => { setSocioEstado(socio); setMotivo(''); setExito('') }}
                          type="button"
                          variant="ghost"
                          icon={socio.estado === 'ACTIVO' ? <UserRoundX size={15} aria-hidden="true" /> : <UserRoundCheck size={15} aria-hidden="true" />}
                        >{socio.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!cargando && !error && socios.length > 0 && <nav aria-label="Paginación de socios" className="socios-pagination"><button disabled={pagina <= 1} onClick={() => setPagina((current) => current - 1)} type="button">Anterior</button><span>Página {pagina} de {totalPaginas}</span><button disabled={pagina >= totalPaginas} onClick={() => setPagina((current) => current + 1)} type="button">Siguiente</button></nav>}
      </Card>

      {socioDetalle && <div className="socios-dialog-backdrop" onClick={() => setSocioDetalle(null)}><section aria-labelledby="socios-detail-title" aria-modal="true" className="socios-dialog socios-detail-dialog" onClick={(event) => event.stopPropagation()} onKeyDown={trapDialogFocus} role="dialog"><button ref={detailCloseRef} aria-label="Cerrar detalle" className="socios-dialog-close" onClick={() => setSocioDetalle(null)} type="button"><X size={19} /></button><p className="socios-dialog-eyebrow">INFORMACIÓN DEL SOCIO</p><div className="socios-detail-heading"><div><h2 id="socios-detail-title">{socioDetalle.nombres} {socioDetalle.apellidos}</h2><p>{socioDetalle.codigoSocio}</p></div><span className={`socios-state socios-state--${socioDetalle.estado.toLowerCase()}`}><span aria-hidden="true" className="socios-state-dot"/>{socioDetalle.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}</span></div><section className="socios-detail-section"><h3>Datos personales</h3><dl className="socios-detail-grid"><div><dt>Documento de identidad</dt><dd>{(socioDetalle.numeroDocumento ?? socioDetalle.dni) ? `${socioDetalle.tipoDocumento ?? 'DNI'} · ${socioDetalle.numeroDocumento ?? socioDetalle.dni}` : 'No registrado'}</dd></div><div><dt>Fecha de nacimiento</dt><dd>{formatDate(socioDetalle.fechaNacimiento, false, 'No registrada')}</dd></div></dl></section><section className="socios-detail-section"><h3>Datos de contacto</h3><dl className="socios-detail-grid"><div><dt>Celular</dt><dd>{socioDetalle.celular || 'No registrado'}</dd></div><div><dt>Correo electrónico</dt><dd>{socioDetalle.correo || 'No registrado'}</dd></div></dl></section><section className="socios-detail-section"><h3>Registro</h3><dl className="socios-detail-grid"><div><dt>Fecha de registro</dt><dd>{formatDate(socioDetalle.fechaRegistro, true)}</dd></div></dl></section>{auth.user?.roles.some((role) => role === 'ADMINISTRADOR' || role === 'RECEPCIONISTA') && <><HistorialMembresias auth={auth} idSocio={socioDetalle.idSocio} /><HistorialPasesDiarios auth={auth} idSocio={socioDetalle.idSocio} /></>}</section></div>}

      {socioEstado && (
        <div className="socios-dialog-backdrop">
          <section aria-labelledby="socios-state-title" aria-modal="true" className="socios-dialog" role="dialog">
            <div className="socios-dialog-heading">
              <h2 id="socios-state-title">Confirmar {socioEstado.estado === 'ACTIVO' ? 'desactivación' : 'activación'}</h2>
              <button aria-label="Cerrar confirmación" className="icon-button" onClick={() => setSocioEstado(null)} type="button"><X size={18} /></button>
            </div>
            <p>{socioEstado.codigoSocio} · {socioEstado.nombres} {socioEstado.apellidos}</p>
            <form onSubmit={(event) => void confirmarCambioEstado(event)}>
              <label className="socios-field">
                <span>Motivo <b aria-hidden="true">*</b></span>
                <textarea autoFocus aria-label="Motivo del cambio de estado" onChange={(event) => setMotivo(event.target.value)} required rows={3} value={motivo} />
              </label>
              <div className="socios-form-actions">
                <Button onClick={() => setSocioEstado(null)} type="button" variant="secondary">Cancelar</Button>
                <Button disabled={guardandoEstado || !motivo.trim()} type="submit">{guardandoEstado ? 'Procesando…' : 'Confirmar operación'}</Button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}

function HistorialMembresias({ auth, idSocio }: { auth: ReturnType<typeof useAuth>; idSocio: number }) {
  const [items, setItems] = useState<Membresia[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let current = true
    setLoading(true); setError('')
    void listarMembresiasPorSocio(auth, idSocio).then((result) => {
      if (current) setItems(result)
    }).catch((reason: unknown) => {
      if (current) setError(messageFor(reason))
    }).finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [auth, idSocio, attempt])

  return <section className="socios-detail-section" aria-label="Historial de membresías">
    <h3>Historial de membresías</h3>
    {loading && <p role="status">Consultando registros…</p>}
    {error && <p className="socios-feedback socios-feedback--error" role="alert">{error} <Button onClick={() => setAttempt((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></p>}
    {!loading && !error && items.length === 0 && <p>No hay membresías registradas para esta persona.</p>}
    {!loading && !error && items.length > 0 && <ul className="socios-membership-history">{items.map((item) => <li key={item.idMembresia}><strong>{item.plan?.nombre ?? 'Plan no disponible'}</strong><span>{item.estado} · {formatDate(item.fechaInicio, false)} – {formatDate(item.fechaVencimiento, false)}</span><span>Precio acordado: {new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(item.precioAcordado)}</span></li>)}</ul>}
  </section>
}

function HistorialPasesDiarios({ auth, idSocio }: { auth: ReturnType<typeof useAuth>; idSocio: number }) {
  const [items, setItems] = useState<PaseDiarioHistorial[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let current = true
    setLoading(true); setError('')
    void listarPasesDiariosPorSocio(auth, idSocio).then((result) => {
      if (current) setItems(result)
    }).catch((reason: unknown) => {
      if (current) setError(messageFor(reason))
    }).finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [auth, idSocio, attempt])

  return <section className="socios-detail-section" aria-label="Historial de pases diarios">
    <h3>Historial de pases diarios</h3>
    {loading && <p role="status">Consultando registros…</p>}
    {error && <p className="socios-feedback socios-feedback--error" role="alert">{error} <Button onClick={() => setAttempt((value) => value + 1)} type="button" variant="secondary">Reintentar</Button></p>}
    {!loading && !error && items.length === 0 && <p>No hay ventas ni accesos de pases diarios vinculados a esta ficha.</p>}
    {!loading && !error && items.length > 0 && <ul className="socios-membership-history">{items.map((item) => <li key={item.idIngreso}>
      <strong>{item.cobradoEnEsteAcceso ? 'Venta de pase diario' : `Reingreso · pase #${item.idPaseDiario ?? 'sin origen'}`}</strong>
      <span>{formatDate(item.fechaHora, true)}</span>
      {item.montoPase !== null && <span>Pago #{item.idPago} · {new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(item.montoPase)} · {item.metodoPago ?? 'Método no disponible'}</span>}
    </li>)}</ul>}
    <p className="socios-history-note">Los ingresos históricos de visitantes sin vínculo con esta ficha no se atribuyen automáticamente.</p>
  </section>
}
