package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public first-install form; role is assigned server-side and cannot be selected by the caller. */
public record InitialAdminRequest(
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotBlank @Email @Size(max = 150) String correo,
        @NotBlank
        @Size(min = 8, max = 72,
                message = "La contraseña debe tener al menos 8 caracteres y no superar el límite técnico.")
        String contrasena) {
}
