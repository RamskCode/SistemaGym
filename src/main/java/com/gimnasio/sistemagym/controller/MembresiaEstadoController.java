package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.EstadoMembresiaRespuesta;
import com.gimnasio.sistemagym.dto.ResultadoCicloVidaRespuesta;
import com.gimnasio.sistemagym.service.MembresiaCicloVidaService;
import com.gimnasio.sistemagym.service.MembresiaEstadoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta del estado de una membresía y ejecución administrativa del proceso
 * de cancelación/vencimiento. La ejecución está restringida a ADMINISTRADOR
 * en la configuración de seguridad.
 */
@RestController
@RequestMapping("/api/membresias")
public class MembresiaEstadoController {

    private final MembresiaEstadoService membresiaEstadoService;
    private final MembresiaCicloVidaService membresiaCicloVidaService;

    public MembresiaEstadoController(
            MembresiaEstadoService membresiaEstadoService,
            MembresiaCicloVidaService membresiaCicloVidaService) {

        this.membresiaEstadoService = membresiaEstadoService;
        this.membresiaCicloVidaService = membresiaCicloVidaService;
    }

    @GetMapping("/{idMembresia}/estado")
    public ResponseEntity<EstadoMembresiaRespuesta> obtenerEstado(
            @PathVariable Long idMembresia) {
        return ResponseEntity.ok(
                membresiaEstadoService.obtenerEstado(idMembresia));
    }

    @PostMapping("/procesar-ciclo-vida")
    public ResponseEntity<ResultadoCicloVidaRespuesta> procesarCicloVida() {
        MembresiaCicloVidaService.ResultadoCicloVida resultado =
                membresiaCicloVidaService.procesarCicloVida();

        return ResponseEntity.ok(new ResultadoCicloVidaRespuesta(
                resultado.canceladas(),
                resultado.vencidas(),
                resultado.fecha()));
    }
}
