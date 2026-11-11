package com.gimnasio.sistemagym.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Holds only a digest; the one-time random token is emitted only to the server console. */
@Component
public class InstallationTokenService {

    private static final Logger log = LoggerFactory.getLogger(InstallationTokenService.class);
    private final AtomicReference<byte[]> tokenDigest = new AtomicReference<>();

    public String generateAndPrint() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        tokenDigest.set(digest(token));
        java.util.Arrays.fill(random, (byte) 0);
        log.warn("ONBOARDING INICIAL — token de instalación (solo consola del servidor): {}", token);
        return token;
    }

    public boolean matches(String candidate) {
        byte[] expected = tokenDigest.get();
        if (expected == null || candidate == null || candidate.isBlank()) return false;
        return MessageDigest.isEqual(expected, digest(candidate));
    }

    public void disable() {
        byte[] previous = tokenDigest.getAndSet(null);
        if (previous != null) java.util.Arrays.fill(previous, (byte) 0);
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no está disponible.", impossible);
        }
    }
}
