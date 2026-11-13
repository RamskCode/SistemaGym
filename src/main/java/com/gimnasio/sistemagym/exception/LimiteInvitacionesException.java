package com.gimnasio.sistemagym.exception;

import java.time.Instant;

/** Safe, typed rejection for the per-user invitation rate limit. */
public class LimiteInvitacionesException extends RuntimeException {
    private final Instant retryAt;

    public LimiteInvitacionesException(Instant retryAt) {
        super("Se alcanzó el límite de invitaciones.");
        this.retryAt = retryAt;
    }

    public Instant getRetryAt() {
        return retryAt;
    }
}
