package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IngresoRespuesta(
        Long idIngreso,
        String tipoIngreso,
        Long idSocio,
        Long idVisitante,
        String tipoDocumento,
        String numeroDocumento,
        LocalDateTime fechaHora,
        boolean paseDiarioCobrado,
        BigDecimal montoCobrado,
        Long idPago,
        String resultado) {
}
