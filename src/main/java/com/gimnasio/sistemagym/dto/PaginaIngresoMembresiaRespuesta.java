package com.gimnasio.sistemagym.dto;

import java.util.List;

public record PaginaIngresoMembresiaRespuesta(
        List<IngresoMembresiaEstadoRespuesta> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) { }
