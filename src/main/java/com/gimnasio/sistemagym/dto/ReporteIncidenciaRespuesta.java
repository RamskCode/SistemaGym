package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import java.time.OffsetDateTime;
import java.util.List;

public record ReporteIncidenciaRespuesta(
        Long idReporte,
        Long idEntrenador,
        String correoEntrenador,
        Long idElemento,
        String nombreElemento,
        Long idChecklist,
        Long idDetalleChecklist,
        String descripcion,
        String prioridad,
        String estado,
        OffsetDateTime fechaReporte,
        OffsetDateTime fechaActualizacion,
        OffsetDateTime fechaSolucion,
        List<HistorialIncidenciaRespuesta> historial) {

    public static ReporteIncidenciaRespuesta desde(
            ReporteIncidencia reporte, List<HistorialIncidenciaRespuesta> historial) {
        var detalle = reporte.getDetalleChecklist();
        return new ReporteIncidenciaRespuesta(
                reporte.getIdReporte(), reporte.getEntrenador().getIdUsuario(),
                reporte.getEntrenador().getCorreo(), reporte.getElemento().getIdElemento(),
                detalle == null ? reporte.getElemento().getNombre()
                        : detalle.getNombreElementoSnapshot(),
                detalle == null ? null : detalle.getChecklist().getIdChecklist(),
                detalle == null ? null : detalle.getIdDetalle(),
                reporte.getDescripcion(), reporte.getPrioridad().name(),
                reporte.getEstado().name(),
                HistorialIncidenciaRespuesta.aHoraLima(reporte.getFechaReporte()),
                HistorialIncidenciaRespuesta.aHoraLima(reporte.getFechaActualizacion()),
                reporte.getFechaSolucion() == null ? null
                        : HistorialIncidenciaRespuesta.aHoraLima(reporte.getFechaSolucion()),
                List.copyOf(historial));
    }
}
