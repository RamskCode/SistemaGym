package com.gimnasio.sistemagym.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Fails before bootstrap unless the manual pruebas profile targets its exact local catalog. */
@Component
@Profile("pruebas")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PruebasDatasourceGuard implements ApplicationRunner, BeanPostProcessor {

    private static final String EXPECTED_URL =
            "jdbc:mysql://127.0.0.1:3306/sistemagym_pruebas";

    private final ApplicationContext applicationContext;

    public PruebasDatasourceGuard(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** Reject a redirected datasource before Hibernate can open its first connection. */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("dataSource".equals(beanName)) {
            if (!(bean instanceof HikariDataSource hikari)
                    || !EXPECTED_URL.equals(hikari.getJdbcUrl())
                    || hikari.getUsername() == null
                    || hikari.getUsername().isBlank()
                    || "root".equalsIgnoreCase(hikari.getUsername())) {
                throw new IllegalStateException(
                        "Perfil pruebas bloqueado antes de conectar: se exige MySQL local "
                                + "sistemagym_pruebas y un usuario dedicado distinto de root.");
            }
        }
        return bean;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DataSource dataSource = applicationContext.getBean(DataSource.class);
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !"sistemagym_pruebas".equals(result.getString(1))) {
                throw new IllegalStateException(
                        "Perfil pruebas bloqueado: el catálogo efectivo no es sistemagym_pruebas.");
            }
        }
    }
}
