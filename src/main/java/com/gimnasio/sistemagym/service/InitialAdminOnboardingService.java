package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.InitialAdminRequest;
import com.gimnasio.sistemagym.dto.InitialAdminRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InitialAdminOnboardingService {

    private final InitialAdminBootstrapService bootstrapService;
    private final InstallationTokenService tokenService;
    private final boolean enabled;
    private volatile boolean available;

    public InitialAdminOnboardingService(
            InitialAdminBootstrapService bootstrapService,
            InstallationTokenService tokenService,
            @Value("${sistemagym.onboarding.enabled:false}") boolean enabled) {
        this.bootstrapService = bootstrapService;
        this.tokenService = tokenService;
        this.enabled = enabled;
    }

    public void initializeAtStartup() {
        boolean completed = bootstrapService.reconciliarEstadoOnboarding();
        available = enabled && !completed;
        if (available) tokenService.generateAndPrint();
        else tokenService.disable();
    }

    public boolean isRequired() {
        return available;
    }

    public boolean createFirstAdministrator(String token, InitialAdminRequest request) {
        if (!enabled || !available || !tokenService.matches(token)) return false;
        bootstrapService.crearPrimerAdministrador(new InitialAdminRequest(
                request.nombres(), request.apellidos(), request.correo(),
                request.contrasena()));
        available = false;
        tokenService.disable();
        return true;
    }
}
