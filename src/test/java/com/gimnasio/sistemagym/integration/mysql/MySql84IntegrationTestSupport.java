package com.gimnasio.sistemagym.integration.mysql;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Shared, fail-closed setup for opt-in MySQL 8.4 integration tests. */
@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(initializers =
        MySql84IntegrationTestSupport.ConnectionSafetyInitializer.class)
@Import(MySql84IntegrationTestSupport.ConnectionSafetyConfiguration.class)
public abstract class MySql84IntegrationTestSupport {

    public static final String TEST_DATABASE = "sistemagym_test";
    private static final String MYSQL_IMAGE = "mysql:8.4.13";
    private static final String MYSQL_VERSION = "8.4.13";
    private static final String TEST_USERNAME = "sistemagym_it";
    private static final String TEST_PASSWORD = UUID.randomUUID().toString();

    @Container
    protected static final MySQLContainer MYSQL = new MySQLContainer(
            DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName(TEST_DATABASE)
            .withUsername(TEST_USERNAME)
            .withPassword(TEST_PASSWORD)
            .withInitScript("db/mysql/sistemagym-test-schema.sql");

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void configurarConexionDelContenedor(DynamicPropertyRegistry registry) {
        String jdbcUrl = MYSQL.getJdbcUrl();
        validarUrlConfigurada(jdbcUrl);

        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("sistemagym.mysql-it.expected-jdbc-url", MYSQL::getJdbcUrl);
        registry.add("sistemagym.initial-admin.bootstrap.enabled", () -> "false");
        registry.add("sistemagym.membresias.scheduler.enabled", () -> "false");
    }

    @BeforeEach
    final void verificarConexionAisladaAntesDeCadaPrueba() {
        MySql84ConnectionGuard.verificar(dataSource, MYSQL);
    }

    private static void validarUrlConfigurada(String jdbcUrl) {
        URI endpoint = endpoint(jdbcUrl);
        String database = endpoint.getPath().replaceFirst("^/", "");
        if (!TEST_DATABASE.equals(database)
                || endpoint.getPort() <= 0
                || endpoint.getHost() == null
                || endpoint.getPort() != MYSQL.getMappedPort(3306)
                || !endpoint.getHost().equalsIgnoreCase(MYSQL.getHost())) {
            throw new IllegalStateException(
                    "La conexión de integración no apunta al contenedor y catálogo de pruebas.");
        }
    }

    private static URI endpoint(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException(
                    "El contenedor no proporcionó una URL JDBC MySQL válida.");
        }
        return URI.create(jdbcUrl.substring("jdbc:".length()));
    }

    /** Validates the resolved URL before Spring refreshes the context or creates JPA beans. */
    public static final class ConnectionSafetyInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (!MYSQL.isRunning()) {
                throw new IllegalStateException(
                        "El contenedor MySQL de integración no está iniciado.");
            }

            String containerUrl = MYSQL.getJdbcUrl();
            validarUrlConfigurada(containerUrl);

            Map<String, Object> isolatedProperties = Map.of(
                    "spring.datasource.url", containerUrl,
                    "spring.datasource.username", MYSQL.getUsername(),
                    "spring.datasource.password", MYSQL.getPassword(),
                    "spring.jpa.hibernate.ddl-auto", "validate",
                    "sistemagym.mysql-it.expected-jdbc-url", containerUrl);
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("sistemagymMysqlItIsolation", isolatedProperties));

            String expectedUrl = context.getEnvironment().getProperty(
                    "sistemagym.mysql-it.expected-jdbc-url");
            String configuredUrl = context.getEnvironment().getProperty(
                    "spring.datasource.url");
            String configuredUsername = context.getEnvironment().getProperty(
                    "spring.datasource.username");
            String configuredPassword = context.getEnvironment().getProperty(
                    "spring.datasource.password");
            String ddlAuto = context.getEnvironment().getProperty(
                    "spring.jpa.hibernate.ddl-auto");

            if (expectedUrl == null
                    || !expectedUrl.equals(configuredUrl)
                    || !MYSQL.getUsername().equals(configuredUsername)
                    || !MYSQL.getPassword().equals(configuredPassword)
                    || !"validate".equals(ddlAuto)
                    || !TEST_DATABASE.equals(MYSQL.getDatabaseName())) {
                throw new IllegalStateException(
                        "La configuración efectiva no está ligada al contenedor MySQL de pruebas.");
            }

            validarUrlConfigurada(configuredUrl);
            MySql84ConnectionGuard.verificarUrlAntesDelContexto(
                    configuredUrl, MYSQL);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class ConnectionSafetyConfiguration {

        @Bean
        static BeanPostProcessor validarCadaDataSourceAntesDeSuUso() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean,
                        String beanName) throws BeansException {
                    if (bean instanceof DataSource candidate) {
                        if (!(candidate instanceof HikariDataSource hikari)) {
                            throw new BeanCreationException(beanName,
                                    "Solo se permite HikariDataSource verificable en pruebas MySQL.");
                        }
                        validarConfiguracionHikari(hikari, beanName);
                    }
                    return bean;
                }

                @Override
                public Object postProcessAfterInitialization(Object bean,
                        String beanName) throws BeansException {
                    if (bean instanceof DataSource candidate) {
                        try {
                            MySql84ConnectionGuard.verificar(candidate, MYSQL);
                        } catch (RuntimeException exception) {
                            throw new BeanCreationException(
                                    beanName,
                                    "Se rechazó un DataSource que no pertenece al contenedor MySQL de pruebas.",
                                    exception);
                        }
                    }
                    return bean;
                }
            };
        }

        private static void validarConfiguracionHikari(HikariDataSource hikari,
                String beanName) {
            if (hikari.getHikariPoolMXBean() != null
                    || hikari.getDataSource() != null
                    || !MYSQL.getJdbcUrl().equals(hikari.getJdbcUrl())
                    || !MYSQL.getUsername().equals(hikari.getUsername())
                    || !MYSQL.getPassword().equals(hikari.getPassword())) {
                throw new BeanCreationException(beanName,
                        "Hikari debe estar sin iniciar y configurado exclusivamente con los datos del contenedor MySQL.");
            }
            validarUrlConfigurada(hikari.getJdbcUrl());
        }
    }

    static final class MySql84ConnectionGuard {

        private MySql84ConnectionGuard() {
        }

        static void verificar(DataSource actualDataSource,
                MySQLContainer container) {
            if (!container.isRunning()
                    || !TEST_DATABASE.equals(container.getDatabaseName())) {
                throw new IllegalStateException(
                        "El contenedor MySQL de pruebas no está activo o no usa el catálogo exclusivo.");
            }

            try (Connection actual = actualDataSource.getConnection()) {
                DatabaseMetaData metadata = actual.getMetaData();
                URI actualEndpoint = endpoint(metadata.getURL());
                URI containerEndpoint = endpoint(container.getJdbcUrl());

                if (!"mysql".equalsIgnoreCase(metadata.getDatabaseProductName())
                        || !TEST_DATABASE.equals(actual.getCatalog())
                        || !mismoEndpoint(actualEndpoint, containerEndpoint)) {
                    throw new IllegalStateException(
                            "La conexión JDBC no pertenece al contenedor MySQL de pruebas.");
                }

                validarIdentidad(actual, container);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo validar de forma segura la conexión MySQL de integración.",
                        exception);
            }
        }

        static void verificarUrlAntesDelContexto(String configuredUrl,
                MySQLContainer container) {
            try (Connection configuredConnection = DriverManager.getConnection(
                    configuredUrl, container.getUsername(), container.getPassword())) {
                URI expectedEndpoint = endpoint(container.getJdbcUrl());
                URI configuredEndpoint = endpoint(
                        configuredConnection.getMetaData().getURL());
                if (!"mysql".equalsIgnoreCase(
                            configuredConnection.getMetaData().getDatabaseProductName())
                        || !TEST_DATABASE.equals(configuredConnection.getCatalog())
                        || !mismoEndpoint(configuredEndpoint, expectedEndpoint)) {
                    throw new IllegalStateException(
                            "La conexión efectiva no pertenece al contenedor MySQL de pruebas.");
                }
                validarIdentidad(configuredConnection, container);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo validar el servidor MySQL antes de iniciar Spring.");
            }
        }

        private static void validarIdentidad(Connection actual,
                MySQLContainer container) throws SQLException {
            String actualUuid = leerIdentidadServidor(actual);
            String containerUuid;
            try (Connection referencia = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword())) {
                containerUuid = leerIdentidadServidor(referencia);
            }
            if (!actualUuid.equals(containerUuid)) {
                throw new IllegalStateException(
                        "La conexión JDBC no corresponde a la instancia MySQL del contenedor.");
            }
        }

        private static boolean mismoEndpoint(URI actual, URI esperado) {
            return actual.getHost() != null
                    && actual.getHost().equalsIgnoreCase(esperado.getHost())
                    && actual.getPort() == esperado.getPort()
                    && TEST_DATABASE.equals(actual.getPath().replaceFirst("^/", ""));
        }

        private static String leerIdentidadServidor(Connection connection)
                throws SQLException {
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "SELECT DATABASE(), @@server_uuid, VERSION(), CURRENT_USER()")) {
                if (!result.next()) {
                    throw new SQLException("MySQL no devolvió la identidad del servidor.");
                }
                if (!TEST_DATABASE.equals(result.getString(1))) {
                    throw new SQLException("MySQL no seleccionó el catálogo de pruebas.");
                }
                String version = result.getString(3);
                if (version == null
                        || !version.matches("^" + MYSQL_VERSION + "(?:[-+].*)?$")) {
                    throw new SQLException(
                            "El servidor no corresponde a la versión MySQL requerida.");
                }
                String effectiveUser = result.getString(4);
                if (effectiveUser == null
                        || !effectiveUser.startsWith(TEST_USERNAME + "@")) {
                    throw new SQLException(
                            "La conexión no utiliza el usuario aislado de integración.");
                }
                String uuid = result.getString(2);
                if (uuid == null || uuid.isBlank()) {
                    throw new SQLException("MySQL no devolvió el UUID del servidor.");
                }
                return uuid.toLowerCase(Locale.ROOT);
            }
        }
    }
}
