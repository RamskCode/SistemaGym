package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;

import java.time.LocalDateTime;

/** Scalar projection for immutable correction/annulment audit records. */
public interface AuditoriaAsistenciaHistorialConsulta {
    Long getIdAuditoria();
    TipoOperacionAuditoriaAsistenciaLaboral getTipoOperacion();
    String getMotivo();
    LocalDateTime getFechaHoraUtc();
    Long getIdActor();
    String getNombresActor();
    String getApellidosActor();
    String getCorreoActor();
    EstadoAsistenciaLaboral getEstadoAnterior();
    EstadoAsistenciaLaboral getEstadoNuevo();
    LocalDateTime getFechaEntradaAnteriorUtc();
    LocalDateTime getFechaSalidaAnteriorUtc();
    LocalDateTime getFechaEntradaNuevaUtc();
    LocalDateTime getFechaSalidaNuevaUtc();
}
