import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ReportesRouteBoundary } from '../app/ReportesRouteBoundary'

function FailedLazyPage(): never { throw new Error('Failed to fetch dynamically imported module') }

describe('ReportesRouteBoundary', () => {
  it('contiene el fallo de carga/renderizado y ofrece una recuperación visible', () => {
    const log = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    render(<ReportesRouteBoundary><FailedLazyPage /></ReportesRouteBoundary>)
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo abrir Reportes')
    expect(screen.getByRole('button', { name: 'Recargar Reportes' })).toBeInTheDocument()
    expect(log).toHaveBeenCalled()
    log.mockRestore()
  })
})
