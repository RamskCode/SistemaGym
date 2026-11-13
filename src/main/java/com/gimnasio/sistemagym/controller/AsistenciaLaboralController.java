package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.AsistenciaLaboralRespuesta;
import com.gimnasio.sistemagym.dto.AsistenciaLaboralPaginaRespuesta;
import com.gimnasio.sistemagym.dto.AsistenciaLaboralHistorialRespuesta;
import com.gimnasio.sistemagym.dto.ReporteAsistenciaLaboralEmpleadoRespuesta;
import com.gimnasio.sistemagym.dto.AnularAsistenciaLaboralRequest;
import com.gimnasio.sistemagym.dto.CorreccionAsistenciaLaboralRequest;
import com.gimnasio.sistemagym.dto.RegistrarAsistenciaLaboralRequest;
import com.gimnasio.sistemagym.dto.PersonalSeleccionableRespuesta;
import com.gimnasio.sistemagym.dto.EmpleadoConHistorialRespuesta;
import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.service.AsistenciaLaboralService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/asistencias-laborales")
public class AsistenciaLaboralController {

    private final AsistenciaLaboralService asistenciaLaboralService;

    public AsistenciaLaboralController(AsistenciaLaboralService asistenciaLaboralService) {
        this.asistenciaLaboralService = asistenciaLaboralService;
    }

    @GetMapping("/personal-seleccionable")
    public List<PersonalSeleccionableRespuesta> listarPersonalSeleccionable() {
        return asistenciaLaboralService.listarPersonalSeleccionable();
    }

    @GetMapping("/empleados-con-historial")
    public List<EmpleadoConHistorialRespuesta> listarEmpleadosConHistorial(
            Authentication autenticacion) {
        return asistenciaLaboralService.listarEmpleadosConHistorial(autenticacion.getName());
    }

    @PostMapping("/mi-entrada")
    public ResponseEntity<AsistenciaLaboralRespuesta> registrarMiEntrada(
            @RequestBody(required = false) String cuerpo,
            Authentication autenticacion) {
        rechazarCuerpoMarcacionPropia(cuerpo);
        AsistenciaLaboral asistencia = asistenciaLaboralService
                .registrarMiEntradaAutenticada(autenticacion.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AsistenciaLaboralRespuesta.desde(
                        asistencia, asistencia.getUsuario().getIdUsuario()));
    }

    @PostMapping("/mi-salida")
    public ResponseEntity<AsistenciaLaboralRespuesta> registrarMiSalida(
            @RequestBody(required = false) String cuerpo,
            Authentication autenticacion) {
        rechazarCuerpoMarcacionPropia(cuerpo);
        AsistenciaLaboral asistencia = asistenciaLaboralService
                .registrarMiSalidaAutenticada(autenticacion.getName());
        return ResponseEntity.ok(AsistenciaLaboralRespuesta.desde(
                asistencia, asistencia.getUsuario().getIdUsuario()));
    }

    private void rechazarCuerpoMarcacionPropia(String cuerpo) {
        if (cuerpo != null && !cuerpo.isBlank()) {
            throw new IllegalArgumentException(
                    "La marcación propia no acepta datos en el cuerpo de la solicitud.");
        }
    }

    @PostMapping("/entrada")
    public ResponseEntity<AsistenciaLaboralRespuesta> registrarEntrada(
            @Valid @RequestBody RegistrarAsistenciaLaboralRequest solicitud,
            Authentication autenticacion) {
        AsistenciaLaboral asistencia = asistenciaLaboralService.registrarEntradaAutenticada(
                solicitud.getIdEmpleado(), autenticacion.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AsistenciaLaboralRespuesta.desde(
                        asistencia, solicitud.getIdEmpleado()));
    }

    @PostMapping("/salida")
    public ResponseEntity<AsistenciaLaboralRespuesta> registrarSalida(
            @Valid @RequestBody RegistrarAsistenciaLaboralRequest solicitud,
            Authentication autenticacion) {
        AsistenciaLaboral asistencia = asistenciaLaboralService.registrarSalidaAutenticada(
                solicitud.getIdEmpleado(), autenticacion.getName());
        return ResponseEntity.ok(AsistenciaLaboralRespuesta.desde(
                asistencia, solicitud.getIdEmpleado()));
    }

    @PatchMapping("/{idAsistencia}/correccion")
    public ResponseEntity<AsistenciaLaboralRespuesta> corregirAsistencia(
            @PathVariable Long idAsistencia,
            @Valid @RequestBody CorreccionAsistenciaLaboralRequest solicitud,
            Authentication autenticacion) {
        AsistenciaLaboral asistencia = asistenciaLaboralService.corregirAsistencia(
                idAsistencia, solicitud.getFechaEntradaUtc(), solicitud.getFechaSalidaUtc(),
                solicitud.getMotivo(), autenticacion.getName());
        return ResponseEntity.ok(AsistenciaLaboralRespuesta.desde(
                asistencia, asistencia.getUsuario().getIdUsuario()));
    }

    @PatchMapping("/{idAsistencia}/anulacion")
    public ResponseEntity<AsistenciaLaboralRespuesta> anularAsistencia(
            @PathVariable Long idAsistencia,
            @Valid @RequestBody AnularAsistenciaLaboralRequest solicitud,
            Authentication autenticacion) {
        AsistenciaLaboral asistencia = asistenciaLaboralService.anularAsistencia(
                idAsistencia, solicitud.getMotivo(), autenticacion.getName());
        return ResponseEntity.ok(AsistenciaLaboralRespuesta.desde(
                asistencia, asistencia.getUsuario().getIdUsuario()));
    }

    @GetMapping("/{idAsistencia}/historial")
    public AsistenciaLaboralHistorialRespuesta consultarHistorialAsistencia(
            @PathVariable Long idAsistencia,
            Authentication autenticacion) {
        return asistenciaLaboralService.consultarHistorialAsistencia(
                idAsistencia, autenticacion.getName());
    }

    @GetMapping("/reportes/empleados/{idEmpleado}")
    public ReporteAsistenciaLaboralEmpleadoRespuesta consultarReporteEmpleado(
            @PathVariable Long idEmpleado,
            @RequestParam LocalDate fechaDesde,
            @RequestParam LocalDate fechaHasta,
            Authentication autenticacion) {
        return asistenciaLaboralService.consultarReporteEmpleado(
                idEmpleado, fechaDesde, fechaHasta, autenticacion.getName());
    }

    @GetMapping("/mis-asistencias")
    public AsistenciaLaboralPaginaRespuesta consultarMias(
            Authentication autenticacion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta) {
        return pagina(asistenciaLaboralService.consultarMias(
                autenticacion.getName(), page, size, fechaDesde, fechaHasta));
    }

    @GetMapping
    public AsistenciaLaboralPaginaRespuesta consultarTodas(
            Authentication autenticacion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            @RequestParam(required = false) EstadoAsistenciaLaboral estado) {
        return pagina(asistenciaLaboralService.consultarTodas(
                autenticacion.getName(), page, size, fechaDesde, fechaHasta, estado));
    }

    @GetMapping("/consulta-recepcionista")
    public AsistenciaLaboralPaginaRespuesta consultarHistorialRecepcionista(
            Authentication autenticacion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            @RequestParam(required = false) EstadoAsistenciaLaboral estado,
            @RequestParam(required = false) Long idEmpleado) {
        return pagina(asistenciaLaboralService.consultarHistorialRecepcionista(
                autenticacion.getName(), idEmpleado, page, size, fechaDesde, fechaHasta, estado));
    }

    @GetMapping("/empleado/{idEmpleado}")
    public AsistenciaLaboralPaginaRespuesta consultarEmpleado(
            @PathVariable Long idEmpleado,
            Authentication autenticacion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            @RequestParam(required = false) EstadoAsistenciaLaboral estado) {
        return pagina(asistenciaLaboralService.consultarEmpleado(
                idEmpleado, autenticacion.getName(), page, size,
                fechaDesde, fechaHasta, estado));
    }

    private AsistenciaLaboralPaginaRespuesta pagina(
            org.springframework.data.domain.Page<AsistenciaLaboralRespuesta> resultado) {
        return new AsistenciaLaboralPaginaRespuesta(
                resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }
}
