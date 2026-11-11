import { apiRequest } from './http'

const ROOT = '/api/auth/password-recovery'

export async function requestPasswordRecovery(correo: string): Promise<string> {
  const result = await apiRequest<{ mensaje: string }>(`${ROOT}/solicitar`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ correo }),
  })
  return result.mensaje
}

export async function validateRecoveryToken(token: string): Promise<void> {
  await apiRequest<void>(`${ROOT}/validar`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
  })
}

export async function resetPassword(token: string, contrasena: string): Promise<void> {
  await apiRequest<void>(`${ROOT}/restablecer`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, contrasena }),
  })
}
