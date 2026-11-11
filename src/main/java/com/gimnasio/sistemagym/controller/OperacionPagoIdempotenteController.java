package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.OperacionPagoIdempotenteRespuesta;
import com.gimnasio.sistemagym.service.OperacionPagoIdempotenteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operaciones-pago")
public class OperacionPagoIdempotenteController {

    private final OperacionPagoIdempotenteService service;

    public OperacionPagoIdempotenteController(OperacionPagoIdempotenteService service) {
        this.service = service;
    }

    @GetMapping("/{idOperacion}")
    public OperacionPagoIdempotenteRespuesta consultar(
            @PathVariable String idOperacion,
            @AuthenticationPrincipal UserDetails usuario) {
        return service.consultar(usuario.getUsername(), idOperacion);
    }

    @GetMapping("/por-clave/{clave}")
    public OperacionPagoIdempotenteRespuesta consultarPorClave(
            @PathVariable String clave,
            @AuthenticationPrincipal UserDetails usuario) {
        return service.consultarPorClave(usuario.getUsername(), clave);
    }
}
