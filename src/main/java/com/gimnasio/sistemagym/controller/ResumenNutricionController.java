package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.ResumenNutricionRespuesta;
import com.gimnasio.sistemagym.service.ResumenNutricionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutricion")
public class ResumenNutricionController {

    private final ResumenNutricionService service;

    public ResumenNutricionController(ResumenNutricionService service) {
        this.service = service;
    }

    @GetMapping("/resumen")
    public ResumenNutricionRespuesta resumen() {
        return service.obtenerResumen();
    }
}
