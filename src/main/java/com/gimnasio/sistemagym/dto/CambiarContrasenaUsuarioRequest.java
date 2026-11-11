package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaUsuarioRequest(
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72,
                message = "La contraseña debe tener al menos 8 caracteres y no superar el límite técnico.")
        String contrasena) {
}
