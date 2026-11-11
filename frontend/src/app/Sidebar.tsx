import {
  Activity,
  BadgeCheck,
  CalendarDays,
  ChartNoAxesColumnIncreasing,
  ClipboardCheck,
  LayoutDashboard,
  Layers3,
  UsersRound,
  UserRoundCog,
  X,
  Dumbbell,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { navigationForRoles } from './roleRoutes'

const iconsByPath = {
  '/': LayoutDashboard,
  '/socios': UsersRound,
  '/membresias': BadgeCheck,
  '/planes': Layers3,
  '/usuarios': UserRoundCog,
  '/asistencia': CalendarDays,
  '/actividad': Activity,
  '/reportes': ChartNoAxesColumnIncreasing,
  '/revisiones': ClipboardCheck,
  '/incidencias': Activity,
  '/checklists': ClipboardCheck,
  '/mi-asistencia': CalendarDays,
  '/entrenamiento': Dumbbell,
  '/mis-checklists': ClipboardCheck,
  '/mis-reportes': Activity,
  '/nutricion': ClipboardCheck,
}

type SidebarProps = {
  isOpen: boolean
  onClose: () => void
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const { user } = useAuth()
  const menuItems = navigationForRoles(user?.roles ?? [])

  return (
    <>
      {isOpen && (
        <button
          aria-label="Cerrar menú lateral"
          className="sidebar-backdrop"
          onClick={onClose}
          type="button"
        />
      )}
      <aside className={`sidebar${isOpen ? ' sidebar--open' : ''}`}>
        <div className="sidebar__brand-row">
          <div className="brand-lockup">
            <span className="brand-lockup__icon"><Dumbbell size={21} strokeWidth={2.4} /></span>
            <span className="brand-lockup__name">Sistema<span>Gym</span></span>
          </div>
          <button
            aria-label="Cerrar menú"
            className={`icon-button sidebar__close${isOpen ? ' sidebar__close--visible' : ''}`}
            onClick={onClose}
            type="button"
          >
            <X size={19} />
          </button>
        </div>

        <p className="sidebar__section-label">MENÚ PRINCIPAL</p>
        <nav aria-label="Navegación principal" className="sidebar__nav">
          {menuItems.map(({ label, path, end }) => {
            const Icon = iconsByPath[path as keyof typeof iconsByPath]
            return (
              <NavLink
                className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
                end={end}
                key={path}
                onClick={onClose}
                to={path}
              >
                <Icon aria-hidden="true" size={19} strokeWidth={1.8} />
                <span>{label}</span>
              </NavLink>
            )
          })}
        </nav>

        <div className="sidebar__bottom"><div className="sidebar__version">SISTEMAGYM <span>·</span> WEB</div></div>
      </aside>
    </>
  )
}
