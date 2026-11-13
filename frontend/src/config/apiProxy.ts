export function createSameOriginApiProxy(target: string) {
  return {
    target,
    // AuthController validates Origin against Host for cookie-backed refresh.
    // Preserve the browser's same-origin Host (e.g. localhost:5173) through Vite.
    changeOrigin: false,
  } as const
}
