import { beforeEach, describe, expect, it, vi } from 'vitest'
import { addMembresiaWriteMarker, readMembresiaWriteMarkers, removeMembresiaWriteMarker, type MembresiaWriteMarker } from '../app/membresiasUncertainWrites'

const first: MembresiaWriteMarker = {
  key: '00000000-0000-4000-8000-000000000001', operation: 'nueva',
  body: { idSocio: 4, idPlan: 7, montoPrimerPago: 40, metodoPago: 'YAPE', referencia: null, observaciones: null }, state: 'uncertain',
}

beforeEach(() => {
  window.localStorage.clear()
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: {
    subtle: { digest: async (_algorithm: AlgorithmIdentifier, input: BufferSource) => {
      const bytes = ArrayBuffer.isView(input)
        ? new Uint8Array(input.buffer, input.byteOffset, input.byteLength)
        : new Uint8Array(input as ArrayBuffer)
      const result = new Uint8Array(32)
      result.set(bytes.slice(0, 32))
      return result.buffer
    } },
  } as unknown as Crypto })
})

describe('persistencia de intentos inciertos de membresías', () => {
  it('aísla las marcas por cuenta y no guarda el correo como clave', async () => {
    await addMembresiaWriteMarker('uno@example.test', first)
    expect(await readMembresiaWriteMarkers('uno@example.test')).toEqual([first])
    expect(await readMembresiaWriteMarkers('dos@example.test')).toEqual([])
    expect(window.localStorage.key(0)).not.toContain('uno@example.test')
  })

  it('conserva la marca en memoria durante la sesión si localStorage deja de estar disponible', async () => {
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('disabled') })
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('disabled') })
    await addMembresiaWriteMarker('memoria@example.test', first)
    expect(await readMembresiaWriteMarkers('memoria@example.test')).toEqual([first])
    get.mockRestore()
    set.mockRestore()
  })

  it('no pierde una marca de memoria si el guardado persistente falla pero la lectura está disponible', async () => {
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota') })
    await addMembresiaWriteMarker('cuota@example.test', first)
    set.mockRestore()
    expect(await readMembresiaWriteMarkers('cuota@example.test')).toEqual([first])
  })

  it('permite retirar solo la marca resuelta', async () => {
    const second = { ...first, key: '00000000-0000-4000-8000-000000000002' }
    await addMembresiaWriteMarker('varias@example.test', first)
    await addMembresiaWriteMarker('varias@example.test', second)
    await removeMembresiaWriteMarker('varias@example.test', first)
    expect(await readMembresiaWriteMarkers('varias@example.test')).toEqual([second])
  })
})
