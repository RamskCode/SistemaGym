import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useNumberInputWheelGuard } from '../hooks/useNumberInputWheelGuard'

describe('useNumberInputWheelGuard', () => {
  it('blurs a focused number input on wheel without cancelling normal scrolling', () => {
    const { unmount } = renderHook(() => useNumberInputWheelGuard())
    const input = document.createElement('input')
    input.type = 'number'
    document.body.append(input)
    input.focus()

    const wheel = new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: 100 })
    act(() => input.dispatchEvent(wheel))

    expect(document.activeElement).not.toBe(input)
    expect(wheel.defaultPrevented).toBe(false)
    expect(input.type).toBe('number')

    input.remove()
    unmount()
  })

  it('leaves keyboard focus on number inputs until a wheel event occurs', () => {
    const { unmount } = renderHook(() => useNumberInputWheelGuard())
    const input = document.createElement('input')
    input.type = 'number'
    document.body.append(input)
    input.focus()

    expect(document.activeElement).toBe(input)

    input.remove()
    unmount()
  })
})
