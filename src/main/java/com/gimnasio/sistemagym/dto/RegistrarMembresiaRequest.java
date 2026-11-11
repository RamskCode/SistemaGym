
package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class RegistrarMembresiaRequest {

    @NotNull(message = "El ID del socio es obligatorio.")
    @Positive(message = "El ID del socio debe ser mayor que cero.")
    private Long idSocio;

    @NotNull(message = "El ID del plan es obligatorio.")
    @Positive(message = "El ID del plan debe ser mayor que cero.")
    private Long idPlan;

    @NotNull(message = "El importe del primer pago es obligatorio.")
    @DecimalMin(
            value = "40.00",
            inclusive = true,
            message = "El primer pago debe ser de al menos S/ 40.00."
    )
    private BigDecimal montoPrimerPago;

    @NotBlank(message = "El método de pago es obligatorio.")
    @Size(max = 30, message = "El método de pago no puede superar 30 caracteres.")
    private String metodoPago;

    @Size(max = 100, message = "La referencia no puede superar 100 caracteres.")
    private String referencia;

    @Size(max = 255, message = "Las observaciones no pueden superar 255 caracteres.")
    private String observaciones;

    public Long getIdSocio() {
        return idSocio;
    }

    public void setIdSocio(Long idSocio) {
        this.idSocio = idSocio;
    }

    public Long getIdPlan() {
        return idPlan;
    }

    public void setIdPlan(Long idPlan) {
        this.idPlan = idPlan;
    }

    public BigDecimal getMontoPrimerPago() {
        return montoPrimerPago;
    }

    public void setMontoPrimerPago(BigDecimal montoPrimerPago) {
        this.montoPrimerPago = montoPrimerPago;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
