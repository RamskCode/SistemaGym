package com.gimnasio.sistemagym.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Usuario;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-reportes-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
class ReportesRepositoryQueryTest {
    @Autowired PagoRepository pagos;
    @Autowired SocioRepository socios;
    @Autowired MembresiaRepository membresias;
    @Autowired IngresoRepository ingresos;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;

    @Test
    void consultasAgregadasYListadosAceptanPeriodosSinDatos() {
        LocalDateTime desde = LocalDate.of(2026, 1, 1).atStartOfDay();
        LocalDateTime hasta = LocalDate.of(2026, 2, 1).atStartOfDay();
        assertTrue(pagos.reportConfirmedTimeline(desde, hasta).isEmpty());
        assertTrue(pagos.reportConfirmedBetween(desde, hasta, PageRequest.of(0, 25)).isEmpty());
        assertTrue(socios.reportNewTimeline(desde, hasta).isEmpty());
        assertTrue(socios.reportNewBetween(desde, hasta, PageRequest.of(0, 25)).isEmpty());
        assertTrue(membresias.reportPlansFiltered(desde, hasta, null).isEmpty());
        assertTrue(membresias.reportBetween(desde, hasta, null, PageRequest.of(0, 25)).isEmpty());
        assertTrue(ingresos.reportTimeline(desde, hasta).isEmpty());
        assertTrue(ingresos.reportPage(desde, hasta, PageRequest.of(0, 25)).isEmpty());
        assertTrue(ingresos.reportPageFiltered(desde, hasta, "", "", null, PageRequest.of(0, 25)).isEmpty());
        assertTrue(ingresos.reportRegistrars(desde, hasta).isEmpty());
    }

    @Test
    void sumaSoloPagosConfirmadosDelPeriodoYAgregaPorMes() {
        Rol role = new Rol(); role.setNombre("ADMINISTRADOR"); roles.save(role);
        Usuario usuario = new Usuario(); usuario.setRol(role); usuario.setNombres("Admin");
        usuario.setApellidos("Prueba"); usuario.setCorreo("admin-reportes@test.local");
        usuario.setContrasenaHash("hash-test"); usuarios.save(usuario);
        guardarPago(usuario, "21.25", "CONFIRMADO");
        guardarPago(usuario, "80.00", "PENDIENTE");
        guardarPago(usuario, "10.00", "ANULADO");
        LocalDateTime desde = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime hasta = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        assertTrue(pagos.sumConfirmedBetween(desde, hasta).compareTo(new java.math.BigDecimal("21.25")) == 0);
        assertTrue(pagos.countByEstadoIgnoreCaseAndFechaPagoGreaterThanEqualAndFechaPagoLessThan("CONFIRMADO", desde, hasta) == 1);
        Object[] monthly = pagos.reportConfirmedTimeline(desde, hasta).getFirst();
        assertTrue(((Number) monthly[2]).longValue() == 1);
        assertTrue(((java.math.BigDecimal) monthly[3]).compareTo(new java.math.BigDecimal("21.25")) == 0);
    }

    private void guardarPago(Usuario usuario, String amount, String estado) {
        Pago pago = new Pago(); pago.setUsuario(usuario); pago.setMonto(new java.math.BigDecimal(amount));
        pago.setMetodoPago("EFECTIVO"); pago.setEstado(estado); pagos.saveAndFlush(pago);
    }
}
