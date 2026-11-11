package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ElementoRevisionRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        String nombre,

        @NotNull(message = "El tipo de elemento es obligatorio.")
        TipoElementoRevision tipo,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres.")
        String descripcion,

        @Size(max = 120, message = "La ubicación no puede superar los 120 caracteres.")
        String ubicacion) {
}
