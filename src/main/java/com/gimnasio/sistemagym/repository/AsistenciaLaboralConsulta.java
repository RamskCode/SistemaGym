package com.gimnasio.sistemagym.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;

/** Flat projection for paginated attendance queries; avoids loading JPA associations. */
public interface AsistenciaLaboralConsulta {
    Long getIdAsistencia();
    Long getIdEmpleado();
    String getNombresEmpleado();
    String getApellidosEmpleado();
    String getRolEmpleado();
    LocalDateTime getFechaEntradaUtc();
    LocalDateTime getFechaSalidaUtc();
    LocalDate getFechaLaboral();
    EstadoAsistenciaLaboral getEstado();
}
