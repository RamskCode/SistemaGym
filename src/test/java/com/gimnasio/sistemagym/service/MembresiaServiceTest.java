package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.MembresiaRespuesta;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembresiaServiceTest {

    @Mock
    private MembresiaRepository membresiaRepository;

    @Mock
    private SocioRepository socioRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PagoService pagoService;

    @InjectMocks
    private MembresiaService membresiaService;

    @Test
    void buscarPorIdInexistenteLanzaRecursoNoEncontrado() {
        when(membresiaRepository.findById(99L)).thenReturn(Optional.empty());

        RecursoNoEncontradoException ex = assertThrows(
                RecursoNoEncontradoException.class,
                () -> membresiaService.buscarPorId(99L)
        );

        assertEquals("No se encontro la membresia con ID: 99", ex.getMessage());
    }

    @Test
    void buscarPorIdDevuelveDtoConSocioYPlan() {
        when(membresiaRepository.findById(1L))
                .thenReturn(Optional.of(membresia()));

        MembresiaRespuesta respuesta = membresiaService.buscarPorId(1L);

        assertEquals("SOC-0015", respuesta.socio().codigoSocio());
        assertEquals("Ana", respuesta.socio().nombres());
        assertEquals(15L, respuesta.plan().idPlan());
        assertEquals("Mensual", respuesta.plan().nombre());
        assertEquals(new BigDecimal("100.00"), respuesta.precioAcordado());
        assertEquals("ACTIVA", respuesta.estado());
        assertNull(respuesta.motivoCancelacion());
    }

    @Test
    void listarTodasDevuelveDtos() {
        when(membresiaRepository.findAll()).thenReturn(List.of(membresia()));

        List<MembresiaRespuesta> respuesta = membresiaService.listarTodas();

        assertEquals(1, respuesta.size());
        assertEquals("SOC-0015", respuesta.getFirst().socio().codigoSocio());
        assertEquals("Mensual", respuesta.getFirst().plan().nombre());
    }

    private Membresia membresia() {
        Socio socio = new Socio();
        socio.setCodigoSocio("SOC-0015");
        socio.setDni("12345678");
        socio.setNombres("Ana");
        socio.setApellidos("Prueba");
        socio.setEstado("ACTIVO");

        Plan plan = new Plan();
        plan.setIdPlan(15L);
        plan.setNombre("Mensual");
        plan.setDescripcion("Plan mensual");
        plan.setPrecio(new BigDecimal("100.00"));
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");

        Membresia membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(new BigDecimal("100.00"));
        membresia.setFechaInicio(LocalDate.of(2026, 1, 1));
        membresia.setFechaVencimiento(LocalDate.of(2026, 1, 31));
        membresia.setFechaLimitePago(LocalDate.of(2026, 1, 6));
        membresia.setEstado("ACTIVA");
        return membresia;
    }
}
