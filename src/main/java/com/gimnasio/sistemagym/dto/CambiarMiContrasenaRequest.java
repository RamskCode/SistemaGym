package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarMiContrasenaRequest(
        @NotBlank(message = "La contraseña actual es obligatoria.")
        @Size(max = 255, message = "La contraseña actual no es válida.")
        String contrasenaActual,
        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 8, max = 72,
                message = "La contraseña nueva debe tener al menos 8 caracteres y no superar el límite técnico.")
        String contrasenaNueva) {
}
