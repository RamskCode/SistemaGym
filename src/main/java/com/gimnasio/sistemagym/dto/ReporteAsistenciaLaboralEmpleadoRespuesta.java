package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Aggregated attendance metrics for one employee and an inclusive work-date range. */
public record ReporteAsistenciaLaboralEmpleadoRespuesta(
        Long idEmpleado,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        long cantidadActivas,
        long cantidadAnuladas,
        long jornadasCerradas,
        long jornadasAbiertas,
        BigDecimal totalHorasRegistradas) {
}
