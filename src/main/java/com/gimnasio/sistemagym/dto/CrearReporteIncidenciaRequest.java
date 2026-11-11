package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.PrioridadIncidencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrearReporteIncidenciaRequest(
        @NotNull(message = "El ID del elemento es obligatorio.")
        @Positive(message = "El ID del elemento debe ser mayor que cero.")
        Long idElemento,

        @NotBlank(message = "La descripción es obligatoria.")
        @Size(max = 4000, message = "La descripción no puede superar los 4000 caracteres.")
        String descripcion,

        @NotNull(message = "La prioridad es obligatoria.")
        PrioridadIncidencia prioridad) {
}
