package com.gimnasio.sistemagym.exception;

public class ConflictoClaveIdempotenciaException extends RuntimeException {
    public ConflictoClaveIdempotenciaException() {
        super("La clave idempotente ya está asociada a una solicitud diferente.");
    }
}
