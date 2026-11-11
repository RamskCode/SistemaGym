package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ActualizarPlanRequest(
        @NotBlank(message = "El nombre del plan es obligatorio.")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres.")
        String nombre,

        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres.")
        String descripcion,

        @NotNull(message = "El precio es obligatorio.")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero.")
        BigDecimal precio,

        @NotNull(message = "La duración es obligatoria.")
        @Positive(message = "La duración debe ser mayor que cero.")
        Integer duracionDias) {
}
