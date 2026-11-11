package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Registro de venta o acceso asociado a un pase diario de una persona. */
public record PaseDiarioHistorialRespuesta(
        Long idIngreso,
        LocalDateTime fechaHora,
        boolean cobradoEnEsteAcceso,
        Long idPaseDiario,
        Long idPago,
        BigDecimal montoPase,
        String metodoPago) {

    public static PaseDiarioHistorialRespuesta desde(Ingreso ingreso) {
        Ingreso venta = ingreso.getPaseOrigen() != null
                ? ingreso.getPaseOrigen()
                : ingreso.getPago() != null ? ingreso : null;
        Pago pago = venta == null ? null : venta.getPago();
        return new PaseDiarioHistorialRespuesta(
                ingreso.getIdIngreso(),
                ingreso.getFechaHora(),
                ingreso.getPago() != null,
                venta == null ? null : venta.getIdIngreso(),
                pago == null ? null : pago.getIdPago(),
                pago == null ? null : pago.getMonto(),
                pago == null ? null : pago.getMetodoPago());
    }
}
