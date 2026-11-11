package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarConfiguracionRequest(

        @NotBlank(message = "El valor de la configuración es obligatorio.")
        @Size(max = 255, message = "El valor no puede superar los 255 caracteres.")
        String valor) {
}
