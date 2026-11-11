package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoEventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read-only, privacy-limited detail and provenance for a single attendance. */
public record AsistenciaLaboralHistorialRespuesta(
        Long idAsistencia,
        Persona empleado,
        Instant fechaEntradaUtc,
        Instant fechaSalidaUtc,
        LocalDate fechaLaboral,
        EstadoAsistenciaLaboral estado,
        Persona registradorEntrada,
        Persona registradorSalida,
        List<Evento> eventos,
        List<Auditoria> auditorias) {

    public record Persona(Long idUsuario, String nombres, String apellidos, String correo) { }

    public record Evento(
            Long idEvento,
            TipoEventoAsistenciaLaboral tipoEvento,
            Instant fechaHoraUtc,
            Persona registrador) { }

    public record Auditoria(
            Long idAuditoria,
            TipoOperacionAuditoriaAsistenciaLaboral tipoOperacion,
            String motivo,
            Instant fechaHoraUtc,
            Persona actor,
            EstadoAsistenciaLaboral estadoAnterior,
            EstadoAsistenciaLaboral estadoNuevo,
            Instant fechaEntradaAnteriorUtc,
            Instant fechaSalidaAnteriorUtc,
            Instant fechaEntradaNuevaUtc,
            Instant fechaSalidaNuevaUtc) { }
}
