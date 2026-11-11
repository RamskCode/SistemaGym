import { apiRequest } from './http'
import { formatPersonName } from '../utils/personName'

export type InitialAdminRequest = {
  nombres: string
  apellidos: string
  correo: string
  contrasena: string
}

export async function getInitialSetupStatus(): Promise<boolean> {
  const response = await apiRequest<unknown>('/api/setup/status')
  return typeof response === 'object' && response !== null
    && (response as { required?: unknown }).required === true
}

export async function createInitialAdministrator(
  token: string,
  request: InitialAdminRequest,
): Promise<void> {
  await apiRequest<void>('/api/setup/administrator', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Installation-Token': token },
    body: JSON.stringify({ ...request, nombres: formatPersonName(request.nombres), apellidos: formatPersonName(request.apellidos) }),
  })
}
