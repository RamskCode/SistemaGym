package com.gimnasio.sistemagym.util;

import com.gimnasio.sistemagym.entity.TipoDocumento;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentoIdentidadTest {
    @Test
    void validaDniExacto() {
        assertTrue(DocumentoIdentidad.esValido(TipoDocumento.DNI, "12345678"));
        for (String value : new String[] {"1234567", "123456789", "1234AB78", "1234-678"}) {
            assertFalse(DocumentoIdentidad.esValido(TipoDocumento.DNI, value));
        }
    }

    @Test
    void validaCeNumericoHastaDoceDigitos() {
        assertEquals("001234", DocumentoIdentidad.normalizar(TipoDocumento.CE, "001234"));
        assertTrue(DocumentoIdentidad.esValido(TipoDocumento.CE, "1".repeat(12)));
        for (String value : new String[] {"1".repeat(13), "AB12", "12 34", "12-34", "12!34"}) {
            assertFalse(DocumentoIdentidad.esValido(TipoDocumento.CE, value));
        }
        assertThrows(IllegalArgumentException.class,
                () -> DocumentoIdentidad.normalizar(TipoDocumento.CE, "AB12"));
    }
}
