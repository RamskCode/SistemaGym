package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public class CorreccionAsistenciaLaboralRequest {

    private Instant fechaEntradaUtc;
    private Instant fechaSalidaUtc;

    @NotBlank(message = "El motivo es obligatorio.")
    private String motivo;

    public Instant getFechaEntradaUtc() {
        return fechaEntradaUtc;
    }

    public void setFechaEntradaUtc(Instant fechaEntradaUtc) {
        this.fechaEntradaUtc = fechaEntradaUtc;
    }

    public Instant getFechaSalidaUtc() {
        return fechaSalidaUtc;
    }

    public void setFechaSalidaUtc(Instant fechaSalidaUtc) {
        this.fechaSalidaUtc = fechaSalidaUtc;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    @AssertTrue(message = "Debe indicar al menos un horario para corregir.")
    public boolean isHorarioInformado() {
        return fechaEntradaUtc != null || fechaSalidaUtc != null;
    }

    @AssertTrue(message = "El motivo no puede superar los 500 caracteres.")
    public boolean isMotivoDentroDelLimite() {
        return motivo == null || motivo.codePointCount(0, motivo.length()) <= 500;
    }

    @JsonAnySetter
    public void rechazarPropiedadDesconocida(String propiedad, Object valor) {
        throw new IllegalArgumentException(
                "La propiedad '" + propiedad + "' no está permitida en esta solicitud.");
    }
}
