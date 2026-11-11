package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Update request that distinguishes omitted document fields from explicit nulls. */
public class ActualizarUsuarioRequest {

    @NotBlank(message = "Los nombres son obligatorios.")
    @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios.")
    @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio.")
    @Email(message = "El correo no tiene un formato válido.")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    private String correo;

    private TipoDocumento tipoDocumento;

    @Size(max = 20, message = "El documento no puede superar los 20 caracteres.")
    @Pattern(regexp = "[A-Za-z0-9]{1,12}",
            message = "El documento solo puede contener letras y números, sin espacios ni guiones.")
    private String numeroDocumento;

    private boolean tipoDocumentoIncluido;
    private boolean numeroDocumentoIncluido;

    public ActualizarUsuarioRequest() {
    }

    public ActualizarUsuarioRequest(String nombres, String apellidos, String correo) {
        this(nombres, apellidos, correo, null, null);
        tipoDocumentoIncluido = false;
        numeroDocumentoIncluido = false;
    }

    public ActualizarUsuarioRequest(String nombres, String apellidos, String correo,
            TipoDocumento tipoDocumento, String numeroDocumento) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.correo = correo;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.tipoDocumentoIncluido = true;
        this.numeroDocumentoIncluido = true;
    }

    public String nombres() {
        return nombres;
    }

    public String apellidos() {
        return apellidos;
    }

    public String correo() {
        return correo;
    }

    public TipoDocumento tipoDocumento() {
        return tipoDocumento;
    }

    public String numeroDocumento() {
        return numeroDocumento;
    }

    public boolean documentoIncluido() {
        return tipoDocumentoIncluido && numeroDocumentoIncluido;
    }

    public boolean algunCampoDocumentoIncluido() {
        return tipoDocumentoIncluido || numeroDocumentoIncluido;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
        this.tipoDocumentoIncluido = true;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
        this.numeroDocumentoIncluido = true;
    }

    @AssertTrue(message = "El tipo y número de documento deben enviarse juntos.")
    @JsonIgnore
    public boolean isDocumentoEnviadoEnPareja() {
        return tipoDocumentoIncluido == numeroDocumentoIncluido;
    }
}
