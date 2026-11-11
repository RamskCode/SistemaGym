import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { ChevronDown, LogOut, Menu, UserRound } from 'lucide-react'
import { Sidebar } from './Sidebar'
import { Button } from '../components/Button'
import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { obtenerMiCuenta } from '../api/miCuenta'

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [accountMenuOpen, setAccountMenuOpen] = useState(false)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileUnavailable, setProfileUnavailable] = useState(false)
  const auth = useAuth()
  const { logout, user } = auth
  const accountMenuRef = useRef<HTMLDivElement>(null)
  const accountTriggerRef = useRef<HTMLButtonElement>(null)
  const menuTriggerRef = useRef<HTMLButtonElement>(null)
  const sessionRole = user?.roles.join(' · ') ?? 'Sin rol'
  const fullName = `${user?.nombres?.trim() ?? ''} ${user?.apellidos?.trim() ?? ''}`.trim()
  const hasProfileName = Boolean(fullName)
  const isLoadingProfile = !hasProfileName && (profileLoading || !profileUnavailable)
  const accountLabel = hasProfileName ? fullName : isLoadingProfile ? 'Cargando…' : '—'
  const headerName = hasProfileName
    ? `${user?.nombres?.trim()} ${user?.apellidos?.trim().split(/\s+/)[0]}`
    : accountLabel
  const avatarInitials = hasProfileName
    ? `${user?.nombres?.trim().charAt(0) ?? ''}${user?.apellidos?.trim().charAt(0) ?? ''}`.toLocaleUpperCase('es-PE')
    : null

  useEffect(() => {
    if (!user) return
    const version = auth.getSessionVersion()
    let active = true
    setProfileUnavailable(false)
    setProfileLoading(!hasProfileName)
    void obtenerMiCuenta(auth).then((profile) => {
      if (active && auth.getSessionVersion() === version) {
        auth.updatePersonalProfile({
          nombres: profile.nombres,
          apellidos: profile.apellidos,
          tipoDocumento: profile.tipoDocumento,
          numeroDocumento: profile.numeroDocumento,
        })
        setProfileUnavailable(false)
      }
    }).catch(() => {
      if (active && auth.getSessionVersion() === version) {
        auth.updatePersonalProfile({ nombres: undefined, apellidos: undefined })
        setProfileUnavailable(true)
      }
    }).finally(() => {
      if (active && auth.getSessionVersion() === version) setProfileLoading(false)
    })
    return () => { active = false }
  }, [auth.getSessionVersion, auth.updatePersonalProfile, user?.correo])

  useEffect(() => {
    if (!accountMenuOpen) return
    const closeOutside = (event: PointerEvent) => {
      if (event.target instanceof Node && !accountMenuRef.current?.contains(event.target)) {
        setAccountMenuOpen(false)
      }
    }
    document.addEventListener('pointerdown', closeOutside)
    return () => document.removeEventListener('pointerdown', closeOutside)
  }, [accountMenuOpen])

  function closeNavigationMenu() {
    setMenuOpen(false)
    menuTriggerRef.current?.focus()
  }

  useEffect(() => {
    if (!menuOpen) return
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      closeNavigationMenu()
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [menuOpen])

  function closeAccountMenu() {
    setAccountMenuOpen(false)
  }

  function handleAccountMenuKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeAccountMenu()
      accountTriggerRef.current?.focus()
    }
  }

  return (
    <div className="app-shell">
      <Sidebar
        isOpen={menuOpen}
        onClose={closeNavigationMenu}
      />

      <div className="app-shell__main">
        <header className="topbar">
          <Button
            aria-expanded={menuOpen}
            aria-label={menuOpen ? 'Cerrar navegación' : 'Abrir navegación'}
            className="topbar__menu"
            onClick={(event) => { menuTriggerRef.current = event.currentTarget; setMenuOpen((open) => !open) }}
            variant="ghost"
          >
            <Menu size={21} />
          </Button>
          <div className="topbar__context">
            <span className="topbar__overline">ESPACIO DE TRABAJO</span>
            <span className="topbar__title">Panel de gestión</span>
          </div>
          <div className="topbar__right">
            <div className="topbar__account" onKeyDown={handleAccountMenuKeyDown} ref={accountMenuRef}>
              <button
                aria-controls="account-menu"
                aria-expanded={accountMenuOpen}
                aria-label={`Abrir opciones de cuenta: ${accountLabel}`}
                className="topbar__account-trigger"
                onClick={() => setAccountMenuOpen((open) => !open)}
                ref={accountTriggerRef}
                type="button"
              >
                <span aria-hidden="true" className="avatar">{avatarInitials || <UserRound size={17} />}</span>
                <span className="topbar__user-meta">
                  <span aria-live={isLoadingProfile || profileUnavailable ? 'polite' : undefined} className="topbar__user-name" title={hasProfileName ? fullName : accountLabel}>
                    {headerName}
                  </span>
                  <span className="topbar__user-role">{sessionRole}</span>
                </span>
                <ChevronDown aria-hidden="true" className="topbar__account-chevron" size={15} />
              </button>
              {accountMenuOpen && <div aria-label="Opciones de cuenta" className="account-menu" id="account-menu">
                <div aria-busy={isLoadingProfile} className="account-menu__identity">
                  <span title={user?.correo}>{user?.correo ?? 'Cuenta autenticada'}</span>
                  {profileUnavailable && <small role="status">Perfil temporalmente no disponible</small>}
                </div>
                <Link className="account-menu__link" onClick={closeAccountMenu} to="/mi-cuenta">
                  <UserRound aria-hidden="true" size={17} /> Mi cuenta
                </Link>
                <button className="account-menu__logout" onClick={() => { closeAccountMenu(); logout() }} type="button">
                  <LogOut aria-hidden="true" size={17} /> Cerrar sesión
                </button>
              </div>}
            </div>
          </div>
        </header>

        <main className="workspace">
          <Outlet />
          <footer className="workspace__footer">
            <span>SistemaGym · WEB</span>
          </footer>
        </main>
      </div>
    </div>
  )
}
