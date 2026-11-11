package com.gimnasio.sistemagym.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void duplicadoDeClaveSeTraduceA409() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException(
                        "Duplicate key",
                        new DuplicateKeyException("Duplicate entry 'X' for key 'uk'")
                );

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarConflictoDeIntegridad(ex);

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals(409, respuesta.getBody().get("status"));
    }

    @Test
    void errorMysql1062SeTraduceA409() {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'SOC-0001' for key 'uk_socios_codigo'",
                "23000",
                1062
        );

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarConflictoDeIntegridad(
                        new DataIntegrityViolationException("duplicado", sql)
                );

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals(409, respuesta.getBody().get("status"));
    }

    @Test
    void errorDeClaveForaneaSeTraduceA500() {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "Cannot add or update a child row: a foreign key constraint fails",
                "23000",
                1452
        );

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarConflictoDeIntegridad(
                        new DataIntegrityViolationException("fk", sql)
                );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        assertEquals(500, respuesta.getBody().get("status"));
        assertEquals("Error interno", respuesta.getBody().get("error"));
    }

    @Test
    void errorDeCheckSeTraduceA500() {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "Check constraint 'chk_estado' is violated.",
                "HY000",
                3819
        );

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarConflictoDeIntegridad(
                        new DataIntegrityViolationException("check", sql)
                );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        assertEquals(500, respuesta.getBody().get("status"));
    }

    @Test
    void errorDeNotNullSeTraduceA500() {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                "Column 'nombre' cannot be null",
                "23000",
                1048
        );

        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarConflictoDeIntegridad(
                        new DataIntegrityViolationException("not null", sql)
                );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        assertEquals(500, respuesta.getBody().get("status"));
    }

    @Test
    void integridadSinCausaDuplicadaSeTraduceA500() {
        ResponseEntity<Map<String, Object>> respuesta =
                handler.manejarConflictoDeIntegridad(
                        new DataIntegrityViolationException("fallo desconocido")
                );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
        assertEquals(500, respuesta.getBody().get("status"));
        assertEquals("Error interno", respuesta.getBody().get("error"));
    }
}
