import type { ApiError } from '../api/http'

const PREFIX = 'sistemagym:uncertain-write:'

async function keyFor(account: string, scope: string) {
  if (!account.trim() || !globalThis.crypto?.subtle) throw new Error('No se pudo preparar el bloqueo persistente.')
  const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(account.trim().toLocaleLowerCase('es')))
  const accountHash = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${PREFIX}${scope}:${accountHash}`
}

function parse(raw: string | null): string[] {
  if (raw === null) return []
  const value: unknown = JSON.parse(raw)
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) {
    throw new Error('No se pudo leer de forma segura el bloqueo persistente.')
  }
  return [...new Set(value)]
}

export async function readUncertainWriteLocks(account: string, scope: string): Promise<string[]> {
  return parse(window.localStorage.getItem(await keyFor(account, scope)))
}

export async function addUncertainWriteLock(account: string, scope: string, lock: string): Promise<void> {
  const key = await keyFor(account, scope)
  const locks = parse(window.localStorage.getItem(key))
  if (!locks.includes(lock)) window.localStorage.setItem(key, JSON.stringify([...locks, lock]))
}

export async function removeUncertainWriteLock(account: string, scope: string, lock: string): Promise<void> {
  const key = await keyFor(account, scope)
  const locks = parse(window.localStorage.getItem(key)).filter((item) => item !== lock)
  if (locks.length) window.localStorage.setItem(key, JSON.stringify(locks))
  else window.localStorage.removeItem(key)
}

const DEFINITE_REJECTIONS = ['validation', 'conflict', 'unauthenticated', 'forbidden', 'not-found']

export function uncertainWriteResult(error: unknown): boolean {
  if (!(error instanceof Error) || !('code' in error)) return true
  const apiError = error as ApiError
  if (apiError.code === 'network' || apiError.code === 'invalid-response' || apiError.code === 'server'
      || apiError.status === 408) return true
  return !DEFINITE_REJECTIONS.includes(apiError.code)
}

export function isSessionCurrent(auth: { getSessionVersion: () => number; getAccessToken: () => string | null }, version: number) {
  return auth.getSessionVersion() === version && auth.getAccessToken() !== null
}
