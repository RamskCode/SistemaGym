package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralConsulta;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Response DTO: instants are serialized as ISO-8601 UTC values ending in Z. */
public record AsistenciaLaboralRespuesta(
        Long idAsistencia,
        Long idEmpleado,
        Instant fechaEntradaUtc,
        Instant fechaSalidaUtc,
        LocalDate fechaLaboral,
        EstadoAsistenciaLaboral estado) {

    public static AsistenciaLaboralRespuesta desde(
            AsistenciaLaboral asistencia, Long idEmpleado) {
        return new AsistenciaLaboralRespuesta(
                asistencia.getIdAsistencia(),
                idEmpleado,
                asistencia.getFechaEntradaUtc().toInstant(ZoneOffset.UTC),
                asistencia.getFechaSalidaUtc() == null
                        ? null
                        : asistencia.getFechaSalidaUtc().toInstant(ZoneOffset.UTC),
                asistencia.getFechaLaboral(), asistencia.getEstado());
    }

    public static AsistenciaLaboralRespuesta desde(AsistenciaLaboralConsulta asistencia) {
        return new AsistenciaLaboralRespuesta(
                asistencia.getIdAsistencia(),
                asistencia.getIdEmpleado(),
                asistencia.getFechaEntradaUtc().toInstant(ZoneOffset.UTC),
                asistencia.getFechaSalidaUtc() == null
                        ? null
                        : asistencia.getFechaSalidaUtc().toInstant(ZoneOffset.UTC),
                asistencia.getFechaLaboral(), asistencia.getEstado());
    }
}
