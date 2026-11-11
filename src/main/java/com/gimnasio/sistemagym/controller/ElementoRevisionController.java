package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ElementoRevisionRequest;
import com.gimnasio.sistemagym.dto.ElementoRevisionRespuesta;
import com.gimnasio.sistemagym.service.ElementoRevisionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/elementos-revision")
public class ElementoRevisionController {

    private final ElementoRevisionService service;

    public ElementoRevisionController(ElementoRevisionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ElementoRevisionRespuesta> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/activos")
    public List<ElementoRevisionRespuesta> listarActivos() {
        return service.listarActivos();
    }

    @GetMapping("/{id}")
    public ElementoRevisionRespuesta buscarPorId(
            @PathVariable Long id, Authentication autenticacion) {
        boolean entrenador = autenticacion.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ENTRENADOR"));
        return entrenador ? service.buscarActivoPorId(id) : service.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ElementoRevisionRespuesta> crear(
            @Valid @RequestBody ElementoRevisionRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(solicitud));
    }

    @PutMapping("/{id}")
    public ElementoRevisionRespuesta actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ElementoRevisionRequest solicitud) {
        return service.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/activar")
    public ElementoRevisionRespuesta activar(@PathVariable Long id) {
        return service.activar(id);
    }

    @PatchMapping("/{id}/desactivar")
    public ElementoRevisionRespuesta desactivar(@PathVariable Long id) {
        return service.desactivar(id);
    }
}
