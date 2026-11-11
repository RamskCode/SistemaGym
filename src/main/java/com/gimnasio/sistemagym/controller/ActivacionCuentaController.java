package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ActivarCuentaRequest;
import com.gimnasio.sistemagym.service.UsuarioInvitacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/activation")
public class ActivacionCuentaController {
    private final UsuarioInvitacionService invitations;
    public ActivacionCuentaController(UsuarioInvitacionService invitations) { this.invitations = invitations; }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@RequestBody TokenRequest request) {
        return invitations.isTokenValid(request.token())
                ? ResponseEntity.noContent().build() : ResponseEntity.status(410).build();
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivarCuentaRequest request) {
        invitations.activate(request.token(), request.contrasena());
        return ResponseEntity.noContent().build();
    }

    public record TokenRequest(@NotBlank String token) { }
}
