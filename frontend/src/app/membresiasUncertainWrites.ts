import type { ApiError, AuthenticatedRequestContext } from '../api/http'

export type MembresiaWriteMarker = {
  key: string
  operation: 'nueva' | 'pago'
  idMembresia?: number
  body: Record<string, unknown>
  state: 'uncertain' | 'pending'
}

/** Old locks had no UUID or request body; keep them blocked, never guess a replacement key. */
export type LegacyMembresiaWriteMarker = { operation: 'nueva' } | { operation: 'pago'; idMembresia: number }
export type AnyMembresiaWriteMarker = MembresiaWriteMarker | LegacyMembresiaWriteMarker

const STORAGE_PREFIX = 'sistemagym:membresias:resultado-incierto:'
const memoryLocks = new Map<string, AnyMembresiaWriteMarker[]>()

async function accountStorageKey(account: string): Promise<string> {
  const normalized = account.trim().toLocaleLowerCase('es')
  if (!normalized || !globalThis.crypto?.subtle) throw new Error('No se pudo aislar el bloqueo por cuenta.')
  const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(normalized))
  const fingerprint = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${STORAGE_PREFIX}${fingerprint}`
}

function same(a: AnyMembresiaWriteMarker, b: AnyMembresiaWriteMarker) {
  if ('key' in a && 'key' in b) return a.key === b.key
  return a.operation === b.operation && (a.operation === 'nueva' || ('idMembresia' in b && b.operation === 'pago' && a.idMembresia === b.idMembresia))
}

function parse(raw: string | null): AnyMembresiaWriteMarker[] {
  if (raw === null) return []
  const value: unknown = JSON.parse(raw)
  if (!Array.isArray(value)) throw new Error('No se pudo leer de forma segura el bloqueo persistente.')
  for (const item of value) {
    if (!item || typeof item !== 'object' || !('operation' in item)
      || (item.operation !== 'nueva' && item.operation !== 'pago')) throw new Error('Bloqueo persistente inválido.')
    if ('key' in item) {
      if (typeof item.key !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(item.key)
        || !('body' in item) || typeof item.body !== 'object' || item.body === null
        || !('state' in item) || (item.state !== 'uncertain' && item.state !== 'pending')) throw new Error('Intento idempotente persistente inválido.')
      continue
    }
    if (item.operation === 'pago' && (!('idMembresia' in item) || !Number.isSafeInteger(item.idMembresia))) throw new Error('Bloqueo heredado inválido.')
  }
  return value as AnyMembresiaWriteMarker[]
}

async function readStore(account: string): Promise<AnyMembresiaWriteMarker[]> {
  const key = await accountStorageKey(account)
  let raw: string | null
  try {
    raw = window.localStorage.getItem(key)
  } catch {
    return memoryLocks.get(key) ?? []
  }
  const found = parse(raw)
  const memory = memoryLocks.get(key) ?? []
  const merged = [...found]
  for (const marker of memory) if (!merged.some((item) => same(item, marker))) merged.push(marker)
  memoryLocks.set(key, merged)
  return merged
}

export async function readMembresiaWriteMarkers(account: string): Promise<AnyMembresiaWriteMarker[]> {
  return readStore(account)
}

export async function addMembresiaWriteMarker(account: string, marker: MembresiaWriteMarker): Promise<void> {
  const key = await accountStorageKey(account)
  const records = await readStore(account)
  const next = records.some((item) => same(item, marker)) ? records.map((item) => same(item, marker) ? marker : item) : [...records, marker]
  memoryLocks.set(key, next)
  try { window.localStorage.setItem(key, JSON.stringify(next)) } catch { /* Session-memory fallback remains a blocking lock. */ }
}

export async function updateMembresiaWriteMarker(account: string, marker: MembresiaWriteMarker): Promise<void> {
  await addMembresiaWriteMarker(account, marker)
}

export async function removeMembresiaWriteMarker(account: string, marker: AnyMembresiaWriteMarker): Promise<void> {
  const key = await accountStorageKey(account)
  const records = await readStore(account)
  const next = records.filter((item) => !same(item, marker))
  memoryLocks.set(key, next)
  try {
    if (next.length) window.localStorage.setItem(key, JSON.stringify(next))
    else window.localStorage.removeItem(key)
  } catch { /* Memory state is still updated for this session. */ }
}

export function markerBlocks(markers: AnyMembresiaWriteMarker[], operation: 'nueva' | 'pago', idMembresia?: number): boolean {
  return markers.some((item) => item.operation === operation
    && (operation === 'nueva' || item.operation === 'pago' && (item.idMembresia === idMembresia || item.idMembresia === -1)))
}

export function errorIsUncertain(error: unknown): boolean {
  if (!(error instanceof Error) || !('code' in error)) return true
  const apiError = error as ApiError
  if (apiError.code === 'network' || apiError.code === 'invalid-response' || apiError.code === 'server'
      || ('status' in error && apiError.status === 408)) return true
  return !['validation', 'conflict', 'unauthenticated', 'forbidden', 'not-found'].includes(String(apiError.code))
}

export function sessionIsCurrent(auth: AuthenticatedRequestContext, version: number): boolean {
  return auth.getSessionVersion() === version && auth.getAccessToken() !== null
}
