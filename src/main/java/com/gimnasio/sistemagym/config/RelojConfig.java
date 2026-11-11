package com.gimnasio.sistemagym.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj de la aplicación. Se expone como bean para que las reglas de fechas
 * (plazo de pago y vencimientos) puedan verificarse con fechas controladas.
 */
@Configuration
public class RelojConfig {

    @Bean
    @Primary
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean("relojMembresias")
    public Clock relojMembresias() {
        return Clock.system(ZoneId.of("America/Lima"));
    }
}
