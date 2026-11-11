package com.gimnasio.sistemagym.dto;

import java.time.LocalDate;

public record IngresoMembresiaEstadoRespuesta(
        Long idSocio,
        String codigoSocio,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        String estadoSocio,
        boolean autorizado,
        String motivo,
        Membresia membresia) {

    public record Membresia(
            Long idMembresia,
            String plan,
            String estado,
            LocalDate fechaInicio,
            LocalDate fechaVencimiento) { }
}
