
package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios", uniqueConstraints =
        @UniqueConstraint(name = "uk_usuarios_documento",
                columnNames = {"tipo_documento", "numero_documento"}))
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @NotNull(message = "El rol es obligatorio.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @NotBlank(message = "Los nombres son obligatorios.")
    @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios.")
    @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String apellidos;

    @NotBlank(message = "El correo es obligatorio.")
    @Email(message = "El correo no tiene un formato válido.")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @NotBlank(message = "La contraseña cifrada es obligatoria.")
    @Size(max = 255)
    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "version_credenciales", nullable = false)
    private long versionCredenciales;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 3)
    private TipoDocumento tipoDocumento;

    @Size(max = 20, message = "El documento no puede superar los 20 caracteres.")
    @Pattern(regexp = "[0-9]{1,12}",
            message = "El documento debe contener solo números.")
    @Column(name = "numero_documento", length = 20)
    private String numeroDocumento;

    @NotBlank(message = "El estado es obligatorio.")
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Usuario() {
    }

    @PrePersist
    protected void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public long getVersionCredenciales() {
        return versionCredenciales;
    }

    public void setVersionCredenciales(long versionCredenciales) {
        this.versionCredenciales = versionCredenciales;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    @AssertTrue(message = "El tipo y número de documento deben ser coherentes.")
    public boolean isDocumentoValido() {
        if (tipoDocumento == null || numeroDocumento == null) {
            return tipoDocumento == null && numeroDocumento == null;
        }
        return switch (tipoDocumento) {
            case DNI -> numeroDocumento.matches("\\d{8}");
            case CE -> numeroDocumento.matches("\\d{1,12}");
        };
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
