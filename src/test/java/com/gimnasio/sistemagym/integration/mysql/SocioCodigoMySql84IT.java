package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimnasio.sistemagym.dto.SocioRespuesta;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.service.SocioService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TestMethodOrder(OrderAnnotation.class)
class SocioCodigoMySql84IT extends MySql84IntegrationTestSupport {

    private static final int CANTIDAD_CONCURRENTE = 8;
    private static final String NOMBRE_BLOQUEO = "sistemagym:socios:codigo";

    @Autowired
    private SocioService socioService;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<String> dnisDePrueba = ConcurrentHashMap.newKeySet();

    @AfterEach
    void limpiarSoloSociosDeEstaPrueba() {
        List<Socio> sociosCreados = dnisDePrueba.stream()
                .map(socioRepository::findByDni)
                .flatMap(java.util.Optional::stream)
                .toList();
        socioRepository.deleteAll(sociosCreados);
        socioRepository.flush();
        dnisDePrueba.clear();
    }

    @Test
    @Order(1)
    void generaCodigosConsecutivosYUnicos() {
        List<SocioRespuesta> registrados = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            registrados.add(registrarSocioValido());
        }

        List<String> codigos = registrados.stream()
                .map(SocioRespuesta::codigoSocio)
                .toList();

        assertEquals(List.of("SOC-0001", "SOC-0002", "SOC-0003"), codigos);
        assertEquals(codigos.size(), new HashSet<>(codigos).size());
        for (SocioRespuesta registrado : registrados) {
            assertTrue(socioRepository.existsByCodigoSocio(registrado.codigoSocio()));
        }
    }

    @Test
    @Order(2)
    void generaCodigosUnicosEnRegistrosConcurrentes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CANTIDAD_CONCURRENTE);
        CountDownLatch listos = new CountDownLatch(CANTIDAD_CONCURRENTE);
        CountDownLatch iniciar = new CountDownLatch(1);
        List<Future<SocioRespuesta>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < CANTIDAD_CONCURRENTE; i++) {
                Socio socio = nuevoSocioUnico();
                dnisDePrueba.add(socio.getDni());
                resultados.add(executor.submit(() -> {
                    listos.countDown();
                    if (!iniciar.await(10, TimeUnit.SECONDS)) {
                        throw new TimeoutException("No se liberó la barrera de inicio.");
                    }
                    return socioService.registrar(socio);
                }));
            }

            assertTrue(listos.await(10, TimeUnit.SECONDS),
                    "Todos los hilos deben estar listos antes del inicio simultáneo.");
            iniciar.countDown();

            List<SocioRespuesta> registrados = new ArrayList<>();
            for (Future<SocioRespuesta> resultado : resultados) {
                registrados.add(resultado.get(30, TimeUnit.SECONDS));
            }

            List<String> codigos = registrados.stream()
                    .map(SocioRespuesta::codigoSocio)
                    .toList();
            assertEquals(CANTIDAD_CONCURRENTE, registrados.size());
            assertEquals(CANTIDAD_CONCURRENTE, new HashSet<>(codigos).size(),
                    "No debe haber códigos duplicados.");
            assertEquals(codigosEsperados(CANTIDAD_CONCURRENTE),
                    codigos.stream().sorted().toList(),
                    "La secuencia debe ser consecutiva y sin huecos.");

            for (SocioRespuesta registrado : registrados) {
                assertTrue(socioRepository.existsByCodigoSocio(registrado.codigoSocio()));
                Socio persistido = socioRepository.findByDni(registrado.dni()).orElseThrow();
                assertEquals(registrado.idSocio(), persistido.getIdSocio());
                assertEquals(registrado.codigoSocio(), persistido.getCodigoSocio());
            }
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Los hilos de registro deben terminar antes de limpiar fixtures.");
        }
    }

    @Test
    @Order(3)
    void liberaElBloqueoTrasUnFalloYPermiteUnRegistroPosterior() {
        Socio invalido = nuevoSocioUnico();
        invalido.setNombres(" ");
        dnisDePrueba.add(invalido.getDni());

        assertThrows(RuntimeException.class, () -> socioService.registrar(invalido));
        assertFalse(socioRepository.existsByDni(invalido.getDni()),
                "El registro fallido no debe dejar un socio persistido.");
        assertTrue(bloqueoDeCodigoEstaLibre(),
                "El bloqueo asesor debe liberarse después del rollback.");

        SocioRespuesta posterior = registrarSocioValido();
        assertEquals("SOC-0001", posterior.codigoSocio());
        assertTrue(socioRepository.existsByCodigoSocio(posterior.codigoSocio()));
    }

    private SocioRespuesta registrarSocioValido() {
        Socio socio = nuevoSocioUnico();
        dnisDePrueba.add(socio.getDni());
        return socioService.registrar(socio);
    }

    private Socio nuevoSocioUnico() {
        String token = UUID.randomUUID().toString().replace("-", "");
        Socio socio = new Socio();
        String dni = String.format(java.util.Locale.ROOT, "%08d",
                Math.floorMod(token.hashCode(), 100_000_000));
        socio.setDni(dni);
        socio.setNombres("Socio Integracion");
        socio.setApellidos("Codigo " + token.substring(0, 8));
        socio.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        socio.setCelular("999999999");
        socio.setCorreo("it-" + token + "@example.test");
        return socio;
    }

    private boolean bloqueoDeCodigoEstaLibre() {
        Boolean libre = jdbcTemplate.queryForObject(
                "SELECT IS_USED_LOCK(?) IS NULL", Boolean.class, NOMBRE_BLOQUEO);
        assertNotNull(libre);
        return libre;
    }

    private static List<String> codigosEsperados(int cantidad) {
        return java.util.stream.IntStream.rangeClosed(1, cantidad)
                .mapToObj(numero -> String.format(java.util.Locale.ROOT,
                        "SOC-%04d", numero))
                .toList();
    }
}
