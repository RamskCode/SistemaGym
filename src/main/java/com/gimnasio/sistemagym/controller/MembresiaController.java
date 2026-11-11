package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.MembresiaRespuesta;
import com.gimnasio.sistemagym.service.MembresiaService;
import org.springframework.web.bind.annotation.*;
import com.gimnasio.sistemagym.dto.PagoPosteriorMembresiaRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarMembresiaRequest;
import com.gimnasio.sistemagym.dto.RegistrarPagoPosteriorRequest;
import com.gimnasio.sistemagym.dto.RegistroMembresiaRespuesta;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/membresias")
@RequiredArgsConstructor
public class MembresiaController {

    private final MembresiaService membresiaService;
    private final com.gimnasio.sistemagym.service.MembresiaIdempotenteService membresiaIdempotenteService;

    @GetMapping
    public List<MembresiaRespuesta> listarTodas() {
        return membresiaService.listarTodas();
    }

    @GetMapping("/{id}")
    public MembresiaRespuesta buscarPorId(@PathVariable Long id) {
        return membresiaService.buscarPorId(id);
    }

    @GetMapping("/socio/{idSocio}")
    public List<MembresiaRespuesta> buscarPorSocio(@PathVariable Long idSocio) {
        return membresiaService.buscarPorSocio(idSocio);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroMembresiaRespuesta registrar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegistrarMembresiaRequest solicitud,
            @AuthenticationPrincipal UserDetails usuarioAutenticado) {
        return membresiaIdempotenteService.registrar(
                membresiaIdempotenteService.parsearClaveIdempotencia(idempotencyKey), solicitud,
                usuarioAutenticado.getUsername());
    }

    @PostMapping("/{idMembresia}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoPosteriorMembresiaRespuesta registrarPagoPosterior(
            @PathVariable Long idMembresia,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegistrarPagoPosteriorRequest solicitud,
            @AuthenticationPrincipal UserDetails usuarioAutenticado) {
        return membresiaIdempotenteService.registrarPago(idMembresia,
                membresiaIdempotenteService.parsearClaveIdempotencia(idempotencyKey),
                solicitud, usuarioAutenticado.getUsername());
    }
}
