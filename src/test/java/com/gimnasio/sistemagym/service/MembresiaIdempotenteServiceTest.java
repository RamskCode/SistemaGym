package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembresiaIdempotenteServiceTest {

    private final MembresiaIdempotenteService service = new MembresiaIdempotenteService(
            mock(OperacionPagoIdempotenteService.class), mock(MembresiaService.class));

    @Test
    void parseaClaveUuidValidaSinCambiarSuValor() {
        UUID expected = UUID.fromString("d4c8e270-5a0e-4a1f-9df2-07282a923455");

        assertEquals(expected, service.parsearClaveIdempotencia(expected.toString()));
    }

    @Test
    void rechazaClavesNoUuidConElMensajeExistente() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.parsearClaveIdempotencia("not-a-uuid"));

        assertEquals("Idempotency-Key debe tener formato UUID válido.", exception.getMessage());
    }

    @Test
    void rechazaUuidConFormatoNoCanonico() {
        assertThrows(IllegalArgumentException.class,
                () -> service.parsearClaveIdempotencia("d4c8e2705a0e4a1f9df207282a923455"));
    }
}
