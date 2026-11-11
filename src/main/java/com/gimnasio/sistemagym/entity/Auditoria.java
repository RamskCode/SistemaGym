package com.gimnasio.sistemagym.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "accion", nullable = false, length = 80)
    private String accion;

    @Column(name = "entidad", nullable = false, length = 80)
    private String entidad;

    @Column(name = "id_registro", length = 80)
    private String idRegistro;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle", columnDefinition = "json")
    private Map<String, String> detalle;

    @Generated(event = EventType.INSERT)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "fecha_hora", nullable = false, insertable = false,
            updatable = false)
    private LocalDateTime fechaHora;

    protected Auditoria() {
    }

    public Auditoria(Long idUsuario, String accion, String entidad,
            String idRegistro, Map<String, String> detalle) {
        this.idUsuario = idUsuario;
        this.accion = accion;
        this.entidad = entidad;
        this.idRegistro = idRegistro;
        this.detalle = detalle;
    }

    public Long getIdAuditoria() {
        return idAuditoria;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public String getIdRegistro() {
        return idRegistro;
    }

    public Map<String, String> getDetalle() {
        return detalle;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
