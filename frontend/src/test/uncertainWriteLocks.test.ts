import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import { addUncertainWriteLock, readUncertainWriteLocks, uncertainWriteResult } from '../app/uncertainWriteLocks'

describe('uncertain write lock support', () => {
  it.each([
    [new ApiError('network'), true],
    [new ApiError('invalid-response'), true],
    [new ApiError('server', 500), true],
    [new ApiError('http', 408), true],
    [new ApiError('validation', 400), false],
    [new ApiError('conflict', 409), false],
    [new ApiError('unauthenticated', 401), false],
    [new ApiError('forbidden', 403), false],
    [new ApiError('not-found', 404), false],
    [new ApiError('http', 429), true],
  ])('classifies %s as uncertain=%s', (error, expected) => {
    expect(uncertainWriteResult(error)).toBe(expected)
  })

  it('persists a minimal marker per account without storing the account identifier', async () => {
    await addUncertainWriteLock('trainer@example.test', 'checklist', 'create')
    expect(await readUncertainWriteLocks('trainer@example.test', 'checklist')).toEqual(['create'])
    expect(await readUncertainWriteLocks('other@example.test', 'checklist')).toEqual([])
    expect(window.localStorage.key(0)).not.toContain('trainer@example.test')
    expect(window.localStorage.getItem(window.localStorage.key(0)!)).toBe('["create"]')
  })
})
