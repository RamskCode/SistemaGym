package com.gimnasio.sistemagym.config;

import java.util.Arrays;
import java.util.List;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Credentialed CORS with exact origins only; wildcard origins are not supported. */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
        List<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (allowedOrigins.isEmpty() || allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS requiere orígenes exactos y explícitos; no se admite '*'.");
        }
        for (String origin : allowedOrigins) {
            try {
                URI parsed = URI.create(origin);
                String path = parsed.getRawPath();
                if (!("https".equalsIgnoreCase(parsed.getScheme())
                        || "http".equalsIgnoreCase(parsed.getScheme()))
                        || parsed.getRawAuthority() == null || parsed.getRawUserInfo() != null
                        || (path != null && !path.isEmpty())
                        || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
                    throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS debe contener orígenes, no URLs con rutas.");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Origen CORS inválido: " + origin, exception);
            }
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept", "Authorization", "Content-Type", "X-CSRF-Token"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
