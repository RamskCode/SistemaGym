package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.RegistrarPlanRequest;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PlanService planService;

    @Test
    void registrarCreaUnPlanNuevoSinIdentificador() {
        when(planRepository.existsByNombreIgnoreCase("Mensual"))
                .thenReturn(false);
        when(planRepository.save(any(Plan.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        planService.registrar(new RegistrarPlanRequest(
                " Mensual ", "Plan mensual",
                new BigDecimal("100.00"), 30
        ));

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(captor.capture());

        Plan guardado = captor.getValue();
        assertNull(guardado.getIdPlan());
        assertEquals("Mensual", guardado.getNombre());
        assertEquals("Plan mensual", guardado.getDescripcion());
        assertEquals(new BigDecimal("100.00"), guardado.getPrecio());
        assertEquals(30, guardado.getDuracionDias());
        assertEquals("ACTIVO", guardado.getEstado());
    }

    @Test
    void registrarRechazaNombreDuplicadoSinGuardar() {
        when(planRepository.existsByNombreIgnoreCase("Mensual"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> planService.registrar(new RegistrarPlanRequest(
                        "Mensual", "Plan mensual",
                        new BigDecimal("100.00"), 30
                ))
        );

        verify(planRepository, never()).save(any(Plan.class));
    }
}
