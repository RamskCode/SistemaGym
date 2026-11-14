package com.gimnasio.sistemagym.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContrasenaInicialSegura {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] LETTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%&*+-_=.?".toCharArray();
    private static final char[] ALL =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*+-_=.?".toCharArray();

    private ContrasenaInicialSegura() { }

    public static String generar(PasswordPolicy policy) {
        for (;;) {
            List<Character> chars = new ArrayList<>(24);
            chars.add(seleccionar(LETTERS));
            chars.add(seleccionar(DIGITS));
            chars.add(seleccionar(SYMBOLS));
            while (chars.size() < 24) chars.add(seleccionar(ALL));
            Collections.shuffle(chars, RANDOM);
            String candidate = chars.stream().collect(
                    StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
            try {
                policy.validate(candidate);
                return candidate;
            } catch (IllegalArgumentException rejected) {
                // Retry if random output coincidentally matches a weak-password pattern.
            }
        }
    }

    private static char seleccionar(char[] alphabet) {
        return alphabet[RANDOM.nextInt(alphabet.length)];
    }
}
