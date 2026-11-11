import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { login as requestLogin, refreshLogin, revokeLogin, type LoginRequest, type LoginResponse } from '../api/auth'

export type AuthRole =
  | 'ADMINISTRADOR'
  | 'RECEPCIONISTA'
  | 'ENTRENADOR'
  | 'NUTRICIONISTA'

export type AuthUser = {
  correo: string
  roles: AuthRole[]
  issuedAt: number
  nombres?: string
  apellidos?: string
  tipoDocumento?: 'DNI' | 'CE' | null
  numeroDocumento?: string | null
}

export type PersonalProfile = Pick<AuthUser,
  'nombres' | 'apellidos' | 'tipoDocumento' | 'numeroDocumento'>

type AuthSession = {
  token: string
  tokenType: 'Bearer'
  expiresAt: number
  user: AuthUser
}

export type AuthContextValue = {
  isAuthenticated: boolean
  isInitializing: boolean
  user: AuthUser | null
  tokenType: 'Bearer' | null
  expiresAt: number | null
  login: (credentials: LoginRequest) => Promise<boolean>
  logout: () => void
  updatePersonalProfile: (profile: PersonalProfile) => void
  getAccessToken: () => string | null
  getSessionVersion: () => number
  logoutIfCurrentSession: (expectedVersion: number) => boolean
  refreshAccessToken: () => Promise<string | null>
}

export class InvalidSessionError extends Error {
  constructor() {
    super('La respuesta de autenticación no contiene una sesión válida.')
    this.name = 'InvalidSessionError'
  }
}

const ROLE_NORMALIZATION: Record<string, AuthRole> = {
  ROLE_ADMINISTRADOR: 'ADMINISTRADOR',
  ROLE_RECEPCIONISTA: 'RECEPCIONISTA',
  ROLE_ENTRENADOR: 'ENTRENADOR',
  ROLE_NUTRICIONISTA: 'NUTRICIONISTA',
}

const MIN_TOKEN_LIFETIME_MS = 60_000
const MAX_TOKEN_LIFETIME_MS = 86_400_000
const JWT_NUMERIC_DATE_TOLERANCE_MS = 2_000
const MAX_FUTURE_ISSUED_AT_SKEW_MS = 60_000
const INITIAL_RESTORE_TIMEOUT_MS = 8_000

type JwtClaims = {
  sub: string
  roles: AuthRole[]
  iat: number
  exp: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function decodeBase64UrlJson(segment: string): unknown {
  if (!segment || !/^[A-Za-z0-9_-]+$/.test(segment) || segment.length % 4 === 1) {
    throw new InvalidSessionError()
  }

  try {
    const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - base64.length % 4) % 4), '=')
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
    const json = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
    return JSON.parse(json) as unknown
  } catch {
    throw new InvalidSessionError()
  }
}

function normalizeRoles(value: unknown): AuthRole[] {
  if (!Array.isArray(value) || value.length === 0) throw new InvalidSessionError()

  const roles = value.map((role): AuthRole => {
    if (typeof role !== 'string') throw new InvalidSessionError()
    const normalized = role.trim().toUpperCase()
    const authority = normalized.startsWith('ROLE_') ? normalized : `ROLE_${normalized}`
    const knownRole = ROLE_NORMALIZATION[authority]
    if (!knownRole) throw new InvalidSessionError()
    return knownRole
  })

  return [...new Set(roles)]
}

function decodeClaims(token: string, now: number): JwtClaims {
  const parts = token.split('.')
  if (parts.length !== 3
      || parts.some((part) => !part || !/^[A-Za-z0-9_-]+$/.test(part) || part.length % 4 === 1)) {
    throw new InvalidSessionError()
  }

  const header = decodeBase64UrlJson(parts[0])
  const payload = decodeBase64UrlJson(parts[1])
  if (!isRecord(header) || typeof header.alg !== 'string' || header.alg.toLowerCase() === 'none') {
    throw new InvalidSessionError()
  }
  if (!isRecord(payload)) throw new InvalidSessionError()

  const { sub, iat, exp } = payload
  if (typeof sub !== 'string' || !sub.trim()) throw new InvalidSessionError()
  if (!Number.isSafeInteger(iat) || !Number.isSafeInteger(exp)) {
    throw new InvalidSessionError()
  }

  const issuedAt = iat as number
  const expiresAt = exp as number
  if (issuedAt <= 0 || expiresAt <= issuedAt) throw new InvalidSessionError()
  if (issuedAt * 1000 > now + MAX_FUTURE_ISSUED_AT_SKEW_MS) {
    throw new InvalidSessionError()
  }

  return {
    sub: sub.trim(),
    roles: normalizeRoles(payload.roles),
    iat: issuedAt,
    exp: expiresAt,
  }
}

function createSession(response: LoginResponse, now: number): AuthSession {
  const claims = decodeClaims(response.token, now)
  const durationMs = response.expiraEnMs

  if (!Number.isSafeInteger(durationMs)
      || durationMs < MIN_TOKEN_LIFETIME_MS
      || durationMs > MAX_TOKEN_LIFETIME_MS) {
    throw new InvalidSessionError()
  }

  const jwtExpiresAt = claims.exp * 1000
  const jwtLifetimeMs = (claims.exp - claims.iat) * 1000
  if (Math.abs(jwtLifetimeMs - durationMs) > JWT_NUMERIC_DATE_TOLERANCE_MS) {
    throw new InvalidSessionError()
  }
  if (jwtExpiresAt <= now) throw new InvalidSessionError()

  const responseExpiresAt = now + durationMs
  const expiresAt = Math.min(jwtExpiresAt, responseExpiresAt)
  if (!Number.isSafeInteger(expiresAt) || expiresAt <= now) {
    throw new InvalidSessionError()
  }

  return {
    token: response.token,
    tokenType: response.tipo,
    expiresAt,
    user: {
      correo: claims.sub,
      roles: claims.roles,
      issuedAt: claims.iat,
    },
  }
}

const AuthContext = createContext<AuthContextValue | null>(null)

type AuthProviderProps = { children: ReactNode }

export function AuthProvider({ children }: AuthProviderProps) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [isInitializing, setIsInitializing] = useState(true)
  const sessionRef = useRef<AuthSession | null>(null)
  const loginAttemptRef = useRef(0)
  const sessionVersionRef = useRef(0)
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null)
  const initialRestoreStartedRef = useRef(false)
  const initialRestoreTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const logout = useCallback(() => {
    revokeLogin()
    loginAttemptRef.current += 1
    sessionVersionRef.current += 1
    sessionRef.current = null
    setSession(null)
    setIsInitializing(false)
  }, [])

  const getSessionVersion = useCallback(() => sessionVersionRef.current, [])

  const logoutIfCurrentSession = useCallback((expectedVersion: number) => {
    if (sessionVersionRef.current !== expectedVersion || !sessionRef.current) return false
    logout()
    return true
  }, [logout])

  const login = useCallback(async (credentials: LoginRequest): Promise<boolean> => {
    const attempt = ++loginAttemptRef.current

    try {
      const response = await requestLogin(credentials)
      if (attempt !== loginAttemptRef.current) return false

      const nextSession = createSession(response, Date.now())
      if (attempt !== loginAttemptRef.current) return false

      sessionVersionRef.current += 1
      sessionRef.current = nextSession
      setSession(nextSession)
      setIsInitializing(false)
      return true
    } catch (error) {
      if (attempt !== loginAttemptRef.current) return false
      throw error
    }
  }, [])

  const getAccessToken = useCallback((): string | null => {
    const current = sessionRef.current
    if (!current || current.expiresAt <= Date.now()) return null
    return current.token
  }, [])

  const refreshAccessToken = useCallback((): Promise<string | null> => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current
    const expectedVersion = sessionVersionRef.current
    const current = sessionRef.current
    const pending = refreshLogin().then((response) => {
      if (expectedVersion !== sessionVersionRef.current) return null
      const refreshed = createSession(response, Date.now())
      const next: AuthSession = { ...refreshed, user: current?.user ?? refreshed.user }
      sessionRef.current = next
      setSession(next)
      return next.token
    }).catch(() => null).finally(() => {
      if (refreshPromiseRef.current === pending) refreshPromiseRef.current = null
    })
    refreshPromiseRef.current = pending
    return pending
  }, [])

  const updatePersonalProfile = useCallback((profile: PersonalProfile) => {
    const current = sessionRef.current
    if (!current) return
    const next: AuthSession = { ...current, user: { ...current.user, ...profile } }
    sessionRef.current = next
    setSession(next)
  }, [])

  useEffect(() => {
    if (!session) {
      if (initialRestoreStartedRef.current) return
      initialRestoreStartedRef.current = true
      initialRestoreTimeoutRef.current = setTimeout(() => {
        initialRestoreTimeoutRef.current = null
        setIsInitializing(false)
      }, INITIAL_RESTORE_TIMEOUT_MS)
      void refreshAccessToken().finally(() => {
        if (initialRestoreTimeoutRef.current !== null) {
          clearTimeout(initialRestoreTimeoutRef.current)
          initialRestoreTimeoutRef.current = null
        }
        setIsInitializing(false)
      })
      return
    }
    setIsInitializing(false)

    const expectedSession = session
    let timeoutId: ReturnType<typeof setTimeout>

    const scheduleExpiry = () => {
      const remainingMs = expectedSession.expiresAt - Date.now()
      const refreshIn = Math.max(0, remainingMs - Math.min(60_000, Math.floor((expectedSession.expiresAt - expectedSession.user.issuedAt * 1000) / 5)))
      if (remainingMs <= 0) {
        if (sessionRef.current === expectedSession) logout()
        return
      }

      timeoutId = setTimeout(() => {
        if (sessionRef.current !== expectedSession) return
        void refreshAccessToken().then((token) => {
          if (!token && sessionRef.current === expectedSession) {
            const left = expectedSession.expiresAt - Date.now()
            if (left <= 0) logout()
            else timeoutId = setTimeout(scheduleExpiry, Math.min(left, 15_000))
          }
        })
      }, refreshIn)
    }

    scheduleExpiry()
    return () => clearTimeout(timeoutId)
  }, [session, logout, refreshAccessToken])

  const value = useMemo<AuthContextValue>(() => ({
    isAuthenticated: session !== null && session.expiresAt > Date.now(),
    isInitializing,
    user: session?.user ?? null,
    tokenType: session?.tokenType ?? null,
    expiresAt: session?.expiresAt ?? null,
    login,
    logout,
    updatePersonalProfile,
    getAccessToken,
    getSessionVersion,
    logoutIfCurrentSession,
    refreshAccessToken,
  }), [session, isInitializing, login, logout, updatePersonalProfile, getAccessToken, getSessionVersion, logoutIfCurrentSession, refreshAccessToken])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe utilizarse dentro de AuthProvider.')
  return context
}
