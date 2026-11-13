package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ReporteFilaRespuesta;
import com.gimnasio.sistemagym.dto.ReporteResumenRespuesta;
import com.gimnasio.sistemagym.dto.PlanReporteRespuesta;
import com.gimnasio.sistemagym.dto.DetalleIngresoClienteRespuesta;
import com.gimnasio.sistemagym.service.ReportesService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
public class ReportesController {
    private final ReportesService service;
    public ReportesController(ReportesService service) { this.service = service; }

    @GetMapping("/resumen")
    public ReporteResumenRespuesta resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long planId) {
        return service.resumen(desde, hasta, planId);
    }

    @GetMapping("/planes")
    public List<PlanReporteRespuesta> planes() { return service.planes(); }

    @GetMapping("/ingresos/{idIngreso}")
    public DetalleIngresoClienteRespuesta detalleIngreso(@org.springframework.web.bind.annotation.PathVariable Long idIngreso) {
        return service.detalleIngresoCliente(idIngreso);
    }

    @GetMapping("/detalle")
    public List<ReporteFilaRespuesta> detalle(
            @RequestParam String tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long planId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano,
            @RequestParam(defaultValue = "") String buscar,
            @RequestParam(defaultValue = "") String modalidad,
            @RequestParam(required = false) Long responsableId) {
        return service.detalle(tipo, desde, hasta, planId, pagina, tamano, buscar, modalidad, responsableId);
    }
}
