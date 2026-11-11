package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ActualizarPlanRequest;
import com.gimnasio.sistemagym.dto.ErrorRespuesta;
import com.gimnasio.sistemagym.dto.PlanRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarPlanRequest;
import com.gimnasio.sistemagym.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/planes")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanRespuesta>> listarTodos() {
        return ResponseEntity.ok(
                planService.listarTodos()
                        .stream()
                        .map(PlanRespuesta::desde)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {

        return planService.buscarPorId(id)
                .<ResponseEntity<?>>map(plan -> ResponseEntity.ok(
                        PlanRespuesta.desde(plan)))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(new ErrorRespuesta(
                                404,
                                "Plan no encontrado",
                                "No existe un plan con el ID " + id + "."
                        )));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<PlanRespuesta>> buscarPorNombre(
            @RequestParam String nombre) {
        return ResponseEntity.ok(
                planService.buscarPorNombreParcial(nombre)
                        .stream()
                        .map(PlanRespuesta::desde)
                        .toList()
        );
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<PlanRespuesta>> listarPorEstado(
            @PathVariable String estado) {
        return ResponseEntity.ok(
                planService.listarPorEstado(estado.toUpperCase())
                        .stream()
                        .map(PlanRespuesta::desde)
                        .toList()
        );
    }

    @PostMapping
    public ResponseEntity<PlanRespuesta> registrar(
            @Valid @RequestBody RegistrarPlanRequest solicitud) {
        PlanRespuesta nuevoPlan = PlanRespuesta.desde(
                planService.registrar(solicitud)
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(nuevoPlan);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanRespuesta> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarPlanRequest solicitud) {
        return ResponseEntity.ok(
                PlanRespuesta.desde(
                        planService.actualizar(id, solicitud)
                )
        );
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<PlanRespuesta> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(
                PlanRespuesta.desde(
                        planService.desactivar(id)
                )
        );
    }
}
