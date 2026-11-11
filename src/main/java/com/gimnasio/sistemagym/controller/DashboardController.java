package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.DashboardResumenRespuesta;
import com.gimnasio.sistemagym.service.DashboardResumenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardResumenService dashboardService;

    public DashboardController(DashboardResumenService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumen")
    public DashboardResumenRespuesta resumen() {
        return dashboardService.obtenerResumen();
    }
}
