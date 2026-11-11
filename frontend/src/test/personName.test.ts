import { describe, expect, it } from 'vitest'
import { formatPersonName } from '../utils/personName'

describe('formatPersonName', () => {
  it('normaliza nombres en mayúsculas, minúsculas y espacios repetidos', () => {
    expect(formatPersonName('  jHoNaTaN   rIcAlDi   OSORIO ')).toBe('Jhonatan Ricaldi Osorio')
  })

  it('conserva acentos y normaliza partículas de apellidos compuestos', () => {
    expect(formatPersonName('MARÍA   DEL CARMEN de la CRUZ')).toBe('María del Carmen de la Cruz')
    expect(formatPersonName('JOSÉ ÁNGEL O’NEILL')).toBe('José Ángel O’Neill')
  })

  it('no altera cadenas vacías y recorta bordes', () => {
    expect(formatPersonName('   ')).toBe('')
    expect(formatPersonName('  ANA  ')).toBe('Ana')
  })
})
