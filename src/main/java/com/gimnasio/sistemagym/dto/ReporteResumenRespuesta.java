package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReporteResumenRespuesta(
        BigDecimal ingresosConfirmados,
        long pagosConfirmados,
        long sociosNuevos,
        long membresiasActivas,
        long membresiasVencidas,
        long membresiasProximasAVencer,
        long ingresosSocios,
        long ingresosVisitantes,
        long personasUnicas,
        long personasUnicasSocios,
        long personasUnicasVisitantes,
        List<ResponsableReporte> responsables,
        List<PuntoReporte> pagosPorFecha,
        List<PuntoReporte> sociosPorFecha,
        List<PuntoReporte> ingresosPorFecha,
        List<PuntoReporte> membresiasPorPlan) {
    public record PuntoReporte(String etiqueta, long cantidad, BigDecimal monto, Long referenciaId) {
        public PuntoReporte(String etiqueta, long cantidad, BigDecimal monto) {
            this(etiqueta, cantidad, monto, null);
        }
    }
    public record ResponsableReporte(Long id, String nombre) { }
}
