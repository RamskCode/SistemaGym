package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DashboardResumenRespuesta(
        long sociosActivos,
        BigDecimal ingresosMes,
        BigDecimal ingresosCobradosHoy,
        long ingresosHoy,
        long ingresosMembresiaHoy,
        long ingresosPaseDiarioHoy,
        long sociosNuevosMes,
        List<IngresoDiario> ingresosSemana,
        List<ActividadReciente> actividadReciente,
        MembresiasResumen membresias) {

    public record IngresoDiario(LocalDate fecha, long cantidad) { }

    public record ActividadReciente(Long idIngreso, String nombre, String tipo,
            OffsetDateTime fechaHora) { }

    public record MembresiasResumen(long activas, long porPagar, long vencidas, long canceladas) { }
}
