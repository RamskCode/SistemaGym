package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Optional;
import java.util.List;

class IngresoServiceVigenciaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void vigenciaIncluyeElUltimoDiaYRechazaDesdeLaMedianocheSiguienteEnLima() {
        Membresia membresia = new Membresia();
        membresia.setFechaInicio(LocalDate.of(2026, 11, 1));
        membresia.setFechaVencimiento(LocalDate.of(2026, 11, 30));

        IngresoService alFinalDelTreinta = servicioConReloj(
                "2026-12-01T04:59:59Z");
        IngresoService alInicioDelPrimero = servicioConReloj(
                "2026-12-01T05:00:00Z");

        assertTrue(alFinalDelTreinta.estaVigente(membresia),
                "El 30 de noviembre sigue siendo el último día vigente en Lima.");
        assertFalse(alInicioDelPrimero.estaVigente(membresia),
                "Desde las 00:00 del 1 de diciembre la membresía ya terminó.");
    }

    @Test
    void verificaSaldoYPlazoDePagoAlAutorizarAccesoAunqueElEstadoSigaActivo() {
        AplicacionPagoMembresiaRepository aplicaciones = mock(AplicacionPagoMembresiaRepository.class);
        IngresoService servicio = new IngresoService(
                null, null, null, null, null, null, aplicaciones, null, null,
                Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), LIMA));
        Membresia membresia = new Membresia();
        ReflectionTestUtils.setField(membresia, "idMembresia", 91L);
        membresia.setPrecioAcordado(new BigDecimal("100.00"));
        membresia.setFechaLimitePago(LocalDate.of(2026, 11, 9));
        when(aplicaciones.sumarAplicacionesPorMembresia(91L)).thenReturn(new BigDecimal("40.00"));

        assertFalse(servicio.pagoPermiteIngreso(membresia, LocalDate.of(2026, 11, 10)),
                "El estado ACTIVA no debe bastar cuando el plazo acabó y sigue el saldo.");

        membresia.setFechaLimitePago(LocalDate.of(2026, 11, 10));
        assertTrue(servicio.pagoPermiteIngreso(membresia, LocalDate.of(2026, 11, 10)),
                "El último día del plazo de pago se conserva conforme a la regla actual.");
    }

    @Test
    void registroVuelveARechazarMembresiaVencidaAunqueElListadoNoLaMuestre() {
        SocioRepository socios = mock(SocioRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        MembresiaRepository membresias = mock(MembresiaRepository.class);
        IngresoRepository ingresos = mock(IngresoRepository.class);
        AplicacionPagoMembresiaRepository aplicaciones = mock(AplicacionPagoMembresiaRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), LIMA);
        IngresoService servicio = new IngresoService(
                socios, null, membresias, usuarios, null, ingresos, aplicaciones, null, null, clock);
        Socio socio = new Socio();
        ReflectionTestUtils.setField(socio, "idSocio", 42L);
        socio.setTipoDocumento("DNI");
        socio.setNumeroDocumento("12345678");
        socio.setDni("12345678");
        socio.setEstado("ACTIVO");
        Membresia vencida = new Membresia();
        ReflectionTestUtils.setField(vencida, "idMembresia", 91L);
        vencida.setEstado("ACTIVA");
        vencida.setFechaInicio(LocalDate.of(2026, 10, 1));
        vencida.setFechaVencimiento(LocalDate.of(2026, 10, 31));
        when(socios.findById(42L)).thenReturn(Optional.of(socio));
        when(usuarios.findByCorreo("recepcion@example.test")).thenReturn(Optional.of(new Usuario()));
        when(membresias.findBySocio_IdSocio(42L)).thenReturn(List.of(vencida));
        when(ingresos.existsByTipoDocumentoAndNumeroDocumentoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("12345678"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(false);

        assertThrows(com.gimnasio.sistemagym.exception.ConflictoException.class,
                () -> servicio.registrarIngresoMembresia(
                        42L, "765c3b46-ec72-4a99-83fd-51f8d85a63b1", "recepcion@example.test"));
        verify(ingresos, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    private IngresoService servicioConReloj(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), LIMA);
        return new IngresoService(null, null, null, null, null, null, null, null, null, clock);
    }
}
