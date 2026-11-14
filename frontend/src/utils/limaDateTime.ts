const HAS_TIME_ZONE = /(?:Z|[+-]\d{2}:\d{2})$/i

/** Parse LocalDateTime API values as Peru wall time; preserve explicit offsets. */
export function parseLimaDateTime(value: string) {
  return new Date(HAS_TIME_ZONE.test(value) ? value : `${value}-05:00`)
}
