import type { AuthRole } from '../auth/AuthContext'

export type RoleRouteDefinition = {
  path: string
  title: string
  roles: readonly AuthRole[]
}

export const roleRouteDefinitions: readonly RoleRouteDefinition[] = [
  { path: 'socios', title: 'Socios', roles: ['ADMINISTRADOR', 'RECEPCIONISTA'] },
  { path: 'membresias', title: 'Membresías', roles: ['ADMINISTRADOR', 'RECEPCIONISTA'] },
  { path: 'planes', title: 'Planes y tarifas', roles: ['ADMINISTRADOR'] },
  { path: 'usuarios', title: 'Usuarios y empleados', roles: ['ADMINISTRADOR'] },
  { path: 'asistencia', title: 'Asistencia', roles: ['ADMINISTRADOR', 'RECEPCIONISTA'] },
  { path: 'actividad', title: 'Actividad', roles: ['ADMINISTRADOR'] },
  { path: 'reportes', title: 'Reportes', roles: ['ADMINISTRADOR'] },
  { path: 'revisiones', title: 'Revisiones', roles: ['ADMINISTRADOR'] },
  { path: 'incidencias', title: 'Incidencias', roles: ['ADMINISTRADOR'] },
  { path: 'checklists', title: 'Checklists del gimnasio', roles: ['ADMINISTRADOR'] },
  { path: 'mi-cuenta', title: 'Mi perfil', roles: ['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA'] },
  { path: 'mi-asistencia', title: 'Mis asistencias', roles: ['ENTRENADOR', 'NUTRICIONISTA'] },
  { path: 'entrenamiento', title: 'Revisión de equipos', roles: ['ENTRENADOR'] },
  { path: 'mis-checklists', title: 'Mis revisiones', roles: ['ENTRENADOR'] },
  { path: 'mis-reportes', title: 'Mis incidencias', roles: ['ENTRENADOR'] },
  { path: 'nutricion', title: 'Nutrición', roles: ['NUTRICIONISTA'] },
]

export type NavigationItem = {
  label: string
  path: string
  end?: boolean
}

const navigationItems: readonly (NavigationItem & { roles: readonly AuthRole[] })[] = [
  { label: 'Inicio', path: '/', end: true, roles: ['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA'] },
  { label: 'Socios', path: '/socios', roles: ['ADMINISTRADOR', 'RECEPCIONISTA'] },
  { label: 'Membresías', path: '/membresias', roles: ['ADMINISTRADOR', 'RECEPCIONISTA'] },
  { label: 'Planes y tarifas', path: '/planes', roles: ['ADMINISTRADOR'] },
  { label: 'Usuarios y empleados', path: '/usuarios', roles: ['ADMINISTRADOR'] },
  { label: 'Asistencia', path: '/asistencia', roles: ['ADMINISTRADOR', 'RECEPCIONISTA'] },
  { label: 'Actividad', path: '/actividad', roles: ['ADMINISTRADOR'] },
  { label: 'Reportes', path: '/reportes', roles: ['ADMINISTRADOR'] },
  { label: 'Equipos y áreas', path: '/revisiones', roles: ['ADMINISTRADOR'] },
  { label: 'Incidencias', path: '/incidencias', roles: ['ADMINISTRADOR'] },
  { label: 'Checklists del gimnasio', path: '/checklists', roles: ['ADMINISTRADOR'] },
  { label: 'Mis asistencias', path: '/mi-asistencia', roles: ['ENTRENADOR', 'NUTRICIONISTA'] },
  { label: 'Revisión de equipos', path: '/entrenamiento', roles: ['ENTRENADOR'] },
  { label: 'Mis revisiones', path: '/mis-checklists', roles: ['ENTRENADOR'] },
  { label: 'Mis incidencias', path: '/mis-reportes', roles: ['ENTRENADOR'] },
  { label: 'Nutrición', path: '/nutricion', roles: ['NUTRICIONISTA'] },
]

export function navigationForRoles(roles: readonly AuthRole[]): NavigationItem[] {
  return navigationItems
    .filter((item) => item.roles.some((role) => roles.includes(role)))
    .map(({ label, path, end }) => ({ label, path, end }))
}

const realDataPaths = new Set([
  '/socios', '/membresias', '/planes', '/usuarios', '/asistencia', '/actividad', '/reportes', '/revisiones',
  '/incidencias', '/checklists', '/mi-cuenta', '/mi-asistencia', '/entrenamiento',
  '/mis-checklists', '/mis-reportes', '/nutricion',
])

export function isRealDataPath(pathname: string): boolean {
  const normalizedPath = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  return realDataPaths.has(normalizedPath)
}

export function canAccessPath(pathname: string, roles: readonly AuthRole[]): boolean {
  const normalizedPath = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  if (normalizedPath === '/') return roles.length > 0

  const route = roleRouteDefinitions.find(({ path }) => `/${path}` === normalizedPath)
  return route?.roles.some((role) => roles.includes(role)) ?? false
}
