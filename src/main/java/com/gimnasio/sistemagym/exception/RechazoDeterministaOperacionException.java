package com.gimnasio.sistemagym.exception;

/** Internal marker for a deterministic business rule failure from a transaction callback. */
public class RechazoDeterministaOperacionException extends RuntimeException {
    public RechazoDeterministaOperacionException(IllegalStateException causa) {
        super(causa.getMessage(), causa);
    }
}
