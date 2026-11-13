package com.gimnasio.sistemagym.repository;

/** Minimal identity and current role for users with at least one attendance record. */
public interface EmpleadoConHistorialConsulta {
    Long getIdEmpleado();
    String getNombres();
    String getApellidos();
    String getRol();
}
