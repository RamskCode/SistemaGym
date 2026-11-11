package com.gimnasio.sistemagym.dto;

/** Minimal employee identity exposed for labor-attendance selection. */
public record PersonalSeleccionableRespuesta(
        Long idEmpleado,
        String nombres,
        String apellidos,
        String rol) {
}
