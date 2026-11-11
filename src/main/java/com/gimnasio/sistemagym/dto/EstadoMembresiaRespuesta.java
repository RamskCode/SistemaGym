package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Estado completo de una membresía para consulta: distingue una membresía
 * pagada, una pendiente dentro del plazo, una cancelada por deuda y una
 * vencida por finalización de su vigencia.
 */
public record EstadoMembresiaRespuesta(
        Long idMembresia,
        String estado,
        String situacion,
        BigDecimal precioAcordado,
        BigDecimal saldoPendiente,
        boolean pagada,
        LocalDate fechaInicio,
        LocalDate fechaVencimiento,
        LocalDate fechaLimitePago,
        String motivoCancelacion,
        LocalDateTime fechaCreacion) {
}
