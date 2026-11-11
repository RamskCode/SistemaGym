package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PagoRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportesServiceTest {
    @Test
    void calculaTotalesExclusivamenteConPagosConfirmadosYAdmitePeriodoVacio() {
        PagoRepository pagos = mock(PagoRepository.class);
        SocioRepository socios = mock(SocioRepository.class);
        MembresiaRepository membresias = mock(MembresiaRepository.class);
        IngresoRepository ingresos = mock(IngresoRepository.class);
        PlanRepository planes = mock(PlanRepository.class);
        LocalDate desde = LocalDate.of(2026, 4, 1), hasta = LocalDate.of(2026, 4, 30);
        when(pagos.reportConfirmedTimeline(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(socios.reportNewTimeline(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(ingresos.reportTimeline(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(membresias.reportPlansFiltered(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull())).thenReturn(List.of());
        when(pagos.sumConfirmedBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("25.50"));
        when(pagos.countByEstadoIgnoreCaseAndFechaPagoGreaterThanEqualAndFechaPagoLessThan(org.mockito.ArgumentMatchers.eq("CONFIRMADO"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(socios.countByFechaRegistroGreaterThanEqualAndFechaRegistroLessThan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(membresias.countByEstadoIgnoreCase("ACTIVA")).thenReturn(2L);
        when(membresias.countByEstadoIgnoreCase("VENCIDA")).thenReturn(0L);
        when(membresias.reportUpcoming(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(ingresos.reportSocioCount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(ingresos.reportVisitanteCount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(ingresos.reportDistinctPeople(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] {com.gimnasio.sistemagym.entity.TipoIngreso.SOCIO,
                                com.gimnasio.sistemagym.entity.TipoDocumento.DNI, "12345678"},
                        new Object[] {com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE,
                                com.gimnasio.sistemagym.entity.TipoDocumento.DNI, "12345678"},
                        new Object[] {com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE,
                                com.gimnasio.sistemagym.entity.TipoDocumento.CE, "ABC123"}));
        var result = new ReportesService(pagos, socios, membresias, ingresos, planes).resumen(desde, hasta, null);
        assertEquals(new BigDecimal("25.50"), result.ingresosConfirmados());
        assertEquals(1, result.pagosConfirmados());
        assertEquals(2, result.membresiasActivas());
        assertEquals(0, result.sociosNuevos());
        assertEquals(List.of(), result.pagosPorFecha());
        assertEquals(2, result.personasUnicas());
        assertEquals(1, result.personasUnicasSocios());
        assertEquals(2, result.personasUnicasVisitantes());
    }

    @Test
    void rechazaRangoInvertidoYSuperiorADosAnios() {
        var service = new ReportesService(mock(PagoRepository.class), mock(SocioRepository.class),
                mock(MembresiaRepository.class), mock(IngresoRepository.class), mock(PlanRepository.class));
        assertThrows(IllegalArgumentException.class, () -> service.resumen(LocalDate.parse("2026-02-02"), LocalDate.parse("2026-02-01"), null));
        assertThrows(IllegalArgumentException.class, () -> service.resumen(LocalDate.parse("2020-01-01"), LocalDate.parse("2026-01-01"), null));
    }
}
