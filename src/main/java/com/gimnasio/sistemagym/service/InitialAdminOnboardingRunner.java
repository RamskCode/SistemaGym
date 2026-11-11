package com.gimnasio.sistemagym.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class InitialAdminOnboardingRunner implements ApplicationRunner {

    private final InitialAdminOnboardingService onboardingService;

    @Override
    public void run(ApplicationArguments args) {
        onboardingService.initializeAtStartup();
    }
}
