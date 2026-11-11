package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ChecklistSalaRespuesta;
import com.gimnasio.sistemagym.dto.CrearChecklistRequest;
import com.gimnasio.sistemagym.service.ChecklistSalaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/checklists")
public class ChecklistSalaController {

    private final ChecklistSalaService service;

    public ChecklistSalaController(ChecklistSalaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ChecklistSalaRespuesta> crear(
            @Valid @RequestBody CrearChecklistRequest request,
            Authentication autenticacion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crear(request, autenticacion.getName()));
    }

    @GetMapping("/mios")
    public List<ChecklistSalaRespuesta> listarMios(Authentication autenticacion) {
        return service.listarMios(autenticacion.getName());
    }

    @GetMapping
    public List<ChecklistSalaRespuesta> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    public ChecklistSalaRespuesta buscar(
            @PathVariable Long id, Authentication autenticacion) {
        boolean administrador = autenticacion.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority()
                        .equals("ROLE_ADMINISTRADOR"));
        return service.buscar(id, autenticacion.getName(), administrador);
    }
}
