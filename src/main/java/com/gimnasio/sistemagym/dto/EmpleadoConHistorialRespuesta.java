package com.gimnasio.sistemagym.dto;

/** Employee directory limited to identities present in labor-attendance history. */
public record EmpleadoConHistorialRespuesta(
        Long idEmpleado,
        String nombres,
        String apellidos,
        String rol) {
}
