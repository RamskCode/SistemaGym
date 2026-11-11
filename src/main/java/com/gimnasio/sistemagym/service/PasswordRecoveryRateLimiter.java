package com.gimnasio.sistemagym.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded single-process limiter. For multi-node deployments use a shared limiter. */
@Component
public class PasswordRecoveryRateLimiter {
    private static final long WINDOW_MS = 15 * 60_000L;
    private static final int MAX_PER_ADDRESS = 3;
    private static final int MAX_PER_ORIGIN = 10;
    private static final int MAX_KEYS = 20_000;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized boolean allow(String email, String origin) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MS);
        if (windows.size() >= MAX_KEYS) return false;
        String addressKey = "email:" + sha256(email.trim().toLowerCase(java.util.Locale.ROOT));
        String originKey = "origin:" + sha256(origin == null ? "unknown" : origin);
        Window address = windows.computeIfAbsent(addressKey, ignored -> new Window(now));
        Window source = windows.computeIfAbsent(originKey, ignored -> new Window(now));
        if (address.count >= MAX_PER_ADDRESS || source.count >= MAX_PER_ORIGIN) return false;
        address.count++;
        source.count++;
        return true;
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Window {
        private final long startedAt;
        private int count;
        private Window(long startedAt) { this.startedAt = startedAt; }
    }
}
