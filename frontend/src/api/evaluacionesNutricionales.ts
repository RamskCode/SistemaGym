import { ApiError, apiGet, apiPost, type AuthenticatedRequestContext } from './http'

export type RegistrarEvaluacionNutricionalRequest = {
  pesoKg: number
  tallaM: number
  porcentajeGrasa: number
  masaMuscularKg: number
  habitosAlimenticios: string
  objetivosNutricionales: string
  recomendacionesNutricionales: string
  observacionesSeguimiento: string | null
}

export type EvaluacionNutricional = RegistrarEvaluacionNutricionalRequest & {
  idEvaluacion: number
  idSocio: number
  idNutricionista: number
  fechaEvaluacion: string
}

export type ResumenNutricion = {
  sociosDisponibles: number
  evaluacionesEsteMes: number
  sociosEvaluadosEsteMes: number
  evaluacionesUltimosSieteDias: number
  evaluacionesRecientes: Array<{
    idEvaluacion: number
    idSocio: number
    codigoSocio: string
    nombres: string
    apellidos: string
    fechaEvaluacion: string
  }>
}

export async function consultarResumenNutricion(
  auth: AuthenticatedRequestContext,
): Promise<ResumenNutricion> {
  const response = await apiGet<unknown>('/api/nutricion/resumen', auth)
  if (!isNutritionSummary(response)) throw new ApiError('invalid-response', 200)
  return response
}

function isNutritionSummary(value: unknown): value is ResumenNutricion {
  if (!value || typeof value !== 'object') return false
  const summary = value as Record<string, unknown>
  return ['sociosDisponibles', 'evaluacionesEsteMes', 'sociosEvaluadosEsteMes', 'evaluacionesUltimosSieteDias']
    .every((key) => Number.isSafeInteger(summary[key]) && (summary[key] as number) >= 0)
    && Array.isArray(summary.evaluacionesRecientes)
    && summary.evaluacionesRecientes.every((item: unknown) => {
      if (!item || typeof item !== 'object') return false
      const recent = item as Record<string, unknown>
      return Number.isSafeInteger(recent.idEvaluacion) && Number.isSafeInteger(recent.idSocio)
        && typeof recent.codigoSocio === 'string' && typeof recent.nombres === 'string'
        && typeof recent.apellidos === 'string' && typeof recent.fechaEvaluacion === 'string'
        && Number.isFinite(Date.parse(recent.fechaEvaluacion))
    })
}

function isEvaluationResponse(value: unknown): value is EvaluacionNutricional {
  if (typeof value !== 'object' || value === null) return false
  const response = value as Record<string, unknown>
  return Number.isInteger(response.idEvaluacion)
    && Number.isInteger(response.idSocio)
    && Number.isInteger(response.idNutricionista)
    && typeof response.fechaEvaluacion === 'string'
    && Number.isFinite(Date.parse(response.fechaEvaluacion))
    && ['pesoKg', 'tallaM', 'porcentajeGrasa', 'masaMuscularKg']
      .every((field) => typeof response[field] === 'number' && Number.isFinite(response[field]))
    && ['habitosAlimenticios', 'objetivosNutricionales', 'recomendacionesNutricionales']
      .every((field) => typeof response[field] === 'string')
    && (response.observacionesSeguimiento === null
      || typeof response.observacionesSeguimiento === 'string')
}

export async function consultarHistorialNutricional(
  auth: AuthenticatedRequestContext,
  idSocio: number,
): Promise<EvaluacionNutricional[]> {
  const response = await apiGet<unknown>(
    `/api/socios/${idSocio}/evaluaciones-nutricionales`, auth,
  )
  if (!Array.isArray(response) || !response.every(isEvaluationResponse)) {
    throw new ApiError('invalid-response', 200)
  }
  return response
}

export async function registrarEvaluacionNutricional(
  auth: AuthenticatedRequestContext,
  idSocio: number,
  request: RegistrarEvaluacionNutricionalRequest,
): Promise<EvaluacionNutricional> {
  try {
    const response = await apiPost<unknown>(
      `/api/socios/${idSocio}/evaluaciones-nutricionales`, auth, request,
      AbortSignal.timeout(30_000), true,
    )
    if (!isEvaluationResponse(response)) throw new ApiError('invalid-response', 201)
    return response
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('network')
  }
}
