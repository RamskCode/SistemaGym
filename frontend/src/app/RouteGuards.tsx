import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth, type AuthRole } from '../auth/AuthContext'
import { canAccessPath } from './roleRoutes'

export function RequireAuth() {
  const { isAuthenticated, isInitializing } = useAuth()

  if (isInitializing) return null

  if (!isAuthenticated) {
    return <Navigate replace to="/login" />
  }

  return <Outlet />
}

type PublicOnlyRouteProps = { children: ReactNode }

export function PublicOnlyRoute({ children }: PublicOnlyRouteProps) {
  const { isAuthenticated, isInitializing, user } = useAuth()

  if (isAuthenticated && user) {
    return <Navigate replace to="/" />
  }

  if (isInitializing) return null

  return children
}

type RequireRoleProps = {
  allowedRoles: readonly AuthRole[]
  children: ReactNode
}

export function RequireRole({ allowedRoles, children }: RequireRoleProps) {
  const { user } = useAuth()
  const location = useLocation()
  const roles = user?.roles ?? []

  if (!allowedRoles.some((role) => roles.includes(role))
      || !canAccessPath(location.pathname, roles)) {
    return <Navigate replace state={{ from: location.pathname }} to="/acceso-denegado" />
  }

  return children
}
