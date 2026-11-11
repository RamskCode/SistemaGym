package com.gimnasio.sistemagym.dto;

import java.util.List;

public record AsistenciaLaboralPaginaRespuesta(
        List<AsistenciaLaboralRespuesta> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {
}
