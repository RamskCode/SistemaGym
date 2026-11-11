package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ActualizarUsuarioRequest;
import com.gimnasio.sistemagym.dto.CambiarContrasenaUsuarioRequest;
import com.gimnasio.sistemagym.dto.CambiarRolUsuarioRequest;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.dto.UsuarioRespuesta;
import com.gimnasio.sistemagym.dto.InvitacionCreadaRespuesta;
import com.gimnasio.sistemagym.service.UsuarioAdminService;
import com.gimnasio.sistemagym.service.UsuarioInvitacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioAdminService usuarioAdminService;
    private final UsuarioInvitacionService invitaciones;

    public UsuarioController(UsuarioAdminService usuarioAdminService,
            UsuarioInvitacionService invitaciones) {
        this.usuarioAdminService = usuarioAdminService;
        this.invitaciones = invitaciones;
    }

    @GetMapping
    public List<UsuarioRespuesta> listarTodos() {
        return usuarioAdminService.listarTodos();
    }

    @GetMapping("/{id}")
    public UsuarioRespuesta buscarPorId(@PathVariable Long id) {
        return usuarioAdminService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<InvitacionCreadaRespuesta> crear(
            @Valid @RequestBody CrearUsuarioRequest solicitud) {
        UsuarioRespuesta user = usuarioAdminService.crear(solicitud);
        UsuarioInvitacionService.Delivery delivery = invitaciones.sendInvitation(user.idUsuario());
        return ResponseEntity.status(HttpStatus.CREATED).body(new InvitacionCreadaRespuesta(
                user, delivery.sent(), delivery.message()));
    }

    @PostMapping("/{id}/invitacion")
    public InvitacionCreadaRespuesta reenviarInvitacion(@PathVariable Long id) {
        UsuarioRespuesta user = usuarioAdminService.buscarPorId(id);
        UsuarioInvitacionService.Delivery delivery = invitaciones.sendInvitation(id);
        return new InvitacionCreadaRespuesta(user, delivery.sent(), delivery.message());
    }

    @PutMapping("/{id}")
    public UsuarioRespuesta actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest solicitud) {
        return usuarioAdminService.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/rol")
    public UsuarioRespuesta cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambiarRolUsuarioRequest solicitud) {
        return usuarioAdminService.cambiarRol(id, solicitud.rol());
    }

    @PatchMapping("/{id}/activar")
    public UsuarioRespuesta activar(@PathVariable Long id) {
        return usuarioAdminService.activar(id);
    }

    @PatchMapping("/{id}/desactivar")
    public UsuarioRespuesta desactivar(@PathVariable Long id) {
        return usuarioAdminService.desactivar(id);
    }

    @PatchMapping("/{id}/contrasena")
    public ResponseEntity<Void> cambiarContrasena(
            @PathVariable Long id,
            @Valid @RequestBody CambiarContrasenaUsuarioRequest solicitud) {
        usuarioAdminService.cambiarContrasena(id, solicitud);
        return ResponseEntity.noContent().build();
    }
}
