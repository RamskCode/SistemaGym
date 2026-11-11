package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.EstadoIncidencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambiarEstadoIncidenciaRequest(
        @NotNull(message = "El nuevo estado es obligatorio.")
        EstadoIncidencia estado,

        @NotBlank(message = "La observación es obligatoria.")
        @Size(max = 2000, message = "La observación no puede superar los 2000 caracteres.")
        String observacion) {
}
