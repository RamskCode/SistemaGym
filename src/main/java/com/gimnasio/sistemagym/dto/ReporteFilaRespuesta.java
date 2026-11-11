package com.gimnasio.sistemagym.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReporteFilaRespuesta(
        Long id,
        String tipo,
        String persona,
        String detalle,
        String estado,
        BigDecimal monto,
        LocalDateTime fecha,
        String documentoProtegido,
        String modalidad,
        String operacion,
        String responsable) {

    /** Keeps the established report response constructor source-compatible. */
    public ReporteFilaRespuesta(Long id, String tipo, String persona, String detalle,
            String estado, BigDecimal monto, LocalDateTime fecha) {
        this(id, tipo, persona, detalle, estado, monto, fecha, null, null, null, null);
    }
}
