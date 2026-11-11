package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivarCuentaRequest(@NotBlank String token,
        @NotBlank @Size(min = 8, max = 72,
                message = "La contraseña debe tener al menos 8 caracteres.") String contrasena) { }
