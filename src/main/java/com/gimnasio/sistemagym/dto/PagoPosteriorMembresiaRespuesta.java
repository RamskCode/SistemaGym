package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Resumen del pago posterior y del saldo actualizado de la membresia. */
public record PagoPosteriorMembresiaRespuesta(
        Long idPago,
        Long idAplicacion,
        Long idMembresia,
        BigDecimal montoPago,
        BigDecimal montoAplicado,
        String metodoPago,
        String referencia,
        String observaciones,
        LocalDateTime fechaPago,
        BigDecimal saldoPendiente,
        String estadoMembresia) implements java.io.Serializable {
}
