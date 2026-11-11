package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnularAsistenciaLaboralRequest {

    @NotBlank(message = "El motivo es obligatorio.")
    private String motivo;

    @AssertTrue(message = "El motivo no puede superar los 500 caracteres.")
    public boolean isMotivoDentroDelLimite() {
        return motivo == null || motivo.codePointCount(0, motivo.length()) <= 500;
    }

    @JsonAnySetter
    public void rechazarPropiedadDesconocida(String propiedad, Object valor) {
        throw new IllegalArgumentException(
                "La propiedad '" + propiedad + "' no está permitida en esta solicitud.");
    }
}
