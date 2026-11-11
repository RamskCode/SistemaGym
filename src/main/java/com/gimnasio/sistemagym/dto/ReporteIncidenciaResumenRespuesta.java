package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import java.time.OffsetDateTime;

public record ReporteIncidenciaResumenRespuesta(
        Long idReporte,
        String descripcion,
        String prioridad,
        String estado,
        OffsetDateTime fechaReporte) {

    public static ReporteIncidenciaResumenRespuesta desde(ReporteIncidencia reporte) {
        return new ReporteIncidenciaResumenRespuesta(
                reporte.getIdReporte(), reporte.getDescripcion(),
                reporte.getPrioridad().name(), reporte.getEstado().name(),
                HistorialIncidenciaRespuesta.aHoraLima(reporte.getFechaReporte()));
    }
}
