package com.gimnasio.sistemagym.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

/** Partial self-service update. Unknown/protected fields are rejected, not ignored. */
public class ActualizarMiCuentaRequest {
    @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
    private String nombres;
    @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
    private String apellidos;
    private TipoDocumento tipoDocumento;
    @Size(max = 20, message = "El documento no puede superar los 20 caracteres.")
    private String numeroDocumento;
    private boolean tipoDocumentoIncluido;
    private boolean numeroDocumentoIncluido;
    private final Set<String> camposNoPermitidos = new HashSet<>();

    public String nombres() { return nombres; }
    public String apellidos() { return apellidos; }
    public TipoDocumento tipoDocumento() { return tipoDocumento; }
    public String numeroDocumento() { return numeroDocumento; }
    public boolean tipoDocumentoIncluido() { return tipoDocumentoIncluido; }
    public boolean numeroDocumentoIncluido() { return numeroDocumentoIncluido; }

    public void setNombres(String nombres) { this.nombres = nombres; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
        this.tipoDocumentoIncluido = true;
    }
    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
        this.numeroDocumentoIncluido = true;
    }

    @JsonAnySetter
    public void capturarCampoNoPermitido(String nombre, Object valor) {
        camposNoPermitidos.add(nombre);
    }

    @AssertTrue(message = "Los nombres, cuando se envían, no pueden estar vacíos.")
    @JsonIgnore
    public boolean isNombresValidos() {
        return nombres == null || !nombres.isBlank();
    }

    @AssertTrue(message = "Los apellidos, cuando se envían, no pueden estar vacíos.")
    @JsonIgnore
    public boolean isApellidosValidos() {
        return apellidos == null || !apellidos.isBlank();
    }

    @AssertTrue(message = "El tipo y número de documento deben enviarse juntos y ser válidos.")
    @JsonIgnore
    public boolean isDocumentoValido() {
        if (tipoDocumentoIncluido != numeroDocumentoIncluido) return false;
        if (!tipoDocumentoIncluido) return true;
        if (tipoDocumento == null || numeroDocumento == null) {
            return tipoDocumento == null && numeroDocumento == null;
        }
        return switch (tipoDocumento) {
            case DNI -> numeroDocumento.matches("[0-9]{8}");
            case CE -> numeroDocumento.matches("[0-9]{1,12}");
        };
    }

    @AssertTrue(message = "La solicitud contiene campos que no se pueden modificar desde Mi cuenta.")
    @JsonIgnore
    public boolean isSinCamposNoPermitidos() {
        return camposNoPermitidos.isEmpty();
    }

    @JsonIgnore
    public boolean tieneCambios() {
        return nombres != null || apellidos != null
                || tipoDocumentoIncluido || numeroDocumentoIncluido;
    }
}
