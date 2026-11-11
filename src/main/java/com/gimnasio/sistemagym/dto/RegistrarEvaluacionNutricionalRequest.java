package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegistrarEvaluacionNutricionalRequest(
        @NotNull(message = "El peso es obligatorio.")
        @DecimalMin(value = "0.01", message = "El peso debe ser mayor que cero.")
        @Digits(integer = 4, fraction = 2, message = "El peso debe tener como máximo 2 decimales.")
        BigDecimal pesoKg,

        @NotNull(message = "La talla es obligatoria.")
        @DecimalMin(value = "0.01", message = "La talla debe ser mayor que cero.")
        @Digits(integer = 2, fraction = 2, message = "La talla debe tener como máximo 2 decimales.")
        BigDecimal tallaM,

        @NotNull(message = "El porcentaje de grasa es obligatorio.")
        @DecimalMin(value = "0.00", message = "El porcentaje de grasa no puede ser negativo.")
        @DecimalMax(value = "100.00", message = "El porcentaje de grasa no puede superar 100.")
        @Digits(integer = 3, fraction = 2,
                message = "El porcentaje de grasa debe tener como máximo 2 decimales.")
        BigDecimal porcentajeGrasa,

        @NotNull(message = "La masa muscular es obligatoria.")
        @DecimalMin(value = "0.01", message = "La masa muscular debe ser mayor que cero.")
        @Digits(integer = 4, fraction = 2,
                message = "La masa muscular debe tener como máximo 2 decimales.")
        BigDecimal masaMuscularKg,

        @NotBlank(message = "Los hábitos alimenticios son obligatorios.")
        String habitosAlimenticios,

        @NotBlank(message = "Los objetivos nutricionales son obligatorios.")
        String objetivosNutricionales,

        @NotBlank(message = "Las recomendaciones nutricionales son obligatorias.")
        String recomendacionesNutricionales,

        String observacionesSeguimiento) {
}
