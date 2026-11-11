package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void aplicaLasCuatroReglasYNoImponeMaximoDeCaracteresDe24() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate("Aa1!abc"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("1234567!"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("Abcdefg!"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("Abcdefg1"));
        assertDoesNotThrow(() -> policy.validate("Alpine1!"));
        assertDoesNotThrow(() -> policy.validate("abc123!x"));
        assertDoesNotThrow(() -> policy.validate("A1!" + "b".repeat(40)));
    }

    @Test
    void rechazaContrasenasComunesPredeciblesOVacias() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate("password123!"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("aaaaaaaa"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("1234567!"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("        "));
    }

    @Test
    void aceptaHasta72BytesYRechazaElExcesoTecnicoDeBcrypt() {
        assertDoesNotThrow(() -> policy.validate("A1!" + "b".repeat(69)));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("😀".repeat(24)));
    }
}
