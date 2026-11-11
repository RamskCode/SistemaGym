import '../styles/global.css'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppShell } from './AppShell'
import { AccessDeniedPage, NotFoundPage, PlaceholderPage, RoleHomePage } from './RoutePages'
import { LoginPage } from '../auth/LoginPage'
import { RequestPasswordRecoveryPage, ResetPasswordPage } from '../auth/PasswordRecoveryPages'
import { AccountActivationPage } from '../auth/AccountActivationPage'
import { PublicOnlyRoute, RequireAuth, RequireRole } from './RouteGuards'
import { roleRouteDefinitions } from './roleRoutes'
import { SociosPage } from './SociosPage'
import { MembresiasPage } from './MembresiasPage'
import { AsistenciaPage } from './AsistenciaPage'
import { RevisionGimnasioPage } from './RevisionGimnasioPage'
import { MisChecklistsPage } from './MisChecklistsPage'
import { MisReportesPage } from './MisReportesPage'
import { NutricionPage } from './NutricionPage'
import { PlanesPage } from './PlanesPage'
import { ElementosRevisionPage } from './ElementosRevisionPage'
import { UsuariosPage } from './UsuariosPage'
import { ReportesRouteBoundary } from './ReportesRouteBoundary'
const ReportesPage = lazy(() => import('./ReportesPage').then((module) => ({ default: module.ReportesPage })))
import { IncidenciasAdminPage } from './IncidenciasAdminPage'
import { ChecklistsAdminPage } from './ChecklistsAdminPage'
import { ActividadPage } from './ActividadPage'
import { MiCuentaPage } from './MiCuentaPage'
import './module-ui.css'
import { useNumberInputWheelGuard } from '../hooks/useNumberInputWheelGuard'

export function App() {
  useNumberInputWheelGuard()
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>} path="/login" />
      <Route element={<RequestPasswordRecoveryPage />} path="/recuperar-contrasena" />
      <Route element={<ResetPasswordPage />} path="/restablecer-contrasena" />
      <Route element={<AccountActivationPage />} path="/activar-cuenta" />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route element={<RoleHomePage />} index />
          {roleRouteDefinitions.map(({ path, title, roles }) => (
            <Route
              element={<RequireRole allowedRoles={roles}>{path === 'socios' ? <SociosPage /> : path === 'membresias' ? <MembresiasPage /> : path === 'planes' ? <PlanesPage /> : path === 'usuarios' ? <UsuariosPage /> : path === 'mi-cuenta' ? <MiCuentaPage /> : path === 'reportes' ? <ReportesRouteBoundary><Suspense fallback={<p role="status">Cargando reportes…</p>}><ReportesPage /></Suspense></ReportesRouteBoundary> : path === 'revisiones' ? <ElementosRevisionPage /> : path === 'incidencias' ? <IncidenciasAdminPage /> : path === 'checklists' ? <ChecklistsAdminPage /> : path === 'actividad' ? <ActividadPage /> : path === 'asistencia' || path === 'mi-asistencia' ? <AsistenciaPage /> : path === 'entrenamiento' ? <RevisionGimnasioPage /> : path === 'mis-checklists' ? <MisChecklistsPage /> : path === 'mis-reportes' ? <MisReportesPage /> : path === 'nutricion' ? <NutricionPage /> : <PlaceholderPage title={title} />}</RequireRole>}
              key={path}
              path={path}
            />
          ))}
          <Route element={<AccessDeniedPage />} path="acceso-denegado" />
          <Route element={<NotFoundPage />} path="*" />
        </Route>
      </Route>
    </Routes>
  )
}
