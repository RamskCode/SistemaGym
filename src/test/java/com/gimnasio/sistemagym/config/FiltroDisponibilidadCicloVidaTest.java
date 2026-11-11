package com.gimnasio.sistemagym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gimnasio.sistemagym.service.DisponibilidadCicloVida;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltroDisponibilidadCicloVidaTest {

    @Test
    void bloqueaApiCon503HastaCompletarLaRevisionInicial() throws Exception {
        DisponibilidadCicloVida disponibilidad = new DisponibilidadCicloVida(true);
        FiltroDisponibilidadCicloVida filtro = new FiltroDisponibilidadCicloVida(disponibilidad);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingresos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filtro.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(true, response.getContentAsString().contains("verificando las membresías"));
        assertNull(chain.getRequest(), "La petición operativa no debe alcanzar el controller.");
    }

    @Test
    void liberaApiTrasRevisionYNoInterfiereCuandoSchedulerEstaDesactivado() throws Exception {
        DisponibilidadCicloVida disponibilidad = new DisponibilidadCicloVida(true);
        FiltroDisponibilidadCicloVida filtro = new FiltroDisponibilidadCicloVida(disponibilidad);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingresos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disponibilidad.marcarDisponible();
        filtro.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());

        DisponibilidadCicloVida desactivada = new DisponibilidadCicloVida(false);
        MockFilterChain cadenaSinScheduler = new MockFilterChain();
        new FiltroDisponibilidadCicloVida(desactivada).doFilter(
                request, new MockHttpServletResponse(), cadenaSinScheduler);
        assertNotNull(cadenaSinScheduler.getRequest());
    }
}
