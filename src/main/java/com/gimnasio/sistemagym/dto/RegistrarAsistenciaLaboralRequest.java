package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrarAsistenciaLaboralRequest {

    @NotNull(message = "El ID del empleado es obligatorio.")
    @Positive(message = "El ID del empleado debe ser positivo.")
    private Long idEmpleado;

    @JsonAnySetter
    public void rechazarPropiedadDesconocida(String propiedad, Object valor) {
        throw new IllegalArgumentException(
                "La propiedad '" + propiedad + "' no está permitida en esta solicitud.");
    }
}
