package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ActualizarMiCuentaRequest;
import com.gimnasio.sistemagym.dto.CambiarMiContrasenaRequest;
import com.gimnasio.sistemagym.dto.MiCuentaRespuesta;
import com.gimnasio.sistemagym.service.MiCuentaService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mi-cuenta")
public class MiCuentaController {
    private final MiCuentaService miCuenta;

    public MiCuentaController(MiCuentaService miCuenta) {
        this.miCuenta = miCuenta;
    }

    @GetMapping
    public MiCuentaRespuesta consultar(Principal principal) {
        return miCuenta.consultar(principal.getName());
    }

    @PatchMapping
    public MiCuentaRespuesta actualizar(Principal principal,
            @Valid @RequestBody ActualizarMiCuentaRequest solicitud) {
        return miCuenta.actualizar(principal.getName(), solicitud);
    }

    @PatchMapping("/contrasena")
    public ResponseEntity<Void> cambiarContrasena(Principal principal,
            @Valid @RequestBody CambiarMiContrasenaRequest solicitud) {
        miCuenta.cambiarContrasena(principal.getName(), solicitud);
        return ResponseEntity.noContent().build();
    }
}
