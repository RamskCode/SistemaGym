package com.gimnasio.sistemagym.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    /** BCrypt considers at most 72 UTF-8 bytes; reject overflow rather than truncate it. */
    public static final int MAX_UTF8_BYTES = 72;
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "password123!", "contrasena", "contraseña",
            "12345678", "123456789", "qwerty", "qwerty123", "admin123",
            "welcome", "letmein", "iloveyou", "abc123", "gym12345");

    public void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede contener solo espacios.");
        }
        if (password.length() > MAX_UTF8_BYTES
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "La contraseña supera el límite técnico de 72 bytes UTF-8.");
        }
        if (password.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("La contraseña debe incluir al menos una letra.");
        }
        if (password.codePoints().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("La contraseña debe incluir al menos un número.");
        }
        if (password.codePoints().noneMatch(PasswordPolicy::isSymbol)) {
            throw new IllegalArgumentException("La contraseña debe incluir al menos un símbolo.");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(normalized) || predictable(normalized)) {
            throw new IllegalArgumentException(
                    "Elige una contraseña menos común o predecible.");
        }
    }

    private static boolean isSymbol(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL;
    }

    private boolean predictable(String password) {
        if (password.codePoints().distinct().count() <= 2) return true;
        String compact = password.replaceAll("\\s+", "");
        return compact.matches("(?i)(012345|123456|234567|345678|456789|abcdef|qwerty|asdfgh).*" )
                || compact.matches("(?i).*(012345|123456|234567|345678|456789|abcdef|qwerty|asdfgh)$");
    }
}
