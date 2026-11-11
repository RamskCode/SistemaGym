package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record RestablecerContrasenaRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}",
                message = "El enlace no es válido o ha caducado.") String token,
        @NotBlank @Size(min = 8, max = 72,
                message = "La contraseña debe tener al menos 8 caracteres y no superar el límite técnico.") String contrasena) { }
