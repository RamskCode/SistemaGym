package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Datos del pago posterior; el responsable se obtiene de la autenticacion. */
public record RegistrarPagoPosteriorRequest(
        @NotNull(message = "El monto del pago es obligatorio.")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero.")
        BigDecimal monto,

        @NotBlank(message = "El metodo de pago es obligatorio.")
        @Size(max = 30, message = "El metodo de pago no puede superar 30 caracteres.")
        String metodoPago,

        @Size(max = 100, message = "La referencia no puede superar 100 caracteres.")
        String referencia,

        @Size(max = 255, message = "Las observaciones no pueden superar 255 caracteres.")
        String observaciones) {
}
