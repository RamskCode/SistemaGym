package com.gimnasio.sistemagym.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.util.DocumentoIdentidad;

/**
 * Datos para registrar un ingreso. Para un visitante se exigen nombre y
 * celular; para un socio solo se usa el documento (o {@code idSocio} como
 * apoyo cuando el registro no tiene DNI).
 */
public record RegistrarIngresoRequest(

        @NotBlank(message = "El tipo de documento es obligatorio.")
        @Size(max = 3, message = "El tipo de documento no es válido.")
        String tipoDocumento,

        @NotBlank(message = "El número de documento es obligatorio.")
        @Size(max = 20, message = "El número de documento no puede superar los 20 caracteres.")
        String numeroDocumento,

        Long idSocio,

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
        String nombre,

        @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
        String apellidos,

        @Size(max = 20, message = "El celular no puede superar los 20 caracteres.")
        String celular,

        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
        String correo,

        @Size(max = 30, message = "El método de pago no puede superar los 30 caracteres.")
        String metodoPago,

        @Size(max = 100, message = "La referencia no puede superar los 100 caracteres.")
        String referencia,

        @Size(max = 36, message = "La clave de idempotencia no es válida.")
        String claveIdempotencia) {

    @AssertTrue(message = "El número de documento no cumple el formato del tipo seleccionado.")
    public boolean isDocumentoValido() {
        if (tipoDocumento == null || numeroDocumento == null) return true;
        try {
            return DocumentoIdentidad.esValido(TipoDocumento.valueOf(
                    tipoDocumento.toUpperCase(java.util.Locale.ROOT)), numeroDocumento);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
