package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Configuracion;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ConfiguracionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguracionServiceTest {

    @Test
    void inicializaPrecioDiarioAusenteSoloConValorAdministrativoValido() {
        ConfiguracionRepository repository = mock(ConfiguracionRepository.class);
        when(repository.findLockedByClave("PRECIO_PASE_DIARIO")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Configuracion.class))).thenAnswer(call -> {
            Configuracion value = call.getArgument(0);
            value.setClave("PRECIO_PASE_DIARIO");
            return value;
        });

        var result = new ConfiguracionService(repository)
                .actualizar("precio_pase_diario", "17.50");

        assertEquals("PRECIO_PASE_DIARIO", result.clave());
        assertEquals("17.50", result.valor());
        verify(repository).saveAndFlush(any(Configuracion.class));
    }

    @Test
    void noCreaOtrasConfiguracionesYRechazaTarifaNoValida() {
        ConfiguracionRepository repository = mock(ConfiguracionRepository.class);
        ConfiguracionService service = new ConfiguracionService(repository);
        when(repository.findLockedByClave("OTRA_CLAVE")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar("OTRA_CLAVE", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizar("PRECIO_PASE_DIARIO", "0"));
    }
}
