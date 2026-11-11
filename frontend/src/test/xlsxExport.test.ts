import { afterEach, describe, expect, it, vi } from 'vitest'
import { unzipSync } from 'fflate'
import { downloadXlsx } from '../utils/xlsxExport'

afterEach(() => vi.restoreAllMocks())
describe('exportación XLSX', () => {
  it('crea un paquete Office Open XML descargable con contenido escapado', async () => {
    let file: Blob | undefined
    vi.spyOn(URL, 'createObjectURL').mockImplementation(blob => { file = blob as Blob; return 'blob:report' })
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    downloadXlsx('reporte.xlsx', [['SistemaGym'], ['Socio & plan'], ['Monto', 15.5]])
    expect(file).toBeDefined()
    const archive = unzipSync(new Uint8Array(await file!.arrayBuffer()))
    const sheet = new TextDecoder().decode(archive['xl/worksheets/sheet1.xml'])
    expect(sheet).toContain('SistemaGym')
    expect(sheet).toContain('Socio &amp; plan')
    expect(sheet).toContain('<v>15.5</v>')
  })
})
