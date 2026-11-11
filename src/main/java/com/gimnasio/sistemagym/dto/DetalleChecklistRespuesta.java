package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.DetalleChecklist;
import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import java.time.OffsetDateTime;

public record DetalleChecklistRespuesta(
        Long idDetalle,
        Long idElemento,
        String nombreElemento,
        String tipoElemento,
        String ubicacionElemento,
        String resultado,
        String observaciones,
        OffsetDateTime fechaRevision,
        ReporteIncidenciaResumenRespuesta incidencia) {

    public static DetalleChecklistRespuesta desde(
            DetalleChecklist detalle, ReporteIncidencia reporte) {
        return new DetalleChecklistRespuesta(
                detalle.getIdDetalle(), detalle.getElemento().getIdElemento(),
                detalle.getNombreElementoSnapshot(), detalle.getTipoElementoSnapshot().name(),
                detalle.getUbicacionSnapshot(), detalle.getResultado().name(),
                detalle.getObservaciones(),
                HistorialIncidenciaRespuesta.aHoraLima(detalle.getFechaRevision()),
                reporte == null ? null : ReporteIncidenciaResumenRespuesta.desde(reporte));
    }
}
