package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;

/** Read-only modality-specific detail for an administrative access record. */
public record DetalleIngresoClienteRespuesta(
        Long idIngreso,
        String modalidad,
        String planNombre,
        BigDecimal importePagado,
        String mensajeSinDato) { }
