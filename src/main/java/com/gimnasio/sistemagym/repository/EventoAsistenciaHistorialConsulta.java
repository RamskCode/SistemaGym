package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.TipoEventoAsistenciaLaboral;

import java.time.LocalDateTime;

/** Scalar projection for an immutable original attendance event. */
public interface EventoAsistenciaHistorialConsulta {
    Long getIdEvento();
    TipoEventoAsistenciaLaboral getTipoEvento();
    LocalDateTime getFechaHoraUtc();
    Long getIdRegistrador();
    String getNombresRegistrador();
    String getApellidosRegistrador();
    String getCorreoRegistrador();
}
