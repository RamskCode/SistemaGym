package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.config.InitialAdminProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Starts the one-time bootstrap after the application context has been created. */
@Component
@ConditionalOnProperty(
        name = "sistemagym.initial-admin.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InitialAdminBootstrapRunner implements ApplicationRunner {

    private final InitialAdminBootstrapService bootstrapService;
    private final InitialAdminProperties properties;
    private final boolean onboardingEnabled;

    public InitialAdminBootstrapRunner(
            InitialAdminBootstrapService bootstrapService,
            InitialAdminProperties properties,
            @Value("${sistemagym.onboarding.enabled:false}") boolean onboardingEnabled) {
        this.bootstrapService = bootstrapService;
        this.properties = properties;
        this.onboardingEnabled = onboardingEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (onboardingEnabled) return;
        bootstrapService.initialize(properties);
    }
}
