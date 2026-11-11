package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.HistorialReporteIncidencia;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public record HistorialIncidenciaRespuesta(
        Long idHistorial,
        Long idUsuario,
        String correoUsuario,
        String estadoAnterior,
        String estadoNuevo,
        String observacion,
        OffsetDateTime fechaHora) {

    private static final ZoneId ZONA_OPERATIVA = ZoneId.of("America/Lima");

    public static HistorialIncidenciaRespuesta desde(HistorialReporteIncidencia evento) {
        return new HistorialIncidenciaRespuesta(
                evento.getIdHistorial(), evento.getUsuario().getIdUsuario(),
                evento.getUsuario().getCorreo(),
                evento.getEstadoAnterior() == null ? null : evento.getEstadoAnterior().name(),
                evento.getEstadoNuevo().name(), evento.getObservacion(),
                aHoraLima(evento.getFechaHora()));
    }

    static OffsetDateTime aHoraLima(LocalDateTime fechaUtc) {
        return fechaUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONA_OPERATIVA)
                .toOffsetDateTime();
    }
}
