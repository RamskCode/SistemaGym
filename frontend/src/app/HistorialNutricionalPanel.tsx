import { useEffect, useState } from 'react'
import { ArrowLeft, Eye } from 'lucide-react'
import { ApiError } from '../api/http'
import {
  consultarHistorialNutricional,
  type EvaluacionNutricional,
} from '../api/evaluacionesNutricionales'
import type { SocioResumenNutricion } from '../api/socios'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Card } from '../components/Card'

type Props = {
  socio: SocioResumenNutricion
  envioIncierto: boolean
  onVolver: () => void
}

function dateInLima(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima',
  }).format(date)
}

function messageFor(error: unknown) {
  if (error instanceof ApiError) return error.message
  return 'No fue posible cargar el historial. Inténtalo nuevamente.'
}

function Detail({ evaluation, onBack }: { evaluation: EvaluacionNutricional; onBack: () => void }) {
  return (
    <Card className="nutricion-history-detail" title="Detalle de evaluación">
      <dl className="nutricion-history-detail__fields">
        <div><dt>Fecha (America/Lima)</dt><dd>{dateInLima(evaluation.fechaEvaluacion)}</dd></div>
        <div><dt>Autor (ID disponible)</dt><dd>Nutricionista #{evaluation.idNutricionista}</dd></div>
        <div><dt>Peso</dt><dd>{evaluation.pesoKg} kg</dd></div>
        <div><dt>Talla</dt><dd>{evaluation.tallaM} m</dd></div>
        <div><dt>Porcentaje de grasa</dt><dd>{evaluation.porcentajeGrasa}%</dd></div>
        <div><dt>Masa muscular</dt><dd>{evaluation.masaMuscularKg} kg</dd></div>
        <div><dt>Hábitos alimenticios</dt><dd>{evaluation.habitosAlimenticios}</dd></div>
        <div><dt>Objetivos nutricionales</dt><dd>{evaluation.objetivosNutricionales}</dd></div>
        <div><dt>Recomendaciones nutricionales</dt><dd>{evaluation.recomendacionesNutricionales}</dd></div>
        {evaluation.observacionesSeguimiento && (
          <div><dt>Observaciones de seguimiento</dt><dd>{evaluation.observacionesSeguimiento}</dd></div>
        )}
      </dl>
      <Button onClick={onBack} variant="secondary"><ArrowLeft aria-hidden="true" size={16} /> Volver al historial</Button>
    </Card>
  )
}

export function HistorialNutricionalPanel({ socio, envioIncierto, onVolver }: Props) {
  const auth = useAuth()
  const [evaluaciones, setEvaluaciones] = useState<EvaluacionNutricional[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [reintento, setReintento] = useState(0)
  const [detalle, setDetalle] = useState<EvaluacionNutricional | null>(null)

  useEffect(() => {
    let mounted = true
    setCargando(true)
    setError('')
    void consultarHistorialNutricional(auth, socio.idSocio)
      .then((result) => { if (mounted) setEvaluaciones([...result].sort((a, b) => Date.parse(b.fechaEvaluacion) - Date.parse(a.fechaEvaluacion))) })
      .catch((reason: unknown) => {
        if (mounted) {
          setEvaluaciones([])
          setError(messageFor(reason))
        }
      })
      .finally(() => { if (mounted) setCargando(false) })
    return () => { mounted = false }
  }, [auth, socio.idSocio, reintento])

  if (detalle) return <Detail evaluation={detalle} onBack={() => setDetalle(null)} />

  return (
    <Card className="nutricion-history" title={`Historial · ${socio.nombres} ${socio.apellidos}`}>
      <p className="nutricion-history__member">Código {socio.codigoSocio} · Estado {socio.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'}</p>
      {envioIncierto && (
        <p className="nutricion-history__uncertain" role="status">
          Hay un envío anterior cuyo resultado no se confirmó. El historial no permite asociar inequívocamente una evaluación con ese envío. Verifica los registros manualmente; el bloqueo para crear otra evaluación se mantiene y no se quitará automáticamente.
        </p>
      )}
      {cargando && <p aria-live="polite" className="nutricion-message">Cargando historial…</p>}
      {!cargando && error && (
        <div className="nutricion-error" role="alert">
          <p>{error}</p>
          <Button onClick={() => setReintento((value) => value + 1)} variant="secondary">Reintentar</Button>
        </div>
      )}
      {!cargando && !error && evaluaciones.length === 0 && (
        <p className="nutricion-message">Este socio todavía no tiene evaluaciones nutricionales.</p>
      )}
      {!cargando && !error && evaluaciones.length > 0 && (
        <ol aria-label="Evaluaciones nutricionales, más recientes primero" className="nutricion-history-list">
          {evaluaciones.map((evaluation) => (
            <li className="nutricion-history-row" key={evaluation.idEvaluacion}>
              <div className="nutricion-history-row__date">
                <strong>{dateInLima(evaluation.fechaEvaluacion)}</strong>
                <span>Nutricionista #{evaluation.idNutricionista}</span>
              </div>
              <dl className="nutricion-history-row__metrics">
                <div><dt>Peso</dt><dd>{evaluation.pesoKg} kg</dd></div>
                <div><dt>Talla</dt><dd>{evaluation.tallaM} m</dd></div>
                <div><dt>Grasa</dt><dd>{evaluation.porcentajeGrasa}%</dd></div>
                <div><dt>Masa muscular</dt><dd>{evaluation.masaMuscularKg} kg</dd></div>
              </dl>
              <Button onClick={() => setDetalle(evaluation)} variant="secondary">
                <Eye aria-hidden="true" size={16} /> Ver detalle
              </Button>
            </li>
          ))}
        </ol>
      )}
      <div className="nutri-form-actions">
        <Button onClick={onVolver} variant="ghost"><ArrowLeft aria-hidden="true" size={16} /> Volver a ficha</Button>
      </div>
    </Card>
  )
}
