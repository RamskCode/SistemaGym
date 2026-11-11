package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import com.gimnasio.sistemagym.config.PruebasDatasourceGuard;
import java.util.concurrent.Callable;

class TareaMembresiasProgramadaTest {

    @Test
    void programaMedianocheEnLimaYExigeActivacionExplicita() throws Exception {
        Scheduled scheduled = TareaMembresiasProgramada.class
                .getMethod("revisarMembresiasVencidas")
                .getAnnotation(Scheduled.class);
        ConditionalOnProperty condition = TareaMembresiasProgramada.class
                .getAnnotation(ConditionalOnProperty.class);

        assertEquals("${sistemagym.membresias.scheduler.cron:0 0 0 * * *}",
                scheduled.cron());
        assertEquals("${sistemagym.membresias.scheduler.zone:America/Lima}",
                scheduled.zone());
        assertEquals("enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,
                TareaMembresiasProgramada.class.getAnnotation(Order.class).value());
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
                PruebasDatasourceGuard.class.getAnnotation(Order.class).value());
    }

    @Test
    void elArranqueEjecutaLaMismaRevisionAseguradaPorElBloqueo() throws Exception {
        MembresiaCicloVidaService ciclo = mock(MembresiaCicloVidaService.class);
        BloqueoDistribuidoCicloVida bloqueo = mock(BloqueoDistribuidoCicloVida.class);
        when(bloqueo.ejecutarSiDisponible(any(Callable.class))).thenAnswer(invocacion -> {
            Callable<?> trabajo = invocacion.getArgument(0);
            return trabajo.call();
        });
        when(ciclo.procesarCicloVida()).thenReturn(
                new MembresiaCicloVidaService.ResultadoCicloVida(0, 0,
                        java.time.LocalDate.of(2026, 12, 1)));
        DisponibilidadCicloVida disponibilidad = new DisponibilidadCicloVida(true);
        TareaMembresiasProgramada tarea = new TareaMembresiasProgramada(
                ciclo, bloqueo, disponibilidad);

        assertFalse(disponibilidad.estaDisponible());
        tarea.run(new DefaultApplicationArguments(new String[0]));

        verify(bloqueo).ejecutarSiDisponible(any(Callable.class));
        verify(ciclo).procesarCicloVida();
        org.junit.jupiter.api.Assertions.assertTrue(disponibilidad.estaDisponible());
    }

    @Test
    void falloDeRevisionDejaElBackendCerradoYAbortaElArranque() throws Exception {
        MembresiaCicloVidaService ciclo = mock(MembresiaCicloVidaService.class);
        BloqueoDistribuidoCicloVida bloqueo = mock(BloqueoDistribuidoCicloVida.class);
        when(bloqueo.ejecutarSiDisponible(any(Callable.class))).thenReturn(null);
        DisponibilidadCicloVida disponibilidad = new DisponibilidadCicloVida(true);
        TareaMembresiasProgramada tarea = new TareaMembresiasProgramada(
                ciclo, bloqueo, disponibilidad);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> tarea.run(new DefaultApplicationArguments(new String[0])));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("inicio cancelado"));
        assertFalse(disponibilidad.estaDisponible());
        org.mockito.Mockito.verifyNoInteractions(ciclo);
    }

    @Test
    void excepcionDuranteLaRevisionImpideMarcarDisponible() throws Exception {
        MembresiaCicloVidaService ciclo = mock(MembresiaCicloVidaService.class);
        BloqueoDistribuidoCicloVida bloqueo = mock(BloqueoDistribuidoCicloVida.class);
        when(bloqueo.ejecutarSiDisponible(any(Callable.class)))
                .thenThrow(new IllegalStateException("fallo controlado"));
        DisponibilidadCicloVida disponibilidad = new DisponibilidadCicloVida(true);
        TareaMembresiasProgramada tarea = new TareaMembresiasProgramada(
                ciclo, bloqueo, disponibilidad);

        assertThrows(IllegalStateException.class,
                () -> tarea.run(new DefaultApplicationArguments(new String[0])));
        assertFalse(disponibilidad.estaDisponible());
    }
}
