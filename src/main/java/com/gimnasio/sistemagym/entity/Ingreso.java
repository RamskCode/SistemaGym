package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Registro único de ingreso al gimnasio, tanto de socios como de visitantes.
 * La exclusividad entre socio y visitante, y la coherencia con el pago del
 * pase diario, se garantizan también mediante restricciones del esquema.
 */
@Entity
@Table(name = "ingresos")
public class Ingreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ingreso")
    private Long idIngreso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_socio")
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contacto")
    private ContactoPromocional contacto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pase_origen")
    private Ingreso paseOrigen;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ingreso", nullable = false, length = 10)
    private TipoIngreso tipoIngreso;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 3)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pago")
    private Pago pago;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    @Column(name = "clave_idempotencia", length = 36, unique = true, updatable = false)
    private String claveIdempotencia;

    @Column(name = "hash_solicitud", length = 64, updatable = false,
            columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String hashSolicitud;

    public Ingreso() {
    }

    @PrePersist
    protected void alCrear() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }

    public Long getIdIngreso() {
        return idIngreso;
    }

    public Socio getSocio() {
        return socio;
    }

    public void setSocio(Socio socio) {
        this.socio = socio;
    }

    public ContactoPromocional getContacto() {
        return contacto;
    }

    public void setContacto(ContactoPromocional contacto) {
        this.contacto = contacto;
    }

    public Ingreso getPaseOrigen() { return paseOrigen; }

    public void setPaseOrigen(Ingreso paseOrigen) { this.paseOrigen = paseOrigen; }

    public TipoIngreso getTipoIngreso() {
        return tipoIngreso;
    }

    public void setTipoIngreso(TipoIngreso tipoIngreso) {
        this.tipoIngreso = tipoIngreso;
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

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getClaveIdempotencia() { return claveIdempotencia; }

    public void setClaveIdempotencia(String claveIdempotencia) { this.claveIdempotencia = claveIdempotencia; }
    public String getHashSolicitud() { return hashSolicitud; }
    public void setHashSolicitud(String hashSolicitud) { this.hashSolicitud = hashSolicitud; }
}
