package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.ChecklistSala;
import java.time.OffsetDateTime;
import java.util.List;

public record ChecklistSalaRespuesta(
        Long idChecklist,
        Long idEntrenador,
        String correoEntrenador,
        OffsetDateTime fechaHora,
        String observaciones,
        List<DetalleChecklistRespuesta> detalles) {

    public static ChecklistSalaRespuesta desde(
            ChecklistSala checklist, List<DetalleChecklistRespuesta> detalles) {
        return new ChecklistSalaRespuesta(
                checklist.getIdChecklist(), checklist.getEntrenador().getIdUsuario(),
                checklist.getEntrenador().getCorreo(),
                HistorialIncidenciaRespuesta.aHoraLima(checklist.getFechaHora()),
                checklist.getObservaciones(), List.copyOf(detalles));
    }
}
