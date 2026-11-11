package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarRolUsuarioRequest(
        @NotBlank(message = "El rol es obligatorio.")
        @Size(max = 30, message = "El rol no puede superar los 30 caracteres.")
        String rol) {
}
