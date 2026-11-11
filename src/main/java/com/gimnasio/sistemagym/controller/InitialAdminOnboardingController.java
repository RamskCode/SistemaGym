package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.InitialAdminRequest;
import com.gimnasio.sistemagym.dto.SetupStatusResponse;
import com.gimnasio.sistemagym.service.InitialAdminOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class InitialAdminOnboardingController {

    private final InitialAdminOnboardingService onboardingService;

    public InitialAdminOnboardingController(InitialAdminOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/status")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(onboardingService.isRequired());
    }

    @PostMapping("/administrator")
    public ResponseEntity<Void> createAdministrator(
            @RequestHeader(value = "X-Installation-Token", required = false) String token,
            @Valid @RequestBody InitialAdminRequest request) {
        if (!onboardingService.createFirstAdministrator(token, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.noContent().build();
    }
}
