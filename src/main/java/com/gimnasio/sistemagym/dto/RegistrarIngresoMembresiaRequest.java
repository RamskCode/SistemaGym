package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarIngresoMembresiaRequest(
        @NotNull(message = "Selecciona un socio.") Long idSocio,
        @NotBlank(message = "La clave de operación es obligatoria.")
        @Size(max = 36, message = "La clave de operación no es válida.") String claveIdempotencia) { }
