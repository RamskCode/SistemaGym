package com.gimnasio.sistemagym.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearChecklistRequest(
        @NotEmpty(message = "El checklist debe incluir al menos un elemento.")
        @Size(max = 100, message = "El checklist no puede superar los 100 elementos.")
        List<@Valid DetalleChecklistRequest> detalles,

        @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres.")
        String observaciones) {
}
