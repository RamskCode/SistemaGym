package com.gimnasio.sistemagym.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invitaciones_usuario", uniqueConstraints =
        @UniqueConstraint(name = "uk_invitaciones_usuario_token", columnNames = "token_hash"),
        indexes = @Index(name = "ix_invitaciones_usuario_pendiente", columnList = "id_usuario, fecha_expiracion"))
public class InvitacionUsuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invitacion")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "fecha_uso") private LocalDateTime fechaUso;
    @Column(name = "fecha_invalidacion") private LocalDateTime fechaInvalidacion;

    protected InvitacionUsuario() { }

    public InvitacionUsuario(Usuario usuario, String tokenHash, LocalDateTime fechaCreacion,
            LocalDateTime fechaExpiracion) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.fechaCreacion = fechaCreacion;
        this.fechaExpiracion = fechaExpiracion;
    }

    public Usuario getUsuario() { return usuario; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public LocalDateTime getFechaUso() { return fechaUso; }
    public LocalDateTime getFechaInvalidacion() { return fechaInvalidacion; }
    public void setFechaUso(LocalDateTime value) { fechaUso = value; }
    public void setFechaInvalidacion(LocalDateTime value) { fechaInvalidacion = value; }
}
