package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Membresia;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-socio-search-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class SocioBusquedaRepositoryTest {

    @Autowired
    private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MembresiaRepository membresiaRepository;

    private Socio carlos;

    @BeforeEach
    void prepararSocios() {
        carlos = socio("SOC-0042", "76543210", "Carlos Alberto",
                "Rojas Pérez", "ACTIVO");
        socioRepository.saveAndFlush(carlos);
        socioRepository.saveAndFlush(socio("SOC-0043", "12345678",
                "Ana", "Soto", "INACTIVO"));
        socioRepository.saveAndFlush(socio("SOC-0044", "55551234",
                "A%lice", "Under_score", "ACTIVO"));
    }

    @Test
    void buscaPorNombresApellidosDniCodigoYFragmentosSinDistinguirMayusculas() {
        assertMatches("cArLoS", carlos);
        assertMatches("ROJAS", carlos);
        assertMatches("5432", carlos);
        assertMatches("0042", carlos);
        assertMatches("arlo", carlos);
    }

    @Test
    void distribuyeTokensEntreNombresYApellidos() {
        assertMatches("Carlos Rojas", carlos);
        assertMatches("  CARLOS   péREZ  ", carlos);
    }

    @Test
    void caracteresLikeSeTratanComoTextoLiteral() {
        List<Socio> porPorcentaje = socioRepository.buscarGlobal("A%");
        List<Socio> porGuionBajo = socioRepository.buscarGlobal("Under_");

        assertEquals(List.of("A%lice"), porPorcentaje.stream()
                .map(Socio::getNombres).toList());
        assertEquals(List.of("Under_score"), porGuionBajo.stream()
                .map(Socio::getApellidos).toList());
    }

    @Test
    void unaBusquedaSinCoincidenciasDevuelveListaVacia() {
        assertTrue(socioRepository.buscarGlobal("no existe").isEmpty());
    }

    @Test
    void filtraPorEstadoActivoEInactivoEnLaBase() {
        assertEquals(2, socioRepository.findByEstado("ACTIVO").size());
        assertEquals(1, socioRepository.findByEstado("INACTIVO").size());
    }

    @Test
    void directorioSoloIncluyeMembresiasActivasVigentesYFiltraYPaginaEnBase() {
        Plan plan = new Plan();
        plan.setNombre("Mensual");
        plan.setPrecio(new BigDecimal("100.00"));
        plan.setDuracionDias(30);
        plan = planRepository.saveAndFlush(plan);

        Socio vigente = socioRepository.saveAndFlush(socio(
                "SOC-0100", "70000001", "Ana María", "Vargas", "ACTIVO"));
        Socio vigenteDos = socioRepository.saveAndFlush(socio(
                "SOC-0103", "70000004", "Zulema", "Gómez", "ACTIVO"));
        Socio socioCe = socio("SOC-0104", null, "Cecilia", "Díaz", "ACTIVO");
        socioCe.setTipoDocumento("CE");
        socioCe.setNumeroDocumento("000123456789");
        socioCe = socioRepository.saveAndFlush(socioCe);
        Socio vencido = socioRepository.saveAndFlush(socio(
                "SOC-0101", "70000002", "Bruno", "Pérez", "ACTIVO"));
        Socio cancelado = socioRepository.saveAndFlush(socio(
                "SOC-0102", "70000003", "Carla", "Ríos", "ACTIVO"));
        membresia(vigente, plan, "ACTIVA", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        membresia(vigenteDos, plan, "ACTIVA", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        membresia(socioCe, plan, "ACTIVA", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        membresia(vencido, plan, "ACTIVA", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        membresia(cancelado, plan, "CANCELADA", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        var listado = socioRepository.listarHabilitadosParaIngreso(
                "", "", "", LocalDate.of(2026, 11, 10),
                PageRequest.of(0, 1, Sort.by("nombres").ascending().and(Sort.by("idSocio"))));
        assertEquals(3, listado.getTotalElements());
        assertEquals("Ana María", listado.getContent().getFirst().getNombres());

        var inverso = socioRepository.listarHabilitadosParaIngreso(
                "", "", "", LocalDate.of(2026, 11, 10),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "nombres")
                        .and(Sort.by(Sort.Direction.DESC, "idSocio"))));
        assertEquals("Zulema", inverso.getContent().getFirst().getNombres());

        var filtrado = socioRepository.listarHabilitadosParaIngreso(
                "ana", "", "", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10));
        assertEquals(1, filtrado.getTotalElements());
        assertEquals(vigente.getIdSocio(), filtrado.getContent().getFirst().getIdSocio());
        assertEquals(1, socioRepository.listarHabilitadosParaIngreso(
                "varg", "", "", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, socioRepository.listarHabilitadosParaIngreso(
                "gomez", "", "", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, socioRepository.listarHabilitadosParaIngreso(
                "", "DNI", "70000001", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, socioRepository.listarHabilitadosParaIngreso(
                "", "DNI", "000123", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, socioRepository.listarHabilitadosParaIngreso(
                "", "CE", "0001", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, socioRepository.listarHabilitadosParaIngreso(
                "Ceci", "CE", "000123", LocalDate.of(2026, 11, 10), PageRequest.of(0, 10)).getTotalElements());
    }

    private void membresia(Socio socio, Plan plan, String estado, LocalDate inicio, LocalDate vencimiento) {
        Membresia membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(new BigDecimal("100.00"));
        membresia.setFechaInicio(inicio);
        membresia.setFechaVencimiento(vencimiento);
        membresia.setEstado(estado);
        membresiaRepository.saveAndFlush(membresia);
    }

    private void assertMatches(String texto, Socio expected) {
        assertEquals(List.of(expected), socioRepository.buscarGlobal(texto));
    }

    private Socio socio(
            String codigo, String dni, String nombres, String apellidos, String estado) {
        Socio socio = new Socio();
        socio.setCodigoSocio(codigo);
        socio.setDni(dni);
        socio.setNombres(nombres);
        socio.setApellidos(apellidos);
        socio.setEstado(estado);
        return socio;
    }
}
