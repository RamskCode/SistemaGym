package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.CrearReporteIncidenciaRequest;
import com.gimnasio.sistemagym.dto.CambiarEstadoIncidenciaRequest;
import com.gimnasio.sistemagym.dto.ReporteIncidenciaRespuesta;
import com.gimnasio.sistemagym.service.ReporteIncidenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reportes-incidencias")
public class ReporteIncidenciaController {

    private final ReporteIncidenciaService service;

    public ReporteIncidenciaController(ReporteIncidenciaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReporteIncidenciaRespuesta> crearDirecto(
            @Valid @RequestBody CrearReporteIncidenciaRequest request,
            Authentication autenticacion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crearDirecto(request, autenticacion.getName()));
    }

    @GetMapping("/mios")
    public List<ReporteIncidenciaRespuesta> listarMios(Authentication autenticacion) {
        return service.listarMios(autenticacion.getName());
    }

    @GetMapping
    public List<ReporteIncidenciaRespuesta> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public ReporteIncidenciaRespuesta buscar(
            @PathVariable Long id, Authentication autenticacion) {
        boolean administrador = autenticacion.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority()
                        .equals("ROLE_ADMINISTRADOR"));
        return service.buscar(id, autenticacion.getName(), administrador);
    }

    @PatchMapping("/{id}/estado")
    public ReporteIncidenciaRespuesta cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoIncidenciaRequest request,
            Authentication autenticacion) {
        return service.cambiarEstado(id, request, autenticacion.getName());
    }
}
