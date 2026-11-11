package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.EvaluacionNutricionalRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarEvaluacionNutricionalRequest;
import com.gimnasio.sistemagym.service.EvaluacionNutricionalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/socios/{idSocio}/evaluaciones-nutricionales")
public class EvaluacionNutricionalController {

    private final EvaluacionNutricionalService service;

    public EvaluacionNutricionalController(EvaluacionNutricionalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EvaluacionNutricionalRespuesta> registrar(
            @PathVariable Long idSocio,
            @Valid @RequestBody RegistrarEvaluacionNutricionalRequest request,
            Authentication autenticacion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrar(idSocio, request, autenticacion.getName()));
    }

    @GetMapping
    public java.util.List<EvaluacionNutricionalRespuesta> historial(
            @PathVariable Long idSocio) {
        return service.historial(idSocio);
    }
}
