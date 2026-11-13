package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank(message = "Los nombres son obligatorios.")
        @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
        String apellidos,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
        String correo,

        @JsonIgnore String contrasena,

        @NotBlank(message = "El rol es obligatorio.")
        @Size(max = 30, message = "El rol no puede superar los 30 caracteres.")
        String rol,

        @NotNull(message = "El tipo de documento es obligatorio.")
        TipoDocumento tipoDocumento,

        @NotBlank(message = "El número de documento es obligatorio.")
        @Size(max = 20, message = "El documento no puede superar los 20 caracteres.")
        @Pattern(regexp = "[0-9]{1,12}",
                message = "El documento solo puede contener de 1 a 12 dígitos.")
        String numeroDocumento) {

    public CrearUsuarioRequest(String nombres, String apellidos, String correo,
            String rol) {
        this(nombres, apellidos, correo, null, rol, null, null);
    }

    /** Legacy constructor only; initial passwords are deliberately ignored. */
    public CrearUsuarioRequest(String nombres, String apellidos, String correo,
            String ignoredPassword, String rol) {
        this(nombres, apellidos, correo, ignoredPassword, rol, null, null);
    }

    @AssertTrue(message = "El tipo y número de documento deben informarse juntos.")
    @JsonIgnore
    public boolean isDocumentoEnviadoEnPareja() {
        return (tipoDocumento == null) == (numeroDocumento == null);
    }

    @AssertTrue(message = "El DNI debe contener exactamente 8 dígitos.")
    @JsonIgnore
    public boolean isDocumentoCompatible() {
        return numeroDocumento == null || tipoDocumento == null
                || tipoDocumento != TipoDocumento.DNI || numeroDocumento.matches("[0-9]{8}");
    }
}
