package com.gimnasio.sistemagym.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InitialAdminOnboardingRunner implements ApplicationRunner {

    private final InitialAdminOnboardingService onboardingService;

    public InitialAdminOnboardingRunner(InitialAdminOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        onboardingService.initializeAtStartup();
    }
}
