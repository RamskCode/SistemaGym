package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.PaginaHistorialIngresosRespuesta;
import com.gimnasio.sistemagym.service.ReportesService;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Scoped read-only endpoint; does not expose the general administrative report API. */
@RestController
@RequestMapping("/api/ingresos/historial")
public class HistorialIngresosRecepcionController {
    private final ReportesService reportes;

    public HistorialIngresosRecepcionController(ReportesService reportes) {
        this.reportes = reportes;
    }

    @GetMapping
    public PaginaHistorialIngresosRespuesta historial(
            Authentication autenticacion,
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano,
            @RequestParam(defaultValue = "") String buscar,
            @RequestParam(defaultValue = "") String modalidad) {
        boolean recepcionista = autenticacion != null && autenticacion.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_RECEPCIONISTA".equals(authority.getAuthority()));
        if (!recepcionista) throw new AccessDeniedException("Solo recepción puede consultar este historial.");
        Page<com.gimnasio.sistemagym.dto.ReporteFilaRespuesta> resultado =
                reportes.historialIngresos(desde, hasta, pagina, tamano, buscar, modalidad);
        return new PaginaHistorialIngresosRespuesta(resultado.getContent(), resultado.getNumber(),
                resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }
}
