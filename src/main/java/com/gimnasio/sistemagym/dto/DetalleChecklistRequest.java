package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.PrioridadIncidencia;
import com.gimnasio.sistemagym.entity.ResultadoChecklist;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DetalleChecklistRequest(
        @NotNull(message = "El ID del elemento es obligatorio.")
        Long idElemento,

        @NotNull(message = "El resultado es obligatorio.")
        ResultadoChecklist resultado,

        @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres.")
        String observaciones,

        @Size(max = 4000, message = "La descripción de incidencia no puede superar los 4000 caracteres.")
        String descripcionIncidencia,

        PrioridadIncidencia prioridadIncidencia) {
}
