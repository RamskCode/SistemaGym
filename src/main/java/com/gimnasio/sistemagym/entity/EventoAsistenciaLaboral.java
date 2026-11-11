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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "eventos_asistencia_laboral", indexes = {
        @Index(name = "idx_eventos_asistencia_fecha",
                columnList = "id_asistencia, fecha_hora_utc")
})
public class EventoAsistenciaLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asistencia", nullable = false,
            foreignKey = @ForeignKey(name = "fk_eventos_asistencia_laboral_asistencia"))
    private AsistenciaLaboral asistencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 10)
    private TipoEventoAsistenciaLaboral tipoEvento;

    @Column(name = "fecha_hora_utc", nullable = false, precision = 6)
    private LocalDateTime fechaHoraUtc;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_registrador", nullable = false,
            foreignKey = @ForeignKey(name = "fk_eventos_asistencia_laboral_registrador"))
    private Usuario usuarioRegistrador;

    public EventoAsistenciaLaboral() {
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public AsistenciaLaboral getAsistencia() {
        return asistencia;
    }

    public void setAsistencia(AsistenciaLaboral asistencia) {
        this.asistencia = asistencia;
    }

    public TipoEventoAsistenciaLaboral getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipoEventoAsistenciaLaboral tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public LocalDateTime getFechaHoraUtc() {
        return fechaHoraUtc;
    }

    public void setFechaHoraUtc(Instant instante) {
        if (instante == null) {
            throw new IllegalArgumentException("El instante del evento es obligatorio.");
        }
        this.fechaHoraUtc = LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    public Usuario getUsuarioRegistrador() {
        return usuarioRegistrador;
    }

    public void setUsuarioRegistrador(Usuario usuarioRegistrador) {
        this.usuarioRegistrador = usuarioRegistrador;
    }
}
