import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

export const server = setupServer(
  http.get('/api/mi-cuenta', () => HttpResponse.json({
    nombres: 'Usuario', apellidos: 'Prueba', correo: 'personal@example.test',
    rol: 'ADMINISTRADOR', tipoDocumento: null, numeroDocumento: null, contrasenaInicial: false,
  })),
  http.get('/api/setup/status', () => HttpResponse.json({ required: false })),
  http.get('/api/ingresos/pase-diario/habilitado', () => HttpResponse.json(false)),
  http.get('/api/membresias', () => HttpResponse.json([])),
  http.get('/api/ingresos/pase-diario/socio/:idSocio', () => HttpResponse.json([])),
  http.get('/api/reportes/detalle', () => HttpResponse.json([])),
  http.get('/api/reportes/resumen', () => HttpResponse.json({
    ingresosConfirmados: 0, pagosConfirmados: 0, sociosNuevos: 0,
    membresiasActivas: 0, membresiasVencidas: 0, membresiasProximasAVencer: 0,
    ingresosSocios: 0, ingresosVisitantes: 0, personasUnicas: 0,
    personasUnicasSocios: 0, personasUnicasVisitantes: 0, responsables: [], pagosPorFecha: [], sociosPorFecha: [],
    ingresosPorFecha: [], membresiasPorPlan: [],
  })),
  http.get('/api/configuraciones/PRECIO_PASE_DIARIO', () => HttpResponse.json({
    idConfiguracion: 1, clave: 'PRECIO_PASE_DIARIO', valor: '12.50',
    descripcion: null, fechaActualizacion: '2026-11-10T00:00:00',
  })),
  http.get('/api/dashboard/resumen', () => HttpResponse.json({
    sociosActivos: 0, ingresosMes: 0, ingresosCobradosHoy: 0, ingresosHoy: 0,
    ingresosMembresiaHoy: 0, ingresosPaseDiarioHoy: 0, sociosNuevosMes: 0,
    ingresosSemana: [], actividadReciente: [],
    membresias: { activas: 0, porPagar: 0, vencidas: 0, canceladas: 0 },
  })),
  http.get('/api/nutricion/resumen', () => HttpResponse.json({
    sociosDisponibles: 0, evaluacionesEsteMes: 0, sociosEvaluadosEsteMes: 0,
    evaluacionesUltimosSieteDias: 0, evaluacionesRecientes: [],
  })),
  http.get('/api/asistencias-laborales/mis-asistencias', () => HttpResponse.json({ contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 })),
  http.get('/api/checklists/mios', () => HttpResponse.json([])),
  http.get('/api/reportes-incidencias/mios', () => HttpResponse.json([])),
  http.get('/api/socios/:id/evaluaciones-nutricionales', () => HttpResponse.json([])),
)
