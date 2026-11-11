const particles = new Set(['a', 'al', 'con', 'de', 'del', 'e', 'la', 'las', 'los', 'y'])

/** Formats name fields only; do not apply to identifiers, email or credentials. */
export function formatPersonName(value: string): string {
  return value.trim().replace(/\s+/gu, ' ').split(' ').filter(Boolean).map((word, index) => {
    const parts = word.split(/([-’'])/u)
    return parts.map((part, partIndex) => {
      if (/^[-’']$/u.test(part)) return part
      const lower = part.toLocaleLowerCase('es-PE')
      if ((index > 0 || partIndex > 0) && particles.has(lower)) return lower
      const [first = '', ...rest] = Array.from(lower)
      return first.toLocaleUpperCase('es-PE') + rest.join('')
    }).join('')
  }).join(' ')
}
