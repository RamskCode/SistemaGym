package com.gimnasio.sistemagym.dto;

import java.util.List;

public record PaginaIngresoSocioRespuesta(
        List<IngresoSocioHistorialRespuesta> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) { }
