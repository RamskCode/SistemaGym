package com.gimnasio.sistemagym.repository;

/** Read-only projection; sensitive user fields are not loaded for this query. */
public interface PersonalSeleccionableConsulta {

    Long getIdEmpleado();

    String getNombres();

    String getApellidos();

    String getRol();
}
