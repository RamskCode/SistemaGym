package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.UUID;

/** A durable reservation. This infrastructure deliberately cannot finalize it. */
@Entity
@Table(name = "operaciones_pago_idempotentes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_operaciones_pago_usuario_clave",
                columnNames = {"id_usuario", "clave_idempotencia"})
})
public class OperacionPagoIdempotente {

    @Id
    @Column(name = "id_operacion", length = 36, nullable = false, updatable = false,
            columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String idOperacion;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false,
            foreignKey = @ForeignKey(name = "fk_operaciones_pago_usuario"))
    private Usuario usuario;

    @NotNull
    @Column(name = "clave_idempotencia", length = 36, nullable = false, updatable = false,
            columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String claveIdempotencia;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", length = 24, nullable = false, updatable = false)
    private TipoOperacionPagoIdempotente tipoOperacion;

    @NotNull
    @Pattern(regexp = "[a-f0-9]{64}")
    @Column(name = "hash_solicitud", length = 64, nullable = false, updatable = false,
            columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String hashSolicitud;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 10, nullable = false)
    private EstadoOperacionPagoIdempotente estado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false, precision = 6)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false, precision = 6)
    private LocalDateTime fechaActualizacion;

    @Column(name = "id_membresia_resultado")
    private Long idMembresiaResultado;

    @Column(name = "id_pago_resultado")
    private Long idPagoResultado;

    @Column(name = "id_aplicacion_resultado")
    private Long idAplicacionResultado;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "resultado_payload")
    private String resultadoPayload;

    @Column(name = "rechazo_tipo", length = 40)
    private String rechazoTipo;

    @Column(name = "rechazo_mensaje", length = 1000)
    private String rechazoMensaje;

    protected OperacionPagoIdempotente() {
    }

    private OperacionPagoIdempotente(
            Usuario usuario,
            UUID claveIdempotencia,
            TipoOperacionPagoIdempotente tipoOperacion,
            String hashSolicitud) {
        this.idOperacion = UUID.randomUUID().toString();
        this.usuario = usuario;
        this.claveIdempotencia = claveIdempotencia.toString();
        this.tipoOperacion = tipoOperacion;
        this.hashSolicitud = hashSolicitud;
        this.estado = EstadoOperacionPagoIdempotente.PENDING;
    }

    public static OperacionPagoIdempotente reservar(
            Usuario usuario,
            UUID claveIdempotencia,
            TipoOperacionPagoIdempotente tipoOperacion,
            String hashSolicitud) {
        return new OperacionPagoIdempotente(
                usuario, claveIdempotencia, tipoOperacion, hashSolicitud);
    }

    @PrePersist
    protected void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) fechaCreacion = ahora;
        if (fechaActualizacion == null) fechaActualizacion = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        fechaActualizacion = LocalDateTime.now();
    }

    public String getIdOperacion() { return idOperacion; }
    public Usuario getUsuario() { return usuario; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public TipoOperacionPagoIdempotente getTipoOperacion() { return tipoOperacion; }
    public String getHashSolicitud() { return hashSolicitud; }
    public EstadoOperacionPagoIdempotente getEstado() { return estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public Long getIdMembresiaResultado() { return idMembresiaResultado; }
    public Long getIdPagoResultado() { return idPagoResultado; }
    public Long getIdAplicacionResultado() { return idAplicacionResultado; }
    public String getResultadoPayload() { return resultadoPayload; }
    public String getRechazoTipo() { return rechazoTipo; }
    public String getRechazoMensaje() { return rechazoMensaje; }

    public void confirmar(Long idMembresia, Long idPago, Long idAplicacion, String resultadoPayload) {
        this.estado = EstadoOperacionPagoIdempotente.CONFIRMED;
        this.idMembresiaResultado = idMembresia;
        this.idPagoResultado = idPago;
        this.idAplicacionResultado = idAplicacion;
        this.resultadoPayload = resultadoPayload;
        this.rechazoTipo = null;
        this.rechazoMensaje = null;
    }

    public void rechazar(String tipo, String mensaje) {
        this.estado = EstadoOperacionPagoIdempotente.REJECTED;
        this.rechazoTipo = tipo;
        this.rechazoMensaje = mensaje;
    }
}
