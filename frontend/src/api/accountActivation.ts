import { apiRequest } from './http'

const ROOT = '/api/auth/activation'
export function validateActivationToken(token: string) {
  return apiRequest<void>(`${ROOT}/validate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
  })
}
export function activateAccount(token: string, contrasena: string) {
  return apiRequest<void>(`${ROOT}/complete`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, contrasena }),
  })
}
