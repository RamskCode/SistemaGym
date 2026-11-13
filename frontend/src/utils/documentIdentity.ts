export type IdentityDocumentType = 'DNI' | 'CE'

export function normalizeDocumentInput(type: IdentityDocumentType | '', value: string): string {
  const allowed = value.replace(/[^0-9]/g, '')
  return allowed.slice(0, type === 'DNI' ? 8 : 12)
}

export function isValidDocument(type: IdentityDocumentType | '', value: string): boolean {
  return type === 'DNI' ? /^[0-9]{8}$/.test(value) : type === 'CE' && /^[0-9]{1,12}$/.test(value)
}

export function documentHelp(type: IdentityDocumentType | ''): string {
  return type === 'DNI' ? 'DNI: exactamente 8 dígitos.' : type === 'CE' ? 'CE: hasta 12 dígitos; solo números.' : 'Selecciona DNI o CE.'
}
