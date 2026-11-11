package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ValidarRecuperacionRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}",
                message = "El enlace no es válido o ha caducado.") String token) { }
