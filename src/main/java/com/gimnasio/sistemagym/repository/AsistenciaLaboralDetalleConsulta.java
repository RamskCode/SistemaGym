package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Scalar projection for attendance detail; avoids loading Usuario entities. */
public interface AsistenciaLaboralDetalleConsulta {
    Long getIdAsistencia();
    Long getIdEmpleado();
    String getNombresEmpleado();
    String getApellidosEmpleado();
    String getCorreoEmpleado();
    LocalDateTime getFechaEntradaUtc();
    LocalDateTime getFechaSalidaUtc();
    LocalDate getFechaLaboral();
    EstadoAsistenciaLaboral getEstado();
    Long getIdRegistradorEntrada();
    String getNombresRegistradorEntrada();
    String getApellidosRegistradorEntrada();
    String getCorreoRegistradorEntrada();
    Long getIdRegistradorSalida();
    String getNombresRegistradorSalida();
    String getApellidosRegistradorSalida();
    String getCorreoRegistradorSalida();
}
