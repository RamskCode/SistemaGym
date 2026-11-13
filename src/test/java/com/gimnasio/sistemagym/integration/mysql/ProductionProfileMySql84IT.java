package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Starts the production Spring profile only against the guarded MySQL Testcontainer. */
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ProductionProfileMySql84IT extends MySql84IntegrationTestSupport {

    private static final String TEMPORARY_JWT_SECRET = crearSecretoJwtTemporal();

    @Autowired private Environment environment;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configurarSecretosTemporales(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret-base64", () -> TEMPORARY_JWT_SECRET);
        registry.add("sistemagym.frontend-url", () -> "https://frontend.example.test");
        registry.add("app.cors.allowed-origins", () -> "https://frontend.example.test");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("app.database.provider", () -> "mysql");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "");
    }

    @Test
    void iniciaPerfilProdContraElContenedorMySqlYValidaConfiguracionSegura() {
        assertTrue(java.util.List.of(environment.getActiveProfiles()).contains("prod"));
        assertEquals("validate", environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", environment.getRequiredProperty("app.auth.cookie-secure"));
        assertEquals("org.hibernate.dialect.MySQLDialect",
                environment.getRequiredProperty("spring.jpa.database-platform"));
        assertEquals("sistemagym_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertTrue(environment.getRequiredProperty("spring.datasource.url")
                .contains("/sistemagym_test"));
    }

    private static String crearSecretoJwtTemporal() {
        byte[] secret = new byte[48];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
}
