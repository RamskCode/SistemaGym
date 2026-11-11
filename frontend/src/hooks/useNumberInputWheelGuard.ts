import { useEffect } from 'react'

export function useNumberInputWheelGuard() {
  useEffect(() => {
    const blurFocusedNumberInput = (event: WheelEvent) => {
      const target = event.target
      if (target instanceof HTMLInputElement && target.type === 'number' && document.activeElement === target) {
        target.blur()
      }
    }

    document.addEventListener('wheel', blurFocusedNumberInput, true)
    return () => document.removeEventListener('wheel', blurFocusedNumberInput, true)
  }, [])
}
