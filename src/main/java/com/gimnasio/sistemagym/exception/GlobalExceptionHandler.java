package com.gimnasio.sistemagym.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarCuerpoInvalido(
            HttpMessageNotReadableException ex) {

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Solicitud inválida",
                "mensaje", "El cuerpo de la solicitud no tiene un formato válido."
        );

        return ResponseEntity.badRequest().body(respuesta);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarErroresValidacion(
            MethodArgumentNotValidException ex) {

        List<String> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .toList();

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Error de validación",
                "errores", errores
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarErroresDeNegocio(
            IllegalArgumentException ex) {

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Solicitud inválida",
                "mensaje", ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> manejarConflictoDeEstado(
            IllegalStateException ex) {

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", "Conflicto",
                "mensaje", ex.getMessage() == null
                        ? "La operación no es válida en el estado actual."
                        : ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(respuesta);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex) {

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Recurso no encontrado",
                "mensaje", ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(respuesta);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarEntidadNoEncontrada(
            EntityNotFoundException ex) {

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Recurso no encontrado",
                "mensaje", ex.getMessage() == null
                        ? "No se encontró el recurso solicitado."
                        : ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(respuesta);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, Object>> manejarConflicto(
            ConflictoException ex) {

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", "Conflicto",
                "mensaje", ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta);
    }

    @ExceptionHandler(ConflictoClaveIdempotenciaException.class)
    public ResponseEntity<Map<String, Object>> manejarConflictoClaveIdempotencia(
            ConflictoClaveIdempotenciaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", "Conflicto",
                "codigo", "IDEMPOTENCY_KEY_REUSED",
                "mensaje", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarConflictoDeIntegridad(
            DataIntegrityViolationException ex) {

        if (esDuplicado(ex)) {
            Map<String, Object> respuesta = Map.of(
                    "status", HttpStatus.CONFLICT.value(),
                    "error", "Conflicto",
                    "mensaje", "La operación entra en conflicto con una restricción de datos."
            );

            return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta);
        }

        log.error("Violación de integridad no duplicada", ex);

        Map<String, Object> respuesta = Map.of(
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Error interno",
                "mensaje", "No se pudo completar la operación por un conflicto de integridad de datos."
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(respuesta);
    }

    private boolean esDuplicado(DataIntegrityViolationException ex) {

        if (ex instanceof DuplicateKeyException) {
            return true;
        }

        Throwable causa = ex.getCause();

        while (causa != null) {
            if (causa instanceof DuplicateKeyException) {
                return true;
            }

            if (causa instanceof SQLException sqlException) {
                if (sqlException.getErrorCode() == 1062) {
                    return true;
                }
                if ("23505".equals(sqlException.getSQLState())) {
                    return true;
                }
                if ("23000".equals(sqlException.getSQLState())
                        && contieneDuplicado(sqlException.getMessage())) {
                    return true;
                }
            }

            causa = causa.getCause();
        }

        return contieneDuplicado(ex.getMostSpecificCause().getMessage());
    }

    private boolean contieneDuplicado(String mensaje) {
        return mensaje != null
                && mensaje.toLowerCase(Locale.ROOT).contains("duplicate entry");
    }
}
