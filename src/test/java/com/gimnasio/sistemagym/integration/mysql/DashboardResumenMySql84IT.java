package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimnasio.sistemagym.dto.DashboardResumenRespuesta;
import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.TipoIngreso;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.PagoRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.DashboardResumenService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class DashboardResumenMySql84IT extends MySql84IntegrationTestSupport {
    @Autowired DashboardResumenService dashboard;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;
    @Autowired SocioRepository socios;
    @Autowired PagoRepository pagos;
    @Autowired IngresoRepository ingresos;
    @Autowired PlanRepository planes;
    @Autowired MembresiaRepository membresias;

    @Test
    @Transactional
    void dashboardEmpiezaEnCeroYReflejaRegistrosValidosDelCatalogoEfimero() {
        DashboardResumenRespuesta empty = dashboard.obtenerResumen();
        assertEquals(0, empty.sociosActivos());
        assertEquals(0, empty.ingresosMes().compareTo(BigDecimal.ZERO));
        assertEquals(0, empty.ingresosHoy());
        assertEquals(0, empty.ingresosMembresiaHoy());
        assertEquals(0, empty.ingresosPaseDiarioHoy());
        assertEquals(0, empty.sociosNuevosMes());
        assertTrue(empty.actividadReciente().isEmpty());
        assertEquals(7, empty.ingresosSemana().size());
        assertEquals(LocalDate.now(ZoneId.of("America/Lima")).minusDays(6), empty.ingresosSemana().getFirst().fecha());
        assertEquals(LocalDate.now(ZoneId.of("America/Lima")), empty.ingresosSemana().getLast().fecha());
        assertTrue(empty.ingresosSemana().stream().allMatch(day -> day.cantidad() == 0));
        assertEquals(0, empty.membresias().activas());
        assertEquals(0, empty.membresias().porPagar());
        assertEquals(0, empty.membresias().vencidas());
        assertEquals(0, empty.membresias().canceladas());

        Rol role = new Rol();
        role.setNombre("RECEPCIONISTA");
        roles.saveAndFlush(role);
        Usuario employee = new Usuario();
        employee.setRol(role);
        employee.setNombres("Prueba");
        employee.setApellidos("Dashboard");
        employee.setCorreo("dashboard-it@example.test");
        employee.setContrasenaHash("$2a$10$test-only-hash");
        employee.setEstado("ACTIVO");
        usuarios.saveAndFlush(employee);

        Socio socio = new Socio();
        socio.setCodigoSocio("SOC-IT-DASHBOARD");
        socio.setNombres("Jhonatan");
        socio.setApellidos("Osorio");
        socio.setEstado("ACTIVO");
        socios.saveAndFlush(socio);

        Plan plan = new Plan();
        plan.setNombre("Plan dashboard IT");
        plan.setPrecio(new BigDecimal("125.50"));
        plan.setDuracionDias(30);
        planes.saveAndFlush(plan);
        Membresia membership = new Membresia();
        membership.setSocio(socio);
        membership.setPlan(plan);
        membership.setPrecioAcordado(new BigDecimal("125.50"));
        membership.setFechaInicio(LocalDate.now(ZoneId.of("America/Lima")));
        membership.setFechaVencimiento(LocalDate.now(ZoneId.of("America/Lima")).plusDays(30));
        membership.setEstado("ACTIVA");
        membresias.saveAndFlush(membership);

        Pago pago = new Pago();
        pago.setUsuario(employee);
        pago.setMonto(new BigDecimal("125.50"));
        pago.setEstado("CONFIRMADO");
        pago.setMetodoPago("EFECTIVO");
        LocalDate hoyLima = LocalDate.now(ZoneId.of("America/Lima"));
        LocalDateTime inicioHoySistema = hoyLima.atStartOfDay(ZoneId.of("America/Lima"))
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        ReflectionTestUtils.setField(pago, "fechaPago", inicioHoySistema.plusHours(1));
        pagos.saveAndFlush(pago);

        Pago pendiente = new Pago();
        pendiente.setUsuario(employee);
        pendiente.setMonto(new BigDecimal("999.00"));
        pendiente.setEstado("PENDIENTE");
        pendiente.setMetodoPago("EFECTIVO");
        ReflectionTestUtils.setField(pendiente, "fechaPago", inicioHoySistema.plusHours(2));
        pagos.saveAndFlush(pendiente);

        Ingreso ingreso = new Ingreso();
        ingreso.setSocio(socio);
        ingreso.setTipoIngreso(TipoIngreso.SOCIO);
        ingreso.setTipoDocumento(TipoDocumento.DNI);
        ingreso.setNumeroDocumento("12345678");
        ingreso.setUsuario(employee);
        ingreso.setFechaHora(LocalDateTime.now(ZoneId.of("America/Lima")));
        ingresos.saveAndFlush(ingreso);

        DashboardResumenRespuesta current = dashboard.obtenerResumen();
        assertEquals(1, current.sociosActivos());
        assertEquals(0, current.ingresosMes().compareTo(new BigDecimal("125.50")));
        assertEquals(0, current.ingresosCobradosHoy().compareTo(new BigDecimal("125.50")));
        assertEquals(1, current.ingresosHoy());
        assertEquals(1, current.ingresosMembresiaHoy());
        assertEquals(0, current.ingresosPaseDiarioHoy());
        assertEquals(1, current.sociosNuevosMes());
        assertEquals(1, current.actividadReciente().size());
        assertEquals("Jhonatan Osorio", current.actividadReciente().getFirst().nombre());
        assertEquals(1, current.membresias().activas());
        // A physical entry without its own payment must not inflate the confirmed-payment total.
        assertEquals(0, dashboard.obtenerResumen().ingresosCobradosHoy()
                .compareTo(new BigDecimal("125.50")));
    }
}
