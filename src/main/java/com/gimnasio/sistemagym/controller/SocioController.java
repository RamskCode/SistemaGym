package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ActualizarSocioRequest;
import com.gimnasio.sistemagym.dto.CambiarEstadoSocioRequest;
import com.gimnasio.sistemagym.dto.RegistrarSocioRequest;
import com.gimnasio.sistemagym.dto.SocioRespuesta;
import com.gimnasio.sistemagym.dto.SocioResumenRespuesta;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.service.SocioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/socios")
public class SocioController {

    private final SocioService socioService;

    public SocioController(SocioService socioService) {
        this.socioService = socioService;
    }

    @GetMapping
    public ResponseEntity<List<?>> listarTodos(Authentication autenticacion) {
        return ResponseEntity.ok(respuestasVisibles(
                socioService.listarTodos(), autenticacion));
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> buscarPorCodigo(
            @PathVariable String codigo, Authentication autenticacion) {
        return ResponseEntity.ok(respuestaVisible(
                socioService.buscarPorCodigo(codigo), autenticacion));
    }

    @GetMapping("/dni/{dni}")
    public ResponseEntity<?> buscarPorDni(
            @PathVariable String dni, Authentication autenticacion) {
        return ResponseEntity.ok(respuestaVisible(
                socioService.buscarPorDni(dni), autenticacion));
    }

    @GetMapping(value = "/buscar", params = {"q", "!apellidos"})
    public ResponseEntity<List<?>> buscarGlobal(
            @RequestParam("q") String texto, Authentication autenticacion) {
        return ResponseEntity.ok(respuestasVisibles(
                socioService.buscarGlobal(texto), autenticacion));
    }

    @GetMapping(value = "/buscar", params = {"apellidos", "!q"})
    public ResponseEntity<List<?>> buscarPorApellidos(
            @RequestParam String apellidos, Authentication autenticacion) {
        return ResponseEntity.ok(respuestasVisibles(
                socioService.buscarPorApellidos(apellidos), autenticacion));
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<?>> listarPorEstado(
            @PathVariable String estado, Authentication autenticacion) {
        return ResponseEntity.ok(respuestasVisibles(
                socioService.listarPorEstado(estado), autenticacion));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(
            @PathVariable Long id, Authentication autenticacion) {
        return ResponseEntity.ok(respuestaVisible(
                socioService.buscarPorId(id), autenticacion));
    }

    @PostMapping
    public ResponseEntity<SocioRespuesta> registrar(
            @Valid @RequestBody RegistrarSocioRequest solicitud) {
        Socio socio = new Socio();
        socio.setDni(solicitud.getDni());
        socio.setTipoDocumento(solicitud.getTipoDocumento());
        socio.setNumeroDocumento(solicitud.getNumeroDocumento());
        socio.setNombres(solicitud.getNombres());
        socio.setApellidos(solicitud.getApellidos());
        socio.setFechaNacimiento(solicitud.getFechaNacimiento());
        socio.setCelular(solicitud.getCelular());
        socio.setCorreo(solicitud.getCorreo());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(socioService.registrar(socio));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SocioRespuesta> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarSocioRequest solicitud) {
        return ResponseEntity.ok(socioService.actualizar(id, solicitud));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<SocioRespuesta> activar(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoSocioRequest solicitud,
            Authentication autenticacion) {
        return ResponseEntity.ok(socioService.activar(
                id, solicitud.getMotivo(), autenticacion.getName()));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<SocioRespuesta> desactivar(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoSocioRequest solicitud,
            Authentication autenticacion) {
        return ResponseEntity.ok(socioService.desactivar(
                id, solicitud.getMotivo(), autenticacion.getName()));
    }

    private List<?> respuestasVisibles(
            List<SocioRespuesta> socios, Authentication autenticacion) {
        return socios.stream().map(socio -> respuestaVisible(socio, autenticacion)).toList();
    }

    private Object respuestaVisible(
            SocioRespuesta socio, Authentication autenticacion) {
        boolean nutricionista = autenticacion.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_NUTRICIONISTA"));
        return nutricionista ? SocioResumenRespuesta.desde(socio) : socio;
    }
}
