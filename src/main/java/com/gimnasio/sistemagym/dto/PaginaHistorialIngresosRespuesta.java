package com.gimnasio.sistemagym.dto;

import java.util.List;

/** Read-only, paginated customer entry history for reception. */
public record PaginaHistorialIngresosRespuesta(
        List<ReporteFilaRespuesta> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {
}
