package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resumen no sensible del alta de membresía y su primer pago aplicado. */
public record RegistroMembresiaRespuesta(
        Long idMembresia,
        Long idSocio,
        Long idPlan,
        BigDecimal precioAcordado,
        LocalDate fechaInicio,
        LocalDate fechaVencimiento,
        LocalDate fechaLimitePago,
        String estado,
        Long idPago,
        BigDecimal montoPrimerPago,
        String metodoPago,
        Long idAplicacion) implements java.io.Serializable {
}
