import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

type State = { failed: boolean }

/** Keeps a failed lazy Reportes chunk from unmounting the rest of the application. */
export class ReportesRouteBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('No se pudo cargar o renderizar el módulo Reportes.', error, info.componentStack)
  }

  render() {
    if (this.state.failed) {
      return <Card className="reportes-route-failure" role="alert" title="No se pudo abrir Reportes">
        <p>El módulo no terminó de cargar. Intenta recargar esta página.</p>
        <Button type="button" variant="secondary" onClick={() => window.location.reload()}>Recargar Reportes</Button>
      </Card>
    }
    return this.props.children
  }
}
