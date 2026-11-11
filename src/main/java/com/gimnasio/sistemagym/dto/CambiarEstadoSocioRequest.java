package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public final class CambiarEstadoSocioRequest {

    @NotBlank(message = "El motivo es obligatorio.")
    private final String motivo;

    @JsonCreator
    public CambiarEstadoSocioRequest(@JsonProperty("motivo") String motivo) {
        this.motivo = motivo;
    }

    @JsonAnySetter
    public void rechazarPropiedadAdicional(String nombre, Object valor) {
        throw new IllegalArgumentException(
                "El cuerpo solo puede contener el campo motivo.");
    }

    public String getMotivo() {
        return motivo;
    }
}
