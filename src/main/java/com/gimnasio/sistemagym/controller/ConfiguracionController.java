package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ActualizarConfiguracionRequest;
import com.gimnasio.sistemagym.dto.ConfiguracionRespuesta;
import com.gimnasio.sistemagym.service.ConfiguracionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/configuraciones")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    @GetMapping
    public ResponseEntity<List<ConfiguracionRespuesta>> listar() {
        return ResponseEntity.ok(configuracionService.listar());
    }

    @GetMapping("/{clave}")
    public ResponseEntity<ConfiguracionRespuesta> buscarPorClave(
            @PathVariable String clave) {
        return ResponseEntity.ok(configuracionService.obtener(clave));
    }

    @PutMapping("/{clave}")
    public ResponseEntity<ConfiguracionRespuesta> actualizar(
            @PathVariable String clave,
            @Valid @RequestBody ActualizarConfiguracionRequest solicitud) {
        return ResponseEntity.ok(
                configuracionService.actualizar(clave, solicitud.valor()));
    }
}
