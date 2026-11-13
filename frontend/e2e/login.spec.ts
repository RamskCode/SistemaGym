import { expect, test } from '@playwright/test'

test('restaura sesión en rutas protegidas, recarga, navega y cierra sesión', async ({ page }) => {
  const correo = process.env.SISTEMAGYM_E2E_USERNAME
  const contrasena = process.env.SISTEMAGYM_E2E_PASSWORD
  const expectedRole = process.env.SISTEMAGYM_E2E_ROLE ?? 'RECEPCIONISTA'
  expect(correo, 'El usuario se inyecta desde el test aislado de Spring').toBeTruthy()
  expect(contrasena, 'La contraseña se inyecta desde el test aislado de Spring').toBeTruthy()

  const loginResponses: number[] = []
  const refreshResponses: number[] = []
  const logoutResponses: number[] = []
  const blockedBusinessWrites: string[] = []
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const authenticationPost = request.method() === 'POST'
      && ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'].includes(path)
    if (request.method() === 'GET' || authenticationPost) {
      await route.continue()
      return
    }
    blockedBusinessWrites.push(`${request.method()} ${path}`)
    await route.abort()
  })
  page.on('response', (response) => {
    const path = new URL(response.url()).pathname
    if (path === '/api/auth/login') loginResponses.push(response.status())
    if (path === '/api/auth/refresh') refreshResponses.push(response.status())
    if (path === '/api/auth/logout') logoutResponses.push(response.status())
  })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
  await page.getByLabel('Correo electrónico').fill(correo!)
  await page.locator('#login-password').fill(contrasena!)
  await page.getByRole('button', { name: 'Ingresar' }).click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('button', { name: /Abrir opciones de cuenta/ })).toBeVisible()
  await expect(page.getByText(new RegExp(expectedRole))).toBeVisible()
  expect(loginResponses).toEqual([200])

  // Full document navigation drops the in-memory access JWT. The HttpOnly
  // refresh cookie must restore a session for each authorized deep link.
  const protectedRoute = expectedRole === 'ADMINISTRADOR' ? '/usuarios' : '/socios'
  const protectedHeading = expectedRole === 'ADMINISTRADOR' ? 'Usuarios y empleados' : 'Socios'
  await page.goto(protectedRoute)
  await expect(page).toHaveURL(new RegExp(`${protectedRoute}$`))
  await expect(page.getByRole('heading', { name: protectedHeading, exact: true })).toBeVisible()

  await page.reload()
  await expect(page).toHaveURL(new RegExp(`${protectedRoute}$`))
  await expect(page.getByRole('heading', { name: protectedHeading, exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Asistencia', exact: true }).click()
  await expect(page).toHaveURL(/\/asistencia$/)
  await expect(page.getByRole('heading', { name: 'Asistencia', exact: true })).toBeVisible()
  expect(refreshResponses.length).toBeGreaterThanOrEqual(2)
  expect(refreshResponses).toEqual(refreshResponses.map(() => 200))

  await page.getByRole('button', { name: /Abrir opciones de cuenta/ }).click()
  const logoutResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/auth/logout')
  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  expect((await logoutResponse).status()).toBe(204)
  await expect(page).toHaveURL(/\/login$/)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
  expect(logoutResponses).toEqual([204])
  expect(blockedBusinessWrites).toEqual([])
})
