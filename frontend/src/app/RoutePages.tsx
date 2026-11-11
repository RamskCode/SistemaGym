import { Link, useLocation } from 'react-router-dom'
import { Card } from '../components/Card'
import { PageTitle } from '../components/PageTitle'
import { useAuth } from '../auth/AuthContext'
import { DashboardDemo } from './DashboardDemo'
import { ReceptionDashboard } from './ReceptionDashboard'
import { TrainerDashboard } from './TrainerDashboard'
import { NutritionDashboard } from './NutritionDashboard'

type PlaceholderPageProps = {
  title: string
}

export function PlaceholderPage({ title }: PlaceholderPageProps) {
  return <div className="dashboard"><PageTitle eyebrow="SISTEMAGYM" title={title} /></div>
}

export function NotFoundPage() {
  const location = useLocation()

  return (
    <div className="dashboard">
      <PageTitle
        description="La dirección solicitada no corresponde a una sección disponible."
        eyebrow="ERROR 404"
        title="Página no encontrada"
      />
      <Card className="route-placeholder route-placeholder--not-found">
        <p>No existe una pantalla para <code>{location.pathname}</code>.</p>
        <Link className="button button--primary route-placeholder__link" to="/">Volver al resumen</Link>
      </Card>
    </div>
  )
}

export function RoleHomePage() {
  const { user } = useAuth()
  const roles = user?.roles ?? []

  if (roles.includes('ADMINISTRADOR')) return <DashboardDemo />
  if (roles.includes('RECEPCIONISTA')) return <ReceptionDashboard />
  if (roles.includes('ENTRENADOR')) return <TrainerDashboard />
  if (roles.includes('NUTRICIONISTA')) return <NutritionDashboard />

  return <div className="dashboard"><PageTitle description="Selecciona una opción del menú para continuar." eyebrow="PORTAL DEL PERSONAL" title="Inicio" /><Card className="route-placeholder"><p>No hay un panel asignado a este perfil.</p></Card></div>
}

export function AccessDeniedPage() {
  return (
    <div className="dashboard">
      <PageTitle
        description="Tu perfil no tiene autorización para abrir esta sección."
        eyebrow="ACCESO RESTRINGIDO"
        title="Acceso no autorizado"
      />
      <Card className="route-placeholder route-placeholder--not-found">
        <p>Solicita acceso a un administrador si consideras que necesitas esta sección.</p>
        <Link className="button button--primary route-placeholder__link" to="/">Ir a mi inicio</Link>
      </Card>
    </div>
  )
}
