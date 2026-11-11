
package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "contactos_promocionales")
public class ContactoPromocional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contacto")
    private Long idContacto;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_socio", unique = true)
    private Socio socio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 3)
    private TipoDocumento tipoDocumento;

    @Size(max = 20, message = "El número de documento no puede superar los 20 caracteres.")
    @Column(name = "numero_documento", length = 20)
    private String numeroDocumento;

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "El celular es obligatorio.")
    @Size(max = 20, message = "El celular no puede superar los 20 caracteres.")
    @Column(nullable = false, length = 20)
    private String celular;

    @Email(message = "El correo no tiene un formato válido.")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    @Column(length = 150)
    private String correo;

    @Column(name = "acepta_whatsapp", nullable = false)
    private Boolean aceptaWhatsapp = false;

    @Column(name = "acepta_correo", nullable = false)
    private Boolean aceptaCorreo = false;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres.")
    @Column(length = 255)
    private String observaciones;

    public ContactoPromocional() {
    }

    @PrePersist
    protected void alCrear() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }

    public Long getIdContacto() {
        return idContacto;
    }

    public Socio getSocio() { return socio; }
    public void setSocio(Socio socio) { this.socio = socio; }

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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public Boolean getAceptaWhatsapp() {
        return aceptaWhatsapp;
    }

    public void setAceptaWhatsapp(Boolean aceptaWhatsapp) {
        this.aceptaWhatsapp = aceptaWhatsapp;
    }

    public Boolean getAceptaCorreo() {
        return aceptaCorreo;
    }

    public void setAceptaCorreo(Boolean aceptaCorreo) {
        this.aceptaCorreo = aceptaCorreo;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
