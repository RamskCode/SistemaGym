package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class RegistrarAsistenciaLaboralRequest {

    @NotNull(message = "El ID del empleado es obligatorio.")
    @Positive(message = "El ID del empleado debe ser positivo.")
    private Long idEmpleado;

    public Long getIdEmpleado() {
        return idEmpleado;
    }

    public void setIdEmpleado(Long idEmpleado) {
        this.idEmpleado = idEmpleado;
    }

    @JsonAnySetter
    public void rechazarPropiedadDesconocida(String propiedad, Object valor) {
        throw new IllegalArgumentException(
                "La propiedad '" + propiedad + "' no está permitida en esta solicitud.");
    }
}
