package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** UTC values are stored as UTC-local DATETIME(6), never in the JVM default zone. */
@Entity
@Table(name = "asistencias_laborales", indexes = {
        @Index(name = "idx_asistencias_laborales_usuario_entrada",
                columnList = "id_usuario, fecha_entrada_utc"),
        @Index(name = "idx_asistencias_laborales_usuario_fecha",
                columnList = "id_usuario, fecha_laboral"),
        @Index(name = "idx_asistencias_laborales_usuario_estado_entrada",
                columnList = "id_usuario, estado, fecha_entrada_utc, id_asistencia")
})
@Check(name = "chk_asistencias_laborales_salida_posterior",
        constraints = "fecha_salida_utc IS NULL OR fecha_salida_utc > fecha_entrada_utc")
@Check(name = "chk_asistencias_laborales_registrador_salida",
        constraints = "(fecha_salida_utc IS NULL AND id_registrado_salida IS NULL) "
                + "OR (fecha_salida_utc IS NOT NULL AND id_registrado_salida IS NOT NULL)")
public class AsistenciaLaboral {

    private static final ZoneId ZONA_LABORAL = ZoneId.of("America/Lima");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asistencia")
    private Long idAsistencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_asistencias_laborales_usuario"))
    private Usuario usuario;

    @Column(name = "fecha_entrada_utc", nullable = false, precision = 6)
    private LocalDateTime fechaEntradaUtc;

    @Column(name = "fecha_salida_utc", precision = 6)
    private LocalDateTime fechaSalidaUtc;

    @Column(name = "fecha_laboral", nullable = false)
    private LocalDate fechaLaboral;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoAsistenciaLaboral estado = EstadoAsistenciaLaboral.ACTIVA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_registrado_entrada", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(
                    name = "fk_asistencias_laborales_registrador_entrada"))
    private Usuario registradoEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_registrado_salida",
            foreignKey = @jakarta.persistence.ForeignKey(
                    name = "fk_asistencias_laborales_registrador_salida"))
    private Usuario registradoSalida;

    public AsistenciaLaboral() {
    }

    public Long getIdAsistencia() {
        return idAsistencia;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getFechaEntradaUtc() {
        return fechaEntradaUtc;
    }

    public void setFechaEntradaUtc(Instant instante) {
        if (instante == null) {
            throw new IllegalArgumentException("El instante de entrada es obligatorio.");
        }
        this.fechaEntradaUtc = LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
        this.fechaLaboral = instante.atZone(ZONA_LABORAL).toLocalDate();
    }

    public LocalDateTime getFechaSalidaUtc() {
        return fechaSalidaUtc;
    }

    public void setFechaSalidaUtc(Instant instante) {
        this.fechaSalidaUtc = instante == null
                ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    public LocalDate getFechaLaboral() {
        return fechaLaboral;
    }

    public EstadoAsistenciaLaboral getEstado() {
        return estado;
    }

    public void setEstado(EstadoAsistenciaLaboral estado) {
        if (estado == null) {
            throw new IllegalArgumentException("El estado de la asistencia es obligatorio.");
        }
        this.estado = estado;
    }

    public Usuario getRegistradoEntrada() {
        return registradoEntrada;
    }

    public void setRegistradoEntrada(Usuario registradoEntrada) {
        this.registradoEntrada = registradoEntrada;
    }

    public Usuario getRegistradoSalida() {
        return registradoSalida;
    }

    public void setRegistradoSalida(Usuario registradoSalida) {
        this.registradoSalida = registradoSalida;
    }
}
