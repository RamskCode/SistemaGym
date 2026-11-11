import { describe, expect, it } from 'vitest'
import { canAccessPath, isRealDataPath, navigationForRoles, roleRouteDefinitions } from '../app/roleRoutes'
import type { AuthRole } from '../auth/AuthContext'

const allRoles: AuthRole[] = ['ADMINISTRADOR', 'RECEPCIONISTA', 'ENTRENADOR', 'NUTRICIONISTA']

describe('role route integration', () => {
  it('has unique paths and a matching navigation destination for every protected route', () => {
    const routePaths = roleRouteDefinitions.map(({ path }) => `/${path}`)
    expect(new Set(routePaths).size).toBe(routePaths.length)
    for (const route of roleRouteDefinitions) {
      if (route.path !== 'mi-cuenta') {
        expect(navigationForRoles(route.roles).some(({ path }) => path === `/${route.path}`)).toBe(true)
      }
      expect(canAccessPath(`/${route.path}`, route.roles)).toBe(true)
    }
    expect(navigationForRoles(['ADMINISTRADOR']).find(({ path }) => path === '/planes'))
      .toMatchObject({ label: 'Planes y tarifas', path: '/planes' })
  })

  it('shows only role-authorized navigation entries and routes', () => {
    for (const role of allRoles) {
      const visible = navigationForRoles([role])
      expect(new Set(visible.map(({ path }) => path)).size).toBe(visible.length)
      for (const item of visible) {
        expect(canAccessPath(item.path, [role])).toBe(true)
      }
      for (const route of roleRouteDefinitions) {
        expect(canAccessPath(`/${route.path}`, [role])).toBe(route.roles.includes(role))
      }
    }
  })

  it('classifies every operational route consistently while demo placeholders stay marked as demo', () => {
    for (const path of ['/socios', '/membresias', '/planes', '/usuarios', '/asistencia', '/revisiones', '/incidencias', '/checklists', '/mi-cuenta', '/mi-asistencia', '/entrenamiento', '/mis-checklists', '/mis-reportes', '/nutricion']) {
      expect(isRealDataPath(path)).toBe(true)
    }
    for (const path of ['/', '/ruta-desconocida']) {
      expect(isRealDataPath(path)).toBe(false)
    }
    expect(isRealDataPath('/actividad')).toBe(true)
    expect(isRealDataPath('/configuracion/')).toBe(false)
  })
})
