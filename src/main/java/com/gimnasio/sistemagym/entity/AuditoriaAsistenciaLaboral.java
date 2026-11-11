package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Immutable application-level audit record for future correction/annulment operations. */
@Entity
@Table(name = "auditorias_asistencia_laboral")
public class AuditoriaAsistenciaLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asistencia", nullable = false,
            foreignKey = @ForeignKey(name = "fk_auditoria_asistencia_laboral_asistencia"))
    private AsistenciaLaboral asistencia;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_actor", nullable = false,
            foreignKey = @ForeignKey(name = "fk_auditoria_asistencia_laboral_actor"))
    private Usuario actor;

    @NotNull
    @Column(name = "fecha_hora_utc", nullable = false, updatable = false, precision = 6)
    private LocalDateTime fechaHoraUtc;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, length = 12, updatable = false)
    private TipoOperacionAuditoriaAsistenciaLaboral tipoOperacion;

    @NotBlank
    @Size(max = 500)
    @Column(name = "motivo", nullable = false, length = 500, updatable = false)
    private String motivo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 10, updatable = false)
    private EstadoAsistenciaLaboral estadoAnterior;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 10, updatable = false)
    private EstadoAsistenciaLaboral estadoNuevo;

    @NotNull
    @Column(name = "fecha_entrada_anterior_utc", nullable = false,
            updatable = false, precision = 6)
    private LocalDateTime fechaEntradaAnteriorUtc;

    @Column(name = "fecha_salida_anterior_utc", updatable = false, precision = 6)
    private LocalDateTime fechaSalidaAnteriorUtc;

    @NotNull
    @Column(name = "fecha_entrada_nueva_utc", nullable = false,
            updatable = false, precision = 6)
    private LocalDateTime fechaEntradaNuevaUtc;

    @Column(name = "fecha_salida_nueva_utc", updatable = false, precision = 6)
    private LocalDateTime fechaSalidaNuevaUtc;

    protected AuditoriaAsistenciaLaboral() {
    }

    public static AuditoriaAsistenciaLaboral crear(
            AsistenciaLaboral asistencia,
            Usuario actor,
            Instant fechaHoraUtc,
            TipoOperacionAuditoriaAsistenciaLaboral tipoOperacion,
            String motivo,
            EstadoAsistenciaLaboral estadoAnterior,
            EstadoAsistenciaLaboral estadoNuevo,
            LocalDateTime fechaEntradaAnteriorUtc,
            LocalDateTime fechaSalidaAnteriorUtc,
            LocalDateTime fechaEntradaNuevaUtc,
            LocalDateTime fechaSalidaNuevaUtc) {
        AuditoriaAsistenciaLaboral auditoria = new AuditoriaAsistenciaLaboral();
        auditoria.asistencia = asistencia;
        auditoria.actor = actor;
        auditoria.fechaHoraUtc = LocalDateTime.ofInstant(fechaHoraUtc, ZoneOffset.UTC);
        auditoria.tipoOperacion = tipoOperacion;
        auditoria.motivo = motivo;
        auditoria.estadoAnterior = estadoAnterior;
        auditoria.estadoNuevo = estadoNuevo;
        auditoria.fechaEntradaAnteriorUtc = fechaEntradaAnteriorUtc;
        auditoria.fechaSalidaAnteriorUtc = fechaSalidaAnteriorUtc;
        auditoria.fechaEntradaNuevaUtc = fechaEntradaNuevaUtc;
        auditoria.fechaSalidaNuevaUtc = fechaSalidaNuevaUtc;
        return auditoria;
    }

    @PreUpdate
    @PreRemove
    private void impedirMutacion() {
        throw new IllegalStateException("La auditoría de asistencia es inmutable.");
    }

    public Long getIdAuditoria() { return idAuditoria; }
    public AsistenciaLaboral getAsistencia() { return asistencia; }
    public Usuario getActor() { return actor; }
    public LocalDateTime getFechaHoraUtc() { return fechaHoraUtc; }
    public TipoOperacionAuditoriaAsistenciaLaboral getTipoOperacion() { return tipoOperacion; }
    public String getMotivo() { return motivo; }
    public EstadoAsistenciaLaboral getEstadoAnterior() { return estadoAnterior; }
    public EstadoAsistenciaLaboral getEstadoNuevo() { return estadoNuevo; }
    public LocalDateTime getFechaEntradaAnteriorUtc() { return fechaEntradaAnteriorUtc; }
    public LocalDateTime getFechaSalidaAnteriorUtc() { return fechaSalidaAnteriorUtc; }
    public LocalDateTime getFechaEntradaNuevaUtc() { return fechaEntradaNuevaUtc; }
    public LocalDateTime getFechaSalidaNuevaUtc() { return fechaSalidaNuevaUtc; }
}
