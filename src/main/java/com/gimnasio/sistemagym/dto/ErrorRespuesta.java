package com.gimnasio.sistemagym.dto;

public class ErrorRespuesta {

    private int status;
    private String error;
    private String mensaje;

    public ErrorRespuesta(int status, String error, String mensaje) {
        this.status = status;
        this.error = error;
        this.mensaje = mensaje;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMensaje() {
        return mensaje;
    }
}