import { useEffect, useRef, useState } from 'react'
import { Search, UserRoundCheck } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  evaluarIngresoMembresia,
  listarSociosHabilitados,
  listarHistorialIngresoSocio,
  registrarIngresoMembresia,
  type EstadoIngresoMembresia,
  type PaginaHistorialIngresoSocio,
} from '../api/ingresos'
import type { AuthContextValue } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { uncertainWriteResult } from './uncertainWriteLocks'
import { normalizeDocumentInput } from '../utils/documentIdentity'

const HISTORY_SIZE = 10
const DIRECTORY_SIZE = 20
type SortOption = 'nombres,asc' | 'nombres,desc' | 'apellidos,asc' | 'apellidos,desc'

function errorText(error: unknown) {
  if (error instanceof ApiError) return error.message
  return error instanceof Error ? error.message : 'No se pudo completar la operación.'
}

function crearUuid() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  if (!globalThis.crypto?.getRandomValues) throw new Error('No se pudo preparar la operación de forma segura.')
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function dateLabel(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeZone: 'UTC' })
    .format(new Date(Date.UTC(year, month - 1, day, 12)))
}

function dateTimeLabel(value: string) {
  const parsed = new Date(`${value}-05:00`)
  return Number.isNaN(parsed.getTime()) ? 'Fecha no disponible' : new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(parsed)
}

function keyStorageName(idSocio: number) { return `sistemagym:ingreso-membresia:${idSocio}` }

export function IngresoMembresiaPanel({ auth }: { auth: AuthContextValue }) {
  const [nameFilter, setNameFilter] = useState('')
  const [documentType, setDocumentType] = useState<'DNI' | 'CE' | ''>('')
  const [documentNumber, setDocumentNumber] = useState('')
  const [results, setResults] = useState<EstadoIngresoMembresia[]>([])
  const [searching, setSearching] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [sort, setSort] = useState<SortOption>('nombres,asc')
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [selection, setSelection] = useState<EstadoIngresoMembresia | null>(null)
  const [history, setHistory] = useState<PaginaHistorialIngresoSocio | null>(null)
  const [loadingSelection, setLoadingSelection] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [historyPage, setHistoryPage] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const searchLock = useRef(0)
  const writeLock = useRef(false)
  const canRegister = auth.user?.roles.includes('RECEPCIONISTA') ?? false
  const hasFilters = Boolean(nameFilter.trim() || documentType || documentNumber)

  useEffect(() => {
    const request = ++searchLock.current
    let active = true
    setSearching(true); setError('')
    const timer = window.setTimeout(() => {
      void listarSociosHabilitados(auth, {
        nombre: nameFilter.trim(), tipoDocumento: documentType, numeroDocumento: documentNumber,
      }, page, DIRECTORY_SIZE, sort)
        .then((response) => {
          if (active && request === searchLock.current) {
            setResults(response.contenido)
            setTotalPages(response.totalPaginas)
            setTotalElements(response.totalElementos)
          }
        })
        .catch((reason: unknown) => {
          if (active && request === searchLock.current) setError(errorText(reason))
        })
        .finally(() => {
          if (active && request === searchLock.current) setSearching(false)
        })
    }, hasFilters ? 250 : 0)
    return () => { active = false; window.clearTimeout(timer) }
  }, [auth, nameFilter, documentType, documentNumber, page, sort, refreshVersion, hasFilters])

  function resetFilterState() {
    setSelection(null); setHistory(null); setHistoryPage(0); setNotice(''); setError('')
  }

  function clearFilters() {
    setNameFilter(''); setDocumentType(''); setDocumentNumber(''); setPage(0)
    resetFilterState()
  }

  async function selectMember(member: EstadoIngresoMembresia) {
    setLoadingSelection(true); setError(''); setNotice(''); setHistory(null); setSelection(null); setHistoryPage(0); setUncertain(false)
    try {
    const [evaluation, records] = await Promise.all([
        evaluarIngresoMembresia(auth, member.idSocio),
        listarHistorialIngresoSocio(auth, member.idSocio, 0, HISTORY_SIZE),
      ])
      setSelection(evaluation); setHistory(records)
    } catch (reason) { setError(errorText(reason)) }
    finally { setLoadingSelection(false) }
  }

  async function loadHistory(page: number) {
    if (!selection) return
    setLoadingHistory(true); setError('')
    try {
      const records = await listarHistorialIngresoSocio(auth, selection.idSocio, page, HISTORY_SIZE)
      setHistory(records); setHistoryPage(page)
    } catch (reason) { setError(errorText(reason)) }
    finally { setLoadingHistory(false) }
  }

  async function registerEntry() {
    if (!selection?.autorizado || submitting || writeLock.current) return
    writeLock.current = true; setSubmitting(true); setError(''); setNotice('')
    const sessionVersion = auth.getSessionVersion()
    let keyName = ''
    let attempted = false
    try {
      keyName = keyStorageName(selection.idSocio)
      let key = window.localStorage.getItem(keyName)
      if (!key || !/^[0-9a-f-]{36}$/i.test(key)) {
        key = crearUuid()
        window.localStorage.setItem(keyName, key)
      }
      attempted = true
      const result = await registrarIngresoMembresia(auth, selection.idSocio, key)
      if (auth.getSessionVersion() !== sessionVersion) return
      if (result.tipoIngreso !== 'SOCIO' || result.idSocio !== selection.idSocio
          || result.paseDiarioCobrado || result.idPago !== null) {
        throw new Error('El servidor no confirmó un ingreso de membresía sin cobro. Revisa el historial antes de continuar.')
      }
      window.localStorage.removeItem(keyName)
      setUncertain(false)
      setNotice(result.resultado === 'IDEMPOTENTE'
        ? 'La entrada ya estaba registrada; se recuperó la misma operación sin crear otro acceso.'
        : `Entrada registrada a las ${dateTimeLabel(result.fechaHora)}. Sin cobro de pase diario.`)
      const refreshedHistory = await listarHistorialIngresoSocio(auth, selection.idSocio, 0, HISTORY_SIZE)
      if (auth.getSessionVersion() === sessionVersion) { setHistory(refreshedHistory); setHistoryPage(0) }
      setRefreshVersion((version) => version + 1)
    } catch (reason) {
      const mayHaveApplied = attempted && uncertainWriteResult(reason)
      if (keyName && attempted && !mayHaveApplied) {
        try { window.localStorage.removeItem(keyName) } catch { /* conserva el error visible */ }
      }
      if (auth.getSessionVersion() === sessionVersion) {
        setUncertain(mayHaveApplied)
        setError(mayHaveApplied
          ? `El resultado no se pudo confirmar. Reintenta la misma operación para evitar duplicados. ${errorText(reason)}`
          : errorText(reason))
      }
    } finally { writeLock.current = false; setSubmitting(false) }
  }

  return <Card className="asistencia-card ingreso-membresia-card" title="Ingreso con membresía">
    <p className="asistencia-description">Socios con membresía vigente y acceso autorizado. La vigencia se valida nuevamente al registrar.</p>
    <div className="ingreso-miembro-filters">
      <label className="ingreso-miembro-filter" htmlFor="buscar-nombre-ingreso">
        <span>Nombre o apellido</span>
        <span className="ingreso-miembro-search__control"><Search aria-hidden="true" size={18} />
          <input autoComplete="off" id="buscar-nombre-ingreso" onChange={(event) => { setNameFilter(event.target.value); setPage(0); resetFilterState() }} placeholder="Buscar por nombre o apellido" type="search" value={nameFilter} />
        </span>
      </label>
      <label className="ingreso-miembro-filter" htmlFor="tipo-documento-ingreso"><span>Tipo de documento</span>
        <select id="tipo-documento-ingreso" onChange={(event) => {
          const nextType = event.target.value as 'DNI' | 'CE' | ''
          setDocumentType(nextType)
          setDocumentNumber((value) => normalizeDocumentInput(nextType, value))
          setPage(0); resetFilterState()
        }} value={documentType}>
          <option value="">Todos</option><option value="DNI">DNI</option><option value="CE">CE</option>
        </select>
      </label>
      <label className="ingreso-miembro-filter" htmlFor="numero-documento-ingreso"><span>Número de documento</span>
        <input autoComplete="off" id="numero-documento-ingreso" inputMode="numeric" maxLength={documentType === 'DNI' ? 8 : 12} onChange={(event) => {
          setDocumentNumber(normalizeDocumentInput(documentType, event.target.value))
          setPage(0); resetFilterState()
        }} pattern="[0-9]*" placeholder={documentType === 'DNI' ? 'Hasta 8 dígitos' : 'Hasta 12 dígitos'} type="text" value={documentNumber} />
      </label>
      <label className="ingreso-miembro-filter" htmlFor="orden-ingreso"><span>Ordenar por</span><select id="orden-ingreso" onChange={(event) => { setSort(event.target.value as SortOption); setPage(0) }} value={sort}>
        <option value="nombres,asc">Nombre A–Z</option><option value="nombres,desc">Nombre Z–A</option>
        <option value="apellidos,asc">Apellido A–Z</option><option value="apellidos,desc">Apellido Z–A</option>
      </select></label>
    </div>
    {!searching && !error && <header className="ingreso-miembro-directory__heading">
      <div><h3>Socios habilitados</h3><span>{totalElements} {totalElements === 1 ? 'socio' : 'socios'}</span></div>
      {hasFilters && <Button className="ingreso-miembro-clear" onClick={clearFilters} type="button" variant="ghost">Limpiar filtros</Button>}
    </header>}
    {searching && <p className="asistencia-message" role="status">Buscando socios…</p>}
    {error && <div className="asistencia-feedback asistencia-feedback--error" role="alert"><span>{error}</span><Button disabled={searching} onClick={() => setRefreshVersion((version) => version + 1)} type="button" variant="secondary">Reintentar</Button></div>}
    {notice && <p className="asistencia-feedback asistencia-feedback--success" role="status">{notice}</p>}
    {!searching && !error && !results.length && <p className="ingreso-miembro-empty" role="status">{hasFilters ? 'No hay socios habilitados que coincidan con los filtros.' : 'No hay socios con membresía vigente y acceso autorizado.'}</p>}
    {!searching && results.length > 0 && <div className="ingreso-miembro-directory">
      <div className="ingreso-miembro-table-wrap"><table className="ingreso-miembro-table"><caption className="asistencia-sr-only">Socios con acceso autorizado por membresía</caption><thead><tr><th scope="col">Socio</th><th scope="col">Documento</th><th scope="col">Plan</th><th scope="col">Vence</th><th scope="col">Estado</th><th scope="col">Acción</th></tr></thead>
        <tbody>{results.map((member) => <tr key={member.idSocio}><td data-label="Socio"><strong>{member.nombres} {member.apellidos}</strong></td><td data-label="Documento">{member.tipoDocumento ?? '—'} · {member.numeroDocumento ?? '—'}</td><td data-label="Plan">{member.membresia?.plan ?? '—'}</td><td data-label="Vence">{member.membresia ? dateLabel(member.membresia.fechaVencimiento) : '—'}</td><td data-label="Estado"><span className="ingreso-miembro-state ingreso-miembro-state--activo">Vigente</span></td><td data-label="Acción"><Button disabled={!canRegister || loadingSelection} onClick={() => void selectMember(member)} type="button" variant="secondary">{canRegister ? 'Registrar entrada' : 'Consultar'}</Button></td></tr>)}</tbody>
      </table></div>
      <nav aria-label="Páginas de socios habilitados" className="ingreso-miembro-pagination"><span>{totalElements} socios · Página {page + 1} de {Math.max(totalPages, 1)}</span><Button disabled={page === 0 || searching} onClick={() => setPage((current) => current - 1)} type="button" variant="secondary">Anterior</Button><Button disabled={page + 1 >= totalPages || searching} onClick={() => setPage((current) => current + 1)} type="button" variant="secondary">Siguiente</Button></nav>
    </div>}
    {loadingSelection && <p className="asistencia-message" role="status">Consultando membresía e historial…</p>}
    {selection && <section aria-label="Ficha y autorización de ingreso" className="ingreso-miembro-profile">
      <header className="ingreso-miembro-profile__header"><div><span className="ingreso-miembro-eyebrow">SOCIO SELECCIONADO</span><h3>{selection.nombres} {selection.apellidos}</h3><p>{selection.tipoDocumento ?? 'Documento'} {selection.numeroDocumento ?? 'No disponible'} · {selection.codigoSocio}</p></div>
        <Button disabled={submitting} onClick={() => { setSelection(null); setHistory(null); setError(''); setNotice(''); setUncertain(false) }} type="button" variant="secondary">Cambiar socio</Button>
      </header>
      <dl className="ingreso-miembro-details"><div><dt>Membresía</dt><dd>{selection.membresia?.plan ?? 'Sin membresía registrada'}</dd></div><div><dt>Estado</dt><dd>{selection.membresia?.estado ?? 'No disponible'}</dd></div><div><dt>Vigencia</dt><dd>{selection.membresia ? `${dateLabel(selection.membresia.fechaInicio)} – ${dateLabel(selection.membresia.fechaVencimiento)}` : 'No disponible'}</dd></div></dl>
      <div className={`ingreso-miembro-decision ${selection.autorizado ? 'ingreso-miembro-decision--allowed' : 'ingreso-miembro-decision--denied'}`} role={selection.autorizado ? 'status' : 'alert'}>
        <span aria-hidden="true">{selection.autorizado ? <UserRoundCheck size={18} /> : null}</span>
        <p>{selection.motivo}</p>
         {selection.autorizado && canRegister && <Button disabled={submitting} onClick={() => void registerEntry()} type="button">{submitting ? 'Registrando…' : uncertain ? 'Reintentar misma entrada' : 'Confirmar entrada'}</Button>}
      </div>
      {uncertain && <p className="ingreso-miembro-uncertain">El acceso pudo registrarse. No cambies de socio; reintenta esta misma operación para recuperar el resultado sin duplicarlo.</p>}
      <section aria-label="Historial de entradas del socio" className="ingreso-miembro-history">
        <header><h4>Historial de entradas</h4><span>{history?.totalElementos ?? 0} registros</span></header>
        {loadingHistory ? <p role="status">Actualizando historial…</p> : !history?.contenido.length ? <p className="ingreso-miembro-history__empty">Este socio aún no tiene entradas registradas.</p> : <>
          <ul>{history.contenido.map((entry) => <li key={entry.idIngreso}><time>{dateTimeLabel(entry.fechaHora)}</time><span>{entry.modalidad === 'SOCIO' ? 'Con membresía' : 'Pase diario'}</span></li>)}</ul>
          {history.totalPaginas > 1 && <nav aria-label="Páginas del historial" className="ingreso-miembro-pagination"><Button disabled={historyPage === 0 || loadingHistory} onClick={() => void loadHistory(historyPage - 1)} type="button" variant="secondary">Anterior</Button><span>Página {historyPage + 1} de {history.totalPaginas}</span><Button disabled={historyPage + 1 >= history.totalPaginas || loadingHistory} onClick={() => void loadHistory(historyPage + 1)} type="button" variant="secondary">Siguiente</Button></nav>}
        </>}
      </section>
    </section>}
  </Card>
}
