package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.IngresoRespuesta;
import com.gimnasio.sistemagym.dto.IngresoMembresiaEstadoRespuesta;
import com.gimnasio.sistemagym.dto.PaginaIngresoSocioRespuesta;
import com.gimnasio.sistemagym.dto.PaginaIngresoMembresiaRespuesta;
import com.gimnasio.sistemagym.dto.PaseDiarioHistorialRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarIngresoMembresiaRequest;
import com.gimnasio.sistemagym.dto.RegistrarIngresoRequest;
import com.gimnasio.sistemagym.service.IngresoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/ingresos")
public class IngresoController {

    private final IngresoService ingresoService;

    public IngresoController(IngresoService ingresoService) {
        this.ingresoService = ingresoService;
    }

    @PostMapping
    public ResponseEntity<IngresoRespuesta> registrar(
            @Valid @RequestBody RegistrarIngresoRequest solicitud,
            Authentication autenticacion) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ingresoService.registrar(
                        solicitud, autenticacion.getName()));
    }

    @GetMapping("/socios/{idSocio}/evaluacion")
    public IngresoMembresiaEstadoRespuesta evaluarIngresoMembresia(@PathVariable Long idSocio) {
        return ingresoService.evaluarIngresoMembresia(idSocio);
    }

    @GetMapping("/socios/habilitados")
    public PaginaIngresoMembresiaRespuesta listarHabilitadosParaIngreso(
            @RequestParam(defaultValue = "") @Size(max = 100) String nombre,
            @RequestParam(defaultValue = "") @Pattern(regexp = "DNI|CE|", message = "El tipo de documento no es válido.") String tipoDocumento,
            @RequestParam(defaultValue = "") @Size(max = 12)
            @Pattern(regexp = "[0-9]*", message = "El documento solo puede contener dígitos.") String numeroDocumento,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "nombres,asc") String sort) {
        if ("DNI".equals(tipoDocumento) && !numeroDocumento.matches("[0-9]*")) {
            throw new IllegalArgumentException("El filtro DNI solo puede contener dígitos.");
        }
        String[] sortParts = sort.split(",", -1);
        String property = switch (sortParts[0]) {
            case "nombres" -> "nombres";
            case "apellidos" -> "apellidos";
            default -> throw new IllegalArgumentException("El orden solicitado no es válido.");
        };
        Sort.Direction direction = sortParts.length == 2
                ? Sort.Direction.fromString(sortParts[1]) : Sort.Direction.ASC;
        return ingresoService.listarHabilitadosParaIngreso(nombre, tipoDocumento, numeroDocumento, page, size,
                Sort.by(direction, property).and(Sort.by(direction, "idSocio")));
    }

    @GetMapping("/socios/{idSocio}/historial")
    public PaginaIngresoSocioRespuesta historialSocio(
            @PathVariable Long idSocio,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ingresoService.listarHistorialSocio(idSocio, page, size);
    }

    @PostMapping("/membresia")
    public ResponseEntity<IngresoRespuesta> registrarIngresoMembresia(
            @Valid @RequestBody RegistrarIngresoMembresiaRequest solicitud,
            Authentication autenticacion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ingresoService.registrarIngresoMembresia(
                solicitud.idSocio(), solicitud.claveIdempotencia(), autenticacion.getName()));
    }

    @GetMapping("/pase-diario/habilitado")
    public ResponseEntity<Boolean> paseDiarioHabilitado() {
        return ResponseEntity.ok(ingresoService.isEsquemaPaseDiarioV2Habilitado());
    }

    @GetMapping("/pase-diario/socio/{idSocio}")
    public List<PaseDiarioHistorialRespuesta> listarHistorialPaseDiario(
            @PathVariable Long idSocio) {
        return ingresoService.listarPasesDiariosPorSocio(idSocio);
    }
}
