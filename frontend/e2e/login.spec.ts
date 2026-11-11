import { expect, test } from '@playwright/test'

test('inicia sesión con usuario temporal a través de Vite, Spring y MySQL', async ({ page }) => {
  const correo = process.env.SISTEMAGYM_E2E_USERNAME
  const contrasena = process.env.SISTEMAGYM_E2E_PASSWORD
  expect(correo, 'El usuario se inyecta desde el test aislado de Spring').toBeTruthy()
  expect(contrasena, 'La contraseña temporal se inyecta desde el test aislado de Spring').toBeTruthy()

  const loginResponses: number[] = []
  page.on('response', (response) => {
    if (new URL(response.url()).pathname === '/api/auth/login') {
      loginResponses.push(response.status())
    }
  })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
  await page.getByLabel('Correo electrónico').fill(correo!)
  await page.locator('#login-password').fill(contrasena!)
  await page.getByRole('button', { name: 'Ingresar' }).click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('button', { name: /Abrir opciones de cuenta/ })).toBeVisible()
  await expect(page.getByText(/RECEPCIONISTA/)).toBeVisible()
  expect(loginResponses).toEqual([200])
})
